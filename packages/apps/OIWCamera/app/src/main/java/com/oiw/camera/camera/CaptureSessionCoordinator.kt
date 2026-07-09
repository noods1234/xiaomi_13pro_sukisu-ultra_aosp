package com.oiw.camera.camera

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.view.Surface
import com.oiw.camera.capture.CaptureProfile
import com.oiw.camera.overlay.LumaFrame
import com.oiw.camera.record.AudioCapture
import com.oiw.camera.record.Recorder
import java.io.File
import java.util.concurrent.Executor

/**
 * Wires the full capture graph for one recording session (the piece CameraActivity's skeleton
 * deferred): preview SurfaceTexture + analysis ImageReader (overlays) + encoder input Surface,
 * builds the validated 3-target session via CameraController, starts the repeating manual request,
 * and coordinates Recorder + AudioCapture lifecycles. Pure wiring — all capability decisions stay
 * in CameraController, all file/muxer logic in Recorder (docs/ARCHITECTURE.md #4.1).
 */
class CaptureSessionCoordinator(
    private val controller: CameraController,
    private val executor: Executor,
    private val analysisHandler: Handler,
    private val listener: Listener,
) {
    interface Listener {
        fun onLumaFrame(luma: LumaFrame.Luma)
        fun onRecordingStateChanged(recording: Boolean)
        fun onAudioMeters(peakDbfs: Double, rmsDbfs: Double, clippedSamples: Int)
        fun onDroppedFrames(totalDropped: Int)
        fun onCaptureResultSample(exposureNanos: Long?, isoSensitivity: Int?)
        fun onError(message: String, cause: Throwable? = null)
    }

    private var analysisReader: ImageReader? = null
    private var recorder: Recorder? = null
    private var audio: AudioCapture? = null
    private var session: CameraCaptureSession? = null
    private var activeProfile: CaptureProfile? = null
    private val metadataWriter = com.oiw.camera.metadata.MetadataWriter()
    @Volatile private var droppedTotal = 0
    private var lastResultSampleNanos = 0L
    @Volatile var isRecording = false; private set

    /**
     * Builds session + starts preview with manual controls from [profile]. Recording starts
     * separately via [startRecording] so the operator previews before rolling.
     */
    suspend fun startPreview(
        profile: CaptureProfile,
        previewTexture: SurfaceTexture,
        outputDir: File,
        clipBaseName: String,
    ): Boolean {
        previewTexture.setDefaultBufferSize(profile.resolution.width, profile.resolution.height)
        val previewSurface = Surface(previewTexture)

        // Analysis at reduced resolution: overlays don't need full 4K luma (docs/CINEMA_FEATURES.md #2).
        val reader = ImageReader.newInstance(
            profile.resolution.width / 4, profile.resolution.height / 4,
            ImageFormat.YUV_420_888, 2,
        )
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                listener.onLumaFrame(LumaFrame.extract(image))
            } finally {
                image.close()
            }
        }, analysisHandler)
        analysisReader = reader

        val rec = Recorder(outputDir, clipBaseName, profile, recorderListener)
        rec.audioEnabled = profile.audioSource != "external_recorder_sync_only"
        recorder = rec
        val encoderSurface = rec.createEncoderInputSurface()

        val s = controller.createSession(previewSurface, encoderSurface, reader.surface, executor)
        if (s == null) {
            listener.onError("Capture session could not be configured for profile '${profile.id}'.")
            return false
        }
        session = s

        activeProfile = profile
        val request = controller.buildManualRequest(
            profile, listOf(previewSurface, encoderSurface, reader.surface),
        ) ?: return false
        // Sample the echoed manual-control values ~1/s (stub-audit fix: previously never wired) —
        // this is how the UI proves the HAL honored the request (docs/TEST_PLAN.md #4 round-trip).
        s.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                req: CaptureRequest,
                result: android.hardware.camera2.TotalCaptureResult,
            ) {
                val now = System.nanoTime()
                if (now - lastResultSampleNanos < 1_000_000_000L) return
                lastResultSampleNanos = now
                listener.onCaptureResultSample(
                    result.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME),
                    result.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY),
                )
            }
        }, analysisHandler)
        return true
    }

    fun startRecording() {
        val rec = recorder ?: return
        if (isRecording) return
        rec.start()
        if (rec.audioEnabled) {
            audio = AudioCapture(listener = audioListener).also { it.start() }
        }
        isRecording = true
        listener.onRecordingStateChanged(true)
    }

    /** Safe stop: audio first (stops feeding muxer), then recorder finalizes the segment. */
    fun stopRecording() {
        if (!isRecording) return
        audio?.stop(); audio = null
        recorder?.stop()
        isRecording = false
        listener.onRecordingStateChanged(false)
    }

    fun release() {
        stopRecording()
        session?.close(); session = null
        analysisReader?.close(); analysisReader = null
        recorder = null
    }

    private val recorderListener = object : Recorder.Listener {
        override fun onSegmentFinalized(file: File, segmentIndex: Int) {
            // Sidecar per segment, written atomically (stub-audit fix: previously unwired).
            val profile = activeProfile ?: return
            val metadata = metadataWriter.buildFromProfile(
                profile = profile,
                cameraId = "0",
                romBuild = android.os.Build.DISPLAY,
                kernelBuild = System.getProperty("os.version") ?: "unknown",
                deviceModel = android.os.Build.MODEL,
            ).copy(droppedFrames = droppedTotal)
            try {
                metadataWriter.writeAtomically(file, metadata)
            } catch (e: Exception) {
                listener.onError("Sidecar write failed for ${file.name} — clip is intact, metadata is not.", e)
            }
        }
        override fun onDroppedFrame(totalDropped: Int) {
            droppedTotal = totalDropped
            listener.onDroppedFrames(totalDropped)
        }
        override fun onError(message: String, cause: Throwable?) = listener.onError(message, cause)
    }

    private val audioListener = object : AudioCapture.Listener {
        override fun onMeters(peakDbfs: Double, rmsDbfs: Double, clippedSamples: Int) =
            listener.onAudioMeters(peakDbfs, rmsDbfs, clippedSamples)
        override fun onEncodedAudio(codec: android.media.MediaCodec, outputIndex: Int, info: android.media.MediaCodec.BufferInfo) {
            recorder?.writeAudioSample(codec, outputIndex, info)
        }
        override fun onAudioFormatReady(format: android.media.MediaFormat) {
            recorder?.onAudioFormatReady(format)
        }
        override fun onError(message: String, cause: Throwable?) = listener.onError(message, cause)
    }
}
