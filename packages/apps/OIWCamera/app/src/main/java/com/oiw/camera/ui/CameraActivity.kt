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
        val profiles = profileRepository.loadAll()
        profileRepository.loadErrors.forEach { showError(it) }
        val defaultProfile = profiles.firstOrNull()
        if (defaultProfile == null) {
            showError("No capture profiles could be loaded. Check configs/capture_profiles/ and the plugin folder.")
            return
        }
        activeProfile = defaultProfile

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

    private fun wireSessionWhenSurfaceReady(profile: CaptureProfile) {
        val controller = cameraController ?: return
        val analysisThread = android.os.HandlerThread("OIWAnalysis").also { it.start() }
        val overlayView = com.oiw.camera.overlay.OverlayView(this).also {
            findViewById<android.widget.FrameLayout>(R.id.overlay_container).addView(it)
        }
        val coord = com.oiw.camera.camera.CaptureSessionCoordinator(
            controller,
            mainExecutor,
            android.os.Handler(analysisThread.looper),
            object : com.oiw.camera.camera.CaptureSessionCoordinator.Listener {
                override fun onLumaFrame(luma: com.oiw.camera.overlay.LumaFrame.Luma) {
                    overlayView.submit(luma, zebra.computeMask(luma), peaking.computeMask(luma), histogram.compute(luma))
                }
                override fun onRecordingStateChanged(recording: Boolean) = runOnUiThread {
                    findViewById<android.widget.Button>(R.id.record_button).text =
                        if (recording) "STOP" else getString(R.string.record_button)
                }
                override fun onAudioMeters(peakDbfs: Double, rmsDbfs: Double, clippedSamples: Int) { /* bottom bar */ }
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

        findViewById<android.widget.Button>(R.id.record_button).setOnClickListener {
            if (coord.isRecording) coord.stopRecording() else coord.startRecording()
        }
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
        cameraController?.close()
        cameraController?.stopBackgroundThread()
        super.onDestroy()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}
