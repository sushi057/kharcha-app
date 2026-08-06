package com.kharcha.app.ingest

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kharcha.app.notify.BudgetNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Thin shell around [MessageIngestor]. All ingestion logic lives in the ingestor; this
 * class unpacks the [WorkerParameters] input data, reports the WorkManager result, and —
 * for a [IngestOutcome.STORED] message with a resolved category — fans the result out to
 * [BudgetNotifier] so a budget crossed by this transaction gets its alert (Task 11; this
 * worker previously discarded the [IngestResult] entirely).
 */
@HiltWorker
class IngestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageIngestor: MessageIngestor,
    private val budgetNotifier: BudgetNotifier
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val receivedAtEpochMillis = inputData.getLong(KEY_RECEIVED_AT, -1L)
        if (receivedAtEpochMillis < 0L) return Result.failure()

        val result = messageIngestor.ingest(sender, body, receivedAtEpochMillis)
        val categoryId = result.categoryId
        val currency = result.currency
        if (result.outcome == IngestOutcome.STORED && categoryId != null && currency != null) {
            // The currency matters: a category can hold one budget per currency, and only
            // the one matching this transaction's currency may be evaluated.
            budgetNotifier.checkAndNotify(categoryId, currency)
        }
        return Result.success()
    }

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_RECEIVED_AT = "receivedAtEpochMillis"
    }
}
