package com.oiw.camera.overlay

import android.media.Image

/**
 * Extracts planes from a YUV_420_888 `ImageReader` frame into flat arrays for the CPU monitoring
 * overlays (docs/CINEMA_FEATURES.md #2). Centralises the plane math so each overlay isn't
 * re-deriving stride handling.
 *
 * Two hardware realities this must respect, both of which silently corrupt output if ignored:
 *  - **rowStride** is frequently wider than the image width (alignment padding).
 *  - **pixelStride** on the U/V planes is commonly **2**, not 1: most devices deliver semi-planar
 *    NV21/NV12 where chroma samples are interleaved (U,V,U,V…) inside one buffer. Reading such a
 *    plane contiguously yields alternating U and V values — garbage. The chroma extraction below
 *    strides explicitly; `ChromaExtraction` is unit-tested against both packed (stride 1) and
 *    interleaved (stride 2) layouts.
 */
object LumaFrame {
    data class Luma(val width: Int, val height: Int, val data: ByteArray) {
        // Value-semantics for arrays (data class defaults to identity for ByteArray).
        override fun equals(other: Any?): Boolean =
            other is Luma && width == other.width && height == other.height && data.contentEquals(other.data)
        override fun hashCode(): Int = (width * 31 + height) * 31 + data.contentHashCode()
    }

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

    /**
     * Extracts the U and V planes as tightly-packed arrays for [VectorscopeOverlay].
     * Chroma planes are half-resolution in both axes for 4:2:0.
     */
    fun extractChroma(image: Image): VectorscopeOverlay.Chroma {
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val cw = image.width / 2
        val ch = image.height / 2
        return VectorscopeOverlay.Chroma(
            width = cw,
            height = ch,
            u = ChromaExtraction.readPlane(uPlane.buffer, uPlane.rowStride, uPlane.pixelStride, cw, ch),
            v = ChromaExtraction.readPlane(vPlane.buffer, vPlane.rowStride, vPlane.pixelStride, cw, ch),
        )
    }
}

/**
 * Plane de-striding, split out from [LumaFrame] so it is testable on a plain JVM `ByteBuffer`
 * without an Android `Image` (which cannot be constructed in a unit test).
 */
object ChromaExtraction {
    /**
     * Reads a [width] x [height] plane out of [buffer], honouring [rowStride] (bytes per row) and
     * [pixelStride] (bytes between successive samples — 2 for interleaved semi-planar chroma).
     * Returns a tightly-packed `width * height` array.
     */
    fun readPlane(
        buffer: java.nio.ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
    ): ByteArray {
        val out = ByteArray(width * height)
        if (pixelStride == 1) {
            // Fast path: rows are contiguous, copy each row wholesale.
            val row = ByteArray(rowStride)
            for (y in 0 until height) {
                val pos = y * rowStride
                if (pos >= buffer.limit()) break
                buffer.position(pos)
                val n = minOf(rowStride, buffer.remaining())
                buffer.get(row, 0, n)
                System.arraycopy(row, 0, out, y * width, minOf(width, n))
            }
            return out
        }
        // Interleaved path: step pixelStride bytes between samples.
        val row = ByteArray(rowStride)
        for (y in 0 until height) {
            val pos = y * rowStride
            if (pos >= buffer.limit()) break
            buffer.position(pos)
            val n = minOf(rowStride, buffer.remaining())
            buffer.get(row, 0, n)
            var src = 0
            var dst = y * width
            var x = 0
            while (x < width && src < n) {
                out[dst] = row[src]
                src += pixelStride
                dst++
                x++
            }
        }
        return out
    }
}
