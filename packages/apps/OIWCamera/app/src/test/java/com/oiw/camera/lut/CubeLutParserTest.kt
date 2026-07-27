package com.oiw.camera.lut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class CubeLutParserTest {

    private val parser = CubeLutParser()

    @Test
    fun `parses an identity 2-point cube and samples it unchanged`() {
        val file = File.createTempFile("identity", ".cube")
        file.writeText(
            """
            TITLE "Identity"
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
            """.trimIndent()
        )

        val lut = parser.parse(file)
        assertEquals(2, lut.size)

        val (r, g, b) = parser.sample(lut, 0.5f, 0.5f, 0.5f)
        assertEquals(0.5f, r, 0.001f)
        assertEquals(0.5f, g, 0.001f)
        assertEquals(0.5f, b, 0.001f)

        file.delete()
    }

    @Test
    fun `rejects a cube file with a value count mismatch`() {
        val file = File.createTempFile("malformed", ".cube")
        file.writeText(
            """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            """.trimIndent()
        )
        assertThrows(IllegalArgumentException::class.java) { parser.parse(file) }
        file.delete()
    }
}
