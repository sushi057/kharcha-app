package com.kharcha.app.ui.dashboard

import com.kharcha.data.CategoryDao
import com.kharcha.data.CategoryEntity
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import com.kharcha.parser.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeTransactionDao(seed: List<TransactionEntity> = emptyList()) : TransactionDao {
    private val flow = MutableStateFlow(seed)
    override suspend fun insert(transaction: TransactionEntity): Long = 0L
    override suspend fun update(transaction: TransactionEntity) = Unit
    override suspend fun delete(transaction: TransactionEntity) = Unit
    override suspend fun getById(id: Long): TransactionEntity? = flow.value.find { it.id == id }
    override suspend fun getByRawMessageId(rawMessageId: Long): TransactionEntity? = null
    override fun observeAll(): Flow<List<TransactionEntity>> = flow
}

private class FakeCategoryDao(private val categories: List<CategoryEntity> = emptyList()) : CategoryDao {
    override suspend fun insert(category: CategoryEntity): Long = 0L
    override suspend fun update(category: CategoryEntity) = Unit
    override suspend fun delete(category: CategoryEntity) = Unit
    override suspend fun getById(id: Long): CategoryEntity? = categories.find { it.id == id }
    override fun observeAll(): Flow<List<CategoryEntity>> = MutableStateFlow(categories)
}

/** Fixed clock so "month-to-date" tests never depend on the real wall clock (ruling 1). */
private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

class DashboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val zone = TimeZone.UTC
    private val fixedNow = LocalDate(2026, 8, 5).atStartOfDayIn(zone)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val incomeCategory = CategoryEntity(id = 1L, name = "Income", colorArgb = 0xFF00FF00.toInt(), isIncome = true, isFee = false)
    private val categories = listOf(incomeCategory)

    private fun epochMillis(day: Int): Long =
        LocalDate(2026, 8, day).atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L

    private fun transaction(
        id: Long,
        amount: Long,
        currency: Currency,
        direction: Direction = Direction.DEBIT,
        categoryId: Long? = null,
        excluded: Boolean = false,
        day: Int = 5,
    ) = TransactionEntity(
        id = id,
        rawMessageId = null,
        sourceAccount = "0###15164761",
        amountMinorUnits = amount,
        currency = currency,
        direction = direction,
        occurredAtEpochMillis = epochMillis(day),
        remark = "remark",
        merchant = "Merchant",
        balanceAfterMinorUnits = null,
        categoryId = categoryId,
        categoryIsManualOverride = false,
        excludedFromSpending = excluded,
        isManualEntry = false,
    )

    private fun newViewModel(
        transactions: List<TransactionEntity>,
        categoryList: List<CategoryEntity> = categories,
    ) = DashboardViewModel(
        transactionDao = FakeTransactionDao(transactions),
        categoryDao = FakeCategoryDao(categoryList),
        clock = FixedClock(fixedNow),
        zone = zone,
    )

    @Test
    fun excludedTransactionsAbsent() = runTest {
        val hugeExcludedTransfer = transaction(id = 1, amount = 101_562_500L, currency = Currency.NPR, excluded = true)
        val qrPayment = transaction(id = 2, amount = 298_400L, currency = Currency.NPR)
        val state = newViewModel(listOf(hugeExcludedTransfer, qrPayment)).state.value

        assertEquals(Money(298_400L, Currency.NPR), state.monthToDateSpend)
    }

    @Test
    fun incomeAndFeesNotCounted() = runTest {
        val incomeCredit = transaction(
            id = 1,
            amount = 2_492_044L,
            currency = Currency.NPR,
            direction = Direction.CREDIT,
            categoryId = incomeCategory.id,
        )
        val state = newViewModel(listOf(incomeCredit)).state.value

        assertEquals(Money(0L, Currency.NPR), state.monthToDateSpend)
    }

    @Test
    fun neutralCreditExcludedByDirection() = runTest {
        val neutralCredit = transaction(
            id = 1,
            amount = 500_000L,
            currency = Currency.NPR,
            direction = Direction.CREDIT,
            categoryId = null,
        )
        val state = newViewModel(listOf(neutralCredit)).state.value

        assertEquals(Money(0L, Currency.NPR), state.monthToDateSpend)
    }

    @Test
    fun debitAsIncomeExcludedByFlag() = runTest {
        val debitMiscategorizedAsIncome = transaction(
            id = 1,
            amount = 750_000L,
            currency = Currency.NPR,
            direction = Direction.DEBIT,
            categoryId = incomeCategory.id,
        )
        val state = newViewModel(listOf(debitMiscategorizedAsIncome)).state.value

        assertEquals(Money(0L, Currency.NPR), state.monthToDateSpend)
    }

    @Test
    fun nprAndUsdSeparated() = runTest {
        val nprSpend = transaction(id = 1, amount = 298_400L, currency = Currency.NPR)
        val usdSpend = transaction(id = 2, amount = 198L, currency = Currency.USD)
        val state = newViewModel(listOf(nprSpend, usdSpend)).state.value

        assertEquals(Currency.NPR, state.monthToDateSpend.currency)
        assertEquals(Money(298_400L, Currency.NPR), state.monthToDateSpend)
    }

    @Test
    fun uncategorizedTransactionsInBucket() = runTest {
        val uncategorized = transaction(id = 1, amount = 1500L, currency = Currency.NPR, categoryId = null)
        val state = newViewModel(listOf(uncategorized)).state.value

        val bucket = state.byCategory.single()
        assertEquals(null, bucket.categoryId)
    }

    @Test
    fun transactionsOutsideMonthExcluded() = runTest {
        val lastMonth = transaction(id = 1, amount = 1000L, currency = Currency.NPR, day = 5).copy(
            occurredAtEpochMillis = LocalDate(2026, 7, 20).atStartOfDayIn(zone).toEpochMilliseconds(),
        )
        val state = newViewModel(listOf(lastMonth)).state.value

        assertEquals(Money(0L, Currency.NPR), state.monthToDateSpend)
    }

    @Test
    fun usdExcludedFromDashboard() = runTest {
        val nprSpend = transaction(id = 1, amount = 298_400L, currency = Currency.NPR)
        val usdSpend = transaction(id = 2, amount = 198L, currency = Currency.USD)
        val state = newViewModel(listOf(nprSpend, usdSpend)).state.value

        assertEquals(Currency.NPR, state.monthToDateSpend.currency)
        assertEquals(Money(298_400L, Currency.NPR), state.monthToDateSpend)
        assertTrue(state.byCategory.all { it.currency == Currency.NPR })
        assertTrue(state.trend.all { it.currency == Currency.NPR })
    }

    @Test
    fun cashFlowIncomeFromCredits() = runTest {
        val credit = transaction(id = 1, amount = 500_000L, currency = Currency.NPR, direction = com.kharcha.parser.Direction.CREDIT)
        val debit = transaction(id = 2, amount = 100_000L, currency = Currency.NPR)
        val state = newViewModel(listOf(credit, debit)).state.value

        assertEquals(Money(500_000L, Currency.NPR), state.monthToDateIncome)
        assertEquals(Money(100_000L, Currency.NPR), state.monthToDateSpend)
    }
}
