package com.kharcha.data

import com.kharcha.parser.ParseResult
import com.kharcha.parser.SenderRuleset
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Re-runs the parsing and categorization pipeline over every stored raw
 * message. This is how rule improvements (new [RuleEntity] rows, ruleset
 * fixes) retroactively improve already-ingested transactions without the
 * user re-importing anything.
 *
 * Two properties make this safe to run repeatedly and often:
 *  - **Idempotent**: each raw message maps to at most one transaction, keyed
 *    by [TransactionEntity.rawMessageId]. Re-parsing updates that existing
 *    row in place rather than inserting a new one, so running it twice never
 *    duplicates transactions.
 *  - **Override-preserving**: a transaction the user has hand-categorized
 *    (`categoryIsManualOverride = true`) is left completely untouched. Only
 *    the auto-assigned `categoryId` of non-overridden rows is recomputed;
 *    fields like `excludedFromSpending` or `isManualEntry` set by the user
 *    are never revisited automatically either.
 */
class ReparseService(
    private val rawMessageDao: RawMessageDao,
    private val transactionDao: TransactionDao,
    private val ruleset: SenderRuleset,
    private val categorizer: Categorizer
) {
    suspend fun reparseAll() {
        for (raw in rawMessageDao.getAll()) {
            if (raw.ignored) continue

            val existing = transactionDao.getByRawMessageId(raw.id)
            if (existing?.categoryIsManualOverride == true) continue

            when (val result = ruleset.parse(raw.body)) {
                is ParseResult.Parsed -> {
                    val parsed = result.transaction
                    val categoryId = categorizer.categorize(parsed.remark, parsed.merchant)

                    if (existing == null) {
                        transactionDao.insert(
                            TransactionEntity(
                                rawMessageId = raw.id,
                                sourceAccount = parsed.sourceAccount,
                                amountMinorUnits = parsed.amount.minorUnits,
                                currency = parsed.amount.currency,
                                direction = parsed.direction,
                                occurredAtEpochMillis = parsed.occurredAt
                                    .toInstant(TimeZone.currentSystemDefault())
                                    .toEpochMilliseconds(),
                                remark = parsed.remark,
                                merchant = parsed.merchant,
                                balanceAfterMinorUnits = parsed.balanceAfter?.minorUnits,
                                categoryId = categoryId,
                                categoryIsManualOverride = false,
                                excludedFromSpending = false,
                                isManualEntry = false
                            )
                        )
                    } else {
                        // Categorization-only refresh: only `categoryId` is
                        // recomputed here. If a future parser improvement
                        // extracts a different amount/remark/merchant/etc.
                        // for the same raw message, those fields are NOT
                        // resynced by reparseAll() — only the category is.
                        transactionDao.update(existing.copy(categoryId = categoryId))
                    }
                }

                is ParseResult.Ignored, ParseResult.Unrecognized -> {
                    // Nothing to categorize; leave any existing transaction alone.
                }
            }
        }
    }
}
