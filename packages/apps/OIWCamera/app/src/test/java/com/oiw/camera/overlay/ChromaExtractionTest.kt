package com.oiw.camera.overlay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Covers the two hardware layouts YUV_420_888 actually ships on real devices. Getting pixelStride
 * wrong is silent corruption — an interleaved plane read contiguously returns alternating U and V
 * samples, which would render a plausible-looking but completely wrong vectorscope.
 */
class ChromaExtractionTest {

    @Test
    fun `packed plane (pixelStride 1, no padding) reads straight through`() {
        val w = 4; val h = 2
        val buf = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val out = ChromaExtraction.readPlane(buf, rowStride = w, pixelStride = 1, width = w, height = h)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), out)
    }

    @Test
    fun `packed plane with row padding skips the padding bytes`() {
        val w = 3; val h = 2; val rowStride = 5 // 2 bytes of alignment padding per row
        val buf = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 99, 99, 4, 5, 6, 99, 99))
        val out = ChromaExtraction.readPlane(buf, rowStride, pixelStride = 1, width = w, height = h)
        assertArrayEquals("padding must not leak into the output", byteArrayOf(1, 2, 3, 4, 5, 6), out)
    }

    @Test
    fun `interleaved plane (pixelStride 2) picks only its own channel`() {
        // Semi-planar NV12-style: U,V,U,V... The U plane view must yield only the U samples.
        val w = 3; val h = 2; val rowStride = 6
        val bytes = byteArrayOf(
            10, 99, 11, 99, 12, 99, // row 0: U=10,11,12 interleaved with V=99
            13, 99, 14, 99, 15, 99, // row 1: U=13,14,15
        )
        val out = ChromaExtraction.readPlane(ByteBuffer.wrap(bytes), rowStride, pixelStride = 2, w, h)
        assertArrayEquals(
            "interleaved read must skip the other channel, not return it",
            byteArrayOf(10, 11, 12, 13, 14, 15), out,
        )
    }

    @Test
    fun `interleaved plane with row padding handles both strides together`() {
        val w = 2; val h = 2; val rowStride = 6 // 2 samples * stride 2 = 4 used, 2 padding
        val bytes = byteArrayOf(
            20, 99, 21, 99, 77, 77,
            22, 99, 23, 99, 77, 77,
        )
        val out = ChromaExtraction.readPlane(ByteBuffer.wrap(bytes), rowStride, pixelStride = 2, w, h)
        assertArrayEquals(byteArrayOf(20, 21, 22, 23), out)
    }

    @Test
    fun `truncated buffer does not throw`() {
        // Defensive: a short/partial buffer must degrade, not crash the analysis thread.
        val bytes = byteArrayOf(1, 2, 3)
        val out = ChromaExtraction.readPlane(ByteBuffer.wrap(bytes), rowStride = 4, pixelStride = 1, width = 4, height = 4)
        assertEquals(16, out.size)
    }

    @Test
    fun `extracted chroma feeds the vectorscope end to end`() {
        // Neutral 128 chroma must land dead-centre — proves the extraction output is actually
        // consumable by VectorscopeOverlay (the wiring F2 was missing).
        val w = 4; val h = 4
        val neutral = ByteArray(w * h) { 128.toByte() }
        val u = ChromaExtraction.readPlane(ByteBuffer.wrap(neutral), w, 1, w, h)
        val v = ChromaExtraction.readPlane(ByteBuffer.wrap(neutral), w, 1, w, h)
        val vs = VectorscopeOverlay(size = 64)
        val (bx, by) = vs.centerBias(vs.compute(VectorscopeOverlay.Chroma(w, h, u, v)))
        assertEquals(0.0, bx, 0.001)
        assertEquals(0.0, by, 0.001)
    }
}
