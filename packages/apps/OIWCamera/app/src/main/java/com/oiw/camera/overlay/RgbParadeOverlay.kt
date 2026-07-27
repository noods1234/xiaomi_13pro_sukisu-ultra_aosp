package com.oiw.camera.overlay

/**
 * RGB parade (docs/CINEMA_FEATURES.md #2): three column-histograms side by side, one per channel.
 * Where the luma waveform answers "how is my exposure distributed across frame", the parade answers
 * "how is my *white balance* distributed" — a colour cast shows as the three traces sitting at
 * different heights, which is the standard on-set check the luma waveform simply cannot make.
 *
 * Reuses the pieces that already exist: [LumaFrame.Luma] for Y and [VectorscopeOverlay.Chroma] for
 * the pixelStride-aware U/V planes, so this adds no new capture-side plumbing.
 *
 * **Resolution:** computed at *chroma* resolution — one sample per 4:2:0 chroma site (a 2x2 luma
 * block), taking that block's top-left luma. This is not an approximation forced by laziness; the
 * chroma planes genuinely carry one U/V pair per 2x2 block, so anything finer would be inventing
 * colour detail that is not in the frame. It also makes the parade ~4x cheaper than the luma
 * waveform for the same source frame.
 *
 * **Colour matrix — an assumption, not a measurement.** [YuvToRgb] uses BT.601 full-range, the
 * conventional interpretation of `YUV_420_888` on Android. Android does not report the stream's
 * actual colour encoding, so on a device that delivers limited-range or BT.709 chroma the traces
 * will be offset from a hardware scope's. That is a monitoring inaccuracy, not a recording one —
 * nothing here touches recorded pixels — but it is unverified until checked against a colour chart
 * on real hardware (`docs/AUDIT_FINDINGS.md` finding V). Do not describe this scope as
 * colorimetrically calibrated.
 *
 * @param columns horizontal buckets **per channel**
 * @param levels vertical bins (256 = full 8-bit resolution)
 */
class RgbParadeOverlay(
    private val columns: Int = 128,
    private val levels: Int = 256,
) {
    /** counts[(channel * columns + col) * levels + level]. One reused buffer, no per-frame alloc. */
    private var counts = IntArray(CHANNELS * columns * levels)
    private var colOf = IntArray(0)
    private var lumaXOf = IntArray(0)
    private var lumaYOf = IntArray(0)

    /**
     * Computes the parade. [step] subsamples the chroma grid in both axes (1 = every chroma site).
     *
     * Returns the raw count grid; the renderer maps counts to brightness. Returns an all-zero grid
     * for an empty or mismatched frame rather than throwing — a monitoring overlay must never be
     * able to take down a take.
     */
    fun compute(luma: LumaFrame.Luma, chroma: VectorscopeOverlay.Chroma, step: Int = 1): IntArray {
        if (counts.size != CHANNELS * columns * levels) counts = IntArray(CHANNELS * columns * levels)
        java.util.Arrays.fill(counts, 0)

        val cw = chroma.width
        val ch = chroma.height
        val lw = luma.width
        val lh = luma.height
        if (cw <= 0 || ch <= 0 || lw <= 0 || lh <= 0) return counts
        if (chroma.u.size < cw * ch || chroma.v.size < cw * ch || luma.data.size < lw * lh) return counts

        // Precompute the three coordinate maps once per frame instead of dividing per pixel. The
        // luma maps are proportional rather than hardcoded x2 so an unexpected plane ratio degrades
        // to a correct-but-coarser scope instead of reading out of bounds.
        if (colOf.size != cw) colOf = IntArray(cw)
        if (lumaXOf.size != cw) lumaXOf = IntArray(cw)
        if (lumaYOf.size != ch) lumaYOf = IntArray(ch)
        for (x in 0 until cw) {
            colOf[x] = x * columns / cw
            lumaXOf[x] = (x.toLong() * lw / cw).toInt().coerceIn(0, lw - 1)
        }
        for (y in 0 until ch) lumaYOf[y] = (y.toLong() * lh / ch).toInt().coerceIn(0, lh - 1)

        val planeStride = columns * levels
        val safeStep = if (step < 1) 1 else step
        var cy = 0
        while (cy < ch) {
            val chromaRow = cy * cw
            val lumaRow = lumaYOf[cy] * lw
            var cx = 0
            while (cx < cw) {
                val y = luma.data[lumaRow + lumaXOf[cx]].toInt() and 0xFF
                val u = chroma.u[chromaRow + cx].toInt() and 0xFF
                val v = chroma.v[chromaRow + cx].toInt() and 0xFF
                val col = colOf[cx]

                val r = YuvToRgb.red(y, v)
                val g = YuvToRgb.green(y, u, v)
                val b = YuvToRgb.blue(y, u)

                counts[(col) * levels + levelOf(r)]++
                counts[planeStride + col * levels + levelOf(g)]++
                counts[2 * planeStride + col * levels + levelOf(b)]++
                cx += safeStep
            }
            cy += safeStep
        }
        return counts
    }

    private fun levelOf(value: Int): Int =
        if (levels == 256) value else (value * levels / 256).coerceAtMost(levels - 1)

    /** Peak count across all three channels — used to normalize brightness when rendering. */
    fun peak(counts: IntArray): Int {
        var m = 0
        for (c in counts) if (c > m) m = c
        return m
    }

    /** Start index of [channel]'s grid within the flat counts array. */
    fun channelOffset(channel: Int): Int = channel * columns * levels

    fun outputSize(): Pair<Int, Int> = columns to levels

    companion object {
        const val CHANNELS = 3
        const val RED = 0
        const val GREEN = 1
        const val BLUE = 2
    }
}

/**
 * BT.601 full-range YUV -> RGB, in 10-bit fixed point so the per-pixel path has no float math.
 * Split out from [RgbParadeOverlay] so the conversion is testable on its own — including the one
 * property that must hold exactly: neutral chroma (U = V = 128) must map to R = G = B = Y, or the
 * parade would show a colour cast on a genuinely grey frame.
 */
object YuvToRgb {
    private const val SHIFT = 10
    private const val R_V = 1436 // 1.402
    private const val G_U = 352 // 0.344136
    private const val G_V = 731 // 0.714136
    private const val B_U = 1815 // 1.772

    fun red(y: Int, v: Int): Int = clamp(y + ((R_V * (v - 128)) shr SHIFT))

    fun green(y: Int, u: Int, v: Int): Int =
        clamp(y - ((G_U * (u - 128) + G_V * (v - 128)) shr SHIFT))

    fun blue(y: Int, u: Int): Int = clamp(y + ((B_U * (u - 128)) shr SHIFT))

    private fun clamp(v: Int): Int = if (v < 0) 0 else if (v > 255) 255 else v
}
