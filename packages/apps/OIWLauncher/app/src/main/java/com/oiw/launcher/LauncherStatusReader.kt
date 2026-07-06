package com.oiw.launcher

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * Reads OIWCamera's last-known status (storage/thermal/battery/last profile) from a small,
 * world-readable JSON status file OIWCamera writes under /sdcard/OIW_MEDIA/.status.json.
 * Decoupled from OIWCamera's process lifecycle deliberately — the launcher must never block on or
 * crash from a bound-service connection to an app that might not even be running
 * (docs/UI_UX.md #1 boot-and-shoot acceptance criteria).
 */
class LauncherStatusReader(private val context: Context) {

    data class Status(
        val storageFreeGb: Double?,
        val thermalMode: String?,
        val batteryPercent: Int?,
        val lastProfileId: String?,
    ) {
        fun summaryLine(): String {
            val storage = storageFreeGb?.let { "%.1fGB free".format(it) } ?: "storage: --"
            val thermal = thermalMode ?: "thermal: --"
            val battery = batteryPercent?.let { "$it%" } ?: "battery: --"
            val profile = lastProfileId ?: "profile: --"
            return "$storage · $thermal · $battery · $profile"
        }
    }

    private val gson = Gson()

    fun readLastKnownStatus(): Status? {
        val statusFile = File(
            context.getExternalFilesDir(null)?.parentFile?.parentFile,
            "OIW_MEDIA/.status.json",
        )
        if (!statusFile.exists()) return null
        return runCatching { gson.fromJson(statusFile.readText(), Status::class.java) }.getOrNull()
    }
}
