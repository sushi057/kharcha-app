package com.kharcha.app.ingest

import com.kharcha.data.Categorizer
import com.kharcha.data.RawMessage
import com.kharcha.data.RawMessageDao
import com.kharcha.data.RuleDao
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import com.kharcha.data.contentHashOf
import com.kharcha.parser.ParseResult
import com.kharcha.parser.ParsedTransaction
import com.kharcha.parser.SenderRuleset
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

enum class IngestOutcome { STORED, DUPLICATE, IGNORED, UNPARSED, WRONG_SENDER }

/**
 * Owns the entire SMS-to-transaction pipeline: sender filtering, dedup, parsing,
 * categorization and persistence. Deliberately plain-Kotlin so it is unit-testable with
 * in-memory fakes for [RawMessageDao], [TransactionDao] and [RuleDao] — no Room, no
 * Robolectric, no Android framework types. The [android.content.BroadcastReceiver] and
 * WorkManager workers around this class must stay thin shells that just call [ingest].
 */
class MessageIngestor(
    private val rawMessageDao: RawMessageDao,
    private val transactionDao: TransactionDao,
    private val ruleset: SenderRuleset,
    private val ruleDao: RuleDao
) {
    suspend fun ingest(sender: String, body: String, receivedAtEpochMillis: Long): IngestOutcome {
        if (!sender.equals(ruleset.senderId, ignoreCase = true)) {
            return IngestOutcome.WRONG_SENDER
        }

        val contentHash = contentHashOf(sender, body, receivedAtEpochMillis)
        val rawMessage = RawMessage(
            sender = sender,
            body = body,
            receivedAtEpochMillis = receivedAtEpochMillis,
            contentHash = contentHash
        )
        val rawMessageId = rawMessageDao.insertIgnoringDuplicates(rawMessage)
        if (rawMessageId == -1L) {
            return IngestOutcome.DUPLICATE
        }

        return when (val result = ruleset.parse(body)) {
            is ParseResult.Parsed -> {
                val categoryId = categorize(result.transaction)
                transactionDao.insert(result.transaction.toEntity(rawMessageId, categoryId))
                IngestOutcome.STORED
            }

            is ParseResult.Ignored -> {
                rawMessageDao.markIgnored(rawMessageId)
                IngestOutcome.IGNORED
            }

            ParseResult.Unrecognized -> IngestOutcome.UNPARSED
        }
    }

    /**
     * Best-effort category lookup. A transaction is always worth storing even if
     * categorization can't be determined or blows up for some unforeseen reason —
     * losing the transaction is strictly worse than leaving it uncategorized.
     */
    private suspend fun categorize(transaction: ParsedTransaction): Long? =
        try {
            val rules = ruleDao.observeAll().first()
            Categorizer(rules).categorize(transaction.remark, transaction.merchant)
        } catch (_: Exception) {
            null
        }

    private fun ParsedTransaction.toEntity(rawMessageId: Long, categoryId: Long?): TransactionEntity =
        TransactionEntity(
            rawMessageId = rawMessageId,
            sourceAccount = sourceAccount,
            amountMinorUnits = amount.minorUnits,
            currency = amount.currency,
            direction = direction,
            occurredAtEpochMillis = occurredAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            remark = remark,
            merchant = merchant,
            balanceAfterMinorUnits = balanceAfter?.minorUnits,
            categoryId = categoryId,
            categoryIsManualOverride = false,
            excludedFromSpending = false,
            isManualEntry = false
        )
}
