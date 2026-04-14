package uk.storitad.capture.whisper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import uk.storitad.capture.metadata.MetadataRepository
import uk.storitad.capture.storage.FileManager

class TranscribeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val basename = inputData.getString(KEY_BASENAME) ?: return Result.failure()
        val ctx = applicationContext

        val repo = MetadataRepository(FileManager.inboxDir(ctx))
        val entry = runCatching { repo.read(basename) }.getOrNull()
            ?: return Result.failure(workDataOf(KEY_ERROR to "sidecar not found"))

        val mm = ModelManager(ctx)
        val model = TINY_EN
        if (!mm.isPresent(model)) {
            return Result.failure(workDataOf(KEY_ERROR to "model not present"))
        }

        val media = repo.mediaFile(entry)
        return try {
            val transcript = Transcriber(mm.fileFor(model).absolutePath).transcribe(media)
            repo.write(entry.copy(
                transcriptDraft = transcript,
                transcriptModel = "whisper-${model.id}"
            ))
            Result.success()
        } catch (t: Throwable) {
            Result.failure(workDataOf(KEY_ERROR to (t.message ?: "transcribe failed")))
        }
    }

    companion object {
        const val KEY_BASENAME = "basename"
        const val KEY_ERROR = "error"

        fun uniqueName(basename: String) = "transcribe/$basename"

        fun enqueue(ctx: Context, basename: String) {
            val req = OneTimeWorkRequestBuilder<TranscribeWorker>()
                .setInputData(Data.Builder().putString(KEY_BASENAME, basename).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                uniqueName(basename), ExistingWorkPolicy.KEEP, req
            )
        }
    }
}
