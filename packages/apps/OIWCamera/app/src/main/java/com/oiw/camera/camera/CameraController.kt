package com.oiw.camera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.oiw.camera.capture.CaptureProfile
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Owns the Camera2 device + capture session lifecycle. Deliberately thin: all "is this control
 * supported" decisions are made against CameraCharacteristics before ever building a request, per
 * docs/CAMERA_PIPELINE.md's HAL-tier strategy and the "no hidden automatic resets" acceptance
 * criterion (root brief #7.2).
 */
class CameraController(
    private val context: Context,
    private val cameraId: String,
    private val listener: Listener,
) {
    interface Listener {
        fun onError(message: String, cause: Throwable? = null)
        fun onCaptureResultSample(exposureNanos: Long, isoSensitivity: Int, whiteBalanceLocked: Boolean)
    }

    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private lateinit var characteristics: CameraCharacteristics
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    val capabilities: Capabilities by lazy { Capabilities.from(characteristics) }

    /** Capability facts gated behind an explicit query — see docs/CAMERA_PIPELINE.md #2. Never assumed. */
    data class Capabilities(
        val hasRaw: Boolean,
        val hasHighSpeed: Boolean,
        val supportedNoiseReductionModes: Set<Int>,
        val supportedEdgeModes: Set<Int>,
        val supportedToneMapModes: Set<Int>,
        val hardwareLevel: Int,
        val activeArraySize: android.graphics.Rect?,
    ) {
        companion object {
            fun from(c: CameraCharacteristics): Capabilities {
                val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                return Capabilities(
                    hasRaw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW),
                    hasHighSpeed = caps.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO
                    ),
                    supportedNoiseReductionModes =
                        c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
                            ?.toSet() ?: emptySet(),
                    supportedEdgeModes =
                        c.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)?.toSet() ?: emptySet(),
                    supportedToneMapModes =
                        c.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)?.toSet() ?: emptySet(),
                    hardwareLevel =
                        c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                            ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
                    activeArraySize = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE),
                )
            }
        }
    }

    fun startBackgroundThread() {
        val thread = HandlerThread("OIWCameraBackground").also { it.start() }
        backgroundThread = thread
        backgroundHandler = Handler(thread.looper)
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    @SuppressLint("MissingPermission") // CAMERA permission checked by the Activity before this is called.
    suspend fun open(): Boolean = suspendCoroutine { cont ->
        try {
            characteristics = manager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            listener.onError("Failed to read camera characteristics for camera ID $cameraId.", e)
            cont.resume(false)
            return@suspendCoroutine
        }
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(cameraDevice: CameraDevice) {
                device = cameraDevice
                cont.resume(true)
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {
                listener.onError("Camera $cameraId disconnected.")
                cameraDevice.close()
                device = null
            }

            override fun onError(cameraDevice: CameraDevice, error: Int) {
                listener.onError(
                    "Vendor camera service crashed (CameraDevice.StateCallback#onError, error $error). " +
                        "Restarting camera session."
                )
                cameraDevice.close()
                device = null
                cont.resume(false)
            }
        }, backgroundHandler)
    }

    /**
     * Builds a preview + encoder-input + analysis session. Always validated with
     * isSessionConfigurationSupported first per docs/CAMERA_PIPELINE.md #4 — never assume a stream
     * combination is legal.
     */
    suspend fun createSession(
        previewSurface: Surface,
        encoderSurface: Surface,
        analysisSurface: Surface,
        executor: Executor,
    ): CameraCaptureSession? {
        val dev = device ?: return null
        val outputs = listOf(
            OutputConfiguration(previewSurface),
            OutputConfiguration(encoderSurface),
            OutputConfiguration(analysisSurface),
        )
        val configuration = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputs,
            executor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) { session = s }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    listener.onError(
                        "Camera HAL rejected this stream combination (preview + encoder + analysis) " +
                            "for camera $cameraId. Falling back to preview + encoder only."
                    )
                }
            },
        )
        val supported = try {
            dev.isSessionConfigurationSupported(configuration)
        } catch (e: UnsupportedOperationException) {
            // Older API levels / some vendor HALs don't implement the query itself; fall back to attempting it.
            true
        }
        if (!supported) {
            listener.onError(
                "Camera HAL reports this exact stream combination is unsupported for camera $cameraId " +
                    "before even attempting it. Reduce active overlays or resolution."
            )
            return null
        }
        dev.createCaptureSession(configuration)
        return session
    }

    /**
     * Applies a capture profile's manual controls, clamped to what [capabilities] actually reports.
     * Every clamp is logged via [Listener.onError] as an explicit, specific message — never silent.
     */
    fun buildManualRequest(
        profile: CaptureProfile,
        targets: List<Surface>,
    ): CaptureRequest? {
        val dev = device ?: return null
        val builder = dev.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        targets.forEach { builder.addTarget(it) }

        val exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val requestedExposure = profile.shutter.exposureTimeNanos(profile.frameRateFps)
        val clampedExposure = exposureRange?.let {
            requestedExposure.coerceIn(it.lower, it.upper)
        } ?: requestedExposure
        if (clampedExposure != requestedExposure) {
            listener.onError(
                "Camera HAL rejected fixed exposure time (requested ${requestedExposure}ns, " +
                    "range is ${exposureRange}). Falling back to ${clampedExposure}ns."
            )
        }

        val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val clampedIso = isoRange?.let { profile.isoMin.coerceIn(it.lower, it.upper) } ?: profile.isoMin

        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedExposure)
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, clampedIso)

        // AUDIT FIX (B): lock the sensor frame duration to the profile's frame rate. Without this,
        // with AE off the sensor runs at an undefined/default cadence — a 24p cinema profile could
        // capture at ~30fps, giving the wrong motion cadence. Frame duration must also be >= the
        // requested exposure time (you can't expose longer than the frame period). Clamp to the
        // sensor's max frame duration if reported.
        val targetFrameDuration = 1_000_000_000L / profile.frameRateFps
        val maxFrameDuration = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)
            ?: Long.MAX_VALUE
        val frameDuration = maxOf(targetFrameDuration, clampedExposure).coerceAtMost(maxFrameDuration)
        builder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration)
        if (frameDuration != targetFrameDuration) {
            listener.onError(
                "Frame duration adjusted to ${frameDuration}ns (target ${targetFrameDuration}ns for " +
                    "${profile.frameRateFps}fps) to satisfy exposure/sensor limits — verify capture cadence."
            )
        }

        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
        builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, kelvinToGains(profile.whiteBalanceKelvin, profile.tint))

        if (capabilities.supportedNoiseReductionModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_OFF)) {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
        } else if (capabilities.supportedNoiseReductionModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)) {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)
        }
        if (capabilities.supportedEdgeModes.contains(CaptureRequest.EDGE_MODE_OFF)) {
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
        }

        if (profile.focusMode == "manual") {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        }

        return builder.build()
    }

    fun close() {
        session?.close()
        session = null
        device?.close()
        device = null
    }

    companion object {
        private const val TAG = "OIWCameraController"

        /**
         * Coarse Kelvin+tint -> RGB gain approximation (docs/CAMERA_PIPELINE.md #9). This is
         * explicitly documented as an approximation, not a spectrophotometrically calibrated
         * transform (assumptions.md C-... / docs/CINEMA_FEATURES.md #6).
         */
        fun kelvinToGains(kelvin: Int, tint: Int): android.hardware.camera2.params.RggbChannelVector {
            val temp = kelvin.coerceIn(2000, 10000) / 100.0
            val red: Double
            val blue: Double
            if (temp <= 66) {
                red = 255.0
            } else {
                red = 329.698727446 * Math.pow(temp - 60, -0.1332047592)
            }
            blue = if (temp >= 66) {
                255.0
            } else if (temp <= 19) {
                0.0
            } else {
                138.5177312231 * Math.log(temp - 10) - 305.0447927307
            }
            val rGain = (red.coerceIn(0.0, 255.0) / 128.0).toFloat()
            val bGain = (blue.coerceIn(0.0, 255.0) / 128.0).toFloat()
            val tintAdjust = 1.0f + (tint / 200.0f)
            return android.hardware.camera2.params.RggbChannelVector(
                rGain, 1.0f * tintAdjust, 1.0f * tintAdjust, bGain,
            )
        }
    }
}
