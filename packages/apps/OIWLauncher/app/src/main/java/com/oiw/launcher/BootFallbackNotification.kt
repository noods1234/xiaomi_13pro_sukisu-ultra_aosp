package com.oiw.launcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat

/** Fallback for when a direct boot-to-camera activity launch is blocked (see BootToCameraReceiver). */
object BootFallbackNotification {
    private const val CHANNEL_ID = "oiw_boot_fallback"
    private const val NOTIFICATION_ID = 7

    fun show(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Boot to camera", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(HomeActivity.CAMERA_PACKAGE)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Tap to enter camera")
            .setContentText("Automatic boot-to-camera was blocked by the system this time.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
