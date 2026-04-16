package uk.storitad.capture.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import uk.storitad.capture.MainActivity
import uk.storitad.capture.R

object ReminderNotification {

    const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID_EVENING = 2001
    private const val NOTIFICATION_ID_MORNING = 2002

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminders_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.reminders_channel_desc)
        }
        nm.createNotificationChannel(channel)
    }

    fun post(context: Context, slot: String) {
        ensureChannel(context)
        val id = when (slot) {
            ReminderScheduler.SLOT_MORNING -> NOTIFICATION_ID_MORNING
            else -> NOTIFICATION_ID_EVENING
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.notify(id, notif)
    }
}
