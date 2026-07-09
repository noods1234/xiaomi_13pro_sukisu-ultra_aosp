package com.oiw.camera.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Single source of truth for the OIW media root. Fixes the stub-audit path bug: the previous
 * `getExternalFilesDir(null).parentFile.parentFile` construction landed in /sdcard/Android/data,
 * which is (a) not the documented /sdcard/OIW_MEDIA and (b) unreadable cross-app on Android 11+.
 *
 * Uses the shared-storage root + MANAGE_EXTERNAL_STORAGE (declared in the manifest; requested via
 * [allFilesAccessIntent] on first run). This is the documented, measured trade-off from
 * docs/SECURITY_AND_RECOVERY.md §8 — segmented high-bitrate writing needs raw File I/O throughput,
 * and cross-app status/log sharing needs a location both apps can reach. Launcher-side status reads
 * additionally go through StatusProvider so the launcher itself needs no storage permission.
 */
object OiwPaths {
    fun mediaRoot(): File = File(Environment.getExternalStorageDirectory(), "OIW_MEDIA")

    fun benchmarksDir(): File = File(mediaRoot(), "benchmarks")

    fun logsDir(): File = File(mediaRoot(), "logs")

    fun userProfilesDir(): File = File(mediaRoot(), "plugins/profiles")

    fun statusFile(): File = File(mediaRoot(), ".status.json")

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    /** Settings screen for the user to grant "All files access" — required once, explained in-UI. */
    fun allFilesAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", context.packageName, null))
}
