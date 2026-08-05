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

    private fun newViewModel(
        fakeDao: FakeTransactionDao = FakeTransactionDao().apply { seed(sampleTransaction()) },
        categoryDao: FakeCategoryDao = FakeCategoryDao(categories),
        ruleDao: FakeRuleDao = FakeRuleDao(),
    ): Triple<TransactionsViewModel, FakeTransactionDao, FakeRuleDao> =
        Triple(TransactionsViewModel(fakeDao, categoryDao, ruleDao), fakeDao, ruleDao)

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
}
