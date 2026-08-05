package com.kharcha.app.dashboard

import com.kharcha.data.CategoryEntity
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import com.kharcha.parser.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardAggregatorTest {

    private val zone = TimeZone.UTC
    private val monthStart = LocalDate(2026, 8, 1).atStartOfDayIn(zone).toEpochMilliseconds()
    private val monthEnd = LocalDate(2026, 9, 1).atStartOfDayIn(zone).toEpochMilliseconds()

    private val foodCategory = CategoryEntity(id = 1L, name = "Food & Dining", colorArgb = 0xFFE8734A.toInt(), isIncome = false, isFee = false)
    private val incomeCategory = CategoryEntity(id = 2L, name = "Income", colorArgb = 0xFF00FF00.toInt(), isIncome = true, isFee = false)
    private val feeCategory = CategoryEntity(id = 3L, name = "Fees", colorArgb = 0xFFAA0000.toInt(), isIncome = false, isFee = true)
    private val categories = listOf(foodCategory, incomeCategory, feeCategory)

    private fun epochMillis(day: Int, hour: Int = 12): Long =
        LocalDate(2026, 8, day).atStartOfDayIn(zone).toEpochMilliseconds() + hour * 3_600_000L

    private fun txn(
        id: Long,
        amount: Long,
        currency: Currency = Currency.NPR,
        direction: Direction = Direction.DEBIT,
        day: Int = 5,
        categoryId: Long? = 1L,
        merchant: String? = "Merchant $id",
        excluded: Boolean = false,
    ) = TransactionEntity(
        id = id,
        rawMessageId = null,
        sourceAccount = "acct",
        amountMinorUnits = amount,
        currency = currency,
        direction = direction,
        occurredAtEpochMillis = epochMillis(day),
        remark = "remark",
        merchant = merchant,
        balanceAfterMinorUnits = null,
        categoryId = categoryId,
        categoryIsManualOverride = false,
        excludedFromSpending = excluded,
        isManualEntry = false,
    )

    private fun aggregate(transactions: List<TransactionEntity>) = DashboardAggregator.aggregate(
        transactions = transactions,
        categories = categories,
        monthStartEpochMillis = monthStart,
        monthEndExclusiveEpochMillis = monthEnd,
        zone = zone,
    )

    @Test
    fun `excluded transactions are absent from every aggregate`() {
        val hugeExcludedTransfer = txn(id = 1, amount = 101_562_500L, excluded = true, merchant = "Transfer")
        val qrPayment = txn(id = 2, amount = 298_400L, merchant = "Restaurant")
        val result = aggregate(listOf(hugeExcludedTransfer, qrPayment))

        assertEquals(Money(298_400L, Currency.NPR), result.monthToDateSpend[Currency.NPR])
        assertTrue(result.byCategory.none { it.total.minorUnits == 101_562_500L })
        assertTrue(result.trend.none { it.total.minorUnits == 101_562_500L })
        assertTrue(result.topMerchants.none { it.merchant == "Transfer" })
    }

    @Test
    fun `income and fees do not count as discretionary spend`() {
        val incomeCredit = txn(id = 1, amount = 2_492_044L, direction = Direction.CREDIT, categoryId = 2L)
        val feeDebit = txn(id = 2, amount = 5000L, categoryId = 3L)
        val result = aggregate(listOf(incomeCredit, feeDebit))

        assertEquals(Money(0L, Currency.NPR), result.monthToDateSpend[Currency.NPR])
        assertTrue(result.byCategory.isEmpty())
    }

    @Test
    fun `NPR and USD are aggregated separately, never summed`() {
        val nprSpend = txn(id = 1, amount = 298_400L, currency = Currency.NPR)
        val usdSpend = txn(id = 2, amount = 198L, currency = Currency.USD)
        val result = aggregate(listOf(nprSpend, usdSpend))

        assertEquals(setOf(Currency.NPR, Currency.USD), result.monthToDateSpend.keys)
        assertEquals(Money(298_400L, Currency.NPR), result.monthToDateSpend[Currency.NPR])
        assertEquals(Money(198L, Currency.USD), result.monthToDateSpend[Currency.USD])
    }

    @Test
    fun `null categoryId is bucketed as an explicit Uncategorized entry, not dropped`() {
        val uncategorized = txn(id = 1, amount = 1500L, categoryId = null)
        val result = aggregate(listOf(uncategorized))

        assertEquals(Money(1500L, Currency.NPR), result.monthToDateSpend[Currency.NPR])
        val bucket = result.byCategory.single()
        assertEquals(null, bucket.categoryId)
        assertEquals(UNCATEGORIZED_CATEGORY_NAME, bucket.categoryName)
    }

    @Test
    fun `transactions outside the month range are excluded`() {
        val lastMonth = txn(id = 1, amount = 1000L, day = 5).copy(
            occurredAtEpochMillis = LocalDate(2026, 7, 20).atStartOfDayIn(zone).toEpochMilliseconds(),
        )
        val result = aggregate(listOf(lastMonth))
        assertTrue(result.monthToDateSpend.isEmpty())
    }
}
