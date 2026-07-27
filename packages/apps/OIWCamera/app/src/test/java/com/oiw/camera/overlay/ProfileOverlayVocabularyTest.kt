package com.oiw.camera.overlay

import com.google.gson.Gson
import com.oiw.camera.capture.CaptureProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tier 1 (pure JVM, executed): pins the shipped capture profiles and the code to the same overlay
 * vocabulary, in **both** directions.
 *
 * This test exists because of two failures that `tools/check_wiring.py` cannot see, since both
 * involve code that is perfectly well referenced:
 *
 *  - **Unreachable features.** `WaveformOverlay` and `VectorscopeOverlay` were built, unit-tested,
 *    benchmarked, hooked into the analysis path and documented as "Implemented + wired" — and *no
 *    shipped profile listed them*, so no user could turn either on. Wiring the code is not the same
 *    as shipping the feature (`docs/AUDIT_FINDINGS.md` finding X).
 *  - **Silent typos.** A profile naming `"rgb-parade"` matches nothing and fails invisibly.
 */
class ProfileOverlayVocabularyTest {

    private val gson = Gson()

    private val assetsDir: File by lazy {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        generateSequence(start) { it.parentFile }
            .flatMap { sequenceOf(File(it, "src/main/assets"), File(it, "app/src/main/assets")) }
            .firstOrNull { it.isDirectory }
            ?: error("could not locate src/main/assets from ${start.absolutePath}")
    }

    private val profiles: List<CaptureProfile> by lazy {
        File(assetsDir, "capture_profiles").listFiles()
            ?.filter { it.extension == "json" }
            ?.sorted()
            ?.map { gson.fromJson(it.readText(), CaptureProfile::class.java) }
            .orEmpty()
    }

    @Test
    fun everyOverlayNamedByAShippedProfileIsUnderstoodByTheCode() {
        profiles.forEach { profile ->
            val unknown = MonitoringOverlays.unknownIn(profile.monitoringOverlays)
            assertTrue(
                "profile '${profile.id}' names overlays this build does not understand: $unknown",
                unknown.isEmpty(),
            )
        }
    }

    @Test
    fun everyOverlayTheCodeSupportsIsReachableFromAtLeastOneShippedProfile() {
        val enabledSomewhere = profiles.flatMap { it.monitoringOverlays }.toSet()
        val unreachable = MonitoringOverlays.ALL - enabledSomewhere
        assertTrue(
            "these overlays are implemented but no shipped profile enables them, so a user cannot " +
                "reach them: $unreachable",
            unreachable.isEmpty(),
        )
    }

    @Test
    fun aProfileListsEachOverlayAtMostOnce() {
        profiles.forEach { profile ->
            val overlays = profile.monitoringOverlays
            assertEquals(
                "profile '${profile.id}' repeats an overlay id: $overlays",
                overlays.size,
                overlays.toSet().size,
            )
        }
    }

    /**
     * `cinema_stealth_street` exists to put an unadorned image on screen. It listed no overlays and
     * still drew four, because the view's flags defaulted to on rather than being profile-driven
     * (finding W). Asserted on the mapping itself so a future default flip is caught here.
     */
    @Test
    fun aProfileWithNoOverlaysTurnsEverythingOff() {
        val stealth = profiles.firstOrNull { it.id == "cinema_stealth_street" }
        assertTrue("expected the stealth profile to ship", stealth != null)
        assertTrue("the stealth profile must request no overlays", stealth!!.monitoringOverlays.isEmpty())

        val flags = MonitoringOverlays.viewFlags(stealth.monitoringOverlays)
        assertTrue("no overlay may be on for a profile that asked for none: $flags", flags.none { it.value })
    }

    @Test
    fun overlayFlagsFollowTheProfileExactly() {
        val flags = MonitoringOverlays.viewFlags(
            listOf(MonitoringOverlays.HISTOGRAM, MonitoringOverlays.RGB_PARADE),
        )
        assertTrue(flags.getValue(MonitoringOverlays.HISTOGRAM))
        assertTrue(flags.getValue(MonitoringOverlays.RGB_PARADE))
        assertTrue("zebra was not requested", !flags.getValue(MonitoringOverlays.ZEBRA))
        assertTrue("guides were not requested", !flags.getValue(MonitoringOverlays.FRAME_GUIDES))
    }

    /** Every id in the vocabulary is either rendered by OverlayView or explicitly handled elsewhere. */
    @Test
    fun theViewFlagTableCoversEveryIdItShould() {
        val flags = MonitoringOverlays.viewFlags(emptyList())
        assertEquals(
            MonitoringOverlays.ALL - MonitoringOverlays.HANDLED_ELSEWHERE,
            flags.keys,
        )
        MonitoringOverlays.HANDLED_ELSEWHERE.forEach {
            assertTrue("$it is handled outside OverlayView and must not be a view flag", it !in flags)
        }
    }
}
