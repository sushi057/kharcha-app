package com.kharcha.app.notify

import com.kharcha.data.BudgetAlertStateEntity
import com.kharcha.data.BudgetEntity
import com.kharcha.data.CategoryEntity
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeBudgetDao(budgets: List<BudgetEntity>) : com.kharcha.data.BudgetDao {
    private val flow = MutableStateFlow(budgets)
    override suspend fun insert(budget: BudgetEntity): Long = 0L
    override suspend fun update(budget: BudgetEntity) = Unit
    override suspend fun delete(budget: BudgetEntity) = Unit
    override suspend fun getById(id: Long): BudgetEntity? = flow.value.find { it.id == id }
    override fun observeAll(): Flow<List<BudgetEntity>> = flow
}

private class FakeCategoryDao(private val categories: List<CategoryEntity>) : com.kharcha.data.CategoryDao {
    override suspend fun insert(category: CategoryEntity): Long = 0L
    override suspend fun update(category: CategoryEntity) = Unit
    override suspend fun delete(category: CategoryEntity) = Unit
    override suspend fun getById(id: Long): CategoryEntity? = categories.find { it.id == id }
    override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(categories)
}

private class FakeTransactionDao : com.kharcha.data.TransactionDao {
    private val transactions = mutableListOf<TransactionEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())

    fun add(amountMinorUnits: Long, currency: Currency, categoryId: Long, occurredAtEpochMillis: Long) {
        transactions += TransactionEntity(
            id = nextId++,
            rawMessageId = null,
            sourceAccount = "acct",
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            direction = Direction.DEBIT,
            occurredAtEpochMillis = occurredAtEpochMillis,
            remark = "remark",
            merchant = "merchant",
            balanceAfterMinorUnits = null,
            categoryId = categoryId,
            categoryIsManualOverride = false,
            excludedFromSpending = false,
            isManualEntry = false,
        )
        flow.value = transactions.toList()
    }

    override suspend fun insert(transaction: TransactionEntity): Long = 0L
    override suspend fun update(transaction: TransactionEntity) = Unit
    override suspend fun delete(transaction: TransactionEntity) = Unit
    override suspend fun getById(id: Long): TransactionEntity? = null
    override suspend fun getByRawMessageId(rawMessageId: Long): TransactionEntity? = null
    override fun observeAll(): Flow<List<TransactionEntity>> = flow
}

private class FakeBudgetAlertStateDao : com.kharcha.data.BudgetAlertStateDao {
    val states = mutableListOf<BudgetAlertStateEntity>()
    private var nextId = 1L

    override suspend fun get(categoryId: Long, currency: Currency, yearMonth: String): BudgetAlertStateEntity? =
        states.find { it.categoryId == categoryId && it.currency == currency && it.yearMonth == yearMonth }

    override suspend fun upsert(state: BudgetAlertStateEntity): Long {
        val index = states.indexOfFirst {
            it.categoryId == state.categoryId && it.currency == state.currency && it.yearMonth == state.yearMonth
        }
        return if (index >= 0) {
            states[index] = state.copy(id = states[index].id)
            states[index].id
        } else {
            val stored = state.copy(id = nextId++)
            states += stored
            stored.id
        }
    }
}

private object NoopPoster : NotificationPoster {
    override fun post(categoryName: String, alert: BudgetAlert, spentMinorUnits: Long, limitMinorUnits: Long, currency: Currency) = Unit
}

class BudgetNotifierTest {

