package com.oiw.camera.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LtcCodecTest {

    @Test
    fun `bit round trip preserves timecode fields`() {
        val tc = LtcTimecode(hours = 12, minutes = 34, seconds = 56, frames = 24)
        val decoded = LtcTimecode.fromBits(LtcTimecode.toBits(tc))
        assertEquals(tc, decoded)
    }

    @Test
    fun `sync word sits at bit 64`() {
        val bits = LtcTimecode.toBits(LtcTimecode(0, 0, 0, 0))
        assertTrue(LtcTimecode.isSyncAt(bits, 64))
    }

    @Test
    fun `drop-frame flag survives round trip and formatting`() {
        val tc = LtcTimecode(1, 2, 3, 4, dropFrame = true)
        val decoded = LtcTimecode.fromBits(LtcTimecode.toBits(tc))
        assertEquals(tc, decoded)
        assertEquals("01:02:03;04", decoded.toString())
    }

    /** Encodes [count] consecutive frames to one continuous PCM buffer and returns what decodes back. */
    private fun roundTrip(sampleRate: Int, fps: Double, count: Int): List<LtcTimecode> {
        val encoder = LtcEncoder(sampleRate, fps)
        val chunks = (0 until count).map { encoder.encodeFrame(LtcTimecode(10, 20, 30, it)) }
        val pcm = ShortArray(chunks.sumOf { it.size })
        var off = 0
        for (c in chunks) { c.copyInto(pcm, off); off += c.size }
        val decoded = mutableListOf<LtcTimecode>()
        LtcDecoder(sampleRate, fps) { decoded += it }.process(pcm)
        return decoded
    }

    private fun assertCleanRun(decoded: List<LtcTimecode>, count: Int, label: String) {
        assertTrue("$label: expected most of $count frames, got ${decoded.size}: $decoded", decoded.size >= count - 2)
        for (d in decoded) {
            assertEquals("$label hours", 10, d.hours)
            assertEquals("$label minutes", 20, d.minutes)
            assertEquals("$label seconds", 30, d.seconds)
            assertTrue("$label frame ${d.frames} out of range", d.frames in 0 until count)
        }
        for (i in 1 until decoded.size) {
            assertTrue("$label frames should increase: ${decoded[i-1].frames} -> ${decoded[i].frames}",
                decoded[i].frames > decoded[i - 1].frames)
        }
    }

    @Test
    fun `round trip at 48000 Hz 25 fps (integer bit clock)`() {
        assertCleanRun(roundTrip(48_000, 25.0, 6), 6, "48k/25")
    }

    @Test
    fun `round trip at 44100 Hz 25 fps (fractional 22_05 samples per bit)`() {
        // AUDIT FIX J: 44100 is the common Android mic default; bit clock is fractional here.
        assertCleanRun(roundTrip(44_100, 25.0, 8), 8, "44.1k/25")
    }

    @Test
    fun `round trip at 44100 Hz 30 fps (fractional 18_375 samples per bit)`() {
        assertCleanRun(roundTrip(44_100, 30.0, 8), 8, "44.1k/30")
    }

    @Test
    fun `round trip at 48000 Hz 29_97 drop frame (fractional fps)`() {
        assertCleanRun(roundTrip(48_000, 30000.0 / 1001.0, 8), 8, "48k/29.97")
    }
}
