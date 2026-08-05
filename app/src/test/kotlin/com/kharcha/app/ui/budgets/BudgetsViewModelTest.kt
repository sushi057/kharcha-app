package com.kharcha.app.ui.budgets

import com.kharcha.data.BudgetDao
import com.kharcha.data.BudgetEntity
import com.kharcha.data.CategoryDao
import com.kharcha.data.CategoryEntity
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeBudgetDao(seed: List<BudgetEntity> = emptyList()) : BudgetDao {
    val flow = MutableStateFlow(seed)
    private var nextId = seed.maxOfOrNull { it.id }?.plus(1) ?: 1L

    override suspend fun insert(budget: BudgetEntity): Long {
        val stored = budget.copy(id = nextId++)
        flow.value = flow.value + stored
        return stored.id
    }

    override suspend fun update(budget: BudgetEntity) {
        flow.value = flow.value.map { if (it.id == budget.id) budget else it }
    }

    override suspend fun delete(budget: BudgetEntity) {
        flow.value = flow.value.filterNot { it.id == budget.id }
    }

    override suspend fun getById(id: Long): BudgetEntity? = flow.value.find { it.id == id }
    override fun observeAll(): Flow<List<BudgetEntity>> = flow
}

private class FakeCategoryDao(private val categories: List<CategoryEntity>) : CategoryDao {
    override suspend fun insert(category: CategoryEntity): Long = 0L
    override suspend fun update(category: CategoryEntity) = Unit
    override suspend fun delete(category: CategoryEntity) = Unit
    override suspend fun getById(id: Long): CategoryEntity? = categories.find { it.id == id }
    override fun observeAll(): Flow<List<CategoryEntity>> = MutableStateFlow(categories)
}

private class FakeTransactionDao(seed: List<TransactionEntity> = emptyList()) : TransactionDao {
    private val flow = MutableStateFlow(seed)
    override suspend fun insert(transaction: TransactionEntity): Long = 0L
    override suspend fun update(transaction: TransactionEntity) = Unit
    override suspend fun delete(transaction: TransactionEntity) = Unit
    override suspend fun getById(id: Long): TransactionEntity? = flow.value.find { it.id == id }
    override suspend fun getByRawMessageId(rawMessageId: Long): TransactionEntity? = null
    override fun observeAll(): Flow<List<TransactionEntity>> = flow
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

class BudgetsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val zone = TimeZone.UTC
    private val fixedNow = LocalDate(2026, 8, 5).atStartOfDayIn(zone)

    private val foodCategory = CategoryEntity(id = 1L, name = "Food & Dining", colorArgb = 0xFFE8734A.toInt(), isIncome = false, isFee = false)
    private val incomeCategory = CategoryEntity(id = 2L, name = "Income", colorArgb = 0xFF00FF00.toInt(), isIncome = true, isFee = false)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun txn(amount: Long, categoryId: Long?, day: Int = 5, currency: Currency = Currency.NPR) = TransactionEntity(
        id = amount + currency.ordinal * 1_000_000_000L,
        rawMessageId = null,
        sourceAccount = "acct",
        amountMinorUnits = amount,
        currency = currency,
        direction = Direction.DEBIT,
        occurredAtEpochMillis = LocalDate(2026, 8, day).atStartOfDayIn(zone).toEpochMilliseconds(),
        remark = "remark",
        merchant = "merchant",
        balanceAfterMinorUnits = null,
        categoryId = categoryId,
        categoryIsManualOverride = false,
        excludedFromSpending = false,
        isManualEntry = false,
    )

    @Test
    fun `income and fee categories are excluded from budget rows`() = runTest {
        val viewModel = BudgetsViewModel(
            budgetDao = FakeBudgetDao(),
            categoryDao = FakeCategoryDao(listOf(foodCategory, incomeCategory)),
            transactionDao = FakeTransactionDao(),
            clock = FixedClock(fixedNow),
            zone = zone,
        )
        assertEquals(listOf(foodCategory.id), viewModel.state.value.rows.map { it.categoryId })
    }

    @Test
    fun `a category with a budget reports month-to-date spend and over-budget flag`() = runTest {
        val budget = BudgetEntity(id = 1L, categoryId = foodCategory.id, monthlyLimitMinorUnits = 1000_00L, currency = Currency.NPR, alertThresholdPercent = 80)
        val viewModel = BudgetsViewModel(
            budgetDao = FakeBudgetDao(listOf(budget)),
            categoryDao = FakeCategoryDao(listOf(foodCategory)),
            transactionDao = FakeTransactionDao(listOf(txn(600_00L, foodCategory.id), txn(500_00L, foodCategory.id))),
            clock = FixedClock(fixedNow),
            zone = zone,
        )
        val row = viewModel.state.value.rows.single()
        assertEquals(1100_00L, row.spentMinorUnits)
        assertEquals(1000_00L, row.limitMinorUnits)
        assertTrue(row.isOverBudget)
    }

    @Test
    fun `a category with no budget set has a null limit and is not over budget`() = runTest {
        val viewModel = BudgetsViewModel(
            budgetDao = FakeBudgetDao(),
            categoryDao = FakeCategoryDao(listOf(foodCategory)),
            transactionDao = FakeTransactionDao(listOf(txn(600_00L, foodCategory.id))),
            clock = FixedClock(fixedNow),
            zone = zone,
        )
        val row = viewModel.state.value.rows.single()
        assertNull(row.limitMinorUnits)
        assertEquals(600_00L, row.spentMinorUnits)
        assertFalse(row.isOverBudget)
    }

