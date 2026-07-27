package com.oiw.camera.metadata

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.oiw.camera.capture.CaptureProfile
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
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tier 1.5 (Robolectric): [MetadataWriter] performs the atomic tmp -> fsync -> rename that every
 * clip's provenance depends on. It had never been executed, so "atomic" was an unverified claim
 * about code, not an observed property of files on disk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetadataWriterRobolectricTest {

    private lateinit var clipDir: File
    private lateinit var clip: File
    private val writer = MetadataWriter()

    @Before
    fun setUp() {
        OiwPaths.mediaRoot().deleteRecursively()
        clipDir = File(OiwPaths.mediaRoot(), "proj/2026-07-26/A/CLIP_0001").apply { mkdirs() }
        clip = File(clipDir, "CLIP_0001.mp4").apply { writeText("fake-media") }
    }

    @After
    fun tearDown() {
        OiwPaths.mediaRoot().deleteRecursively()
    }

    @Test
    fun writeAtomicallyLandsTheSidecarBesideTheClipAndLeavesNoTmp() {
        writer.writeAtomically(clip, metadata())

        val sidecar = File(clipDir, "CLIP_0001.json")
        assertTrue("sidecar must sit beside the clip", sidecar.isFile)
        assertFalse("no .tmp may survive a successful write", File(clipDir, "CLIP_0001.json.tmp").exists())
        assertEquals(
            "sidecar and clip must share a basename so the pairing is unambiguous",
            clip.nameWithoutExtension,
            sidecar.nameWithoutExtension,
        )
    }

    @Test
    fun theWrittenSidecarIsCompleteParseableJson() {
        writer.writeAtomically(clip, metadata(scene = "12A", take = 3))

        val obj = Gson().fromJson(File(clipDir, "CLIP_0001.json").readText(), JsonObject::class.java)
        assertEquals("untitled", obj.get("project").asString)
        assertEquals("12A", obj.get("scene").asString)
        assertEquals(3, obj.get("take").asInt)
        assertEquals("3840x2160", obj.get("resolution").asString)
        assertEquals(24, obj.get("frameRateFps").asInt)
        assertEquals(180_000_000L, obj.get("bitrateBps").asLong)
    }

    @Test
    fun rewritingAClipReplacesTheSidecarRatherThanAppending() {
        writer.writeAtomically(clip, metadata(scene = "first"))
        writer.writeAtomically(clip, metadata(scene = "second"))

        val reread = writer.readForRecovery(clip)
        assertNotNull(reread)
        assertEquals("second", reread!!.scene)
    }

    @Test
    fun readForRecoveryAcceptsEitherTheClipOrTheSidecarPath() {
        writer.writeAtomically(clip, metadata(scene = "42", take = 7))

        val fromClip = writer.readForRecovery(clip)
        val fromSidecar = writer.readForRecovery(File(clipDir, "CLIP_0001.json"))
        assertNotNull(fromClip)
        assertNotNull(fromSidecar)
        assertEquals("42", fromClip!!.scene)
        assertEquals(fromClip.take, fromSidecar!!.take)
    }

    @Test
    fun readForRecoveryReturnsNullForAnOrphanedClip() {
        assertNull("a clip with no sidecar must read as null, not throw", writer.readForRecovery(clip))
    }

    @Test
    fun readForRecoveryReturnsNullForACorruptSidecarInsteadOfCrashing() {
        File(clipDir, "CLIP_0001.json").writeText("{ truncated mid-write")
        assertNull(writer.readForRecovery(clip))
    }

    /**
     * A torn sidecar left by an earlier crash must not be mistaken for the real one: the writer
     * targets `<clip>.json`, so a stale `<clip>.json.tmp` is simply overwritten on the next take.
     */
    @Test
    fun aStaleTmpFromAnEarlierCrashDoesNotPoisonTheNextWrite() {
        File(clipDir, "CLIP_0001.json.tmp").writeText("{ torn")

        writer.writeAtomically(clip, metadata(scene = "clean"))

        assertEquals("clean", writer.readForRecovery(clip)!!.scene)
        assertFalse(File(clipDir, "CLIP_0001.json.tmp").exists())
    }

    @Test
    fun buildFromProfileCarriesTheProfileThroughToTheSidecarFields() {
        val built = writer.buildFromProfile(
            profile = profile(),
            cameraId = "0",
            romBuild = "oiw-0.1.0",
            kernelBuild = "oiw-cinema-aurora",
            deviceModel = "Xiaomi 14 Ultra",
        )
        assertEquals("3840x2160", built.resolution)
        assertEquals("angle", built.shutter)
        assertEquals(100, built.isoSensitivity)
        assertEquals("hevc10", built.codec)
        assertEquals("flat_approximation", built.colorProfile)
        assertEquals("oiw-cinema-aurora", built.kernelBuild)
        assertFalse("a LUT is preview-only unless explicitly baked", built.lutBaked)
    }

    private fun profile() = CaptureProfile(
        id = "cinema_4k_24_log",
        displayName = "Cinema 4K 24p Flat/Log",
        resolution = CaptureProfile.Resolution(3840, 2160),
        frameRateFps = 24,
        shutter = CaptureProfile.ShutterStrategy(mode = "angle", shutterAngleDegrees = 180.0),
        isoMin = 100,
        isoMax = 3200,
        whiteBalanceKelvin = 5600,
        focusMode = "manual",
        codec = "hevc10",
        bitrateBps = 180_000_000,
        bitDepth = 10,
        colorProfile = "flat_approximation",
        lutId = "rec709_neutral_monitor",
        storageTarget = "internal",
        thermalProfileId = "balanced_field",
    )

    private fun metadata(scene: String? = null, take: Int? = null) =
        MetadataWriter().buildFromProfile(profile(), "0", "oiw-0.1.0", "oiw-cinema-aurora", "Xiaomi 14 Ultra")
            .copy(scene = scene, take = take)
}
