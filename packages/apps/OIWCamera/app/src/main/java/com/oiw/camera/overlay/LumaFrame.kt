package com.oiw.camera.overlay

import android.media.Image

/**
 * Extracts the Y (luma) plane from a YUV_420_888 ImageReader frame as an unsigned-byte array,
 * respecting row stride (which is frequently wider than width due to hardware alignment).
 * Shared by Histogram/Zebra/FocusPeaking so each overlay isn't re-deriving plane math
 * (docs/CINEMA_FEATURES.md #2).
 */
object LumaFrame {
    data class Luma(val width: Int, val height: Int, val data: ByteArray)

    fun extract(image: Image): Luma {
        val yPlane = image.planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = image.width
        val height = image.height
        val out = ByteArray(width * height)
        val rowBytes = ByteArray(rowStride)
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(rowBytes, 0, minOf(rowStride, buffer.remaining()))
            System.arraycopy(rowBytes, 0, out, row * width, width)
        }
        return Luma(width, height, out)
    }
}