    private val categoryId = 1L
    private val zone = TimeZone.UTC
    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-08-05T12:00:00Z")
    }

    private val category = CategoryEntity(id = categoryId, name = "Food & Dining", colorArgb = 0xFFE8734A.toInt(), isIncome = false, isFee = false)

    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var alertStateDao: FakeBudgetAlertStateDao

    private fun newNotifier(limit: Long, thresholdPercent: Int, currency: Currency = Currency.NPR): BudgetNotifier {
        transactionDao = FakeTransactionDao()
        alertStateDao = FakeBudgetAlertStateDao()
        val budgetDao = FakeBudgetDao(
            listOf(BudgetEntity(id = 1L, categoryId = categoryId, monthlyLimitMinorUnits = limit, currency = currency, alertThresholdPercent = thresholdPercent))
        )
        val categoryDao = FakeCategoryDao(listOf(category))
        return BudgetNotifier(
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            transactionDao = transactionDao,
            alertStateDao = alertStateDao,
            clock = fixedClock,
            zone = zone,
            poster = NoopPoster,
        )
    }

    private fun spend(amountMinorUnits: Long, currency: Currency = Currency.NPR) {
        transactionDao.add(amountMinorUnits, currency, categoryId, fixedClock.now().toEpochMilliseconds())
    }

    @Test
    fun `crossing the threshold fires once, not on every subsequent transaction`() = runTest {
        val notifier = newNotifier(limit = 1000_00L, thresholdPercent = 80)
        spend(850_00L)
        assertEquals(BudgetAlert.THRESHOLD_CROSSED, notifier.checkAndNotify(categoryId, Currency.NPR))
        spend(20_00L)
        assertNull(notifier.checkAndNotify(categoryId, Currency.NPR))
    }

    @Test
    fun `exceeding the limit fires a separate alert`() = runTest {
        val notifier = newNotifier(limit = 1000_00L, thresholdPercent = 80)
        spend(1_100_00L)
        assertEquals(BudgetAlert.EXCEEDED, notifier.checkAndNotify(categoryId, Currency.NPR))
    }

    @Test
    fun `exceeding after already crossing the threshold still fires EXCEEDED once`() = runTest {
        val notifier = newNotifier(limit = 1000_00L, thresholdPercent = 80)
        spend(850_00L)
        assertEquals(BudgetAlert.THRESHOLD_CROSSED, notifier.checkAndNotify(categoryId, Currency.NPR))
        spend(300_00L)
        assertEquals(BudgetAlert.EXCEEDED, notifier.checkAndNotify(categoryId, Currency.NPR))
        assertNull(notifier.checkAndNotify(categoryId, Currency.NPR))
    }

    @Test
    fun `a budget with no spend this month is at zero, not an error`() = runTest {
        val notifier = newNotifier(limit = 1000_00L, thresholdPercent = 80)
        assertNull(notifier.checkAndNotify(categoryId, Currency.NPR))
    }

    /**
     * Reviewer's Important 3. Task 11's fix round 2 made a second budget row per
     * `(categoryId, currency)` legal, but [BudgetNotifier] still selected a budget with
     * `firstOrNull { it.categoryId == categoryId }` — whichever row the DAO happened to
     * return first — then filtered spend and keyed alert state by *that* row's currency.
     * With an NPR 20,000 and a USD 100 budget on Shopping, a USD 95 transaction picked the
     * NPR budget, compared NPR spend (0) to the NPR limit, and never alerted. The USD
     * budget could not fire in any month.
     */
    @Test
    fun `a category with two currencies evaluates the triggering transaction's own budget`() = runTest {
        transactionDao = FakeTransactionDao()
        alertStateDao = FakeBudgetAlertStateDao()
        val budgetDao = FakeBudgetDao(
            listOf(
                BudgetEntity(id = 1L, categoryId = categoryId, monthlyLimitMinorUnits = 20_000_00L, currency = Currency.NPR, alertThresholdPercent = 80),
                BudgetEntity(id = 2L, categoryId = categoryId, monthlyLimitMinorUnits = 100_00L, currency = Currency.USD, alertThresholdPercent = 80),
            )
        )
        val notifier = BudgetNotifier(
            budgetDao = budgetDao,
            categoryDao = FakeCategoryDao(listOf(category)),
            transactionDao = transactionDao,
            alertStateDao = alertStateDao,
            clock = fixedClock,
            zone = zone,
            poster = NoopPoster,
        )

        spend(95_00L, Currency.USD)

        assertEquals(
            BudgetAlert.THRESHOLD_CROSSED,
            notifier.checkAndNotify(categoryId, Currency.USD),
            "USD 95 against a USD 100 budget must cross the 80% threshold",
        )
        // The NPR budget, untouched by that USD spend, must stay silent.
        assertNull(notifier.checkAndNotify(categoryId, Currency.NPR))
    }

    @Test
    fun `alert state is keyed by the triggering currency, so each currency fires independently`() = runTest {
        transactionDao = FakeTransactionDao()
        alertStateDao = FakeBudgetAlertStateDao()
        val budgetDao = FakeBudgetDao(
            listOf(
                BudgetEntity(id = 1L, categoryId = categoryId, monthlyLimitMinorUnits = 1000_00L, currency = Currency.NPR, alertThresholdPercent = 80),
                BudgetEntity(id = 2L, categoryId = categoryId, monthlyLimitMinorUnits = 100_00L, currency = Currency.USD, alertThresholdPercent = 80),
            )
        )
        val notifier = BudgetNotifier(
            budgetDao, FakeCategoryDao(listOf(category)), transactionDao, alertStateDao, fixedClock, zone, NoopPoster,
        )

        spend(900_00L, Currency.NPR)
        assertEquals(BudgetAlert.THRESHOLD_CROSSED, notifier.checkAndNotify(categoryId, Currency.NPR))

        spend(95_00L, Currency.USD)
        assertEquals(BudgetAlert.THRESHOLD_CROSSED, notifier.checkAndNotify(categoryId, Currency.USD))
    }

    @Test
    fun `the fire-once rule is enforced by persisted state, not in-memory only`() = runTest {
        // Same scenario as the threshold test, but a second, independent BudgetNotifier
        // instance is constructed against the *same* alertStateDao — simulating process
        // death between the two calls. If persistence were removed (state kept only in
        // the BudgetNotifier instance), this would incorrectly fire twice.
        val limit = 1000_00L
        val thresholdPercent = 80
        transactionDao = FakeTransactionDao()
        alertStateDao = FakeBudgetAlertStateDao()
        val budgetDao = FakeBudgetDao(
            listOf(BudgetEntity(id = 1L, categoryId = categoryId, monthlyLimitMinorUnits = limit, currency = Currency.NPR, alertThresholdPercent = thresholdPercent))
        )
        val categoryDao = FakeCategoryDao(listOf(category))

        val firstNotifier = BudgetNotifier(budgetDao, categoryDao, transactionDao, alertStateDao, fixedClock, zone, NoopPoster)
        spend(850_00L)
        assertEquals(BudgetAlert.THRESHOLD_CROSSED, firstNotifier.checkAndNotify(categoryId, Currency.NPR))

        val secondNotifier = BudgetNotifier(budgetDao, categoryDao, transactionDao, alertStateDao, fixedClock, zone, NoopPoster)
        assertNull(secondNotifier.checkAndNotify(categoryId, Currency.NPR))
    }
}
