package com.oiw.camera.storage

import android.content.Context
import android.os.StatFs
import android.os.storage.StorageManager as AndroidStorageManager
import java.io.File

/**
 * Storage preflight, folder layout, and crash-recovery scan (docs/STORAGE_MEDIA.md).
 * Folder layout: /OIW_MEDIA/<project>/<date>/<cam>/<clip>/CLIP_NNNN.{mp4,json,cube,jpg}
 */
class StorageManager(private val context: Context) {

    data class PreflightResult(
        val ok: Boolean,
        val message: String,
        val freeBytes: Long,
        val estimatedRecordSeconds: Long,
    )

    data class BenchmarkResult(val sustainedWriteMbps: Double, val sampleSizeBytes: Long)

    /** Never trust a spec sheet — always compare against a real, recent benchmark (docs/RED_TEAM_AUDIT.md #7). */
    fun preflight(targetDir: File, requiredBitrateBps: Long, lastBenchmark: BenchmarkResult?): PreflightResult {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return PreflightResult(false, "Could not create storage folder: ${targetDir.absolutePath}", 0, 0)
        }
        val statFs = StatFs(targetDir.absolutePath)
        val freeBytes = statFs.availableBytes
        val requiredMbps = requiredBitrateBps / 1_000_000.0
        if (lastBenchmark == null) {
            return PreflightResult(
                false,
                "No storage write-speed benchmark on file for ${targetDir.absolutePath}. " +
                    "Run tools/storage_benchmark.sh before enabling this profile.",
                freeBytes,
                0,
            )
        }
        val marginedRequirement = requiredMbps * SAFETY_MARGIN
        if (lastBenchmark.sustainedWriteMbps < marginedRequirement) {
            return PreflightResult(
                false,
                "Measured write speed %.1f MB/s is below the %.1f MB/s required (with safety margin) " +
                    "by this profile. Lower bitrate or use faster storage.".format(
                        lastBenchmark.sustainedWriteMbps, marginedRequirement,
                    ),
                freeBytes,
                0,
            )
        }
        val estimatedSeconds = if (requiredBitrateBps > 0) (freeBytes * 8) / requiredBitrateBps else 0
        return PreflightResult(true, "Storage OK.", freeBytes, estimatedSeconds)
    }

    fun projectClipDir(project: String, date: String, camLabel: String, clipName: String): File {
        val root = File(mediaRoot(), listOf(sanitize(project), date, sanitize(camLabel), sanitize(clipName))
            .joinToString(File.separator))
        root.mkdirs()
        return root
    }

    fun mediaRoot(): File = com.oiw.camera.util.OiwPaths.mediaRoot()

    /**
     * Loads the last write-speed benchmark for a target ("internal" | "external_ssd") from the JSON
     * that tools/storage_benchmark.sh writes (user copies/redirects it to
     * OIW_MEDIA/benchmarks/<target>.json), or that a future in-app benchmark writes to the same spot.
     */
    fun loadLastBenchmark(target: String): BenchmarkResult? {
        val file = File(com.oiw.camera.util.OiwPaths.benchmarksDir(), "$target.json")
        if (!file.exists()) return null
        return runCatching {
            val obj = com.google.gson.Gson().fromJson(file.readText(), com.google.gson.JsonObject::class.java)
            BenchmarkResult(
                sustainedWriteMbps = obj.get("sustainedWriteMbps").asDouble,
                sampleSizeBytes = obj.get("sampleSizeBytes")?.asLong ?: 0L,
            )
        }.getOrNull()
    }

    /** Detected via the public StorageVolume API — no custom USB mass-storage driver logic here. */
    fun listExternalVolumes(): List<String> {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as AndroidStorageManager
        return sm.storageVolumes
            .filter { it.isRemovable }
            .mapNotNull { it.getDescription(context) }
    }

    /**
     * Scans for clips with a .mp4 (or .mp4.tmp) file but no finalized sidecar — signals an interrupted
     * recording (crash, force-kill, power loss). Never silently drops a partial file (docs/STORAGE_MEDIA.md #9).
     */
    fun scanForRecoverableClips(): List<File> {
        val root = mediaRoot()
        if (!root.isDirectory) return emptyList()
        val results = mutableListOf<File>()
        root.walkTopDown().forEach { file ->
            if (file.extension == "mp4" || file.extension == "tmp") {
                val sidecar = File(file.parentFile, "${file.nameWithoutExtension}.json")
                if (!sidecar.exists()) results += file
            }
        }
        return results
    }

    /** Simple sanity check that free space isn't exhausted mid-recording (ENOSPC prevention). */
    fun freeBytes(dir: File): Long = StatFs(dir.absolutePath).availableBytes

    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9_\\-]"), "_")

    companion object {
        private const val SAFETY_MARGIN = 1.2 // 20% margin, docs/STORAGE_MEDIA.md #6
    }
}
