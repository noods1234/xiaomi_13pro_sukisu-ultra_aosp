package com.oiw.camera.audio

/**
 * Streaming SMPTE LTC decoder (docs/AUDIO_TIMECODE.md Tier 4). Recovers timecode from PCM captured
 * on an audio input (mic/USB) fed a house LTC signal. Pure DSP — no Android — so it is unit-tested
 * against the encoder's synthetic signal by round-trip.
 *
 * Algorithm: detect transitions (zero-sign changes); measure the sample interval between them;
 * classify each interval as a full bit period ('0') or a half period (two consecutive halves = '1'),
 * i.e. biphase-mark decode; accumulate bits and emit a frame whenever the trailing 16 bits match the
 * LTC sync word. Free-wheeling PLL / jitter tracking is intentionally omitted for v1 — a clean input
 * (external TC device or the app's own encoder) decodes deterministically; documented, not hidden.
 */
class LtcDecoder(
    sampleRate: Int = 48_000,
    frameRate: Int = 25,
    private val onFrame: (LtcTimecode) -> Unit,
) {
    private val samplesPerBit: Double = sampleRate.toDouble() / (frameRate * LtcTimecode.BITS_PER_FRAME)
    // Interval <= this is a half-bit ('1' pair); above is a full bit ('0'). Midway between 0.5 and 1.0.
    private val shortMax: Double = samplesPerBit * 0.75
    // Anything longer than this is a dropout/gap — reset the bit assembler rather than emit garbage.
    private val gapMax: Double = samplesPerBit * 1.6

    private var lastSign = 0
    private var samplesSinceTransition = 0
    private var haveFirstTransition = false
    private var pendingHalf = false
    private val window = ArrayDeque<Int>() // last <=80 decoded bits, oldest first

    /** Feed [count] mono PCM samples from [buf]. Emits via [onFrame] as frames complete. */
    fun process(buf: ShortArray, count: Int = buf.size) {
        for (i in 0 until count) {
            val sign = if (buf[i] >= 0) 1 else -1
            if (!haveFirstTransition) {
                if (lastSign != 0 && sign != lastSign) {
                    haveFirstTransition = true
                    samplesSinceTransition = 0
                }
                lastSign = sign
                continue
            }
            samplesSinceTransition++
            if (sign != lastSign) {
                onInterval(samplesSinceTransition.toDouble())
                samplesSinceTransition = 0
                lastSign = sign
            }
        }
    }

    private fun onInterval(interval: Double) {
        when {
            interval > gapMax -> { pendingHalf = false } // dropout: drop the partial bit
            interval > shortMax -> { pushBit(0); pendingHalf = false } // full period => '0'
            else -> { // half period
                if (pendingHalf) { pushBit(1); pendingHalf = false } else pendingHalf = true
            }
        }
    }

    private fun pushBit(bit: Int) {
        window.addLast(bit)
        while (window.size > LtcTimecode.BITS_PER_FRAME) window.removeFirst()
        if (window.size == LtcTimecode.BITS_PER_FRAME) {
            val bits = window.toIntArray()
            if (LtcTimecode.isSyncAt(bits, 64)) {
                onFrame(LtcTimecode.fromBits(bits))
                window.clear() // consume the frame; next frame's bits start fresh
            }
        }
    }
}

private fun ArrayDeque<Int>.toIntArray(): IntArray {
    val out = IntArray(size)
    var i = 0
    for (v in this) out[i++] = v
    return out
}
