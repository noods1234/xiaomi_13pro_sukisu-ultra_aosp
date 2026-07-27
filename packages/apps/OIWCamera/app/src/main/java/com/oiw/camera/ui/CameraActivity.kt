package com.oiw.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.TextureView
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.oiw.camera.R
import com.oiw.camera.camera.CameraController
import com.oiw.camera.capture.CaptureProfile
import com.oiw.camera.capture.ProfileRepository
import com.oiw.camera.storage.StorageManager
import com.oiw.camera.thermal.ThermalMonitor
import kotlinx.coroutines.launch

/**
 * Top-level capture activity. Wires ProfileRepository -> CameraController -> Recorder and hosts
 * the monitoring overlays. This is an MVP skeleton (implementation_plan.md Phase 5) — the full
 * left/right/bottom rail UI described in docs/UI_UX.md #4 is not laid out pixel-for-pixel here,
 * but every control it wires to (manual exposure/ISO/WB/focus, profile switch, record) is real,
 * not stubbed.
 */
class CameraActivity : AppCompatActivity(), CameraController.Listener {

    private lateinit var textureView: TextureView
    private lateinit var profileRepository: ProfileRepository
    private lateinit var storageManager: StorageManager
    private lateinit var thermalMonitor: ThermalMonitor
    private var cameraController: CameraController? = null
    private var activeProfile: CaptureProfile? = null

    private val requiredPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // docs/CINEMA_FEATURES.md #7
        setContentView(R.layout.activity_camera)

