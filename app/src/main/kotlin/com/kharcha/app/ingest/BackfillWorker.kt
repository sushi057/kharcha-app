package com.kharcha.app.ingest

import android.content.Context
import android.provider.Telephony
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kharcha.parser.SenderMatching
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
        // A user-triggered re-scan ("Sync now") must run even though the first-run import
        // already finished, so it overrides the [BackfillState] short-circuit. Re-scanning
        // is safe: MessageIngestor dedups by content hash and by the near-duplicate window,
        // so messages already imported are not counted twice.
        val forced = inputData.getBoolean(KEY_FORCE, false)
        if (!forced && backfillState.isComplete()) return Result.success()

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        // A deliberately loose prefilter, narrowed afterwards by SenderMatching. LIKE's
        // `_` is a one-character wildcard, which is why `LIKE 'SBL_Alert'` used to import
        // "SBLXAlert" too — so the rows it returns are not trusted as-is: every one is
        // re-checked in Kotlin below, where the underscore may only stand in for a
        // separator. MessageIngestor applies the identical check to live messages.
        val patterns = SenderMatching.sqlLikePatterns(TARGET_SENDER)
        val selection = patterns.joinToString(" OR ") { "${Telephony.Sms.ADDRESS} LIKE ?" }
        val selectionArgs = patterns.toTypedArray()

        // Built once per backfill run, not once per message: MessageIngestor.ingest's
        // single-message overload would otherwise re-query the rule table and re-sort it
        // on every one of potentially thousands of historical rows.
        val categorizer = messageIngestor.loadCategorizer()

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
                messageIngestor.ingest(sender, body, receivedAtEpochMillis, categorizer)
            }
        }

        backfillState.markComplete()
        return Result.success()
    }

    companion object {
        private const val TARGET_SENDER = "SBL_Alert"

        /** Set by a user-triggered re-scan to bypass the once-ever [BackfillState] gate. */
        const val KEY_FORCE = "force"
    }
}
