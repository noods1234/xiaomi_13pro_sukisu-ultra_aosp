package com.oiw.camera.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 (pure JVM, executed): [RgbParadeOverlay] and its [YuvToRgb] colour conversion.
 *
 * The property that matters most is [neutralChromaGivesIdenticalChannels]. A scope whose channels
 * disagree on a genuinely grey frame is worse than no scope — it would send a colourist chasing a
 * cast that only exists in the monitor.
 */
class RgbParadeOverlayTest {

    private val columns = 16
    private val levels = 256
    private val parade = RgbParadeOverlay(columns = columns, levels = levels)

    // --- YuvToRgb ---

    @Test
    fun neutralChromaRoundTripsLumaUnchangedOnEveryChannel() {
        for (y in 0..255) {
            assertEquals("R at Y=$y", y, YuvToRgb.red(y, 128))
            assertEquals("G at Y=$y", y, YuvToRgb.green(y, 128, 128))
            assertEquals("B at Y=$y", y, YuvToRgb.blue(y, 128))
        }
    }

    @Test
    fun chromaMovesTheExpectedChannelsInTheExpectedDirection() {
        // V above neutral is the red axis: R up, G down, B untouched.
        assertTrue(YuvToRgb.red(128, 200) > 128)
        assertTrue(YuvToRgb.green(128, 128, 200) < 128)
        assertEquals(128, YuvToRgb.blue(128, 128))

        // U above neutral is the blue axis: B up, G down, R untouched.
        assertTrue(YuvToRgb.blue(128, 200) > 128)
        assertTrue(YuvToRgb.green(128, 200, 128) < 128)
        assertEquals(128, YuvToRgb.red(128, 128))
    }

    @Test
    fun conversionClampsRatherThanWrappingAtBothEnds() {
        assertEquals(255, YuvToRgb.red(255, 255))
        assertEquals(0, YuvToRgb.red(0, 0))
        assertEquals(255, YuvToRgb.blue(255, 255))
        assertEquals(0, YuvToRgb.blue(0, 0))
        assertEquals(255, YuvToRgb.green(255, 0, 0))
        assertEquals(0, YuvToRgb.green(0, 255, 255))
    }

    // --- parade ---

    @Test
    fun aFlatGreyFrameStacksAllThreeChannelsOnTheSameLevel() {
        val counts = parade.compute(luma(64, 64, 120), chroma(32, 32, 128, 128))

        for (channel in 0 until RgbParadeOverlay.CHANNELS) {
            val base = parade.channelOffset(channel)
            for (col in 0 until columns) {
                for (level in 0 until levels) {
                    val n = counts[base + col * levels + level]
                    if (level == 120) {
                        assertTrue("channel $channel col $col should be populated at level 120", n > 0)
                    } else {
                        assertEquals("channel $channel col $col level $level should be empty", 0, n)
                    }
                }
            }
        }
    }

    @Test
    fun everyChromaSampleIsCountedOnceInEachChannel() {
        val cw = 32
        val ch = 32
        val counts = parade.compute(luma(64, 64, 90), chroma(cw, ch, 128, 128))

        for (channel in 0 until RgbParadeOverlay.CHANNELS) {
            val base = parade.channelOffset(channel)
            var total = 0
            for (i in 0 until columns * levels) total += counts[base + i]
            assertEquals("channel $channel total", cw * ch, total)
        }
    }

    @Test
    fun aBlueCastSeparatesTheBlueTraceFromTheOthers() {
        // U well above neutral, V neutral: blue lifts, green dips, red holds.
        val counts = parade.compute(luma(64, 64, 128), chroma(32, 32, u = 200, v = 128))

        assertEquals(YuvToRgb.red(128, 128), dominantLevel(counts, RgbParadeOverlay.RED))
        assertEquals(YuvToRgb.blue(128, 200), dominantLevel(counts, RgbParadeOverlay.BLUE))
        assertTrue(
            "blue must sit above red on a blue cast",
            dominantLevel(counts, RgbParadeOverlay.BLUE) > dominantLevel(counts, RgbParadeOverlay.RED),
        )
        assertTrue(
            "green must sit below red on a blue cast",
            dominantLevel(counts, RgbParadeOverlay.GREEN) < dominantLevel(counts, RgbParadeOverlay.RED),
        )
    }

