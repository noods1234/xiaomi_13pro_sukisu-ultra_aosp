package com.oiw.camera.overlay

/**
 * Real 256-bin luma histogram (docs/CINEMA_FEATURES.md #2). Waveform/RGB-parade/vectorscope are
 * NOT implemented here — those need a GPU compute path to hit frame rate; see
 * implementation_plan.md Phase 6 for why a histogram is CPU-feasible but a full waveform isn't at
 * acceptable latency on this SoC tier without a shader.
 */
class HistogramOverlay {
    fun compute(luma: LumaFrame.Luma): IntArray {
        val bins = IntArray(256)
        // Sample every 4th pixel; a full 4K frame doesn't need per-pixel precision for a histogram
        // shape, and this keeps per-frame CPU cost low enough to run live alongside the encoder.
        var i = 0
        while (i < luma.data.size) {
            val value = luma.data[i].toInt() and 0xFF
            bins[value]++
            i += 4
        }
        return bins
    }

    /** Clipping indicator: fraction of sampled pixels pinned at 0 or 255 (docs/CINEMA_FEATURES.md). */
    fun clippingFraction(bins: IntArray): Pair<Double, Double> {
        val total = bins.sum().coerceAtLeast(1)
        return (bins[0].toDouble() / total) to (bins[255].toDouble() / total)
    }
}
