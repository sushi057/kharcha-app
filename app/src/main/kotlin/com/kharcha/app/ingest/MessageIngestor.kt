package com.kharcha.app.ingest

import com.kharcha.data.Categorizer
import com.kharcha.data.RawMessage
import com.kharcha.data.RawMessageDao
import com.kharcha.data.RuleDao
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import com.kharcha.data.contentHashOf
import com.kharcha.parser.SenderMatching
import com.kharcha.parser.Currency
import com.kharcha.parser.ParseResult
import com.kharcha.parser.ParsedTransaction
import com.kharcha.parser.SenderRuleset
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

enum class IngestOutcome { STORED, DUPLICATE, IGNORED, UNPARSED, WRONG_SENDER }

/**
 * [outcome] plus, for a [IngestOutcome.STORED] result, the category the new transaction
 * landed in (`null` if uncategorized) and the [currency] it was denominated in. Task 11's
 * [com.kharcha.app.notify.BudgetNotifier] needs both: a category can hold one budget per
 * currency, and only the budget matching *this* transaction's currency may be evaluated.
 * NPR and USD are never summed or converted, here or anywhere else.
 */
data class IngestResult(
    val outcome: IngestOutcome,
    val categoryId: Long? = null,
    val currency: Currency? = null,
)

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
        // Tolerates the separator substitution described in SenderMatching; an exact
        // equals() here drops those messages silently and the user never learns why.
        if (!SenderMatching.matches(sender, ruleset.senderId)) {
            return IngestResult(IngestOutcome.WRONG_SENDER)
        }

        // Exact-content dedup is the primary gate, but it cannot cover the first-run
        // overlap window on its own. SmsReceiver hashes SmsMessage.timestampMillis (the
        // SMSC timestamp); BackfillWorker hashes Telephony.Sms.DATE (the device's own
        // reception time for the inbox row). For one and the same message those differ by
        // seconds, so the hashes differ and the unique index never fires — the user grants
        // permission, the backfill starts scanning, an SBL_Alert arrives and is ingested
        // live, the provider has already written it to the inbox, the cursor reaches it,
        // and the same transaction is counted twice in month-to-date spend.
        //
        // Chosen fix: keep the exact hash exactly as it is (it stays length-prefixed
        // against delimiter collisions, per Task 5) and add a secondary
        // (sender, body, ±NEAR_DUPLICATE_WINDOW_MILLIS) check that only runs when the hash
        // misses. Normalizing the two timestamp sources is not possible — they are
        // genuinely different clocks — and a coarse time bucket would still split a pair
        // that straddles a bucket boundary while merging unrelated messages inside one.
        // The window is deliberately short: two genuinely distinct transactions can share
        // a body (same merchant, same amount, a different day) and must both be stored.
        val nearDuplicate = rawMessageDao.findNearDuplicate(
            sender = sender,
            body = body,
            fromEpochMillis = receivedAtEpochMillis - NEAR_DUPLICATE_WINDOW_MILLIS,
            toEpochMillis = receivedAtEpochMillis + NEAR_DUPLICATE_WINDOW_MILLIS,
        )
        if (nearDuplicate != null) {
            return IngestResult(IngestOutcome.DUPLICATE)
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
                IngestResult(IngestOutcome.STORED, categoryId, result.transaction.amount.currency)
            }

            is ParseResult.Ignored -> {
                rawMessageDao.markIgnored(rawMessageId, result.reason)
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

    companion object {
        /**
         * How far apart the SMSC timestamp and the inbox row's reception time may be and
         * still describe the same delivery. Ten minutes comfortably covers normal delivery
         * skew (seconds) plus a phone that was briefly out of coverage, while staying far
         * below the gap between two real, separately-initiated payments to the same
         * merchant for the same amount.
         */
        internal const val NEAR_DUPLICATE_WINDOW_MILLIS = 10L * 60 * 1000
    }
}
