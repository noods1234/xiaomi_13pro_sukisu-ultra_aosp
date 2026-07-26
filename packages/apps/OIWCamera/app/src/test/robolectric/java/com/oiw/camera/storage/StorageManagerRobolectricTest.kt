package com.oiw.camera.storage

import android.content.Context
import com.oiw.camera.util.OiwPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.File

/**
 * Tier 1.5 (Robolectric): [StorageManager] does real filesystem work — mkdirs, StatFs, buffered
 * writes with fsync, a recursive recovery scan. None of it had ever executed.
 *
 * The headline case is [benchmarkRoundTripsThroughTheFileTheAppActuallyReads]: audit finding C was
 * a broken write/read loop (the app read a benchmark JSON that nothing ever wrote, so recording
 * could never start). That defect type-checks perfectly; only execution catches it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageManagerRobolectricTest {

    private lateinit var context: Context
    private lateinit var manager: StorageManager
    private lateinit var target: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        manager = StorageManager(context)
        OiwPaths.mediaRoot().deleteRecursively()
        target = File(OiwPaths.mediaRoot(), "cards/internal")
        // ShadowStatFs reports 0 for unregistered paths; register real numbers so the free-space
        // and remaining-record-time math is exercised with values we can predict exactly.
        ShadowStatFs.registerStats(target, TOTAL_BLOCKS, FREE_BLOCKS, AVAILABLE_BLOCKS)
    }

    @After
    fun tearDown() {
        ShadowStatFs.reset()
        OiwPaths.mediaRoot().deleteRecursively()
    }

    @Test
    fun benchmarkRoundTripsThroughTheFileTheAppActuallyReads() {
        assertNull("no benchmark should exist before one is run", manager.loadLastBenchmark("internal"))

        val written = manager.runAndStoreBenchmark(target, "internal", sizeMb = 1)
        assertNotNull("in-app benchmark must succeed on a writable dir", written)
        assertTrue("must measure a positive write speed", written!!.sustainedWriteMBps > 0.0)
        assertEquals(1L * 1024 * 1024, written.sampleSizeBytes)

        val reloaded = manager.loadLastBenchmark("internal")
        assertNotNull("loadLastBenchmark must find what runAndStoreBenchmark wrote", reloaded)
        assertEquals(written.sustainedWriteMBps, reloaded!!.sustainedWriteMBps, 1e-9)
        assertEquals(written.sampleSizeBytes, reloaded.sampleSizeBytes)

        assertFalse("benchmark scratch file must not be left behind", File(target, ".oiw_bench.tmp").exists())
    }

    @Test
    fun preflightRefusesWithNoBenchmarkAndStillReportsFreeSpace() {
        val result = manager.preflight(target, requiredBitrateBps = 100_000_000, lastBenchmark = null)
        assertFalse(result.ok)
        assertTrue(result.message.contains("benchmark"))
        assertEquals(AVAILABLE_BLOCKS.toLong() * BLOCK_SIZE, result.freeBytes)
    }

    @Test
    fun preflightRefusesWhenMeasuredSpeedIsBelowTheSafetyMargin() {
        // 100 Mbps = 12.5 MB/s required; x1.2 margin = 15 MB/s. 14 MB/s must be refused.
        val result = manager.preflight(
            target,
            requiredBitrateBps = 100_000_000,
            lastBenchmark = StorageManager.BenchmarkResult(sustainedWriteMBps = 14.0, sampleSizeBytes = 1),
        )
        assertFalse(result.ok)
        assertTrue("message must name both numbers so the user can act", result.message.contains("14.0"))
        assertTrue("message must state the requirement too", result.message.contains("15.0"))
    }

    /**
     * Regression guard, audit finding T, half 1: `"a" + "b".format(x)` binds `.format` to the second
     * literal only, so the placeholders in the first half reached the user verbatim — the refusal
     * read "Measured write speed %.1f MB/s is below the %.1f MB/s required". docs/UI_UX.md promises
     * a specific, actionable message; a raw format string is the opposite of that.
     */
    @Test
    fun theRefusalMessageContainsNoUnsubstitutedFormatPlaceholders() {
        val result = manager.preflight(
            target,
            requiredBitrateBps = 180_000_000,
            lastBenchmark = StorageManager.BenchmarkResult(sustainedWriteMBps = 1.0, sampleSizeBytes = 1),
        )
        assertFalse(result.ok)
        assertFalse(
            "unsubstituted placeholder reached the user: ${result.message}",
            result.message.contains("%.1f") || result.message.contains("%s") || result.message.contains("%d"),
        )
    }

    /**
     * Regression guard, audit finding T, half 2: bitrate is BITS/s and the benchmark is
     * mega BYTES/s. Dividing only by 1e6 demanded 8x the real throughput, so the flagship 180 Mbps
     * 4K profile refused every card slower than 216 MB/s when it actually needs ~27 MB/s.
     */
    @Test
    fun requiredThroughputIsDerivedFromBitsNotBytes() {
        // 180 Mbps = 22.5 MB/s; x1.2 margin = 27 MB/s.
        val justUnder = manager.preflight(
            target,
            requiredBitrateBps = 180_000_000,
            lastBenchmark = StorageManager.BenchmarkResult(sustainedWriteMBps = 26.9, sampleSizeBytes = 1),
        )
        val justOver = manager.preflight(
            target,
            requiredBitrateBps = 180_000_000,
            lastBenchmark = StorageManager.BenchmarkResult(sustainedWriteMBps = 27.1, sampleSizeBytes = 1),
        )
        assertFalse("26.9 MB/s is below the 27 MB/s margin", justUnder.ok)
        assertTrue("27.1 MB/s clears 180 Mbps with margin: ${justOver.message}", justOver.ok)
    }

    @Test
    fun preflightAcceptsWhenMeasuredSpeedClearsTheMarginAndEstimatesRuntime() {
        val bitrate = 100_000_000L
        val result = manager.preflight(
            target,
            requiredBitrateBps = bitrate,
            lastBenchmark = StorageManager.BenchmarkResult(sustainedWriteMBps = 40.0, sampleSizeBytes = 1),
        )
        assertTrue(result.message, result.ok)
        val expectedSeconds = (AVAILABLE_BLOCKS.toLong() * BLOCK_SIZE * 8) / bitrate
        assertEquals(expectedSeconds, result.estimatedRecordSeconds)
    }

    @Test
    fun preflightCreatesTheTargetDirectoryWhenMissing() {
        assertFalse(target.exists())
        manager.preflight(target, requiredBitrateBps = 1, lastBenchmark = null)
        assertTrue("preflight must create the storage folder it is asked about", target.isDirectory)
    }

    @Test
    fun projectClipDirBuildsTheDocumentedLayoutAndSanitisesUserText() {
        val dir = manager.projectClipDir("My Film/2026", "2026-07-26", "cam A", "CLIP_0001")
        assertTrue("clip dir must be created eagerly", dir.isDirectory)
        val relative = dir.absolutePath.removePrefix(manager.mediaRoot().absolutePath + File.separator)
        assertEquals("My_Film_2026/2026-07-26/cam_A/CLIP_0001", relative.replace(File.separatorChar, '/'))
    }

    @Test
    fun recoveryScanFindsOrphanedClipsAndIgnoresFinalisedOnes() {
        val dir = manager.projectClipDir("proj", "2026-07-26", "A", "CLIP_0001")
        val orphan = File(dir, "CLIP_0001.mp4").apply { writeText("orphan") }
        val partial = File(dir, "CLIP_0002.mp4.tmp").apply { writeText("partial") }
        val finalised = File(dir, "CLIP_0003.mp4").apply { writeText("done") }
        File(dir, "CLIP_0003.json").writeText("{}")

        val recoverable = manager.scanForRecoverableClips().map { it.absolutePath }.toSet()
        assertTrue("clip with no sidecar is recoverable", orphan.absolutePath in recoverable)
        assertTrue("interrupted .tmp is recoverable", partial.absolutePath in recoverable)
        assertFalse("clip with a sidecar is already finalised", finalised.absolutePath in recoverable)
    }

    @Test
    fun recoveryScanIsEmptyWhenNothingHasBeenRecorded() {
        assertTrue(manager.scanForRecoverableClips().isEmpty())
    }

    /** A benchmark file written by an older build must still read; only the key name changed. */
    @Test
    fun loadLastBenchmarkAcceptsTheLegacyKeyName() {
        OiwPaths.benchmarksDir().mkdirs()
        File(OiwPaths.benchmarksDir(), "internal.json")
            .writeText("""{"sustainedWriteMbps":42.5,"sampleSizeBytes":1024}""")

        val loaded = manager.loadLastBenchmark("internal")
        assertNotNull(loaded)
        assertEquals(42.5, loaded!!.sustainedWriteMBps, 1e-9)
    }

    @Test
    fun loadLastBenchmarkSurvivesACorruptBenchmarkFile() {
        OiwPaths.benchmarksDir().mkdirs()
        File(OiwPaths.benchmarksDir(), "internal.json").writeText("{not json")
        assertNull("a corrupt benchmark must read as absent, never crash", manager.loadLastBenchmark("internal"))
    }

    /** Proves the STORAGE_SERVICE cast and StorageVolume API path actually run under a real Context. */
    @Test
    fun listExternalVolumesRunsAgainstTheRealStorageService() {
        assertNotNull(context.getSystemService(Context.STORAGE_SERVICE))
        assertNotNull(manager.listExternalVolumes())
    }

    private companion object {
        const val BLOCK_SIZE = 4096 // ShadowStatFs's fixed block size
        const val TOTAL_BLOCKS = 200_000
        const val FREE_BLOCKS = 100_000
        const val AVAILABLE_BLOCKS = 100_000
    }
}
