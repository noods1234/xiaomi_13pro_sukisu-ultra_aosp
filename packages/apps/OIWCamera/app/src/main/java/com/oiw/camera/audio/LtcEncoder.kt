package com.oiw.camera.audio

/**
 * Encodes LTC frames to biphase-mark-coded PCM (docs/AUDIO_TIMECODE.md Tier 5 — jam-sync/genlock
 * out). Biphase mark: a transition at every bit boundary; a '1' adds a mid-bit transition, a '0'
 * does not.
 *
 * AUDIT FIX (J): timing is **phase-accurate for fractional bit clocks**, so real device rates work —
 * 44100 Hz (44100/(25*80) = 22.05 samples/bit) and drop-frame 29.97 (30000/1001 fps) no longer throw.
 * Transition sample positions are computed by rounding an exact running phase, anchored to a global
 * bit counter so rounding error can't accumulate across frames (drift-free). Level carries across
 * calls, so encoding consecutive frames yields a continuous signal.
 */
class LtcEncoder(
    private val sampleRate: Int = 48_000,
    private val frameRate: Double = 25.0,
    private val amplitude: Short = 20_000,
) {
    private val samplesPerBit: Double = sampleRate / (frameRate * LtcTimecode.BITS_PER_FRAME)
    private var level = 1          // +1 / -1, carried across bits so boundaries always transition
    private var globalBit = 0L     // total bits emitted across all frames (phase anchor)

    init {
        require(samplesPerBit >= 3.0) {
            "samplesPerBit=$samplesPerBit too small at ${sampleRate}Hz/${frameRate}fps to encode biphase transitions"
        }
    }

    /** Encodes one frame's PCM. Sample count varies slightly frame-to-frame for fractional rates. */
    fun encodeFrame(tc: LtcTimecode): ShortArray {
        val bits = LtcTimecode.toBits(tc)
        val startSample = Math.round(globalBit * samplesPerBit)
        val endSample = Math.round((globalBit + LtcTimecode.BITS_PER_FRAME) * samplesPerBit)
        val n = (endSample - startSample).toInt()

        // Transition sample indices (relative to this frame's start), sorted ascending.
        val transitions = ArrayList<Int>(LtcTimecode.BITS_PER_FRAME * 2)
        for (j in 0 until LtcTimecode.BITS_PER_FRAME) {
            val g = globalBit + j
            transitions.add((Math.round(g * samplesPerBit) - startSample).toInt())          // bit boundary
            if (bits[j] == 1) {
                transitions.add((Math.round((g + 0.5) * samplesPerBit) - startSample).toInt()) // mid-bit for '1'
            }
        }
        transitions.sort()

        val out = ShortArray(n)
        var ti = 0
        for (s in 0 until n) {
            while (ti < transitions.size && transitions[ti] == s) { level = -level; ti++ }
            out[s] = (level * amplitude).toShort()
        }
        globalBit += LtcTimecode.BITS_PER_FRAME
        return out
    }

    /** Nominal samples per frame (rounds for fractional rates). */
    fun samplesPerFrame(): Int = Math.round(samplesPerBit * LtcTimecode.BITS_PER_FRAME).toInt()
}
