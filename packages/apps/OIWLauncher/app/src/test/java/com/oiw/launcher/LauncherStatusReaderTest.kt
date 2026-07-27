package com.oiw.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherStatusReaderTest {

    @Test
    fun `summary line falls back to placeholders for missing fields`() {
        val status = LauncherStatusReader.Status(
            storageFreeGb = null,
            thermalMode = null,
            batteryPercent = null,
            lastProfileId = null,
        )
        assertEquals("storage: -- · thermal: -- · battery: -- · profile: --", status.summaryLine())
    }

    @Test
    fun `summary line formats populated fields`() {
        val status = LauncherStatusReader.Status(
            storageFreeGb = 42.567,
            thermalMode = "balanced_field",
            batteryPercent = 81,
            lastProfileId = "cinema_4k_24_log",
        )
        assertEquals("42.6GB free · balanced_field · 81% · cinema_4k_24_log", status.summaryLine())
    }
}
