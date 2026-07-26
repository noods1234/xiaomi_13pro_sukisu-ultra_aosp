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
    var showWaveform = false
    var showVectorscope = false
    var showGuides = true
    var guideAspect: Float? = 2.39f

    private var waveformCounts: IntArray? = null
    private var waveformDims: Pair<Int, Int> = 0 to 0
    private var waveformPeak = 1
    private var vectorGrid: IntArray? = null
    private var vectorSize = 0
    private var vectorPeak = 1

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
    private val scopePaint = Paint()

    /** Called from the analysis thread's results; posts invalidate to the UI thread. */
    fun submit(
        luma: LumaFrame.Luma,
        zebraMask: BooleanArray?,
        peakingMask: BooleanArray?,
        bins: IntArray?,
        falseColorPixels: IntArray? = null,
        /** counts grid, (columns to levels), peak — from [WaveformOverlay]. */
        waveform: Triple<IntArray, Pair<Int, Int>, Int>? = null,
        /** density grid, size, peak — from [VectorscopeOverlay]. */
        vectorscope: Triple<IntArray, Int, Int>? = null,
    ) {
        maskWidth = luma.width
        maskHeight = luma.height
        if (pixelBuffer.size != luma.width * luma.height) pixelBuffer = IntArray(luma.width * luma.height)
        histogramBins = bins
        zebraBitmap = zebraMask?.let { maskToBitmap(it, zebraBitmap, Color.argb(160, 255, 60, 60)) }
        peakingBitmap = peakingMask?.let { maskToBitmap(it, peakingBitmap, Color.argb(200, 60, 255, 60)) }
        waveform?.let { (counts, dims, peak) ->
            waveformCounts = counts; waveformDims = dims; waveformPeak = peak.coerceAtLeast(1)
        }
        vectorscope?.let { (grid, size, peak) ->
            vectorGrid = grid; vectorSize = size; vectorPeak = peak.coerceAtLeast(1)
        }
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
        if (showWaveform) drawWaveform(canvas)
        if (showVectorscope) drawVectorscope(canvas)
        if (showGuides) drawGuides(canvas)
    }

    /**
     * Waveform: x = image column, y = IRE (0 at bottom), brightness = pixel count at that level.
     * Drawn bottom-left, sized to a quarter of the view so it doesn't dominate the frame.
     */
    private fun drawWaveform(canvas: Canvas) {
        val counts = waveformCounts ?: return
        val (cols, levels) = waveformDims
        if (cols <= 0 || levels <= 0) return
        val ww = width * 0.30f
        val wh = height * 0.22f
        val left = 16f
        val top = height - wh - 16f
        canvas.drawRect(left, top, left + ww, top + wh, histogramBgPaint)
        val colW = ww / cols
        val lvlH = wh / levels
        for (c in 0 until cols) {
            for (l in 0 until levels) {
                val n = counts[c * levels + l]
                if (n == 0) continue
                // Perceptual-ish compression: sqrt keeps sparse traces visible without blowing out.
                val a = (255.0 * Math.sqrt(n.toDouble() / waveformPeak)).toInt().coerceIn(24, 255)
                scopePaint.color = Color.argb(a, 120, 255, 140)
                val x = left + c * colW
                val y = top + wh - (l + 1) * lvlH // level 0 at the bottom
                canvas.drawRect(x, y, x + colW, y + lvlH, scopePaint)
            }
        }
    }

    /** Vectorscope: chroma density on the U/V plane, drawn bottom-right with a gamut circle. */
    private fun drawVectorscope(canvas: Canvas) {
        val grid = vectorGrid ?: return
        if (vectorSize <= 0) return
        val side = minOf(width * 0.26f, height * 0.26f)
        val left = width - side - 16f
        val top = height - side - 16f
        canvas.drawRect(left, top, left + side, top + side, histogramBgPaint)
        val cell = side / vectorSize
        for (y in 0 until vectorSize) {
            for (x in 0 until vectorSize) {
                val n = grid[y * vectorSize + x]
                if (n == 0) continue
                val a = (255.0 * Math.sqrt(n.toDouble() / vectorPeak)).toInt().coerceIn(24, 255)
                scopePaint.color = Color.argb(a, 255, 210, 120)
                canvas.drawRect(left + x * cell, top + y * cell, left + (x + 1) * cell, top + (y + 1) * cell, scopePaint)
            }
        }
        // 90%-radius legal-gamut circle + centre crosshair for neutral reference.
        val cx = left + side / 2; val cy = top + side / 2
        canvas.drawCircle(cx, cy, side / 2 * 0.9f, guidePaint)
        canvas.drawLine(cx - 6, cy, cx + 6, cy, guidePaint)
        canvas.drawLine(cx, cy - 6, cx, cy + 6, guidePaint)
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
