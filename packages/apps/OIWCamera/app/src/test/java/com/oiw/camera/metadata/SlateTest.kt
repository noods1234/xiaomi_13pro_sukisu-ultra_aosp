package com.oiw.camera.metadata

import com.oiw.camera.capture.CaptureProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 (pure JVM, executed): the slate merge.
 *
 * The rule worth defending is [aBlankSlateFieldDoesNotEraseAProfileDefault]. A slate that
 * overwrote profile lens defaults with empty strings would silently strip metadata from every clip
 * the moment someone opened the slate screen and closed it — the kind of loss nobody notices until
 * the edit, when the lens column in the shot list is blank for half a shoot day.
 */
class SlateTest {

    @Test
    fun slateFieldsReachTheSidecar() {
        val merged = Slate(
            project = "Rain Later",
            scene = "12",
            shot = "A",
            take = 3,
            circledTake = true,
            lensName = "Helios 44-2",
            focalLengthMm = 58.0,
            aperture = "T2.1",
            adapter = "M42 to E",
            filterStack = listOf("Black Pro-Mist 1/4", "CPL"),
            ndValue = "ND0.9",
            notes = "practical lamp in shot",
        ).applyTo(baseMetadata())

        assertEquals("Rain Later", merged.project)
        assertEquals("12", merged.scene)
        assertEquals("A", merged.shot)
        assertEquals(3, merged.take)
        assertTrue(merged.circledTake)
        assertEquals("Helios 44-2", merged.lensName)
        assertEquals(58.0, merged.focalLengthMm!!, 1e-9)
        assertEquals("T2.1", merged.aperture)
        assertEquals("M42 to E", merged.adapter)
        assertEquals(listOf("Black Pro-Mist 1/4", "CPL"), merged.filterStack)
        assertEquals("ND0.9", merged.ndValue)
        assertEquals("practical lamp in shot", merged.notes)
    }

    @Test
    fun aBlankSlateFieldDoesNotEraseAProfileDefault() {
        val fromProfile = baseMetadata().copy(
            lensName = "Sirui 35mm Anamorphic",
            focalLengthMm = 35.0,
            aperture = "T1.8",
            adapter = "none",
            ndValue = "ND0.6",
            filterStack = listOf("UV"),
            notes = "profile note",
        )

        // An operator who opened the slate, typed a scene, and left the lens fields alone.
        val merged = Slate(scene = "4", shot = "  ", lensName = "", aperture = null).applyTo(fromProfile)

        assertEquals("4", merged.scene)
        assertEquals("a whitespace-only shot must not overwrite", fromProfile.shot, merged.shot)
        assertEquals("Sirui 35mm Anamorphic", merged.lensName)
        assertEquals(35.0, merged.focalLengthMm!!, 1e-9)
        assertEquals("T1.8", merged.aperture)
        assertEquals("none", merged.adapter)
        assertEquals("ND0.6", merged.ndValue)
        assertEquals(listOf("UV"), merged.filterStack)
        assertEquals("profile note", merged.notes)
    }

    @Test
    fun aBlankProjectFallsBackRatherThanShippingAnEmptyProject() {
        val merged = Slate(project = "   ").applyTo(baseMetadata().copy(project = "untitled"))
        assertEquals("untitled", merged.project)
    }

    /** The take number is authoritative from the slate — including a deliberate reset to 1. */
    @Test
    fun theTakeNumberAlwaysComesFromTheSlate() {
        val merged = Slate(take = 1).applyTo(baseMetadata().copy(take = 99))
        assertEquals(1, merged.take)
    }

    @Test
    fun nextTakeAdvancesTheNumberAndClearsTheCircle() {
        val next = Slate(scene = "12", shot = "A", take = 3, circledTake = true, lensName = "Helios").nextTake()
        assertEquals(4, next.take)
        assertFalse("the circle belongs to the take that earned it", next.circledTake)
        assertEquals("scene persists across takes", "12", next.scene)
        assertEquals("shot persists across takes", "A", next.shot)
        assertEquals("the glass did not change", "Helios", next.lensName)
    }

    @Test
    fun nextShotResetsTheTakeButKeepsTheGlass() {
        val next = Slate(
            scene = "12", shot = "A", take = 7, circledTake = true,
            lensName = "Helios", ndValue = "ND0.9", filterStack = listOf("CPL"),
        ).nextShot("B")

        assertEquals("B", next.shot)
        assertEquals(1, next.take)
        assertFalse(next.circledTake)
        assertEquals("12", next.scene)
        assertEquals("Helios", next.lensName)
        assertEquals("ND0.9", next.ndValue)
        assertEquals(listOf("CPL"), next.filterStack)
    }

    @Test
    fun displayLineReadsLikeASlate() {
        assertEquals("12A/3", Slate(scene = "12", shot = "A", take = 3).displayLine())
        assertEquals("12A/3 (C)", Slate(scene = "12", shot = "A", take = 3, circledTake = true).displayLine())
        assertEquals("nothing slated yet", "--/1", Slate().displayLine())
        assertEquals("shot may be absent", "7/2", Slate(scene = "7", take = 2).displayLine())
    }

    @Test
    fun aDefaultSlateLeavesAProfileDerivedSidecarAlone() {
        val base = baseMetadata().copy(scene = "from profile", lensName = "profile lens")
        val merged = Slate().applyTo(base)

        assertEquals("from profile", merged.scene)
        assertEquals("profile lens", merged.lensName)
        assertNull(merged.shot)
        assertEquals(1, merged.take)
        assertFalse(merged.circledTake)
    }

    private fun baseMetadata(): MetadataWriter.ClipMetadata = MetadataWriter().buildFromProfile(
        profile = CaptureProfile(
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
            colorProfile = "flat_approximation",
            storageTarget = "internal",
            thermalProfileId = "balanced_field",
        ),
        cameraId = "0",
        romBuild = "oiw-0.1.0",
        kernelBuild = "oiw-cinema-aurora",
        deviceModel = "Xiaomi 14 Ultra",
    )
}
