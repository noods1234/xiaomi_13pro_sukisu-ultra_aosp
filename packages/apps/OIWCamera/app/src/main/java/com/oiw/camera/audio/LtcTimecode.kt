package com.oiw.camera.audio

/**
 * SMPTE 12M timecode value + the 80-bit LTC frame codec primitives shared by the encoder and
 * decoder. Pure data/bit logic — no Android, fully unit-tested (docs/AUDIO_TIMECODE.md Tier 4).
 */
data class LtcTimecode(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val frames: Int,
    val dropFrame: Boolean = false,
) {
    override fun toString(): String {
        val sep = if (dropFrame) ';' else ':'
        return "%02d:%02d:%02d%c%02d".format(hours, minutes, seconds, sep, frames)
    }

    companion object {
        const val BITS_PER_FRAME = 80
        // 16-bit sync word, transmission order (bits 64..79): 0011 1111 1111 1101.
        val SYNC_WORD = intArrayOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1)

        /** Encodes a timecode into the 80 LTC bits (transmission order, bit 0 first). */
        fun toBits(tc: LtcTimecode): IntArray {
            val bits = IntArray(BITS_PER_FRAME)
            putBcd(bits, 0, tc.frames % 10, 4)      // frame units, bits 0-3
            putBcd(bits, 8, tc.frames / 10, 2)      // frame tens, bits 8-9
            if (tc.dropFrame) bits[10] = 1          // drop-frame flag
            putBcd(bits, 16, tc.seconds % 10, 4)    // sec units, bits 16-19
            putBcd(bits, 24, tc.seconds / 10, 3)    // sec tens, bits 24-26
            putBcd(bits, 32, tc.minutes % 10, 4)    // min units, bits 32-35
            putBcd(bits, 40, tc.minutes / 10, 3)    // min tens, bits 40-42
            putBcd(bits, 48, tc.hours % 10, 4)      // hour units, bits 48-51
            putBcd(bits, 56, tc.hours / 10, 2)      // hour tens, bits 56-57
            SYNC_WORD.copyInto(bits, destinationOffset = 64)
            return bits
        }

        /** Parses 80 LTC bits (transmission order) back into a timecode. Sync word not re-validated here. */
        fun fromBits(bits: IntArray): LtcTimecode {
            require(bits.size == BITS_PER_FRAME) { "LTC frame must be $BITS_PER_FRAME bits, got ${bits.size}" }
            val frames = getBcd(bits, 8, 2) * 10 + getBcd(bits, 0, 4)
            val seconds = getBcd(bits, 24, 3) * 10 + getBcd(bits, 16, 4)
            val minutes = getBcd(bits, 40, 3) * 10 + getBcd(bits, 32, 4)
            val hours = getBcd(bits, 56, 2) * 10 + getBcd(bits, 48, 4)
            return LtcTimecode(hours, minutes, seconds, frames, dropFrame = bits[10] == 1)
        }

        /** True if the 16 bits at [offset] match the LTC sync word. */
        fun isSyncAt(bits: IntArray, offset: Int): Boolean {
            if (offset + 16 > bits.size) return false
            for (i in SYNC_WORD.indices) if (bits[offset + i] != SYNC_WORD[i]) return false
            return true
        }

        // LTC BCD fields are stored LSB-first across [width] bits starting at [start].
        private fun putBcd(bits: IntArray, start: Int, value: Int, width: Int) {
            for (i in 0 until width) bits[start + i] = (value ushr i) and 1
        }

        private fun getBcd(bits: IntArray, start: Int, width: Int): Int {
            var v = 0
            for (i in 0 until width) v = v or (bits[start + i] shl i)
            return v
        }
    }
}
