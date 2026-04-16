package uk.storitad.capture.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val WORK_NAME_EVENING = "reminder-evening"
    private const val WORK_NAME_MORNING = "reminder-morning"

    fun reschedule(context: Context) {
        val prefs = ReminderPrefs(context)
        if (!prefs.enabled) {
            cancelAll(context)
            return
        }
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        enqueue(context, WORK_NAME_EVENING, SLOT_EVENING,
            computeInitialDelay(now, prefs.eveningTime))
        enqueue(context, WORK_NAME_MORNING, SLOT_MORNING,
            computeInitialDelay(now, prefs.morningTime))
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_NAME_EVENING)
        wm.cancelUniqueWork(WORK_NAME_MORNING)
    }

    private fun enqueue(
        context: Context,
        name: String,
        slot: String,
        initialDelay: Duration
    ) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KEY_SLOT, slot).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            name, ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }

    const val KEY_SLOT = "slot"
    const val SLOT_EVENING = "evening"
    const val SLOT_MORNING = "morning"
}
