package com.kharcha.app.ingest

import android.content.Context
import android.provider.Telephony
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-time historical import: reads the device's existing `SBL_Alert` messages from
 * [Telephony.Sms.Inbox] and runs each through [MessageIngestor], exactly like a live
 * message would go through [IngestWorker]. Safe to re-run — [MessageIngestor] dedups by
 * content hash — but [BackfillState] short-circuits repeat runs once the first pass has
 * completed, so a routine WorkManager re-enqueue doesn't re-scan the whole inbox.
 */
@HiltWorker
class BackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageIngestor: MessageIngestor,
    private val backfillState: BackfillState
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (backfillState.isComplete()) return Result.success()

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        // LIKE uses SQLite's default case-insensitive ASCII collation, matching the
        // case-insensitive sender check MessageIngestor itself applies.
        val selection = "${Telephony.Sms.ADDRESS} LIKE ?"
        val selectionArgs = arrayOf(TARGET_SENDER)

        applicationContext.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            // Cursor.moveToNext() pages through the underlying CursorWindow in fixed-size
            // chunks automatically, so a straightforward iteration is sufficient here —
            // the whole result set is never held in memory at once.
            while (cursor.moveToNext()) {
                val sender = cursor.getString(addressIndex) ?: continue
                val body = cursor.getString(bodyIndex) ?: continue
                val receivedAtEpochMillis = cursor.getLong(dateIndex)
                messageIngestor.ingest(sender, body, receivedAtEpochMillis)
            }
        }

        backfillState.markComplete()
        return Result.success()
    }

    companion object {
        private const val TARGET_SENDER = "SBL_Alert"
    }
}
