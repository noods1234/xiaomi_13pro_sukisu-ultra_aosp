package com.oiw.camera.capture

/**
 * Mirrors configs/schema/capture_profile.schema.json. Every field here must round-trip through
 * Gson without custom adapters so the on-disk JSON stays hand-editable (docs/CINEMA_FEATURES.md #Capture Profiles).
 */
data class CaptureProfile(
    val id: String,
    val displayName: String,
    val resolution: Resolution,
    val frameRateFps: Int,
    val shutter: ShutterStrategy,
    val isoMin: Int,
    val isoMax: Int,
    val whiteBalanceKelvin: Int,
    val tint: Int = 0,
    val focusMode: String, // "manual" | "continuous_locked_on_start"
    val codec: String, // "hevc" | "hevc10" | "avc"
    val bitrateBps: Long,
    val bitDepth: Int = 8,
    val colorProfile: String, // "standard" | "flat_approximation" | "vendor_log_if_available"
    val lutId: String? = null,
    val stabilization: String = "off", // "off" | "eis" | "ois_if_available"
    val audioSource: String = "internal_mic",
    val storageTarget: String, // "internal" | "external_ssd"
    val thermalProfileId: String,
    val monitoringOverlays: List<String> = emptyList(),
    val lensMetadataDefaults: LensMetadataDefaults? = null,
    val buttonMappingId: String = "default",
) {
    data class Resolution(val width: Int, val height: Int)

    data class ShutterStrategy(
        val mode: String, // "angle" | "speed_fraction"
        val shutterAngleDegrees: Double? = null,
        val shutterSpeedDenominator: Int? = null,
    ) {
        /** Returns the exposure time in nanoseconds for a given frame rate, per docs/CAMERA_PIPELINE.md #3. */
        fun exposureTimeNanos(frameRateFps: Int): Long {
            val frameDurationNanos = 1_000_000_000L / frameRateFps
            return when (mode) {
                "angle" -> {
                    val angle = shutterAngleDegrees
                        ?: error("shutterAngleDegrees required when mode == angle")
                    (frameDurationNanos * (angle / 360.0)).toLong()
                }
                "speed_fraction" -> {
                    val denom = shutterSpeedDenominator
                        ?: error("shutterSpeedDenominator required when mode == speed_fraction")
                    1_000_000_000L / denom
                }
                else -> error("Unknown shutter mode: $mode")
            }
        }
    }

    data class LensMetadataDefaults(
        val lensName: String? = null,
        val focalLengthMm: Double? = null,
        val aperture: String? = null,
        val adapter: String? = null,
        val speedBoosterFactor: Double? = null,
        val anamorphicSqueeze: Double? = null,
        val ndValue: String? = null,
    )
}
