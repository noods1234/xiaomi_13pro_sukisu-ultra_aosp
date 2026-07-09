package com.oiw.camera.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FalseColorOverlayTest {
    private val overlay = FalseColorOverlay()

    @Test
    fun `band edges resolve to adjacent bands`() {
        assertEquals(0, overlay.bandIndexFor(0))
        assertEquals(0, overlay.bandIndexFor(7))
        assertEquals(1, overlay.bandIndexFor(8))
        assertEquals(4, overlay.bandIndexFor(45))   // 18% gray band
        assertEquals(9, overlay.bandIndexFor(100))  // clipped
    }

    @Test
    fun `colorize maps black and white to different colors`() {
        val luma = LumaFrame.Luma(2, 1, byteArrayOf(0, -1)) // 0 and 255 (unsigned)
        val px = overlay.colorize(luma)
        assertNotEquals(px[0], px[1])
    }
}
