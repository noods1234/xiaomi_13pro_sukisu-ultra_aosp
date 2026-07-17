package com.oiw.camera.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real behavioral coverage for the three CPU monitoring overlays. These run on plain JVM (the
 * algorithms take a LumaFrame.Luma and return arrays — no Android types), so they are exercised in
 * the same audited unit-test pass as the parser/exporter logic.
 */
class OverlayAlgorithmsTest {

    private fun luma(w: Int, h: Int, fill: (Int, Int) -> Int): LumaFrame.Luma {
        val data = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) data[y * w + x] = fill(x, y).toByte()
        return LumaFrame.Luma(w, h, data)
    }

    @Test
    fun `histogram counts every-4th sampled pixel into the right bin`() {
        // 16 pixels all = 100; compute() samples i += 4 => 4 samples land in bin 100.
        val l = luma(16, 1) { _, _ -> 100 }
        val bins = HistogramOverlay().compute(l)
        assertEquals(4, bins[100])
        assertEquals(0, bins[99])
    }

    @Test
    fun `clipping fraction reports pinned blacks and whites`() {
        // 8 pixels: two black (0), two white (255), rest mid. Sampled i+=4 => indices 0 and 4.
        val data = byteArrayOf(0, 50, 50, 50, 255.toByte(), 50, 50, 50)
        val l = LumaFrame.Luma(8, 1, data)
        val h = HistogramOverlay()
        val (black, white) = h.clippingFraction(h.compute(l))
        assertEquals(0.5, black, 0.001) // index 0 sampled = black
        assertEquals(0.5, white, 0.001) // index 4 sampled = white
    }

    @Test
    fun `zebra masks highlights and crushed blacks but not mids`() {
        val z = ZebraOverlay(thresholdHigh = 245, thresholdLow = 16)
        val l = luma(3, 1) { x, _ -> intArrayOf(250, 100, 10)[x] }
        val mask = z.computeMask(l)
        assertTrue("250 >= 245 should zebra", mask[0])
        assertFalse("100 is a mid, no zebra", mask[1])
        assertTrue("10 <= 16 should zebra", mask[2])
    }

    @Test
    fun `focus peaking fires on an edge and stays quiet on flat areas`() {
        // Vertical edge: left half black, right half white.
        val l = luma(8, 8) { x, _ -> if (x < 4) 0 else 255 }
        val mask = FocusPeakingOverlay(threshold = 40, gridStep = 2).computeMask(l)
        // Some pixel on the boundary column region must peak.
        val edgePeaks = (1 until 7).any { y -> mask[y * 8 + 3] || mask[y * 8 + 4] }
        assertTrue("edge should produce peaking", edgePeaks)
        // A deep-interior flat pixel (all neighbors identical) must not peak.
        assertFalse("flat black interior should not peak", mask[4 * 8 + 1])
    }

    @Test
    fun `focus peaking threshold gates sensitivity`() {
        // A gentle 1-LSB gradient should not trip a high threshold.
        val l = luma(8, 8) { x, _ -> x } // gradient 0..7 across width
        val strict = FocusPeakingOverlay(threshold = 200, gridStep = 1).computeMask(l)
        assertFalse("no pixel should exceed a 200 threshold on a 1-LSB gradient", strict.any { it })
    }
}
