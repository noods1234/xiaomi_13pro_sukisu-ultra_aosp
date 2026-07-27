package com.oiw.camera.metadata

import com.google.gson.Gson
import com.oiw.camera.util.OiwPaths
import java.io.File
import java.io.FileOutputStream

/**
 * The digital slate (docs/CINEMA_FEATURES.md #2, #9). Scene / shot / take plus the lens and filter
 * details that no adapted lens can report electronically.
 *
 * [MetadataWriter.ClipMetadata] has carried these fields all along — they were simply always null,
 * because nothing ever set them. This is the missing half: the operator's entry, merged into every
 * sidecar the recorder writes, and therefore into `session_index.csv`.
 *
 * Deliberately a plain data class with a pure [applyTo]: the merge is the part that must be right,
 * and it is testable without Android.
 */
data class Slate(
    val project: String = "untitled",
    val scene: String? = null,
    val shot: String? = null,
    val take: Int = 1,
    /** The "circled take" — the one the script supervisor marks as the keeper. */
    val circledTake: Boolean = false,
    val lensName: String? = null,
    val focalLengthMm: Double? = null,
    val aperture: String? = null,
    val adapter: String? = null,
    val filterStack: List<String> = emptyList(),
    val ndValue: String? = null,
    val notes: String? = null,
) {
    /**
     * Overlays the slate onto a profile-derived sidecar.
     *
     * Slate values win where the operator supplied one, and the profile's lens defaults survive
     * where they did not — a blank field on the slate must not erase a default the profile already
     * knew (that would silently lose metadata the moment someone opened the slate screen and
     * closed it again).
     */
    fun applyTo(metadata: MetadataWriter.ClipMetadata): MetadataWriter.ClipMetadata = metadata.copy(
        project = project.ifBlank { metadata.project },
        scene = scene?.takeIf { it.isNotBlank() } ?: metadata.scene,
        shot = shot?.takeIf { it.isNotBlank() } ?: metadata.shot,
        take = take,
        circledTake = circledTake,
        lensName = lensName?.takeIf { it.isNotBlank() } ?: metadata.lensName,
        focalLengthMm = focalLengthMm ?: metadata.focalLengthMm,
        aperture = aperture?.takeIf { it.isNotBlank() } ?: metadata.aperture,
        adapter = adapter?.takeIf { it.isNotBlank() } ?: metadata.adapter,
        filterStack = filterStack.ifEmpty { metadata.filterStack },
        ndValue = ndValue?.takeIf { it.isNotBlank() } ?: metadata.ndValue,
        notes = notes?.takeIf { it.isNotBlank() } ?: metadata.notes,
    )

    /**
     * The next take of the same setup: take number up, circle cleared. Scene/shot/lens persist,
     * because on a real set they change far less often than the take number does.
     */
    fun nextTake(): Slate = copy(take = take + 1, circledTake = false)

    /** A new setup: take resets to 1. Lens and filter details persist — the glass has not changed. */
    fun nextShot(shot: String?): Slate = copy(shot = shot, take = 1, circledTake = false)

    /** Compact slate line for the status bar, e.g. `12A/3` or `12A/3 (C)`. */
    fun displayLine(): String {
        val scenePart = scene?.takeIf { it.isNotBlank() } ?: "--"
        val shotPart = shot?.takeIf { it.isNotBlank() }.orEmpty()
        val circle = if (circledTake) " (C)" else ""
        return "$scenePart$shotPart/$take$circle"
    }
}

/**
 * Persists the slate to `/sdcard/OIW_MEDIA/.slate.json`.
 *
 * On disk rather than in memory for a mundane but decisive reason: a camera app gets killed. If the
 * take number resets to 1 because the process restarted between setups, the sidecars lie and the
 * edit-bay shot list is wrong — and nobody notices until the edit. Same atomic tmp + fsync + rename
 * discipline as the clip sidecars.
 */
class SlateStore(private val file: File = File(OiwPaths.mediaRoot(), ".slate.json")) {

    private val gson = Gson()

    /** Returns the stored slate, or a fresh one if there is none or it is unreadable. */
    fun load(): Slate {
        if (!file.isFile) return Slate()
        return runCatching { gson.fromJson(file.readText(), Slate::class.java) }.getOrNull() ?: Slate()
    }

    /** @return true if the slate reached disk. A failed save must not stop a take. */
    fun save(slate: Slate): Boolean = runCatching {
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) return false
        val tmp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(tmp).use { fos ->
            fos.write(gson.toJson(slate).toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        true
    }.getOrDefault(false)

    /** Advances to the next take and persists. Returns the new slate even if the write failed. */
    fun advanceTake(): Slate = load().nextTake().also { save(it) }
}
