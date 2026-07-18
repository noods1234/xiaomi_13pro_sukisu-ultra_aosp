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

    @Test
    fun `full signal round trip - encode PCM then decode recovers consecutive timecodes`() {
        val sampleRate = 48_000
        val fps = 25
        val encoder = LtcEncoder(sampleRate, fps)
        val start = LtcTimecode(10, 20, 30, 0)

        // Encode 6 consecutive frames into one continuous PCM buffer (level carries across frames).
        val frames = (0 until 6).map { LtcTimecode(start.hours, start.minutes, start.seconds, it) }
        val pcm = ShortArray(encoder.samplesPerFrame() * frames.size)
        var off = 0
        for (f in frames) {
            val chunk = encoder.encodeFrame(f)
            chunk.copyInto(pcm, off); off += chunk.size
        }

        val decoded = mutableListOf<LtcTimecode>()
        LtcDecoder(sampleRate, fps) { decoded += it }.process(pcm)

        // The decoder needs a full frame of history before the first sync; expect >=4 of 6 recovered,
        // and every recovered frame must match the frame it was encoded from (no bit errors).
        assertTrue("expected most frames decoded, got ${decoded.size}: $decoded", decoded.size >= 4)
        for (d in decoded) {
            assertEquals(10, d.hours); assertEquals(20, d.minutes); assertEquals(30, d.seconds)
            assertTrue("frame ${d.frames} out of encoded range", d.frames in 0..5)
        }
        // Frames must be strictly increasing in the order recovered.
        for (i in 1 until decoded.size) {
            assertTrue("frames should increase: ${decoded[i-1].frames} -> ${decoded[i].frames}",
                decoded[i].frames > decoded[i - 1].frames)
        }
    }
}
