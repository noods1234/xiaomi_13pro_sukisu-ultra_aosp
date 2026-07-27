package com.oiw.camera.overlay

/**
 * Real luma-threshold zebra-stripe mask (docs/CINEMA_FEATURES.md #2). Returns a boolean mask the
 * same size as the luma frame; the rendering layer (GL/Canvas overlay on the preview TextureView)
 * draws diagonal stripes wherever the mask is true.
 */
class ZebraOverlay(private var thresholdHigh: Int = 245, private var thresholdLow: Int = 16) {

    fun setThresholds(high: Int, low: Int) {
        thresholdHigh = high.coerceIn(0, 255)
        thresholdLow = low.coerceIn(0, 255)
    }

    fun computeMask(luma: LumaFrame.Luma): BooleanArray {
        val mask = BooleanArray(luma.data.size)
        for (i in luma.data.indices) {
            val value = luma.data[i].toInt() and 0xFF
            mask[i] = value >= thresholdHigh || value <= thresholdLow
        }
        return mask
    }
}
