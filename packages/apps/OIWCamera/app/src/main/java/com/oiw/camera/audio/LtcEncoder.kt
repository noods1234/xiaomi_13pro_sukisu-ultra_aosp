package com.oiw.camera.audio

/**
 * Encodes LTC frames to biphase-mark-coded PCM (docs/AUDIO_TIMECODE.md Tier 5 — jam-sync/genlock
 * out). Biphase mark: a transition at every bit boundary; a '1' adds a mid-bit transition, a '0'
 * does not. Pure — used both to drive an audio-out track and to generate deterministic test signals.
 */
class LtcEncoder(
    private val sampleRate: Int = 48_000,
    private val frameRate: Int = 25,
    private val amplitude: Short = 20_000,
) {
    private val samplesPerBit: Int = sampleRate / (frameRate * LtcTimecode.BITS_PER_FRAME)
    private var level = 1 // +1 / -1, carried across bits so boundaries always transition

    init {
        require(sampleRate % (frameRate * LtcTimecode.BITS_PER_FRAME) == 0) {
            "sampleRate ($sampleRate) must divide evenly by frameRate*80 for a clean bit clock"
        }
    }

    /** Encodes one frame's PCM (samplesPerBit*80 samples). Level carries into the next call. */
    fun encodeFrame(tc: LtcTimecode): ShortArray {
        val bits = LtcTimecode.toBits(tc)
        val out = ShortArray(samplesPerBit * LtcTimecode.BITS_PER_FRAME)
        var idx = 0
        val half = samplesPerBit / 2
        for (bit in bits) {
            level = -level // transition at every bit boundary
            for (s in 0 until samplesPerBit) {
                if (bit == 1 && s == half) level = -level // mid-bit transition for '1'
                out[idx++] = (level * amplitude).toShort()
            }
        }
        return out
    }

    fun samplesPerFrame(): Int = samplesPerBit * LtcTimecode.BITS_PER_FRAME
}
