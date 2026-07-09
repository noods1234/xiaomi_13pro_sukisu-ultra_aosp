package com.oiw.camera.overlay

import android.graphics.Color

/**
 * IRE-banded false color (docs/CINEMA_FEATURES.md #2 — was a stub-audit gap, now real).
 * Standard exposure-band convention: purple = crushed blacks, blue = deep shadows,
 * green = mid-gray/skin-shadow band, pink = typical skin highlight band, yellow = near-clip,
 * red = clipped. Returns an ARGB pixel array the same size as the luma plane, consumed by
 * OverlayView the same way as the zebra/peaking masks.
 */
class FalseColorOverlay {

    private val bandColors = intArrayOf(
        Color.argb(255, 98, 0, 178),    //  0– 7 IRE: crushed
        Color.argb(255, 30, 80, 255),   //  8–15: deep shadow
        Color.argb(255, 80, 130, 255),  // 16–23: shadow
        Color.argb(255, 120, 120, 120), // 24–42: low-mid (neutral gray)
        Color.argb(255, 60, 180, 60),   // 43–47: 18% gray band
        Color.argb(255, 150, 150, 150), // 48–54: mid
        Color.argb(255, 235, 150, 170), // 55–63: skin-tone highlight band
        Color.argb(255, 180, 180, 180), // 64–83: upper-mid
        Color.argb(255, 255, 235, 80),  // 84–93: near-clip
        Color.argb(255, 255, 40, 40),   // 94–100: clipped
    )
    private val bandUpperBoundsIre = intArrayOf(7, 15, 23, 42, 47, 54, 63, 83, 93, 100)

    fun colorize(luma: LumaFrame.Luma, out: IntArray? = null): IntArray {
        val pixels = out?.takeIf { it.size == luma.data.size } ?: IntArray(luma.data.size)
        for (i in luma.data.indices) {
            val ire = ((luma.data[i].toInt() and 0xFF) * 100) / 255
            pixels[i] = bandColors[bandIndexFor(ire)]
        }
        return pixels
    }

    fun bandIndexFor(ire: Int): Int {
        for (b in bandUpperBoundsIre.indices) {
            if (ire <= bandUpperBoundsIre[b]) return b
        }
        return bandUpperBoundsIre.lastIndex
    }
}
