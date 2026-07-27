package com.oiw.camera.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tier 1.5 (Robolectric): [OverlayView] is an Android `View`, so until now nothing had ever
 * constructed one — its flag mapping and its whole draw path were compile-only.
 *
 * Two things are worth executing here:
 *
 *  - **The flag mapping**, against the real view rather than a test-side copy of the table. The
 *    defaults used to be `true`, which meant a profile listing no overlays still drew four of them
 *    (`docs/AUDIT_FINDINGS.md` finding W).
 *  - **The draw path itself.** Every scope renderer indexes into a flat counts array; an off-by-one
 *    in that arithmetic is an `ArrayIndexOutOfBoundsException` on the UI thread, mid-take. Drawing
 *    to a real `Canvas` is the cheapest way to find that without a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayViewRobolectricTest {

    private lateinit var view: OverlayView

    @Before
    fun setUp() {
        view = OverlayView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 1920, 1080)
    }

    @Test
    fun aFreshViewDrawsNothingUntilAProfileAsksForIt() {
        assertFalse(view.showHistogram)
        assertFalse(view.showZebra)
        assertFalse(view.showPeaking)
        assertFalse(view.showFalseColor)
        assertFalse(view.showWaveform)
        assertFalse(view.showVectorscope)
        assertFalse(view.showRgbParade)
        assertFalse(view.showGuides)
    }

    @Test
    fun applyProfileOverlaysMatchesTheDeclaredVocabulary() {
        val requested = listOf(
            MonitoringOverlays.HISTOGRAM,
            MonitoringOverlays.WAVEFORM,
            MonitoringOverlays.RGB_PARADE,
        )
        view.applyProfileOverlays(requested)

        val expected = MonitoringOverlays.viewFlags(requested)
        assertEquals(expected.getValue(MonitoringOverlays.HISTOGRAM), view.showHistogram)
        assertEquals(expected.getValue(MonitoringOverlays.ZEBRA), view.showZebra)
        assertEquals(expected.getValue(MonitoringOverlays.FOCUS_PEAKING), view.showPeaking)
        assertEquals(expected.getValue(MonitoringOverlays.FALSE_COLOR), view.showFalseColor)
        assertEquals(expected.getValue(MonitoringOverlays.WAVEFORM), view.showWaveform)
        assertEquals(expected.getValue(MonitoringOverlays.VECTORSCOPE), view.showVectorscope)
        assertEquals(expected.getValue(MonitoringOverlays.RGB_PARADE), view.showRgbParade)
        assertEquals(expected.getValue(MonitoringOverlays.FRAME_GUIDES), view.showGuides)
    }

    /** A profile switch must turn overlays OFF as well as on, or state leaks across profiles. */
    @Test
    fun switchingToAProfileWithFewerOverlaysClearsTheOldOnes() {
        view.applyProfileOverlays(MonitoringOverlays.ALL.toList())
        assertTrue(view.showZebra)

        view.applyProfileOverlays(emptyList())
        assertFalse("stale overlay survived a profile switch", view.showZebra)
        assertFalse(view.showRgbParade)
        assertFalse(view.showGuides)
    }

    @Test
    fun everyScopeRendersWithoutIndexingPastItsBuffers() {
        view.applyProfileOverlays(MonitoringOverlays.ALL.toList())
        submitAllScopes()

        // Would throw on any indexing slip in drawWaveform / drawRgbParade / drawVectorscope.
        view.draw(canvas())
    }

    @Test
    fun rendersBeforeAnyFrameHasArrived() {
        view.applyProfileOverlays(MonitoringOverlays.ALL.toList())
        view.draw(canvas()) // null grids everywhere — must be a no-op, not a crash
    }

    /** The parade panels shift right when the waveform shares the bottom edge; both must fit. */
    @Test
    fun waveformAndParadeCoexistOnTheBottomEdge() {
        view.applyProfileOverlays(listOf(MonitoringOverlays.WAVEFORM, MonitoringOverlays.RGB_PARADE))
        submitAllScopes()
        view.draw(canvas())

        view.applyProfileOverlays(listOf(MonitoringOverlays.RGB_PARADE))
        view.draw(canvas())
    }

    /** A narrow view must not produce negative panel widths. */
    @Test
    fun rendersInADegenerateViewport() {
        val tiny = OverlayView(RuntimeEnvironment.getApplication())
        tiny.layout(0, 0, 8, 8)
        tiny.applyProfileOverlays(MonitoringOverlays.ALL.toList())
        tiny.draw(Canvas(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)))
    }

    private fun canvas() = Canvas(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888))

    private fun submitAllScopes() {
        val lw = 64
        val lh = 64
        val cw = lw / 2
        val ch = lh / 2
        val luma = LumaFrame.Luma(lw, lh, ByteArray(lw * lh) { (it % 256).toByte() })
        val chroma = VectorscopeOverlay.Chroma(
            cw, ch, ByteArray(cw * ch) { 128.toByte() }, ByteArray(cw * ch) { 128.toByte() },
        )

        val waveform = WaveformOverlay()
        val waveformCounts = waveform.compute(luma)
        val vectorscope = VectorscopeOverlay()
        val vectorGrid = vectorscope.compute(chroma)
        val parade = RgbParadeOverlay()
        val paradeCounts = parade.compute(luma, chroma)

        view.submit(
            luma,
            BooleanArray(lw * lh) { it % 3 == 0 },
            BooleanArray(lw * lh) { it % 5 == 0 },
            IntArray(64) { it },
            IntArray(lw * lh) { android.graphics.Color.GRAY },
            Triple(waveformCounts, waveform.outputSize(), waveform.peak(waveformCounts)),
            Triple(vectorGrid, vectorscope.outputSize(), vectorGrid.max()),
            Triple(paradeCounts, parade.outputSize(), parade.peak(paradeCounts)),
        )
    }
}
