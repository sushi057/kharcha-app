package com.kharcha.app.ui.transactions

import com.kharcha.data.CategoryDao
import com.kharcha.data.CategoryEntity
import com.kharcha.data.RuleDao
import com.kharcha.data.RuleEntity
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class FakeTransactionDao : TransactionDao {
    private var nextId = 1L
    val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())

    fun seed(vararg entities: TransactionEntity) {
        val stored = entities.map { it.copy(id = if (it.id == 0L) nextId++ else it.id) }
        flow.value = stored
    }

    override suspend fun insert(transaction: TransactionEntity): Long {
        val stored = transaction.copy(id = nextId++)
        flow.value = flow.value + stored
        return stored.id
    }

    override suspend fun update(transaction: TransactionEntity) {
        flow.value = flow.value.map { if (it.id == transaction.id) transaction else it }
    }

    override suspend fun delete(transaction: TransactionEntity) {
        flow.value = flow.value.filterNot { it.id == transaction.id }
    }

    override suspend fun getById(id: Long): TransactionEntity? = flow.value.find { it.id == id }

    override suspend fun getByRawMessageId(rawMessageId: Long): TransactionEntity? =
        flow.value.find { it.rawMessageId == rawMessageId }

    override fun observeAll(): Flow<List<TransactionEntity>> = flow

    fun get(id: Long): TransactionEntity = flow.value.first { it.id == id }
}

private class FakeCategoryDao(private val categories: List<CategoryEntity> = emptyList()) : CategoryDao {
    override suspend fun insert(category: CategoryEntity): Long = 0L
    override suspend fun update(category: CategoryEntity) = Unit
    override suspend fun delete(category: CategoryEntity) = Unit
    override suspend fun getById(id: Long): CategoryEntity? = categories.find { it.id == id }
    override fun observeAll(): Flow<List<CategoryEntity>> = MutableStateFlow(categories)
}

private class FakeRuleDao(private val rules: List<RuleEntity> = emptyList()) : RuleDao {
    val inserted = mutableListOf<RuleEntity>()
    override suspend fun insert(rule: RuleEntity): Long {
        inserted += rule
        return 0L
    }
    override suspend fun update(rule: RuleEntity) = Unit
    override suspend fun delete(rule: RuleEntity) = Unit
    override suspend fun getById(id: Long): RuleEntity? = rules.find { it.id == id }
    override fun observeAll(): Flow<List<RuleEntity>> = MutableStateFlow(rules)
}

class TransactionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val categories = listOf(
        CategoryEntity(id = 1L, name = "Food & Dining", colorArgb = 0xFFE8734A.toInt(), isIncome = false, isFee = false),
        CategoryEntity(id = 3L, name = "Shopping", colorArgb = 0xFFC968A6.toInt(), isIncome = false, isFee = false),
    )

    private fun sampleTransaction(id: Long = 1L, categoryId: Long? = 1L) = TransactionEntity(
        id = id,
        rawMessageId = 10L,
        sourceAccount = "0###15164761",
        amountMinorUnits = 298400L,
        currency = Currency.NPR,
        direction = Direction.DEBIT,
        occurredAtEpochMillis = 1_754_000_000_000L,
        remark = "QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU",
        merchant = "JAWALAKHEL HANKOOK SARANG RESTAU",
        balanceAfterMinorUnits = 500000L,
        categoryId = categoryId,
        categoryIsManualOverride = false,
        excludedFromSpending = false,
        isManualEntry = false,
    )

    /** No SMS behind anything: these tests are about filtering and categorization. */
    private val emptyRawMessageDao = object : com.kharcha.data.RawMessageDao {
        override suspend fun insertIgnoringDuplicates(message: com.kharcha.data.RawMessage): Long = -1L
        override suspend fun findNearDuplicate(sender: String, body: String, fromEpochMillis: Long, toEpochMillis: Long): com.kharcha.data.RawMessage? = null
        override suspend fun markIgnored(id: Long, reason: String) = Unit
        override fun observeIgnored(): Flow<List<com.kharcha.data.RawMessage>> = MutableStateFlow(emptyList())
        override suspend fun restore(id: Long) = Unit
        override suspend fun count(): Int = 0
        override suspend fun getAll(): List<com.kharcha.data.RawMessage> = emptyList()
        override fun observeUnparsed(): Flow<List<com.kharcha.data.RawMessage>> = MutableStateFlow(emptyList())
        override suspend fun markDismissed(id: Long) = Unit
        override fun observeDismissed(): Flow<List<com.kharcha.data.RawMessage>> = MutableStateFlow(emptyList())
        override suspend fun undismiss(id: Long) = Unit
        override suspend fun getById(id: Long): com.kharcha.data.RawMessage? = null
        override fun observeAll(): Flow<List<com.kharcha.data.RawMessage>> = MutableStateFlow(emptyList())
    }

    private fun newViewModel(
        fakeDao: FakeTransactionDao = FakeTransactionDao().apply { seed(sampleTransaction()) },
        categoryDao: FakeCategoryDao = FakeCategoryDao(categories),
        ruleDao: FakeRuleDao = FakeRuleDao(),
        openDayRequests: OpenDayRequests = OpenDayRequests(),
    ): Triple<TransactionsViewModel, FakeTransactionDao, FakeRuleDao> =
        Triple(
            TransactionsViewModel(
                transactionDao = fakeDao,
                categoryDao = categoryDao,
                rawMessageDao = emptyRawMessageDao,
                ruleDao = ruleDao,
                openDayRequests = openDayRequests,
                zone = kotlinx.datetime.TimeZone.UTC,
                reparseService = com.kharcha.data.ReparseService(
                    rawMessageDao = emptyRawMessageDao,
                    transactionDao = fakeDao,
                    ruleset = com.kharcha.parser.SblAlertRuleset,
                    categorizer = com.kharcha.data.Categorizer(emptyList()),
                ),
                ioDispatcher = testDispatcher,
            ),
            fakeDao,
            ruleDao,
        )

    @Test
    fun `setting a category marks it as a manual override`() = runTest {
        val (vm, fakeDao, _) = newViewModel()
        vm.setCategory(txnId = 1L, categoryId = 3L)
        val txn = fakeDao.get(1L)
        assertEquals(3L, txn.categoryId)
        assertTrue(txn.categoryIsManualOverride)
    }

    @Test
    fun `excluded transactions still appear in the list`() = runTest {
        val (vm, _, _) = newViewModel()
        vm.setExcludedFromSpending(1L, true)
        val state = vm.state.value
        assertTrue(state.transactions.any { it.id == 1L && it.excludedFromSpending })
    }

    @Test
    fun `accepting the always-categorize prompt inserts a prefix rule above seed priority`() = runTest {
        val (vm, _, ruleDao) = newViewModel()
        vm.setCategory(txnId = 1L, categoryId = 3L)
        vm.confirmAlwaysCategorize(merchant = "JAWALAKHEL HANKOOK SARANG RESTAU", categoryId = 3L)

        val inserted = ruleDao.inserted.single()
        assertEquals("JAWALAKHEL HANKOOK SARANG RESTAU", inserted.matchPattern)
        assertTrue(inserted.matchesPrefix)
        assertEquals(3L, inserted.categoryId)
        assertTrue(inserted.priority > 100)
    }

    @Test
    fun `adding a manual transaction sets isManualEntry and no raw message`() = runTest {
        val (vm, fakeDao, _) = newViewModel(fakeDao = FakeTransactionDao())
        vm.addManualTransaction(
            amountMinorUnits = 15000L,
            currency = Currency.NPR,
            direction = Direction.DEBIT,
            occurredAtEpochMillis = 1_754_000_000_000L,
            remark = "Manual entry",
            merchant = "Corner Store",
            categoryId = 1L,
        )
        val txn = fakeDao.flow.value.single()
        assertTrue(txn.isManualEntry)
        assertEquals(null, txn.rawMessageId)
        assertNotNull(txn.categoryId)
    }

    @Test
    fun `deleting a transaction removes it from state`() = runTest {
        val (vm, _, _) = newViewModel()
        vm.deleteTransaction(1L)
        assertTrue(vm.state.value.transactions.none { it.id == 1L })
    }

    @Test
    fun `a day tapped on the dashboard filters the ledger to that whole day`() = runTest {
        val bus = OpenDayRequests()
        val (vm, _, _) = newViewModel(openDayRequests = bus)

        // The sample transaction's own day, in the ViewModel's UTC zone.
        bus.request(kotlinx.datetime.LocalDate(2025, 8, 1))

        val state = vm.state.value
        // 2025-08-01T00:00:00Z .. 2025-08-01T23:59:59.999Z
        assertEquals(1_754_006_400_000L, state.dateRangeStartEpochMillis)
        assertEquals(1_754_092_799_999L, state.dateRangeEndEpochMillis)
        assertEquals(null, bus.requests.value, "the request must be consumed once applied")
    }

    @Test
    fun `sort and excluded-only survive a change to the search query`() = runTest {
        val (vm, _, _) = newViewModel()
        vm.setSort(TransactionSort.Highest)
        vm.setExcludedOnly(true)

        vm.setSearchQuery("hankook")

        val state = vm.state.value
        assertEquals(TransactionSort.Highest, state.sort)
        assertTrue(state.excludedOnly)
        assertEquals("hankook", state.searchQuery)
    }

    @Test
    fun `clearing filters keeps the search query the user is still typing`() = runTest {
        val (vm, _, _) = newViewModel()
        vm.setSearchQuery("hankook")
        vm.setSort(TransactionSort.Lowest)
        vm.setCategoryFilter(3L)
        vm.setExcludedOnly(true)
        vm.setDateRangeFilter(1L, 2L)

        vm.clearFilters()

        val state = vm.state.value
        assertEquals("hankook", state.searchQuery)
        assertTrue(!state.hasActiveFilters)
    }
}
