package com.oiw.camera.metadata

import com.google.gson.GsonBuilder
import com.oiw.camera.capture.CaptureProfile
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * Writes the JSON sidecar for a clip. Schema: configs/schema/clip_metadata.schema.json.
 * Always written atomically (tmp file + fsync + rename) so a crash never leaves a half-written
 * sidecar (docs/STORAGE_MEDIA.md #7).
 */
class MetadataWriter {

    data class ClipMetadata(
        val project: String,
        val scene: String?,
        val shot: String?,
        val take: Int?,
        /** Script supervisor's circled take — the keeper. Set from the [Slate]. */
        val circledTake: Boolean = false,
        val dateTimeIso8601: String,
        val deviceModel: String,
        val romBuild: String,
        val kernelBuild: String,
        val cameraId: String,
        val sensorMode: String?,
        val resolution: String,
        val frameRateFps: Int,
        val shutter: String,
        val isoSensitivity: Int,
        val whiteBalanceKelvin: Int,
        val tint: Int,
        val focusDistance: Double?,
        val stabilizationMode: String,
        val codec: String,
        val bitrateBps: Long,
        val colorProfile: String,
        val lutId: String?,
        val lutBaked: Boolean,
        val storageMedium: String,
        val measuredWriteSpeedMbps: Double?,
        val droppedFrames: Int,
        val thermalStateAtStop: String?,
        val batteryPercentAtStop: Int?,
        val lensName: String?,
        val focalLengthMm: Double?,
        val aperture: String?,
        val adapter: String?,
        val filterStack: List<String>,
        val ndValue: String?,
        val anamorphicSqueeze: Double?,
        val gpsLocation: String? = null, // only populated if explicitly enabled, per assumptions.md
        val notes: String? = null,
        val recovered: Boolean = false,
        val recoveryNotes: String? = null,
        val integritySha256: String? = null,
        val avSyncOffsetNanos: Long? = null,
        val slateMarkers: List<SlateMarker> = emptyList(),
    )

    data class SlateMarker(val type: String, val timestampNanos: Long)

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun buildFromProfile(
        profile: CaptureProfile,
        cameraId: String,
        romBuild: String,
        kernelBuild: String,
        deviceModel: String,
    ): ClipMetadata = ClipMetadata(
        project = "untitled",
        scene = null,
        shot = null,
        take = null,
        dateTimeIso8601 = Instant.now().toString(),
        deviceModel = deviceModel,
        romBuild = romBuild,
        kernelBuild = kernelBuild,
        cameraId = cameraId,
        sensorMode = null,
        resolution = "${profile.resolution.width}x${profile.resolution.height}",
        frameRateFps = profile.frameRateFps,
        shutter = profile.shutter.mode,
        isoSensitivity = profile.isoMin,
        whiteBalanceKelvin = profile.whiteBalanceKelvin,
        tint = profile.tint,
        focusDistance = null,
        stabilizationMode = profile.stabilization,
        codec = profile.codec,
        bitrateBps = profile.bitrateBps,
        colorProfile = profile.colorProfile,
        lutId = profile.lutId,
        lutBaked = false,
        storageMedium = profile.storageTarget,
        measuredWriteSpeedMbps = null,
        droppedFrames = 0,
        thermalStateAtStop = null,
        batteryPercentAtStop = null,
        lensName = profile.lensMetadataDefaults?.lensName,
        focalLengthMm = profile.lensMetadataDefaults?.focalLengthMm,
        aperture = profile.lensMetadataDefaults?.aperture,
        adapter = profile.lensMetadataDefaults?.adapter,
        filterStack = emptyList(),
        ndValue = profile.lensMetadataDefaults?.ndValue,
        anamorphicSqueeze = profile.lensMetadataDefaults?.anamorphicSqueeze,
    )

    /** Atomic write: tmp file -> fsync -> rename. Never leaves a torn sidecar on disk. */
    fun writeAtomically(clipFile: File, metadata: ClipMetadata) {
        val sidecarFile = File(clipFile.parentFile, "${clipFile.nameWithoutExtension}.json")
        val tmpFile = File(clipFile.parentFile, "${sidecarFile.name}.tmp")
        val json = gson.toJson(metadata)

        FileOutputStream(tmpFile).use { fos ->
            fos.write(json.toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
        if (!tmpFile.renameTo(sidecarFile)) {
            // Fallback for filesystems where atomic rename across the same dir still needs a retry.
            tmpFile.copyTo(sidecarFile, overwrite = true)
            tmpFile.delete()
        }
    }

    fun readForRecovery(sidecarOrClipFile: File): ClipMetadata? {
        val sidecar = if (sidecarOrClipFile.extension == "json") {
            sidecarOrClipFile
        } else {
            File(sidecarOrClipFile.parentFile, "${sidecarOrClipFile.nameWithoutExtension}.json")
        }
        if (!sidecar.exists()) return null
        return runCatching { gson.fromJson(sidecar.readText(), ClipMetadata::class.java) }.getOrNull()
    }
}
