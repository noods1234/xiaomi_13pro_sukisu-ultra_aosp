package com.oiw.camera.lut

import java.io.File

/**
 * Parser for the Adobe/Resolve-standard .cube 3D LUT format (17/33/65-point). Preview-only
 * consumer (docs/CINEMA_FEATURES.md #6) — this class only parses and samples; it never touches
 * the recorded file. Trilinear interpolation for sampling between grid points.
 */
class CubeLutParser {

    data class Lut3D(val size: Int, val table: FloatArray) // table is size^3 * 3 (RGB) floats, index = ((r*size+g)*size+b)*3

    fun parse(file: File): Lut3D {
        var size = 0
        val values = mutableListOf<Float>()
        file.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            when {
                line.startsWith("LUT_3D_SIZE") -> {
                    size = line.substringAfter("LUT_3D_SIZE").trim().toInt()
                }
                line.startsWith("TITLE") || line.startsWith("DOMAIN_MIN") || line.startsWith("DOMAIN_MAX") -> {
                    // Metadata lines we don't need for preview sampling; explicitly ignored, not silently mis-parsed.
                }
                else -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size == 3) {
                        values += parts[0].toFloat()
                        values += parts[1].toFloat()
                        values += parts[2].toFloat()
                    }
                }
            }
        }
        require(size > 0) { "Malformed .cube file: missing LUT_3D_SIZE (${file.name})" }
        require(values.size == size * size * size * 3) {
            "Malformed .cube file: expected ${size * size * size * 3} values, got ${values.size} (${file.name})"
        }
        return Lut3D(size, values.toFloatArray())
    }

    /** Trilinear-sampled preview transform for a single normalized (0..1) RGB input. */
    fun sample(lut: Lut3D, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val n = lut.size
        val rf = (r.coerceIn(0f, 1f) * (n - 1))
        val gf = (g.coerceIn(0f, 1f) * (n - 1))
        val bf = (b.coerceIn(0f, 1f) * (n - 1))
        val r0 = rf.toInt().coerceIn(0, n - 1); val r1 = (r0 + 1).coerceAtMost(n - 1)
        val g0 = gf.toInt().coerceIn(0, n - 1); val g1 = (g0 + 1).coerceAtMost(n - 1)
        val b0 = bf.toInt().coerceIn(0, n - 1); val b1 = (b0 + 1).coerceAtMost(n - 1)
        val rd = rf - r0; val gd = gf - g0; val bd = bf - b0

        fun at(ri: Int, gi: Int, bi: Int, channel: Int): Float =
            lut.table[((ri * n + gi) * n + bi) * 3 + channel]

        fun lerp(a: Float, bVal: Float, t: Float) = a + (bVal - a) * t

        var out = floatArrayOf(0f, 0f, 0f)
        for (channel in 0..2) {
            val c000 = at(r0, g0, b0, channel); val c100 = at(r1, g0, b0, channel)
            val c010 = at(r0, g1, b0, channel); val c110 = at(r1, g1, b0, channel)
            val c001 = at(r0, g0, b1, channel); val c101 = at(r1, g0, b1, channel)
            val c011 = at(r0, g1, b1, channel); val c111 = at(r1, g1, b1, channel)
            val c00 = lerp(c000, c100, rd); val c10 = lerp(c010, c110, rd)
            val c01 = lerp(c001, c101, rd); val c11 = lerp(c011, c111, rd)
            val c0 = lerp(c00, c10, gd); val c1 = lerp(c01, c11, gd)
            out[channel] = lerp(c0, c1, bd)
        }
        return Triple(out[0], out[1], out[2])
    }
}
