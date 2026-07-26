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

    /**
     * @param sustainedWriteMBps mega**BYTES** per second — both producers (this class's
     *   [runAndStoreBenchmark] and tools/storage_benchmark.sh) compute `bytes / 1e6 / seconds`.
     *   The old name `sustainedWriteMbps` read as megaBITS and is what caused audit finding T, so
     *   the capitalisation here is load-bearing, not cosmetic.
     */
    data class BenchmarkResult(val sustainedWriteMBps: Double, val sampleSizeBytes: Long)

    /** Never trust a spec sheet — always compare against a real, recent benchmark (docs/RED_TEAM_AUDIT.md #7). */
    fun preflight(targetDir: File, requiredBitrateBps: Long, lastBenchmark: BenchmarkResult?): PreflightResult {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return PreflightResult(false, "Could not create storage folder: ${targetDir.absolutePath}", 0, 0)
        }
        val statFs = StatFs(targetDir.absolutePath)
        val freeBytes = statFs.availableBytes
        // AUDIT FIX T: bitrate is in BITS/s, the benchmark is in mega BYTES/s. Dividing only by 1e6
        // compared Mbit/s against MB/s and demanded 8x the real throughput, so the 180 Mbps 4K
        // profile refused any card slower than 216 MB/s instead of the 27 MB/s it actually needs.
        val requiredMBps = requiredBitrateBps / 8.0 / 1_000_000.0
        if (lastBenchmark == null) {
            // Pure comparison stays pure: with no benchmark data we cannot certify the target, so we
            // refuse. The CALLER is responsible for running runAndStoreBenchmark() first so this
            // branch isn't hit in the normal flow (AUDIT FIX C — previously nothing ever wrote the
            // benchmark file the app reads, so recording could never start).
            return PreflightResult(
                false,
                "No write-speed benchmark yet for ${targetDir.absolutePath}; run one before this profile.",
                freeBytes,
                0,
            )
        }
        val marginedRequirement = requiredMBps * SAFETY_MARGIN
        if (lastBenchmark.sustainedWriteMBps < marginedRequirement) {
            return PreflightResult(
                false,
                // AUDIT FIX T: the parentheses matter. `"a" + "b".format(x)` applies format to "b"
                // ONLY, so the placeholders in the first half shipped to the user as literal "%.1f".
                ("Measured write speed %.1f MB/s is below the %.1f MB/s required (with safety " +
                    "margin) by this profile. Lower bitrate or use faster storage.")
                    .format(lastBenchmark.sustainedWriteMBps, marginedRequirement),
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
            // Accepts the legacy `sustainedWriteMbps` key so a benchmark file written by an older
            // build (or an older copy of tools/storage_benchmark.sh) still reads. Same unit either
            // way — only the name was misleading.
            val speed = obj.get("sustainedWriteMBps") ?: obj.get("sustainedWriteMbps")
            BenchmarkResult(
                sustainedWriteMBps = speed.asDouble,
                sampleSizeBytes = obj.get("sampleSizeBytes")?.asLong ?: 0L,
            )
        }.getOrNull()
    }

    /**
     * Real in-app sustained-write benchmark (AUDIT FIX C): writes a temp file to [targetDir]'s
     * filesystem, measures MB/s, stores the result at benchmarks/<target>.json (same location
     * loadLastBenchmark reads), and returns it. Uses buffered writes + fsync so the number reflects
     * media, not page cache. Deletes the temp file. Returns null on I/O failure.
     */
    fun runAndStoreBenchmark(targetDir: File, target: String, sizeMb: Int = 64): BenchmarkResult? {
        if (!targetDir.exists() && !targetDir.mkdirs()) return null
        val tmp = File(targetDir, ".oiw_bench.tmp")
        val bytes = sizeMb.toLong() * 1024 * 1024
        val chunk = ByteArray(1 * 1024 * 1024)
        return try {
            val start = System.nanoTime()
            java.io.FileOutputStream(tmp).use { fos ->
                var written = 0L
                while (written < bytes) {
                    fos.write(chunk)
                    written += chunk.size
                }
                fos.flush()
                fos.fd.sync()
            }
            val elapsedS = (System.nanoTime() - start) / 1_000_000_000.0
            val megabytesPerSecond = if (elapsedS > 0) (bytes / 1_000_000.0) / elapsedS else 0.0
            tmp.delete()
            val result = BenchmarkResult(sustainedWriteMBps = megabytesPerSecond, sampleSizeBytes = bytes)
            val benchDir = com.oiw.camera.util.OiwPaths.benchmarksDir().apply { mkdirs() }
            // Both keys are written so a file from this build still reads on an older one.
            File(benchDir, "$target.json").writeText(
                """{"sustainedWriteMBps":$megabytesPerSecond,""" +
                    """"sustainedWriteMbps":$megabytesPerSecond,"sampleSizeBytes":$bytes}"""
            )
            result
        } catch (e: Exception) {
            tmp.delete()
            null
        }
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
