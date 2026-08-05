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
 * [outcome] plus, for a [IngestOutcome.STORED] result, the category the new transaction
 * landed in (`null` if uncategorized). Task 11's [com.kharcha.app.notify.BudgetNotifier]
 * needs this signal — [IngestWorker] previously discarded [outcome] entirely.
 */
data class IngestResult(val outcome: IngestOutcome, val categoryId: Long? = null)

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
    /**
     * Single-message entry point, used by the live [IngestWorker] path. Builds its own
     * [Categorizer] from the current rule set on every call, so a message arriving right
     * after the user edits a rule picks up that change immediately.
     *
     * For batch use (backfill), prefer [ingest] with an explicit [categorizer] parameter
     * built once via [loadCategorizer] — re-querying [ruleDao] and re-sorting the rule set
     * per message is wasted work across a large batch and would visibly slow down a
     * first-run historical import.
     */
    suspend fun ingest(sender: String, body: String, receivedAtEpochMillis: Long): IngestResult =
        ingest(sender, body, receivedAtEpochMillis, categorizer = null)

    /**
     * Loads the current rule set once and builds a [Categorizer] from it. Callers doing
     * batch ingestion (e.g. [BackfillWorker]) should call this once per run and pass the
     * result to [ingest] for every message in the batch, instead of letting each message
     * re-query [ruleDao] on its own.
     */
    suspend fun loadCategorizer(): Categorizer = Categorizer(ruleDao.observeAll().first())

    /**
     * Batch entry point: identical pipeline to the single-message [ingest], except
     * categorization uses the supplied [categorizer] instead of querying [ruleDao] itself.
     * Pass a [Categorizer] built once via [loadCategorizer] for a whole batch run.
     */
    suspend fun ingest(
        sender: String,
        body: String,
        receivedAtEpochMillis: Long,
        categorizer: Categorizer?
    ): IngestResult {
        if (!sender.equals(ruleset.senderId, ignoreCase = true)) {
            return IngestResult(IngestOutcome.WRONG_SENDER)
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
            return IngestResult(IngestOutcome.DUPLICATE)
        }

        return when (val result = ruleset.parse(body)) {
            is ParseResult.Parsed -> {
                val categoryId = categorize(result.transaction, categorizer)
                transactionDao.insert(result.transaction.toEntity(rawMessageId, categoryId))
                IngestResult(IngestOutcome.STORED, categoryId)
            }

            is ParseResult.Ignored -> {
                rawMessageDao.markIgnored(rawMessageId)
                IngestResult(IngestOutcome.IGNORED)
            }

            ParseResult.Unrecognized -> IngestResult(IngestOutcome.UNPARSED)
        }
    }

    /**
     * Best-effort category lookup. A transaction is always worth storing even if
     * categorization can't be determined or blows up for some unforeseen reason —
     * losing the transaction is strictly worse than leaving it uncategorized. When
     * [categorizer] is null (the single-message path), the current rule set is fetched
     * fresh from [ruleDao] on every call so a just-added rule takes effect immediately.
     */
    private suspend fun categorize(transaction: ParsedTransaction, categorizer: Categorizer?): Long? =
        try {
            val effectiveCategorizer = categorizer ?: Categorizer(ruleDao.observeAll().first())
            effectiveCategorizer.categorize(transaction.remark, transaction.merchant)
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
