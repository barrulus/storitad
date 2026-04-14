package uk.storitad.capture.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import uk.storitad.capture.MainActivity

class RecordingService : Service() {

    inner class LocalBinder : Binder() { val service get() = this@RecordingService }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val type = intent?.getStringExtra(EXTRA_TYPE) ?: TYPE_MIC
        startInForeground(type)
        return START_NOT_STICKY
    }

    private fun startInForeground(type: String) {
        val notif = buildNotification("Recording…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val svcType = when (type) {
                TYPE_CAMERA -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                               ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIF_ID, notif, svcType)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    fun updateElapsed(ms: Long) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, buildNotification(formatElapsed(ms)))
    }

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val tap = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Storitad")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(tap)
            .build()
    }

    private fun formatElapsed(ms: Long): String {
        val sec = ms / 1000
        return "Recording · %d:%02d".format(sec / 60, sec % 60)
    }

    companion object {
        const val CHANNEL = "storitad.recording"
        const val NOTIF_ID = 1001
        const val EXTRA_TYPE = "type"
        const val TYPE_MIC = "mic"
        const val TYPE_CAMERA = "camera"
    }
}
