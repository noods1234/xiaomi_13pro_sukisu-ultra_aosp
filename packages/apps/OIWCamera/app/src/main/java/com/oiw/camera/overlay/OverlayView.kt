package com.oiw.camera.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

/**
 * Composites the monitoring overlays over the preview (docs/CINEMA_FEATURES.md #2). Consumes the
 * pure-computation results (HistogramOverlay / ZebraOverlay / FocusPeakingOverlay masks) and draws
 * them; scaling the quarter-resolution analysis masks up to view size via a bitmap keeps per-frame
 * allocation bounded (one reused bitmap per mask type).
 */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    var showHistogram = true
    var showZebra = true
    var showPeaking = true
    var showFalseColor = false // exposure-judgment mode: replaces the image, so opt-in per profile
    var showGuides = true
    var guideAspect: Float? = 2.39f

    private var histogramBins: IntArray? = null
    private var maskWidth = 0
    private var maskHeight = 0
    private var zebraBitmap: Bitmap? = null
    private var peakingBitmap: Bitmap? = null
    private var falseColorBitmap: Bitmap? = null
    private var pixelBuffer: IntArray = IntArray(0)

    private val histogramPaint = Paint().apply { color = Color.argb(200, 255, 255, 255); strokeWidth = 2f }
    private val histogramBgPaint = Paint().apply { color = Color.argb(90, 0, 0, 0) }
    private val guidePaint = Paint().apply {
        color = Color.argb(180, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val bitmapPaint = Paint()

    /** Called from the analysis thread's results; posts invalidate to the UI thread. */
    fun submit(
        luma: LumaFrame.Luma,
        zebraMask: BooleanArray?,
        peakingMask: BooleanArray?,
        bins: IntArray?,
        falseColorPixels: IntArray? = null,
    ) {
        maskWidth = luma.width
        maskHeight = luma.height
        if (pixelBuffer.size != luma.width * luma.height) pixelBuffer = IntArray(luma.width * luma.height)
        histogramBins = bins
        zebraBitmap = zebraMask?.let { maskToBitmap(it, zebraBitmap, Color.argb(160, 255, 60, 60)) }
        peakingBitmap = peakingMask?.let { maskToBitmap(it, peakingBitmap, Color.argb(200, 60, 255, 60)) }
        falseColorPixels?.let { px ->
            val bmp = falseColorBitmap?.takeIf { it.width == maskWidth && it.height == maskHeight }
                ?: Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, maskWidth, 0, 0, maskWidth, maskHeight)
            falseColorBitmap = bmp
        }
        postInvalidate()
    }

    private fun maskToBitmap(mask: BooleanArray, reuse: Bitmap?, argb: Int): Bitmap {
        val bmp = if (reuse?.width == maskWidth && reuse.height == maskHeight) reuse
        else Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val px = pixelBuffer
        for (i in mask.indices) px[i] = if (mask[i]) argb else Color.TRANSPARENT
        bmp.setPixels(px, 0, maskWidth, 0, 0, maskWidth, maskHeight)
        return bmp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dst = Rect(0, 0, width, height)
        if (showFalseColor) falseColorBitmap?.let { canvas.drawBitmap(it, null, dst, bitmapPaint) }
        if (showZebra) zebraBitmap?.let { canvas.drawBitmap(it, null, dst, bitmapPaint) }
        if (showPeaking) peakingBitmap?.let { canvas.drawBitmap(it, null, dst, bitmapPaint) }
        if (showHistogram) drawHistogram(canvas)
        if (showGuides) drawGuides(canvas)
    }

    private fun drawHistogram(canvas: Canvas) {
        val bins = histogramBins ?: return
        val hw = width * 0.28f
        val hh = height * 0.14f
        val left = width - hw - 16f
        val top = 16f
        canvas.drawRect(left, top, left + hw, top + hh, histogramBgPaint)
        val max = bins.max().coerceAtLeast(1)
        val step = hw / bins.size
        for (i in bins.indices) {
            val h = hh * bins[i] / max
            canvas.drawLine(left + i * step, top + hh, left + i * step, top + hh - h, histogramPaint)
        }
    }

    private fun drawGuides(canvas: Canvas) {
        val aspect = guideAspect ?: return
        val viewAspect = width.toFloat() / height
        if (aspect > viewAspect) {
            val guideH = width / aspect
            val yOff = (height - guideH) / 2
            canvas.drawRect(0f, yOff, width.toFloat(), yOff + guideH, guidePaint)
        } else {
            val guideW = height * aspect
            val xOff = (width - guideW) / 2
            canvas.drawRect(xOff, 0f, xOff + guideW, height.toFloat(), guidePaint)
        }
        // Center marker
        canvas.drawLine(width / 2f - 24, height / 2f, width / 2f + 24, height / 2f, guidePaint)
        canvas.drawLine(width / 2f, height / 2f - 24, width / 2f, height / 2f + 24, guidePaint)
    }
}
