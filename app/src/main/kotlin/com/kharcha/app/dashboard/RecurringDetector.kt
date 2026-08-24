package com.kharcha.app.dashboard

import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

/**
 * A detected recurring charge: same merchant, similar amount (±10%), roughly monthly cadence.
 * [nextOccurrenceDate] is projected based on average cadence from seen occurrences.
 */
data class RecurringCharge(
    val merchant: String,
    val currency: Currency,
    val amountMinorUnits: Long,
    val occurrenceCount: Int,
    val averageDaysBetweenOccurrences: Int,
    val nextOccurrenceDate: LocalDate,
    val totalMinorUnits: Long,
)

/**
 * Detects recurring charges: same merchant, within ~10% amount variance,
 * occurring at roughly monthly intervals (25–35 days apart), with at least 3 occurrences.
 *
 * Eligible transactions: DEBIT direction, not excluded from spending, within a category
 * that is neither income nor fee.
 *
 * Returns an empty list if no recurring patterns found.
 */
object RecurringDetector {

    private const val MIN_OCCURRENCES = 3
    private const val MIN_DAYS_BETWEEN = 25
    private const val MAX_DAYS_BETWEEN = 35
    private const val AMOUNT_VARIANCE_PERCENT = 10

    fun detectRecurring(
        transactions: List<TransactionEntity>,
        zone: TimeZone,
    ): List<RecurringCharge> {
        // Filter to DEBIT transactions, not excluded, with non-blank merchant
        val eligible = transactions
            .filter { it.direction == Direction.DEBIT && !it.excludedFromSpending && !it.merchant.isNullOrBlank() }
            .sortedBy { it.occurredAtEpochMillis }

        if (eligible.size < MIN_OCCURRENCES) return emptyList()

        // Group by merchant
        val byMerchant = eligible.groupBy { it.merchant ?: "" }

        return byMerchant.mapNotNull { (merchant, txns) ->
            if (merchant.isNotBlank()) {
                detectPattern(merchant, txns, zone)
            } else {
                null
            }
        }
    }

    private fun detectPattern(
        merchant: String,
        transactions: List<TransactionEntity>,
        zone: TimeZone,
    ): RecurringCharge? {
        if (transactions.size < MIN_OCCURRENCES) return null

        // Group by currency
        val byCurrency = transactions.groupBy { it.currency }

        return byCurrency.mapNotNull { (currency, currencyTxns) ->
            detectPatternForCurrency(merchant, currency, currencyTxns, zone)
        }.firstOrNull() // Return the first currency with a pattern (typically only one per merchant)
    }

    private fun detectPatternForCurrency(
        merchant: String,
        currency: Currency,
        transactions: List<TransactionEntity>,
        zone: TimeZone,
    ): RecurringCharge? {
        if (transactions.size < MIN_OCCURRENCES) return null

        // Find the most common amount (modal)
        val amountCounts = transactions.groupingBy { it.amountMinorUnits }.eachCount()
        val modalAmount = amountCounts.maxByOrNull { it.value }?.key ?: return null

        // Check that at least 3 transactions fall within ±10% of modal
        val withinVariance = transactions.filter { txn ->
            val variance = abs(txn.amountMinorUnits - modalAmount).toDouble() / modalAmount
            variance <= AMOUNT_VARIANCE_PERCENT / 100.0
        }

        if (withinVariance.size < MIN_OCCURRENCES) return null

        // Verify cadence: check gaps between sorted occurrences
        val sorted = withinVariance.sortedBy { it.occurredAtEpochMillis }
        val gaps = mutableListOf<Int>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            val daysBetween = daysBetween(prev.occurredAtEpochMillis, curr.occurredAtEpochMillis)
            gaps.add(daysBetween)
        }

        // All gaps should be in the [MIN_DAYS_BETWEEN, MAX_DAYS_BETWEEN] range
        if (gaps.any { it !in MIN_DAYS_BETWEEN..MAX_DAYS_BETWEEN }) return null

        // Compute average cadence and project next occurrence
        val averageCadence = if (gaps.isNotEmpty()) gaps.average().toInt() else 30
        val lastOccurrence = sorted.last()
        val nextDate = addDays(
            dateFromEpochMillis(lastOccurrence.occurredAtEpochMillis, zone),
            averageCadence
        )

        val total = withinVariance.sumOf { it.amountMinorUnits }

        return RecurringCharge(
            merchant = merchant,
            currency = currency,
            amountMinorUnits = modalAmount,
            occurrenceCount = withinVariance.size,
            averageDaysBetweenOccurrences = averageCadence,
            nextOccurrenceDate = nextDate,
            totalMinorUnits = total,
        )
    }

    private fun daysBetween(earlierEpochMillis: Long, laterEpochMillis: Long): Int {
        val diffMillis = laterEpochMillis - earlierEpochMillis
        return (diffMillis / (24 * 60 * 60 * 1000)).toInt()
    }

    private fun dateFromEpochMillis(epochMillis: Long, zone: TimeZone): LocalDate {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
        return instant.toLocalDateTime(zone).date
    }

    private fun addDays(date: LocalDate, days: Int): LocalDate {
        return date.plus(days, kotlinx.datetime.DateTimeUnit.DAY)
    }
}
