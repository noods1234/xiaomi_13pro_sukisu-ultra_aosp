package com.oiw.camera.metadata

import com.oiw.camera.util.OiwPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tier 1.5 (Robolectric): [SlateStore] persistence.
 *
 * The slate lives on disk rather than in memory for one blunt reason: camera apps get killed. If
 * the take number silently resets to 1 because the process restarted between setups, every
 * subsequent sidecar carries the wrong take, `session_index.csv` is wrong, and nobody finds out
 * until the edit. So "survives a restart" is the behaviour under test, not an implementation note.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SlateStoreRobolectricTest {

    private lateinit var store: SlateStore

    @Before
    fun setUp() {
        OiwPaths.mediaRoot().deleteRecursively()
        store = SlateStore()
    }

    @After
    fun tearDown() {
        OiwPaths.mediaRoot().deleteRecursively()
    }

    @Test
    fun anAbsentSlateLoadsAsAFreshOneRatherThanFailing() {
        val slate = store.load()
        assertEquals("untitled", slate.project)
        assertEquals(1, slate.take)
        assertFalse(slate.circledTake)
    }

    @Test
    fun aSavedSlateSurvivesAProcessRestart() {
        val original = Slate(
            project = "Rain Later",
            scene = "12",
            shot = "A",
            take = 4,
            circledTake = true,
            lensName = "Helios 44-2",
            focalLengthMm = 58.0,
            filterStack = listOf("Black Pro-Mist 1/4", "CPL"),
            ndValue = "ND0.9",
        )
        assertTrue(store.save(original))

        // A brand-new store instance is what a relaunched process gets.
        assertEquals(original, SlateStore().load())
    }

    @Test
    fun theFirstSaveCreatesTheMediaRootAndLeavesNoTemporaryFile() {
        assertFalse(OiwPaths.mediaRoot().exists())
        assertTrue(store.save(Slate(scene = "1")))

        assertTrue(OiwPaths.mediaRoot().isDirectory)
        assertTrue(File(OiwPaths.mediaRoot(), ".slate.json").isFile)
        assertFalse(File(OiwPaths.mediaRoot(), ".slate.json.tmp").exists())
    }

    @Test
    fun advanceTakeBumpsTheNumberAndPersistsIt() {
        store.save(Slate(scene = "12", shot = "A", take = 3, circledTake = true))

        val advanced = store.advanceTake()
        assertEquals(4, advanced.take)
        assertFalse(advanced.circledTake)
        assertEquals("the bump must be on disk, not just in the return value", 4, SlateStore().load().take)
        assertEquals("12", SlateStore().load().scene)
    }

    @Test
    fun advanceTakeStartsFromOneWhenNoSlateHasEverBeenSaved() {
        assertEquals(2, store.advanceTake().take)
    }

    @Test
    fun aCorruptSlateFileFallsBackToAFreshSlateInsteadOfCrashing() {
        OiwPaths.mediaRoot().mkdirs()
        File(OiwPaths.mediaRoot(), ".slate.json").writeText("{ truncated mid-write")

        val slate = store.load()
        assertEquals("untitled", slate.project)
        assertEquals(1, slate.take)
    }

    @Test
    fun savingReplacesTheSlateRatherThanAppending() {
        store.save(Slate(scene = "12", take = 1))
        store.save(Slate(scene = "13", take = 9))

        val reloaded = SlateStore().load()
        assertEquals("13", reloaded.scene)
        assertEquals(9, reloaded.take)
        assertFalse(
            "stale content survived the rewrite",
            File(OiwPaths.mediaRoot(), ".slate.json").readText().contains("\"12\""),
        )
    }

    /** The slate is a convenience; a failed write must report failure, not throw mid-take. */
    @Test
    fun anUnwritableLocationReportsFailureRatherThanThrowing() {
        val blocked = File(OiwPaths.mediaRoot(), "blocked")
        blocked.parentFile!!.mkdirs()
        blocked.writeText("this is a file, not a directory")

        // Parent path exists as a regular file, so mkdirs and the write cannot succeed.
        assertFalse(SlateStore(File(blocked, ".slate.json")).save(Slate(scene = "1")))
    }

    /** End-to-end: a slate on disk reaches the clip sidecar the recorder writes. */
    @Test
    fun aStoredSlateReachesTheClipSidecar() {
        store.save(Slate(project = "Rain Later", scene = "12", shot = "B", take = 5, circledTake = true))

        val clipDir = File(OiwPaths.mediaRoot(), "Rain_Later/2026-07-26/A/CLIP_0001").apply { mkdirs() }
        val clip = File(clipDir, "CLIP_0001.mp4").apply { writeText("fake-media") }
        val writer = MetadataWriter()
        val base = writer.buildFromProfile(
            profile = com.oiw.camera.capture.CaptureProfile(
                id = "p", displayName = "p",
                resolution = com.oiw.camera.capture.CaptureProfile.Resolution(3840, 2160),
                frameRateFps = 24,
                shutter = com.oiw.camera.capture.CaptureProfile.ShutterStrategy("angle", 180.0),
                isoMin = 100, isoMax = 3200, whiteBalanceKelvin = 5600, focusMode = "manual",
                codec = "hevc10", bitrateBps = 1, colorProfile = "standard",
                storageTarget = "internal", thermalProfileId = "balanced_field",
            ),
            cameraId = "0", romBuild = "r", kernelBuild = "k", deviceModel = "d",
        )

        writer.writeAtomically(clip, SlateStore().load().applyTo(base))

        val reread = writer.readForRecovery(clip)!!
        assertEquals("Rain Later", reread.project)
        assertEquals("12", reread.scene)
        assertEquals("B", reread.shot)
        assertEquals(5, reread.take)
        assertTrue(reread.circledTake)
    }
}
