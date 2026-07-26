package com.oiw.camera.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformOverlayTest {

    private fun luma(w: Int, h: Int, fill: (Int, Int) -> Int): LumaFrame.Luma {
        val d = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) d[y * w + x] = fill(x, y).toByte()
        return LumaFrame.Luma(w, h, d)
    }

    @Test
    fun `flat gray frame puts every count in one level row`() {
        val wf = WaveformOverlay(columns = 64, levels = 256)
        val counts = wf.compute(luma(64, 32) { _, _ -> 128 })
        // Every column should have all 32 rows' pixels at level 128 and nothing elsewhere.
        for (col in 0 until 64) {
            assertEquals("col $col at level 128", 32, counts[col * 256 + 128])
            assertEquals("col $col at level 0", 0, counts[col * 256 + 0])
        }
    }

    @Test
    fun `horizontal ramp places increasing levels in increasing columns`() {
        val wf = WaveformOverlay(columns = 4, levels = 256)
        // Left quarter black, right quarter white.
        val counts = wf.compute(luma(64, 8) { x, _ -> if (x < 16) 0 else if (x >= 48) 255 else 128 })
        assertTrue("leftmost column should hold blacks", counts[0 * 256 + 0] > 0)
        assertTrue("rightmost column should hold whites", counts[3 * 256 + 255] > 0)
        assertEquals("leftmost column should have no whites", 0, counts[0 * 256 + 255])
    }

    @Test
    fun `total counts equal sampled pixel count`() {
        val wf = WaveformOverlay(columns = 32, levels = 256)
        val counts = wf.compute(luma(128, 64) { x, y -> (x + y) % 256 })
        assertEquals(128 * 64, counts.sum())
    }

    /**
     * Measures the real per-frame cost at 960x540 — the quarter-res analysis size for a 4K capture.
     * This exists to keep an earlier *asserted* claim ("CPU waveform is too slow, needs GPU") honest:
     * the budget below is what a 24fps overlay can afford on a background analysis thread while the
     * encoder runs. If a future change regresses past it, this fails and the GPU path is mandatory.
     *
     * Note this is a JVM/desktop measurement — a phone's big core is slower, so the budget is set
     * conservatively (well under a 41 ms frame period) rather than at the wire.
     */
    @Test
    fun `waveform at quarter-4K stays within per-frame budget`() {
        val wf = WaveformOverlay(columns = 256, levels = 256)
        val frame = luma(960, 540) { x, y -> (x * 7 + y * 13) % 256 }

        repeat(20) { wf.compute(frame) } // warm up JIT

        val iterations = 50
        val start = System.nanoTime()
        repeat(iterations) { wf.compute(frame) }
        val msPerFrame = (System.nanoTime() - start) / 1_000_000.0 / iterations

        println("WAVEFORM BENCH: 960x540 full-sample = %.2f ms/frame".format(msPerFrame))
        assertTrue(
            "waveform took %.2f ms/frame, over the 15 ms budget — GPU path now required".format(msPerFrame),
            msPerFrame < 15.0,
        )
    }

    @Test
    fun `subsampling reduces cost and preserves shape`() {
        val wf = WaveformOverlay(columns = 128, levels = 256)
        val frame = luma(960, 540) { _, _ -> 200 }
        val full = wf.compute(frame).sum()
        val subsampled = wf.compute(frame, rowStep = 2, colStep = 2).sum()
        assertEquals("row/col step 2 should sample a quarter of the pixels", full / 4, subsampled)
    }
}
