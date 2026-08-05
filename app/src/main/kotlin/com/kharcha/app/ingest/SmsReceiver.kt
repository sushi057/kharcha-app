package com.kharcha.app.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Registered for `android.provider.Telephony.SMS_RECEIVED`. Deliberately does no parsing
 * and no database access — a SMS receiver has a hard main-thread time budget and will be
 * killed if it does real work. It only extracts message bodies from the intent,
 * concatenates multipart parts from the same sender, and hands off to [IngestWorker].
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.first().originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val receivedAtEpochMillis = messages.first().timestampMillis

        val inputData = Data.Builder()
            .putString(IngestWorker.KEY_SENDER, sender)
            .putString(IngestWorker.KEY_BODY, body)
            .putLong(IngestWorker.KEY_RECEIVED_AT, receivedAtEpochMillis)
            .build()

        val request = OneTimeWorkRequestBuilder<IngestWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