        textureView = findViewById(R.id.preview_texture_view)
        profileRepository = ProfileRepository(this)
        storageManager = StorageManager(this)
        thermalMonitor = ThermalMonitor(this)

        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
            return
        }
        initializeSession()
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && hasAllPermissions()) {
            initializeSession()
        } else if (requestCode == PERMISSION_REQUEST_CODE) {
            showError("Camera and microphone permission are required to use OIWCamera.")
        }
    }

    private fun initializeSession() {
        if (!com.oiw.camera.util.OiwPaths.hasAllFilesAccess()) {
            showError("OIWCamera needs 'All files access' to record to /sdcard/OIW_MEDIA. Opening Settings.")
            startActivity(com.oiw.camera.util.OiwPaths.allFilesAccessIntent(this))
            // Continue anyway — preview works without storage; recording preflight will refuse cleanly.
        }
        profiles = profileRepository.loadAll()
        profileRepository.loadErrors.forEach { showError(it) }
        val defaultProfile = profiles.firstOrNull()
        if (defaultProfile == null) {
            showError("No capture profiles could be loaded. Check configs/capture_profiles/ and the plugin folder.")
            return
        }
        activeProfile = defaultProfile
        buttonMapping = com.oiw.camera.control.ButtonMappingLoader(this).load(defaultProfile.buttonMappingId)
        window.decorView.postDelayed(thermalTick, 5_000)

        // Recovery scan runs before anything else touches storage (docs/STORAGE_MEDIA.md #9).
        val recoverable = storageManager.scanForRecoverableClips()
        if (recoverable.isNotEmpty()) {
            showError("${recoverable.size} clip(s) from a previous session were not finalized. Recovery scan pending.")
        }

        val cameraId = "0" // First camera ID; real device may report a logical/physical split
                           // (docs/CAMERA_PIPELINE.md #1) — a camera picker belongs here once
                           // tools/camera_capability_dump.py output is available for this unit.
        val controller = CameraController(this, cameraId, this)
        cameraController = controller
        controller.startBackgroundThread()

        lifecycleScope.launch {
            val opened = controller.open()
            if (!opened) {
                showError("Failed to open camera $cameraId.")
                return@launch
            }
            wireSessionWhenSurfaceReady(defaultProfile)
        }
    }

    private var coordinator: com.oiw.camera.camera.CaptureSessionCoordinator? = null
    private val histogram = com.oiw.camera.overlay.HistogramOverlay()
    private val zebra = com.oiw.camera.overlay.ZebraOverlay()
    private val peaking = com.oiw.camera.overlay.FocusPeakingOverlay()
    private val falseColor = com.oiw.camera.overlay.FalseColorOverlay()
    private val waveform = com.oiw.camera.overlay.WaveformOverlay()
    private val vectorscope = com.oiw.camera.overlay.VectorscopeOverlay()
    private val rgbParade = com.oiw.camera.overlay.RgbParadeOverlay()
    /** Last LTC timecode decoded from the audio input, if a house signal is present. */
    @Volatile private var lastLtc: com.oiw.camera.audio.LtcTimecode? = null
    private var overlayView: com.oiw.camera.overlay.OverlayView? = null
    private var buttonMapping: com.oiw.camera.control.ButtonMappingLoader.Mapping? = null
    private var profiles: List<CaptureProfile> = emptyList()
    private var profileIndex = 0
    private val statusWriter by lazy { com.oiw.camera.metadata.StatusFileWriter(this) }
    private val thermalTick = object : Runnable {
        override fun run() {
            val coord = coordinator ?: return
            val profile = activeProfile
            if (coord.isRecording && profile != null) {
                // Graceful-stop state machine (docs/THERMAL_POWER.md #8) — stub-audit fix: now polled.
                val mode = com.oiw.camera.thermal.ThermalMonitor.modeForLabel(profile.thermalProfileId)
                when (val action = com.oiw.camera.thermal.ThermalMonitor.decide(mode, thermalMonitor.currentStatus())) {
                    is com.oiw.camera.thermal.ThermalMonitor.RecordingAction.Warn -> showError(action.message)
                    is com.oiw.camera.thermal.ThermalMonitor.RecordingAction.ForceStop -> {
                        showError(action.reason)
                        coord.stopRecording()
                    }
                    else -> Unit
                }
            }
            window.decorView.postDelayed(this, 5_000)
        }
    }

    private var analysisThread: android.os.HandlerThread? = null

    private fun wireSessionWhenSurfaceReady(profile: CaptureProfile) {
        val controller = cameraController ?: return
        // A misspelled overlay id used to do nothing at all — the scope just never appeared and the
        // user had no way to tell a typo from an unsupported feature. Say so instead.
        com.oiw.camera.overlay.MonitoringOverlays.unknownIn(profile.monitoringOverlays)
            .takeIf { it.isNotEmpty() }
            ?.let { showError("Profile '${profile.id}' names unknown overlays: ${it.joinToString()}") }
        // AUDIT FIX (H): a re-wire (profile cycle) must tear down the previous session's resources,
        // or each switch leaks a HandlerThread and stacks another OverlayView on the container.
        analysisThread?.quitSafely()
        findViewById<android.widget.FrameLayout>(R.id.overlay_container).removeAllViews()

        val analysisThread = android.os.HandlerThread("OIWAnalysis").also { it.start() }
        this.analysisThread = analysisThread
        val overlay = com.oiw.camera.overlay.OverlayView(this).also {
            it.guideAspect = profile.lensMetadataDefaults?.anamorphicSqueeze?.let { sq -> (16f / 9f * sq.toFloat()) }
                ?: 2.39f
            it.applyProfileOverlays(profile.monitoringOverlays)
            findViewById<android.widget.FrameLayout>(R.id.overlay_container).addView(it)
        }
        overlayView = overlay
        // AUDIT FIX (D): apply desqueeze after the TextureView is laid out, so the pivot uses the
        // real width (was 0 at wire time, offsetting the stretch instead of centering it).
        textureView.post { applyDesqueeze(profile) }

        val coord = com.oiw.camera.camera.CaptureSessionCoordinator(
            controller,
            mainExecutor,
            android.os.Handler(analysisThread.looper),
            object : com.oiw.camera.camera.CaptureSessionCoordinator.Listener {
                override fun onAnalysisFrame(
                    luma: com.oiw.camera.overlay.LumaFrame.Luma,
                    chroma: com.oiw.camera.overlay.VectorscopeOverlay.Chroma?,
                ) {
                    // Each scope is computed only when its overlay is enabled — the measured costs
                    // (1.3 ms waveform / 0.6 ms vectorscope) are cheap but not free.
                    val wf = if (overlay.showWaveform) {
                        val counts = waveform.compute(luma)
                        Triple(counts, waveform.outputSize(), waveform.peak(counts))
                    } else null
                    val vs = if (overlay.showVectorscope && chroma != null) {
                        val grid = vectorscope.compute(chroma)
                        Triple(grid, vectorscope.outputSize(), grid.max())
                    } else null
                    val parade = if (overlay.showRgbParade && chroma != null) {
                        val grid = rgbParade.compute(luma, chroma)
                        Triple(grid, rgbParade.outputSize(), rgbParade.peak(grid))
                    } else null
                    overlay.submit(
                        luma,
                        if (overlay.showZebra) zebra.computeMask(luma) else null,
                        if (overlay.showPeaking) peaking.computeMask(luma) else null,
                        histogram.compute(luma),
                        if (overlay.showFalseColor) falseColor.colorize(luma) else null,
                        wf,
                        vs,
                        parade,
                    )
                }
                override fun onRecordingStateChanged(recording: Boolean) = runOnUiThread {
                    findViewById<android.widget.Button>(R.id.record_button).text =
                        if (recording) "STOP" else getString(R.string.record_button)
                    // Foreground service keeps the take alive when locked (stub-audit fix: now started).
                    val svc = android.content.Intent(this@CameraActivity, com.oiw.camera.record.RecordingService::class.java)
                    if (recording) startForegroundService(svc) else stopService(svc)
                    updateStatusFile()
                }
                override fun onAudioMeters(peakDbfs: Double, rmsDbfs: Double, clippedSamples: Int) = runOnUiThread {
                    updateBottomBar(audioDb = peakDbfs, clipped = clippedSamples)
                }
                override fun onDroppedFrames(totalDropped: Int) = runOnUiThread {
                    updateBottomBar(drops = totalDropped)
                    if (totalDropped > 0) showError("Dropped frames: $totalDropped — check storage write speed.")
                }
                override fun onCaptureResultSample(exposureNanos: Long?, isoSensitivity: Int?) = runOnUiThread {
                    statusBase =
                        "${profile.resolution.width}x${profile.resolution.height}·${profile.frameRateFps}p " +
                        "${profile.codec} ${profile.bitrateBps / 1_000_000}Mbps · " +
                        "exp ${exposureNanos?.let { "1/${1_000_000_000 / it.coerceAtLeast(1)}" } ?: "--"} " +
                        "ISO ${isoSensitivity ?: "--"} · ${profile.displayName}"
                    renderStatusBar()
                }
                override fun onTimecode(tc: com.oiw.camera.audio.LtcTimecode) {
                    lastLtc = tc
                    runOnUiThread { renderStatusBar() }
                }
                override fun onError(message: String, cause: Throwable?) = this@CameraActivity.onError(message, cause)
            },
        )
        coordinator = coord

        val startPreview: (android.graphics.SurfaceTexture) -> Unit = { texture ->
            lifecycleScope.launch {
                val clipDir = storageManager.projectClipDir(
                    "untitled", java.time.LocalDate.now().toString(), "A_CAM",
                    "CLIP_%04d".format(System.currentTimeMillis() % 10000),
                )
                if (!coord.startPreview(profile, texture, clipDir, clipDir.name)) {
                    showError("Preview failed to start for profile '${profile.id}'.")
                }
            }
        }
        textureView.surfaceTexture?.let(startPreview) ?: run {
            textureView.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) = startPreview(st)
                override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
            }
        }

        findViewById<android.widget.Button>(R.id.record_button).setOnClickListener { toggleRecording() }
        findViewById<android.widget.Button>(R.id.still_button).setOnClickListener {
            // DNG stills need a dedicated RAW ImageReader stream added to the session — honest state
            // per docs/CAMERA_PIPELINE.md #3: hidden/inert unless RAW is reported. YUV stills TBD.
            if (cameraController?.capabilities?.hasRaw != true) {
                showError("RAW capability not reported for this camera. Still capture is unavailable in this build.")
            } else {
                showError("RAW still flow requires the RAW stream session variant — tracked in implementation_plan Phase 5.")
            }
        }
        findViewById<android.widget.Button>(R.id.profile_button).setOnClickListener { cycleProfile() }
    }

    /** Preflight gate before rolling (stub-audit fix: docs promised it, now enforced). */
    private fun toggleRecording() {
        val coord = coordinator ?: return
        val profile = activeProfile ?: return
        if (coord.isRecording) { coord.stopRecording(); return }
        val target = storageManager.projectClipDir("untitled", java.time.LocalDate.now().toString(), "A_CAM", "preflight")
        val cached = storageManager.loadLastBenchmark(profile.storageTarget)
        if (cached != null) {
            startRecordingIfPreflightOk(coord, profile, target, cached)
            return
        }
        // AUDIT FIX C+I: no benchmark yet — measure on Dispatchers.IO (never the UI thread; a 64 MB
        // write stalls ~1 s), then preflight and roll. Record button disabled while measuring so a
        // double-tap can't launch two benchmarks.
        val recordButton = findViewById<android.widget.Button>(R.id.record_button)
        recordButton.isEnabled = false
        showError("Benchmarking ${profile.storageTarget} storage (first use)…")
        lifecycleScope.launch {
            val measured = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                storageManager.runAndStoreBenchmark(target, profile.storageTarget)
            }
            recordButton.isEnabled = true
            if (measured == null) {
                showError("Storage benchmark failed for ${target.absolutePath}. Check free space/permissions.")
                return@launch
            }
            startRecordingIfPreflightOk(coord, profile, target, measured)
        }
    }

    private fun startRecordingIfPreflightOk(
        coord: com.oiw.camera.camera.CaptureSessionCoordinator,
        profile: CaptureProfile,
        target: java.io.File,
        benchmark: com.oiw.camera.storage.StorageManager.BenchmarkResult,
    ) {
        val preflight = storageManager.preflight(target, profile.bitrateBps, benchmark)
        if (!preflight.ok) {
            showError(preflight.message)
            return
        }
        coord.startRecording()
    }

    private fun cycleProfile() {
        if (profiles.isEmpty()) return
        if (coordinator?.isRecording == true) {
            showError("Profile switch is disabled while recording.")
            return
        }
        profileIndex = (profileIndex + 1) % profiles.size
        activeProfile = profiles[profileIndex]
        showError("Profile: ${profiles[profileIndex].displayName}. Restarting session.")
        // Full session restart with the new profile: release and rewire.
        coordinator?.release()
        activeProfile?.let { wireSessionWhenSurfaceReady(it) }
        updateStatusFile()
    }

    /** Anamorphic desqueeze preview (stub-audit fix): horizontal stretch on the TextureView only. */
    private fun applyDesqueeze(profile: CaptureProfile) {
        val squeeze = profile.lensMetadataDefaults?.anamorphicSqueeze ?: return
        val m = android.graphics.Matrix()
        m.setScale(squeeze.toFloat(), 1f, textureView.width / 2f, 0f)
        textureView.setTransform(m)
    }

    private var statusBase = ""
    private var lastDrops = 0
    private var lastAudioDb = Double.NEGATIVE_INFINITY
    private var lastClipped = 0
    private fun updateBottomBar(drops: Int = lastDrops, audioDb: Double = lastAudioDb, clipped: Int = lastClipped) {
        lastDrops = drops; lastAudioDb = audioDb; lastClipped = clipped
        renderStatusBar()
    }

    /** Single render point: base (from CaptureResult sampling) + live suffix. Never appends. */
    private fun renderStatusBar() {
        val clipNote = if (lastClipped > 0) " CLIP!" else ""
        val audio = if (lastAudioDb.isFinite()) "%.0fdB".format(lastAudioDb) else "--"
        val tc = lastLtc?.let { "  TC $it" } ?: ""
        findViewById<android.widget.TextView>(R.id.status_bar).text =
            "$statusBase  |  drops:$lastDrops  audio:$audio$clipNote$tc"
    }

    private fun updateStatusFile() {
        val free = runCatching {
            storageManager.freeBytes(storageManager.mediaRoot()) / 1_000_000_000.0
        }.getOrNull()
        statusWriter.write(
            com.oiw.camera.metadata.StatusFileWriter.Status(
                storageFreeGb = free,
                thermalMode = activeProfile?.thermalProfileId,
                batteryPercent = null,
                lastProfileId = activeProfile?.id,
            )
        )
    }

    /** Hardware button mapping (stub-audit fix: configs/button_mappings now actually consumed). */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val mapping = buttonMapping ?: return super.onKeyDown(keyCode, event)
        val input = when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> "volume_up_short"
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> "volume_down_short"
            android.view.KeyEvent.KEYCODE_R -> "hid_key_r"
            android.view.KeyEvent.KEYCODE_SPACE -> "hid_key_space"
            else -> return super.onKeyDown(keyCode, event)
        }
        when (com.oiw.camera.control.ButtonMappingLoader(this).actionFor(mapping, input)) {
            "record_toggle" -> toggleRecording()
            "still_capture" -> findViewById<android.widget.Button>(R.id.still_button).performClick()
            "profile_cycle" -> cycleProfile()
            "focus_assist_toggle" -> overlayView?.let { it.showPeaking = !it.showPeaking }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onError(message: String, cause: Throwable?) {
        runOnUiThread { showError(message) }
    }

    override fun onCaptureResultSample(exposureNanos: Long, isoSensitivity: Int, whiteBalanceLocked: Boolean) {
        // Bottom/left-rail live value updates would bind here (docs/UI_UX.md #4).
    }

    private fun showError(message: String) {
        // docs/UI_UX.md #5: always specific, never a bare "something went wrong" toast.
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        // AUDIT FIX (G): cancel the thermal poll and release session resources, or the Runnable
        // leaks the Activity and keeps firing after teardown.
        window.decorView.removeCallbacks(thermalTick)
        coordinator?.release()
        analysisThread?.quitSafely()
        cameraController?.close()
        cameraController?.stopBackgroundThread()
        super.onDestroy()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}