    /** A horizontal gradient must read as a diagonal trace, not a flat line — x maps to column. */
    @Test
    fun horizontalPositionMapsToTheParadeColumn() {
        val cw = columns // one chroma sample column per output column
        val ch = 8
        val y = ByteArray(cw * ch)
        for (row in 0 until ch) {
            for (col in 0 until cw) y[row * cw + col] = (col * 16).toByte()
        }
        val counts = parade.compute(
            LumaFrame.Luma(cw, ch, y),
            VectorscopeOverlay.Chroma(cw, ch, neutral(cw * ch), neutral(cw * ch)),
        )

        val base = parade.channelOffset(RgbParadeOverlay.RED)
        for (col in 0 until columns) {
            val expected = (col * 16) and 0xFF
            assertTrue(
                "column $col should hold level $expected",
                counts[base + col * levels + expected] > 0,
            )
        }
    }

    @Test
    fun stepSubsamplesWithoutChangingWhereTheTraceSits() {
        val full = parade.compute(luma(64, 64, 100), chroma(32, 32, 128, 128), step = 1).copyOf()
        val sub = parade.compute(luma(64, 64, 100), chroma(32, 32, 128, 128), step = 2)

        assertEquals(dominantLevel(full, RgbParadeOverlay.RED), dominantLevel(sub, RgbParadeOverlay.RED))
        assertTrue("subsampling must cost counts", parade.peak(sub) < parade.peak(full))
    }

    @Test
    fun mismatchedOrEmptyPlanesYieldAnEmptyGridRatherThanThrowing() {
        val emptyFrame = parade.compute(LumaFrame.Luma(0, 0, ByteArray(0)), chroma(0, 0, 128, 128))
        assertEquals(0, parade.peak(emptyFrame))

        // Chroma claiming more samples than its buffers hold — a truncated frame.
        val truncated = parade.compute(
            luma(64, 64, 100),
            VectorscopeOverlay.Chroma(32, 32, ByteArray(10), ByteArray(10)),
        )
        assertEquals("a short buffer must not be read past its end", 0, parade.peak(truncated))
    }

    /**
     * Budget test, same discipline as the waveform's. Measured on a 480x270 chroma grid — the
     * chroma resolution of a 960x540 analysis frame, itself the quarter-res of 4K. If this ever
     * regresses past the budget, the GPU path stops being optional.
     */
    @Test
    fun paradeStaysWithinItsPerFrameBudget() {
        val bench = RgbParadeOverlay()
        val lw = 960
        val lh = 540
        val cw = lw / 2
        val ch = lh / 2
        val y = ByteArray(lw * lh) { ((it * 7) % 256).toByte() }
        val u = ByteArray(cw * ch) { ((it * 3) % 256).toByte() }
        val v = ByteArray(cw * ch) { ((it * 5) % 256).toByte() }
        val lumaFrame = LumaFrame.Luma(lw, lh, y)
        val chromaFrame = VectorscopeOverlay.Chroma(cw, ch, u, v)

        repeat(5) { bench.compute(lumaFrame, chromaFrame) } // warm up JIT
        val iterations = 20
        val start = System.nanoTime()
        repeat(iterations) { bench.compute(lumaFrame, chromaFrame) }
        val msPerFrame = (System.nanoTime() - start) / 1_000_000.0 / iterations

        println("RGB PARADE BENCH: ${cw}x$ch chroma = %.2f ms/frame".format(msPerFrame))
        assertTrue(
            "RGB parade took %.2f ms/frame, over the 20 ms budget".format(msPerFrame),
            msPerFrame < 20.0,
        )
    }

    // --- helpers ---

    private fun dominantLevel(counts: IntArray, channel: Int): Int {
        val base = parade.channelOffset(channel)
        var bestLevel = -1
        var best = 0
        for (col in 0 until columns) {
            for (level in 0 until levels) {
                val n = counts[base + col * levels + level]
                if (n > best) { best = n; bestLevel = level }
            }
        }
        return bestLevel
    }

    private fun luma(w: Int, h: Int, value: Int) =
        LumaFrame.Luma(w, h, ByteArray(w * h) { value.toByte() })

    private fun chroma(w: Int, h: Int, u: Int, v: Int) = VectorscopeOverlay.Chroma(
        w, h, ByteArray(w * h) { u.toByte() }, ByteArray(w * h) { v.toByte() },
    )

    private fun neutral(size: Int) = ByteArray(size) { 128.toByte() }
}