    @Test
    fun `a category with USD-only spend and no budget surfaces a USD row, not a hidden zero-NPR row`() = runTest {
        // Regression for the reviewer finding: `budget?.currency ?: Currency.NPR` picked
        // NPR whenever there was no budget, so a category whose only spend is USD would
        // look up spendByCategoryAndCurrency[categoryId to NPR], miss, and silently render
        // "No budget set" / 0 spent while real USD spend existed. This must fail under
        // that old behaviour: it would assert a single NPR row with 0 spent instead.
        val viewModel = BudgetsViewModel(
            budgetDao = FakeBudgetDao(),
            categoryDao = FakeCategoryDao(listOf(foodCategory)),
            transactionDao = FakeTransactionDao(listOf(txn(50_00L, foodCategory.id, currency = Currency.USD))),
            clock = FixedClock(fixedNow),
            zone = zone,
        )
        val row = viewModel.state.value.rows.single()
        assertEquals(Currency.USD, row.currency)
        assertEquals(50_00L, row.spentMinorUnits)
        assertNull(row.limitMinorUnits)
    }

    @Test
    fun `a category with an NPR budget that also has USD spend surfaces both currencies independently`() = runTest {
        // Old behaviour picked the budget's currency (NPR) unconditionally and never
        // looked at USD at all, so the USD spend never appeared anywhere on the screen.
        val budget = BudgetEntity(id = 1L, categoryId = foodCategory.id, monthlyLimitMinorUnits = 1000_00L, currency = Currency.NPR, alertThresholdPercent = 80)
        val viewModel = BudgetsViewModel(
            budgetDao = FakeBudgetDao(listOf(budget)),
            categoryDao = FakeCategoryDao(listOf(foodCategory)),
            transactionDao = FakeTransactionDao(
                listOf(
                    txn(300_00L, foodCategory.id, currency = Currency.NPR),
                    txn(75_00L, foodCategory.id, currency = Currency.USD),
                )
            ),
            clock = FixedClock(fixedNow),
            zone = zone,
        )
        val rows = viewModel.state.value.rows.associateBy { it.currency }
        assertEquals(2, rows.size)

        val nprRow = rows.getValue(Currency.NPR)
        assertEquals(300_00L, nprRow.spentMinorUnits)
        assertEquals(1000_00L, nprRow.limitMinorUnits)

        // The NPR budget must never apply to the USD row — no limit, no cross-currency
        // comparison (invariant: currencies are never summed or converted).
        val usdRow = rows.getValue(Currency.USD)
        assertEquals(75_00L, usdRow.spentMinorUnits)
        assertNull(usdRow.limitMinorUnits)
    }

    @Test
    fun `a category with a budget but zero spend in that currency still shows as zero, not hidden`() = runTest {
        val budget = BudgetEntity(id = 1L, categoryId = foodCategory.id, monthlyLimitMinorUnits = 1000_00L, currency = Currency.USD, alertThresholdPercent = 80)
        val viewModel = BudgetsViewModel(
            budgetDao = FakeBudgetDao(listOf(budget)),
            categoryDao = FakeCategoryDao(listOf(foodCategory)),
            transactionDao = FakeTransactionDao(),
            clock = FixedClock(fixedNow),
            zone = zone,
        )
        val row = viewModel.state.value.rows.single()
        assertEquals(Currency.USD, row.currency)
        assertEquals(0L, row.spentMinorUnits)
        assertEquals(1000_00L, row.limitMinorUnits)
        assertFalse(row.isOverBudget)
    }

    @Test
    fun `setBudget inserts a new budget when the category has none`() = runTest {
        val budgetDao = FakeBudgetDao()
        val viewModel = BudgetsViewModel(
            budgetDao = budgetDao,
            categoryDao = FakeCategoryDao(listOf(foodCategory)),
            transactionDao = FakeTransactionDao(),
            clock = FixedClock(fixedNow),
            zone = zone,
        )
        viewModel.setBudget(foodCategory.id, 500_00L, Currency.NPR, 75)
        val stored = budgetDao.flow.value.single()
        assertEquals(foodCategory.id, stored.categoryId)
        assertEquals(500_00L, stored.monthlyLimitMinorUnits)
        assertEquals(75, stored.alertThresholdPercent)
    }

    @Test
    fun `setBudget updates the existing budget for the category instead of duplicating it`() = runTest {
        val existing = BudgetEntity(id = 1L, categoryId = foodCategory.id, monthlyLimitMinorUnits = 500_00L, currency = Currency.NPR, alertThresholdPercent = 80)
        val budgetDao = FakeBudgetDao(listOf(existing))
        val viewModel = BudgetsViewModel(
            budgetDao = budgetDao,
            categoryDao = FakeCategoryDao(listOf(foodCategory)),
            transactionDao = FakeTransactionDao(),
            clock = FixedClock(fixedNow),
            zone = zone,
        )
        viewModel.setBudget(foodCategory.id, 900_00L, Currency.NPR, 90)
        assertEquals(1, budgetDao.flow.value.size)
        val stored = budgetDao.flow.value.single()
        assertEquals(1L, stored.id)
        assertEquals(900_00L, stored.monthlyLimitMinorUnits)
        assertEquals(90, stored.alertThresholdPercent)
    }
}
