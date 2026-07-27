package com.oiw.camera.overlay

/**
 * Luma waveform monitor (docs/CINEMA_FEATURES.md #2). For each output column, accumulates a
 * histogram of luma values in the corresponding source column band — the standard waveform display:
 * x = horizontal image position, y = IRE level, brightness = how many pixels at that level.
 *
 * CPU implementation, sized for the quarter-resolution analysis stream. The earlier docs asserted
 * this needed a GPU compute path; that claim was **measured, not assumed** — see
 * `WaveformPerformanceTest`, which benchmarks a 960x540 analysis frame (the quarter-res of 4K) and
 * enforces a per-frame budget. If the measurement ever regresses past the budget, that test fails
 * and the GPU path becomes required rather than optional.
 *
 * @param columns output width (waveform buckets across the image)
 * @param levels output height (luma bins, 256 = full 8-bit resolution)
 */
class WaveformOverlay(
    private val columns: Int = 256,
    private val levels: Int = 256,
) {
    /** Reusable output buffer: counts[col * levels + level]. Avoids per-frame allocation. */
    private var counts = IntArray(columns * levels)

    /**
     * Computes the waveform. Returns the raw count grid; the renderer maps counts to brightness.
     * [rowStep]/[colStep] subsample for speed — 1 means every pixel.
     */
    fun compute(luma: LumaFrame.Luma, rowStep: Int = 1, colStep: Int = 1): IntArray {
        if (counts.size != columns * levels) counts = IntArray(columns * levels)
        java.util.Arrays.fill(counts, 0)

        val w = luma.width
        val h = luma.height
        val data = luma.data
        // Precompute x -> column mapping once per frame instead of per pixel.
        val colOf = IntArray(w)
        for (x in 0 until w) colOf[x] = x * columns / w

        var y = 0
        while (y < h) {
            val rowBase = y * w
            var x = 0
            while (x < w) {
                val v = data[rowBase + x].toInt() and 0xFF
                val level = if (levels == 256) v else v * levels / 256
                counts[colOf[x] * levels + level]++
                x += colStep
            }
            y += rowStep
        }
        return counts
    }

    /** Peak count in the grid — used to normalize brightness when rendering. */
    fun peak(counts: IntArray): Int {
        var m = 0
        for (c in counts) if (c > m) m = c
        return m
    }

    fun outputSize(): Pair<Int, Int> = columns to levels
}
