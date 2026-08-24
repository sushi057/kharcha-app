package com.kharcha.app.dashboard

import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurringDetectorTest {

    private val zone = TimeZone.UTC

    private fun epochMillis(date: LocalDate, hour: Int = 12): Long =
        date.atStartOfDayIn(zone).toEpochMilliseconds() + hour * 3_600_000L

    private fun txn(
        id: Long,
        date: LocalDate,
        amount: Long,
        merchant: String = "Netflix",
        direction: Direction = Direction.DEBIT,
        currency: Currency = Currency.NPR,
        excluded: Boolean = false,
    ) = TransactionEntity(
        id = id,
        rawMessageId = null,
        sourceAccount = "acct",
        amountMinorUnits = amount,
        currency = currency,
        direction = direction,
        occurredAtEpochMillis = epochMillis(date),
        remark = "remark",
        merchant = merchant,
        balanceAfterMinorUnits = null,
        categoryId = 1L,
        categoryIsManualOverride = false,
        excludedFromSpending = excluded,
        isManualEntry = false,
    )

    @Test
    fun threeIdenticalTransactionsAreDetected() {
        val transactions = listOf(
            txn(1, LocalDate(2026, 8, 5), 99900L, merchant = "Netflix"),
            txn(2, LocalDate(2026, 9, 5), 99900L, merchant = "Netflix"),
            txn(3, LocalDate(2026, 10, 5), 99900L, merchant = "Netflix"),
        )
        val recurring = RecurringDetector.detectRecurring(transactions, zone)

        assertEquals(1, recurring.size)
        val charge = recurring[0]
        assertEquals("Netflix", charge.merchant)
        assertEquals(99900L, charge.amountMinorUnits)
        assertEquals(3, charge.occurrenceCount)
    }

    @Test
    fun irregularTransactionsNotDetected() {
        val transactions = listOf(
            txn(1, LocalDate(2026, 8, 5), 50000L, merchant = "Random"),
            txn(2, LocalDate(2026, 8, 20), 40000L, merchant = "Random"),
            txn(3, LocalDate(2026, 9, 5), 60000L, merchant = "Random"),
        )
        val recurring = RecurringDetector.detectRecurring(transactions, zone)

        assertTrue(recurring.isEmpty())
    }

    @Test
    fun lessThan3TransactionsNotDetected() {
        val transactions = listOf(
            txn(1, LocalDate(2026, 8, 5), 99900L, merchant = "Netflix"),
            txn(2, LocalDate(2026, 9, 5), 99900L, merchant = "Netflix"),
        )
        val recurring = RecurringDetector.detectRecurring(transactions, zone)

        assertTrue(recurring.isEmpty())
    }

    @Test
    fun transactionsWithHighVarianceNotRecurring() {
        val transactions = listOf(
            txn(1, LocalDate(2026, 8, 5), 100000L, merchant = "Spotify"),
            txn(2, LocalDate(2026, 9, 5), 115000L, merchant = "Spotify"), // 15% increase
            txn(3, LocalDate(2026, 10, 5), 100000L, merchant = "Spotify"),
        )
        val recurring = RecurringDetector.detectRecurring(transactions, zone)

        assertTrue(recurring.isEmpty())
    }

    @Test
    fun creditTransactionsExcluded() {
        val transactions = listOf(
            txn(1, LocalDate(2026, 8, 5), 99900L, merchant = "Netflix", direction = Direction.CREDIT),
            txn(2, LocalDate(2026, 9, 5), 99900L, merchant = "Netflix", direction = Direction.CREDIT),
            txn(3, LocalDate(2026, 10, 5), 99900L, merchant = "Netflix", direction = Direction.CREDIT),
        )
        val recurring = RecurringDetector.detectRecurring(transactions, zone)

        assertTrue(recurring.isEmpty())
    }

    @Test
    fun excludedTransactionsSkipped() {
        val transactions = listOf(
            txn(1, LocalDate(2026, 8, 5), 99900L, merchant = "Netflix", excluded = false),
            txn(2, LocalDate(2026, 9, 5), 99900L, merchant = "Netflix", excluded = true),
            txn(3, LocalDate(2026, 10, 5), 99900L, merchant = "Netflix", excluded = false),
        )
        val recurring = RecurringDetector.detectRecurring(transactions, zone)

        assertTrue(recurring.isEmpty())
    }

    @Test
    fun blankMerchantsExcluded() {
        val transactions = listOf(
            txn(1, LocalDate(2026, 8, 5), 99900L, merchant = ""),
            txn(2, LocalDate(2026, 9, 5), 99900L, merchant = ""),
            txn(3, LocalDate(2026, 10, 5), 99900L, merchant = ""),
        )
        val recurring = RecurringDetector.detectRecurring(transactions, zone)

        assertTrue(recurring.isEmpty())
    }

    @Test
    fun multipleRecurringMerchantsDetected() {
        val transactions = listOf(
            // Netflix: 3 occurrences
            txn(1, LocalDate(2026, 8, 5), 99900L, merchant = "Netflix"),
            txn(2, LocalDate(2026, 9, 5), 99900L, merchant = "Netflix"),
            txn(3, LocalDate(2026, 10, 5), 99900L, merchant = "Netflix"),
            // Spotify: 3 occurrences
            txn(4, LocalDate(2026, 8, 10), 12500L, merchant = "Spotify"),
            txn(5, LocalDate(2026, 9, 10), 12500L, merchant = "Spotify"),
            txn(6, LocalDate(2026, 10, 10), 12500L, merchant = "Spotify"),
        )
        val recurring = RecurringDetector.detectRecurring(transactions, zone)

        assertEquals(2, recurring.size)
        val merchants = recurring.map { it.merchant }.sorted()
        assertEquals(listOf("Netflix", "Spotify"), merchants)
    }
}
