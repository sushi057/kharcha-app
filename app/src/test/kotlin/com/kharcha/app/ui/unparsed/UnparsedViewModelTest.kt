package com.kharcha.app.ui.unparsed

import com.kharcha.data.CategoryDao
import com.kharcha.data.CategoryEntity
import com.kharcha.data.RawMessage
import com.kharcha.data.RawMessageDao
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
import kotlin.test.assertTrue

private class FakeRawMessageDao : RawMessageDao {
    private var nextId = 1L
    val messages = MutableStateFlow<List<RawMessage>>(emptyList())
    val transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())

    fun seed(vararg raw: RawMessage) {
        messages.value = raw.map { it.copy(id = if (it.id == 0L) nextId++ else it.id) }
    }

    override suspend fun insertIgnoringDuplicates(message: RawMessage): Long {
        val stored = message.copy(id = nextId++)
        messages.value = messages.value + stored
        return stored.id
    }

    override suspend fun markIgnored(id: Long) {
        messages.value = messages.value.map { if (it.id == id) it.copy(ignored = true) else it }
    }

    override suspend fun count(): Int = messages.value.size

    override suspend fun getAll(): List<RawMessage> = messages.value

    override fun observeUnparsed(): Flow<List<RawMessage>> {
        // Mirror the real query's join semantics in-memory for the fake.
        return kotlinx.coroutines.flow.combine(messages, transactions) { msgs, txns ->
            val linkedRawIds = txns.mapNotNull { it.rawMessageId }.toSet()
            msgs.filter { !it.ignored && !it.dismissed && it.id !in linkedRawIds }
        }
    }

    override suspend fun markDismissed(id: Long) {
        messages.value = messages.value.map { if (it.id == id) it.copy(dismissed = true) else it }
    }
}

private class FakeTransactionDao : TransactionDao {
    private var nextId = 1L
    val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())

    override suspend fun insert(transaction: TransactionEntity): Long {
        val stored = transaction.copy(id = nextId++)
        flow.value = flow.value + stored
        return stored.id
    }

    override suspend fun update(transaction: TransactionEntity) = Unit
    override suspend fun delete(transaction: TransactionEntity) = Unit
    override suspend fun getById(id: Long): TransactionEntity? = flow.value.find { it.id == id }
    override suspend fun getByRawMessageId(rawMessageId: Long): TransactionEntity? =
        flow.value.find { it.rawMessageId == rawMessageId }

    override fun observeAll(): Flow<List<TransactionEntity>> = flow
}

private class FakeCategoryDao(private val categories: List<CategoryEntity> = emptyList()) : CategoryDao {
    override suspend fun insert(category: CategoryEntity): Long = 0L
    override suspend fun update(category: CategoryEntity) = Unit
    override suspend fun delete(category: CategoryEntity) = Unit
    override suspend fun getById(id: Long): CategoryEntity? = categories.find { it.id == id }
    override fun observeAll(): Flow<List<CategoryEntity>> = MutableStateFlow(categories)
}

class UnparsedViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun rawOtp(id: Long = 0L) = RawMessage(
        id = id,
        sender = "SBL_Alert",
        body = "Your OTP is 123456",
        receivedAtEpochMillis = 1_754_000_000_000L,
        contentHash = "otp-$id",
        ignored = true,
    )

    private fun rawUnknown(id: Long = 0L) = RawMessage(
        id = id,
        sender = "SBL_Alert",
        body = "Your statement is ready.",
        receivedAtEpochMillis = 1_754_000_001_000L,
        contentHash = "unknown-$id",
        ignored = false,
    )

    private fun newViewModel(
        rawDao: FakeRawMessageDao = FakeRawMessageDao().apply { seed(rawOtp(), rawUnknown()) },
        transactionDao: FakeTransactionDao = FakeTransactionDao(),
        categoryDao: FakeCategoryDao = FakeCategoryDao(),
    ): Triple<UnparsedViewModel, FakeRawMessageDao, FakeTransactionDao> =
        Triple(UnparsedViewModel(rawDao, transactionDao, categoryDao), rawDao, transactionDao)

    @Test
    fun `ignored messages never appear in the unparsed inbox`() = runTest {
        val (vm, _, _) = newViewModel()
        val state = vm.state.value
        assertEquals(1, state.messages.size)
        assertEquals("Your statement is ready.", state.messages.single().body)
    }

    @Test
    fun `dismissing removes a message from the inbox`() = runTest {
        val (vm, _, _) = newViewModel()
        val state = vm.state.value
        vm.dismiss(state.messages.single().id)
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `a message with a linked transaction never appears in the unparsed inbox`() = runTest {
        val rawDao = FakeRawMessageDao().apply { seed(rawUnknown()) }
        val txnDao = FakeTransactionDao()
        val rawId = rawDao.messages.value.single().id
        txnDao.insert(
            TransactionEntity(
                rawMessageId = rawId,
                sourceAccount = "acc",
                amountMinorUnits = 100L,
                currency = Currency.NPR,
                direction = Direction.DEBIT,
                occurredAtEpochMillis = 1L,
                remark = "r",
                merchant = null,
                balanceAfterMinorUnits = null,
                categoryId = null,
                categoryIsManualOverride = false,
                excludedFromSpending = false,
                isManualEntry = true,
            )
        )
        rawDao.transactions.value = txnDao.flow.value

        val vm = UnparsedViewModel(rawDao, txnDao, FakeCategoryDao())
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `createManualTransactionFrom links the transaction to the raw message`() = runTest {
        val (vm, rawDao, txnDao) = newViewModel()
        val rawId = rawDao.messages.value.single { !it.ignored }.id

        vm.createManualTransactionFrom(
            rawId = rawId,
            amountMinorUnits = 50000L,
            merchant = "Bank",
            remark = "Your statement is ready.",
            categoryId = null,
        )

        val txn = txnDao.flow.value.single()
        assertEquals(rawId, txn.rawMessageId)
        assertTrue(txn.isManualEntry)
        assertEquals(50000L, txn.amountMinorUnits)
    }
}
