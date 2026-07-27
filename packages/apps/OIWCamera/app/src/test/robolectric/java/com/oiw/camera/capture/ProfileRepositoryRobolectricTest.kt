package com.oiw.camera.capture

import android.content.Context
import com.oiw.camera.util.OiwPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tier 1.5 (Robolectric): the user-plugin half of [ProfileRepository] — real files in
 * `/sdcard/OIW_MEDIA/plugins/profiles/`.
 *
 * SCOPE, stated plainly: the offline harness runs Robolectric in BINARY resources mode with no
 * app resource APK (building one needs aapt2 from the Android SDK), so `context.assets` is empty
 * here and the *bundled* asset branch is not exercised by this file. That branch is covered two
 * other ways: `BundledProfileAssetsTest` (tier 1) parses every shipped JSON file under
 * `assets/capture_profiles` straight off disk with the same Gson + [CaptureProfile] types, and
 * `./gradlew testDebugUnitTest` runs this same class with AGP supplying real assets.
 *
 * (The wording above avoids a glob: Kotlin block comments nest, so a literal slash-star inside a
 * KDoc opens a comment that never closes. That exact bug broke ProfileRepository once already.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileRepositoryRobolectricTest {

    private lateinit var context: Context
    private lateinit var repo: ProfileRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repo = ProfileRepository(context)
        OiwPaths.mediaRoot().deleteRecursively()
    }

    @After
    fun tearDown() {
        OiwPaths.mediaRoot().deleteRecursively()
    }

    @Test
    fun missingUserProfileDirectoryIsNotAnError() {
        assertFalse(OiwPaths.userProfilesDir().exists())
        repo.loadAll() // must not throw
        assertTrue("an absent plugin dir is normal, not a failure", repo.loadErrors.isEmpty())
    }

    @Test
    fun aUserProfileIsLoadedFromTheDocumentedPluginDirectory() {
        userProfile("my_look", validProfileJson("my_look", displayName = "My Look"))

        val loaded = repo.loadAll()
        val mine = loaded.firstOrNull { it.id == "my_look" }
        assertNotNull("a hand-dropped profile must appear without a rebuild", mine)
        assertEquals("My Look", mine!!.displayName)
        assertEquals(3840, mine.resolution.width)
        assertEquals(180.0, mine.shutter.shutterAngleDegrees!!, 1e-9)
    }

    @Test
    fun oneMalformedProfileIsSkippedAndReportedWhileTheRestSurvive() {
        userProfile("good_a", validProfileJson("good_a"))
        userProfile("broken", "{ this is not json")
        userProfile("good_b", validProfileJson("good_b"))

        val ids = repo.loadAll().map { it.id }
        assertTrue("good_a" in ids)
        assertTrue("good_b" in ids)
        assertFalse("broken" in ids)
        assertEquals("exactly one load error expected", 1, repo.loadErrors.size)
        assertTrue(
            "the error must name the offending file so the user can fix it: ${repo.loadErrors}",
            repo.loadErrors.single().contains("broken.json"),
        )
    }

    @Test
    fun loadErrorsAreClearedBetweenLoadsRatherThanAccumulating() {
        userProfile("broken", "{ nope")
        repo.loadAll()
        assertEquals(1, repo.loadErrors.size)

        File(OiwPaths.userProfilesDir(), "broken.json").delete()
        repo.loadAll()
        assertTrue("a fixed profile must clear its old error", repo.loadErrors.isEmpty())
    }

    @Test
    fun nonJsonFilesInThePluginDirectoryAreIgnored() {
        userProfile("real", validProfileJson("real"))
        File(OiwPaths.userProfilesDir(), "notes.txt").writeText("shopping list")
        File(OiwPaths.userProfilesDir(), "look.cube").writeText("LUT_3D_SIZE 2")

        assertTrue(repo.loadErrors.isEmpty())
        assertTrue("real" in repo.loadAll().map { it.id })
    }

    @Test
    fun aUserProfileOverridesRatherThanDuplicatesTheSameId() {
        userProfile("dupe_a", validProfileJson("shared_id", displayName = "First"))
        userProfile("dupe_b", validProfileJson("shared_id", displayName = "Second"))

        val matching = repo.loadAll().filter { it.id == "shared_id" }
        assertEquals("same id must collapse to one entry, not two", 1, matching.size)
    }

    @Test
    fun profilesRoundTripEveryFieldTheCaptureLayerReads() {
        userProfile("full", validProfileJson("full"))

        val profile = repo.loadAll().first { it.id == "full" }
        assertEquals(24, profile.frameRateFps)
        assertEquals("hevc10", profile.codec)
        assertEquals(10, profile.bitDepth)
        assertEquals(180_000_000L, profile.bitrateBps)
        assertEquals("flat_approximation", profile.colorProfile)
        assertEquals("balanced_field", profile.thermalProfileId)
        assertEquals(listOf("histogram", "waveform"), profile.monitoringOverlays)
        // The exposure the capture request will actually set, from the on-disk shutter angle.
        assertEquals(20_833_333L, profile.shutter.exposureTimeNanos(24))
    }

    private fun userProfile(name: String, json: String) {
        val dir = OiwPaths.userProfilesDir().apply { mkdirs() }
        File(dir, "$name.json").writeText(json)
    }

    private fun validProfileJson(id: String, displayName: String = "Profile $id") = """
        {
          "id": "$id",
          "displayName": "$displayName",
          "resolution": { "width": 3840, "height": 2160 },
          "frameRateFps": 24,
          "shutter": { "mode": "angle", "shutterAngleDegrees": 180 },
          "isoMin": 100,
          "isoMax": 3200,
          "whiteBalanceKelvin": 5600,
          "focusMode": "manual",
          "codec": "hevc10",
          "bitrateBps": 180000000,
          "bitDepth": 10,
          "colorProfile": "flat_approximation",
          "storageTarget": "internal",
          "thermalProfileId": "balanced_field",
          "monitoringOverlays": ["histogram", "waveform"]
        }
    """.trimIndent()
}
