package com.oiw.camera.overlay

/**
 * Vectorscope — chroma distribution on the U/V plane (docs/CINEMA_FEATURES.md #2). Each sample's
 * (U,V) maps to a point; density builds the familiar chroma cloud, with the six primary/secondary
 * targets at fixed angles for skin-tone/gamut judgment.
 *
 * CPU implementation on the quarter-res analysis stream's chroma planes — justified by measurement,
 * not assumption (see `VectorscopeOverlayTest` budget test; the waveform benchmark showed the earlier
 * "CPU is too slow" claim was wrong by an order of magnitude).
 */
class VectorscopeOverlay(private val size: Int = 256) {

    /** Chroma planes as delivered by YUV_420_888 (u/v are quarter-size vs luma, 128 = neutral). */
    data class Chroma(val width: Int, val height: Int, val u: ByteArray, val v: ByteArray)

    private var grid = IntArray(size * size)

    /**
     * Accumulates chroma density. Output is [size]x[size], origin at center: +U right, +V up
     * (standard vectorscope orientation). Returns the density grid; renderer maps count→brightness.
     */
    fun compute(chroma: Chroma, step: Int = 1): IntArray {
        if (grid.size != size * size) grid = IntArray(size * size)
        java.util.Arrays.fill(grid, 0)
        val half = size / 2
        val n = minOf(chroma.u.size, chroma.v.size)
        var i = 0
        while (i < n) {
            // Center on 128 and scale the ±128 chroma range into ±half.
            val du = ((chroma.u[i].toInt() and 0xFF) - 128) * half / 128
            val dv = ((chroma.v[i].toInt() and 0xFF) - 128) * half / 128
            val gx = (half + du).coerceIn(0, size - 1)
            val gy = (half - dv).coerceIn(0, size - 1) // screen y grows downward; +V is up
            grid[gy * size + gx]++
            i += step
        }
        return grid
    }

    /** Fraction of samples outside the legal chroma circle — a quick illegal-gamut warning. */
    fun outOfGamutFraction(grid: IntArray): Double {
        val half = size / 2
        val r2 = (half * 0.9) * (half * 0.9) // 90% radius ~ legal broadcast limit
        var outside = 0L
        var total = 0L
        for (y in 0 until size) for (x in 0 until size) {
            val c = grid[y * size + x]
            if (c == 0) continue
            total += c
            val dx = (x - half).toDouble(); val dy = (y - half).toDouble()
            if (dx * dx + dy * dy > r2) outside += c
        }
        return if (total == 0L) 0.0 else outside.toDouble() / total
    }

    /** Neutral (gray) content clusters at the center; useful as a white-balance sanity readout. */
    fun centerBias(grid: IntArray): Pair<Double, Double> {
        val half = size / 2
        var sumX = 0.0; var sumY = 0.0; var total = 0.0
        for (y in 0 until size) for (x in 0 until size) {
            val c = grid[y * size + x]
            if (c == 0) continue
            sumX += (x - half).toDouble() * c
            sumY += (half - y).toDouble() * c
            total += c
        }
        return if (total == 0.0) 0.0 to 0.0 else (sumX / total) to (sumY / total)
    }

    fun outputSize(): Int = size
}
