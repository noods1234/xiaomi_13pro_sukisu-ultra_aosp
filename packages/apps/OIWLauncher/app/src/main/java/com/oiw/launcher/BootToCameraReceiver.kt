package com.oiw.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Optional boot-to-camera behavior (docs/UI_UX.md #1, root brief #7.1). Only fires if the user has
 * opted in via Settings — never a surprise default. If starting an activity directly from a boot
 * receiver is blocked by this HyperOS version's background-start restrictions (assumptions.md
 * A-2 — verify per OS version), this falls back to a persistent notification rather than failing
 * silently, per the "no silent failure" rule.
 */
class BootToCameraReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = LauncherPreferences(context)
        if (!prefs.bootToCameraEnabled) return

        val launchIntent = context.packageManager.getLaunchIntentForPackage(HomeActivity.CAMERA_PACKAGE)
        if (launchIntent == null) {
            Log.w(TAG, "Boot-to-camera enabled but OIWCamera package not found.")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launchIntent)
        } catch (e: SecurityException) {
            // Background-activity-start restriction hit (varies by OS version, assumptions.md A-2).
            // Fall back to a persistent "tap to enter camera" notification instead of silently doing nothing.
            BootFallbackNotification.show(context)
        }
    }

    companion object {
        private const val TAG = "OIWBootToCamera"
    }
}
