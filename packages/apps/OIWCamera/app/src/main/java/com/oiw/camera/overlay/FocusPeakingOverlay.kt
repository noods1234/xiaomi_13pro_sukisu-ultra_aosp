package com.oiw.camera.overlay

/**
 * Real Sobel-edge-magnitude focus peaking (docs/CINEMA_FEATURES.md #2). CPU implementation,
 * downsampled for performance — full-resolution per-pixel Sobel on a 4K luma plane every frame is
 * not free; this samples on a coarse grid, sufficient for a visual peaking overlay (not a
 * scientific sharpness measurement).
 */
class FocusPeakingOverlay(private var threshold: Int = 40, private val gridStep: Int = 2) {

    fun setThreshold(value: Int) {
        threshold = value.coerceIn(0, 255)
    }

    fun computeMask(luma: LumaFrame.Luma): BooleanArray {
        val w = luma.width
        val h = luma.height
        val data = luma.data
        val mask = BooleanArray(data.size)

        fun px(x: Int, y: Int): Int = data[(y * w + x)].toInt() and 0xFF

        var y = gridStep
        while (y < h - gridStep) {
            var x = gridStep
            while (x < w - gridStep) {
                val gx = (px(x + 1, y - 1) + 2 * px(x + 1, y) + px(x + 1, y + 1)) -
                    (px(x - 1, y - 1) + 2 * px(x - 1, y) + px(x - 1, y + 1))
                val gy = (px(x - 1, y + 1) + 2 * px(x, y + 1) + px(x + 1, y + 1)) -
                    (px(x - 1, y - 1) + 2 * px(x, y - 1) + px(x + 1, y - 1))
                val magnitude = Math.sqrt((gx * gx + gy * gy).toDouble())
                if (magnitude >= threshold) {
                    mask[y * w + x] = true
                }
                x += gridStep
            }
            y += gridStep
        }
        return mask
    }
}
