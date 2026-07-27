package com.oiw.camera.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.oiw.camera.R

/**
 * Foreground service keeping the camera/recording pipeline alive when the screen is locked
 * during a take (docs/CINEMA_FEATURES.md #7 rig mode, docs/UI_UX.md #2 lock-screen behavior).
 * The actual encode/mux pipeline lives in [Recorder]; this service's job is solely to hold a
 * FOREGROUND_SERVICE_TYPE_CAMERA/MICROPHONE promise so the OS doesn't kill the process mid-take.
 */
class RecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(elapsedLabel = "00:00:00"))
        return START_STICKY
    }

    fun updateElapsed(label: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(label))
    }

    private fun buildNotification(elapsedLabel: String): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OIW-ROM recording")
            .setContentText("Elapsed: $elapsedLabel — tap to open OIWCamera")
            .setSmallIcon(R.drawable.ic_record_indicator)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recording status", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "oiw_recording"
    }
}
