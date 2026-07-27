package com.oiw.camera.capture

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureProfileTest {

    private val gson = Gson()

    @Test
    fun `shutter angle converts to exposure time at given frame rate`() {
        val shutter = CaptureProfile.ShutterStrategy(mode = "angle", shutterAngleDegrees = 180.0)
        // 24fps, 180-degree shutter -> frame duration / 2 = (1/24 s) / 2 = ~20833333 ns
        val exposureNanos = shutter.exposureTimeNanos(frameRateFps = 24)
        assertEquals(20_833_333L, exposureNanos)
    }

    @Test
    fun `shutter speed fraction converts to exposure time directly`() {
        val shutter = CaptureProfile.ShutterStrategy(mode = "speed_fraction", shutterSpeedDenominator = 48)
        val exposureNanos = shutter.exposureTimeNanos(frameRateFps = 24)
        assertEquals(1_000_000_000L / 48, exposureNanos)
    }

    @Test
    fun `profile round-trips through gson without custom adapters`() {
        val profile = CaptureProfile(
            id = "cinema_4k_24_log",
            displayName = "Cinema 4K 24p Log",
            resolution = CaptureProfile.Resolution(3840, 2160),
            frameRateFps = 24,
            shutter = CaptureProfile.ShutterStrategy(mode = "angle", shutterAngleDegrees = 180.0),
            isoMin = 100,
            isoMax = 3200,
            whiteBalanceKelvin = 5600,
            focusMode = "manual",
            codec = "hevc10",
            bitrateBps = 180_000_000,
            bitDepth = 10,
            colorProfile = "flat_approximation",
            storageTarget = "internal",
            thermalProfileId = "balanced_field",
        )
        val json = gson.toJson(profile)
        val roundTripped = gson.fromJson(json, CaptureProfile::class.java)
        assertEquals(profile, roundTripped)
    }
}
