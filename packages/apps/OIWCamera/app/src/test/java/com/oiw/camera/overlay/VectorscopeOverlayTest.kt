package com.oiw.camera.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorscopeOverlayTest {

    private fun chroma(n: Int, u: (Int) -> Int, v: (Int) -> Int): VectorscopeOverlay.Chroma {
        val uu = ByteArray(n); val vv = ByteArray(n)
        for (i in 0 until n) { uu[i] = u(i).toByte(); vv[i] = v(i).toByte() }
        return VectorscopeOverlay.Chroma(n, 1, uu, vv)
    }

    @Test
    fun `neutral gray lands at the center`() {
        val vs = VectorscopeOverlay(size = 256)
        val grid = vs.compute(chroma(1000, { 128 }, { 128 }))
        assertEquals("all neutral samples at center", 1000, grid[128 * 256 + 128])
        val (bx, by) = vs.centerBias(grid)
        assertEquals(0.0, bx, 0.001)
        assertEquals(0.0, by, 0.001)
    }

    @Test
    fun `positive U pushes right and positive V pushes up`() {
        val vs = VectorscopeOverlay(size = 256)
        val (bx, by) = vs.centerBias(vs.compute(chroma(100, { 200 }, { 128 })))
        assertTrue("+U should bias right (x>0), got $bx", bx > 0)
        assertEquals("V neutral means no vertical bias", 0.0, by, 0.001)

        val (bx2, by2) = vs.centerBias(vs.compute(chroma(100, { 128 }, { 200 })))
        assertEquals(0.0, bx2, 0.001)
        assertTrue("+V should bias up (y>0), got $by2", by2 > 0)
    }

    @Test
    fun `saturated chroma is flagged out of gamut, neutral is not`() {
        val vs = VectorscopeOverlay(size = 256)
        val neutral = vs.outOfGamutFraction(vs.compute(chroma(500, { 128 }, { 128 })))
        assertEquals("neutral content is fully in gamut", 0.0, neutral, 0.001)

        val hot = vs.outOfGamutFraction(vs.compute(chroma(500, { 255 }, { 255 })))
        assertTrue("fully saturated corner should read out of gamut, got $hot", hot > 0.9)
    }

    @Test
    fun `vectorscope at quarter-4K chroma stays within per-frame budget`() {
        // YUV_420_888 chroma planes are quarter-size vs luma: 960x540 luma -> 480x270 chroma.
        val vs = VectorscopeOverlay(size = 256)
        val c = chroma(480 * 270, { (it * 7) % 256 }, { (it * 13) % 256 })

        repeat(20) { vs.compute(c) }
        val iterations = 50
        val start = System.nanoTime()
        repeat(iterations) { vs.compute(c) }
        val msPerFrame = (System.nanoTime() - start) / 1_000_000.0 / iterations

        println("VECTORSCOPE BENCH: 480x270 chroma = %.2f ms/frame".format(msPerFrame))
        assertTrue("vectorscope %.2f ms/frame exceeds 10 ms budget".format(msPerFrame), msPerFrame < 10.0)
    }
}
