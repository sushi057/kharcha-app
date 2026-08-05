package com.kharcha.app.ingest

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Thin shell around [MessageIngestor]. All ingestion logic lives in the ingestor; this
 * class only unpacks the [WorkerParameters] input data and reports the WorkManager result.
 */
@HiltWorker
class IngestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageIngestor: MessageIngestor
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val receivedAtEpochMillis = inputData.getLong(KEY_RECEIVED_AT, -1L)
        if (receivedAtEpochMillis < 0L) return Result.failure()

        messageIngestor.ingest(sender, body, receivedAtEpochMillis)
        return Result.success()
    }

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_RECEIVED_AT = "receivedAtEpochMillis"
    }
}
