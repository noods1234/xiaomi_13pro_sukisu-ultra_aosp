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

    /**
     * Reads via OIWCamera's StatusProvider ContentProvider (permission-free handshake — the
     * stub-audit fix for the Android/data cross-app path bug). Falls back to the shared-storage
     * file directly only if the provider is unreachable AND this app happens to have read access.
     */
    fun readLastKnownStatus(): Status? {
        val viaProvider = runCatching {
            context.contentResolver.call(
                android.net.Uri.parse("content://com.oiw.camera.status"),
                "get_status", null, null,
            )?.getString("status_json")
        }.getOrNull()
        val json = viaProvider ?: runCatching {
            val f = File(android.os.Environment.getExternalStorageDirectory(), "OIW_MEDIA/.status.json")
            if (f.exists()) f.readText() else null
        }.getOrNull() ?: return null
        return runCatching { gson.fromJson(json, Status::class.java) }.getOrNull()
    }
}
