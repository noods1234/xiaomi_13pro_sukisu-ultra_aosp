package com.oiw.launcher

import android.content.Context

/** Backing store for launcher-level settings (docs/UI_UX.md #1). Plain SharedPreferences — no need for DataStore at this scale. */
class LauncherPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("oiw_launcher_prefs", Context.MODE_PRIVATE)

    var distractionFreeMode: Boolean
        get() = prefs.getBoolean(KEY_DISTRACTION_FREE, false)
        set(value) = prefs.edit().putBoolean(KEY_DISTRACTION_FREE, value).apply()

    var bootToCameraEnabled: Boolean
        get() = prefs.getBoolean(KEY_BOOT_TO_CAMERA, false)
        set(value) = prefs.edit().putBoolean(KEY_BOOT_TO_CAMERA, value).apply()

    companion object {
        private const val KEY_DISTRACTION_FREE = "distraction_free_mode"
        private const val KEY_BOOT_TO_CAMERA = "boot_to_camera_enabled"
    }
}
