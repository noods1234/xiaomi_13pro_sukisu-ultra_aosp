package com.oiw.camera.capture

import com.google.gson.Gson
import com.oiw.camera.control.ButtonMappingLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tier 1 (pure JVM, executed): parses every capture profile and button mapping this app actually
 * ships, straight off disk, with the same Gson + [CaptureProfile] types the app uses at runtime.
 *
 * Why here and not in the Robolectric tier: the offline harness has no app resource APK, so
 * `context.assets` is empty there. Reading the asset files directly proves the more valuable thing
 * anyway — that the *shipped JSON* is valid and complete. A profile that fails to parse would
 * silently vanish from the profile list at runtime (ProfileRepository skips it by design), so
 * without this test a typo in a shipped profile is invisible until someone reaches for it on set.
 */
class BundledProfileAssetsTest {

    private val gson = Gson()

    private val assetsDir: File by lazy {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        generateSequence(start) { it.parentFile }
            .flatMap { sequenceOf(File(it, "src/main/assets"), File(it, "app/src/main/assets")) }
            .firstOrNull { it.isDirectory }
            ?: error("could not locate src/main/assets from ${start.absolutePath}")
    }

    @Test
    fun everyShippedCaptureProfileParses() {
        val files = profileFiles()
        assertTrue("expected the bundled capture profiles to be present", files.isNotEmpty())
        files.forEach { file ->
            val profile = try {
                gson.fromJson(file.readText(), CaptureProfile::class.java)
            } catch (e: Exception) {
                throw AssertionError("${file.name} is not a parseable CaptureProfile: ${e.message}", e)
            }
            assertNotNull("${file.name} parsed to null", profile)
        }
    }

    @Test
    fun everyShippedProfileHasTheFieldsTheCaptureLayerDereferences() {
        profileFiles().forEach { file ->
            val p = gson.fromJson(file.readText(), CaptureProfile::class.java)
            val where = file.name
            assertTrue("$where: blank id", p.id.isNotBlank())
            assertTrue("$where: blank displayName", p.displayName.isNotBlank())
            assertTrue("$where: frameRateFps must be positive", p.frameRateFps > 0)
            assertTrue("$where: resolution must be positive", p.resolution.width > 0 && p.resolution.height > 0)
            assertTrue("$where: bitrateBps must be positive", p.bitrateBps > 0)
            assertTrue("$where: isoMin must not exceed isoMax", p.isoMin <= p.isoMax)
            assertNotNull("$where: null codec", p.codec)
            assertNotNull("$where: null colorProfile", p.colorProfile)
            assertTrue("$where: blank thermalProfileId", p.thermalProfileId.isNotBlank())
        }
    }

    /**
     * `exposureTimeNanos` throws on a shutter block missing the field its mode requires. That throw
     * would surface as a crash the moment the profile is selected, so every shipped profile must
     * survive it here.
     */
    @Test
    fun everyShippedProfileYieldsAUsableExposureTime() {
        profileFiles().forEach { file ->
            val p = gson.fromJson(file.readText(), CaptureProfile::class.java)
            val exposure = p.shutter.exposureTimeNanos(p.frameRateFps)
            assertTrue("${file.name}: non-positive exposure $exposure ns", exposure > 0)
            assertTrue(
                "${file.name}: exposure ${exposure}ns exceeds the ${p.frameRateFps}fps frame duration",
                exposure <= 1_000_000_000L / p.frameRateFps,
            )
        }
    }

    @Test
    fun profileIdsAreUniqueAndMatchTheirFilenames() {
        val seen = mutableSetOf<String>()
        profileFiles().forEach { file ->
            val p = gson.fromJson(file.readText(), CaptureProfile::class.java)
            assertTrue("duplicate profile id '${p.id}' — one would silently shadow the other", seen.add(p.id))
            assertEquals(
                "${file.name}: filename and id must agree so the plugin dir can override predictably",
                file.nameWithoutExtension,
                p.id,
            )
        }
    }

    /** Every profile names a button mapping; a missing one leaves the hardware buttons dead. */
    @Test
    fun everyProfileReferencesAButtonMappingThatShips() {
        val available = mappingFiles().map { it.nameWithoutExtension }.toSet()
        assertTrue("expected at least the default button mapping to ship", "default" in available)
        profileFiles().forEach { file ->
            val p = gson.fromJson(file.readText(), CaptureProfile::class.java)
            assertTrue(
                "${file.name} references button mapping '${p.buttonMappingId}', which is not bundled $available",
                p.buttonMappingId in available,
            )
        }
    }

    @Test
    fun everyShippedButtonMappingParsesAndMapsEachInputOnce() {
        mappingFiles().forEach { file ->
            val mapping = gson.fromJson(file.readText(), ButtonMappingLoader.Mapping::class.java)
            assertEquals("${file.name}: id must match filename", file.nameWithoutExtension, mapping.id)
            assertTrue("${file.name}: no mappings", mapping.mappings.isNotEmpty())
            val inputs = mapping.mappings.map { it.input }
            assertEquals(
                "${file.name}: an input is mapped twice; the later entry is unreachable",
                inputs.size,
                inputs.toSet().size,
            )
            mapping.mappings.forEach {
                assertTrue("${file.name}: blank input", it.input.isNotBlank())
                assertTrue("${file.name}: blank action for '${it.input}'", it.action.isNotBlank())
            }
        }
    }

    /**
     * docs/CINEMA_FEATURES.md §3 makes a safety promise: the power button never stops a recording.
     * It is a promise about a shipped config file, so it is checked against that file.
     */
    @Test
    fun theDefaultMappingKeepsThePowerButtonAwayFromRecordingState() {
        val default = mappingFiles().first { it.nameWithoutExtension == "default" }
        val mapping = gson.fromJson(default.readText(), ButtonMappingLoader.Mapping::class.java)
        mapping.mappings.filter { it.input.startsWith("power") }.forEach {
            assertFalse(
                "power input '${it.input}' maps to '${it.action}', which touches recording state",
                it.action.contains("record") || it.action.contains("stop"),
            )
        }
    }

    private fun profileFiles(): List<File> =
        File(assetsDir, "capture_profiles").listFiles()?.filter { it.extension == "json" }?.sorted().orEmpty()

    private fun mappingFiles(): List<File> =
        File(assetsDir, "button_mappings").listFiles()?.filter { it.extension == "json" }?.sorted().orEmpty()
}
