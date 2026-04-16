package uk.storitad.capture.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import uk.storitad.capture.storage.FileManager
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ReminderWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ReminderPrefs(ctx)
        if (!prefs.enabled) return Result.success()

        val slot = inputData.getString(ReminderScheduler.KEY_SLOT)
            ?: ReminderScheduler.SLOT_EVENING

        val files = FileManager.inboxDir(ctx).listFiles()
            ?.filter { it.extension.lowercase() in setOf("m4a", "mp4") }
            .orEmpty()

        val alreadyRecorded = when (slot) {
            ReminderScheduler.SLOT_EVENING ->
                hasFilenameForDate(files, LocalDate.now(ZoneId.systemDefault()))
            ReminderScheduler.SLOT_MORNING ->
                hasRecordingSince(files, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24))
            else ->
                hasFilenameForDate(files, LocalDate.now(ZoneId.systemDefault()))
        }

        if (!alreadyRecorded) {
            ReminderNotification.post(ctx, slot)
        }
        return Result.success()
    }
}
