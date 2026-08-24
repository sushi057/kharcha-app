package com.kharcha.app.ui.unparsed

import com.kharcha.app.ui.onboarding.BackfillGate
import com.kharcha.data.CategoryDao
import com.kharcha.data.CategoryEntity
import com.kharcha.data.RawMessage
import com.kharcha.data.RawMessageDao
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [blockRescan] lets a test hold the re-scan open so the "syncing" state is observable
 * mid-flight, rather than only after the whole sync has already collapsed back to idle.
 */
private class FakeBackfillGate : BackfillGate {
    var rescanCount = 0
    private var gate: CompletableDeferred<Unit>? = null

    fun blockRescan() {
        gate = CompletableDeferred()
    }

    fun finishRescan() {
        gate?.complete(Unit)
    }

    override suspend fun isComplete(): Boolean = true
    override fun enqueueOnce() = Unit

    override suspend fun rescan() {
        rescanCount++
        gate?.await()
    }
}

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

    override suspend fun findNearDuplicate(sender: String, body: String, fromEpochMillis: Long, toEpochMillis: Long): RawMessage? =
        messages.value.firstOrNull { msg ->
            msg.sender == sender &&
                msg.body == body &&
                msg.receivedAtEpochMillis in fromEpochMillis..toEpochMillis
        }

    override suspend fun markIgnored(id: Long, reason: String) {
        messages.value = messages.value.map { if (it.id == id) it.copy(ignored = true, ignoreReason = reason) else it }
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

    override fun observeIgnored(): Flow<List<RawMessage>> =
        messages.map { it.filter { msg -> msg.ignored }.sortedByDescending { msg -> msg.id } }

    override suspend fun restore(id: Long) {
        messages.value = messages.value.map { if (it.id == id) it.copy(ignored = false, ignoreReason = null) else it }
    }

    override fun observeDismissed(): Flow<List<RawMessage>> =
        kotlinx.coroutines.flow.combine(messages, transactions) { msgs, txns ->
            val linkedRawIds = txns.mapNotNull { it.rawMessageId }.toSet()
            msgs.filter { it.dismissed && it.id !in linkedRawIds }.sortedByDescending { it.id }
        }

    override suspend fun undismiss(id: Long) {
        messages.value = messages.value.map { if (it.id == id) it.copy(dismissed = false) else it }
    }

    override suspend fun getById(id: Long): RawMessage? = messages.value.firstOrNull { it.id == id }

    override fun observeAll(): Flow<List<RawMessage>> = messages
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
        ignoreReason = "OTP",
    )

    private fun rawUnknown(id: Long = 0L) = RawMessage(
        id = id,
        sender = "SBL_Alert",
        body = "Your statement is ready.",
        receivedAtEpochMillis = 1_754_000_001_000L,
        contentHash = "unknown-$id",
        ignored = false,
        ignoreReason = null,
    )

    private fun newViewModel(
        rawDao: FakeRawMessageDao = FakeRawMessageDao().apply { seed(rawOtp(), rawUnknown()) },
        transactionDao: FakeTransactionDao = FakeTransactionDao(),
        categoryDao: FakeCategoryDao = FakeCategoryDao(),
        backfillGate: BackfillGate = FakeBackfillGate(),
    ): Triple<UnparsedViewModel, FakeRawMessageDao, FakeTransactionDao> =
        Triple(
            UnparsedViewModel(rawDao, transactionDao, categoryDao, backfillGate),
            rawDao,
            transactionDao,
        )

    @Test
    fun `sync re-scans the SMS inbox instead of only stamping a sync time`() = runTest {
        val gate = FakeBackfillGate()
        val (vm, _, _) = newViewModel(backfillGate = gate)

        vm.sync()

        assertEquals(1, gate.rescanCount)
        assertNotNull(vm.state.value.lastSyncAtEpochMillis)
        assertFalse(vm.state.value.isSyncing)
    }

    @Test
    fun `sync reports itself as running until the re-scan finishes`() = runTest {
        val gate = FakeBackfillGate()
        val (vm, _, _) = newViewModel(backfillGate = gate)

        gate.blockRescan()
        vm.sync()
        assertTrue(vm.state.value.isSyncing)
        assertNull(vm.state.value.lastSyncAtEpochMillis)

        gate.finishRescan()
        assertFalse(vm.state.value.isSyncing)
        assertNotNull(vm.state.value.lastSyncAtEpochMillis)
    }

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

        val vm = UnparsedViewModel(rawDao, txnDao, FakeCategoryDao(), FakeBackfillGate())
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

    @Test
    fun `messages partition into needsReview and ignored sections`() = runTest {
        val (vm, _, _) = newViewModel()
        val state = vm.state.value
        assertEquals(1, state.needsReview.size)
        assertEquals(1, state.ignored.size)
        assertEquals("Your statement is ready.", state.needsReview.single().body)
        assertEquals("Your OTP is 123456", state.ignored.single().body)
    }

    @Test
    fun `a dismissed message leaves review but stays visible under dismissed`() = runTest {
        val (vm, rawDao, _) = newViewModel()
        val reviewId = rawDao.messages.value.single { !it.ignored }.id

        vm.dismiss(reviewId)

        val state = vm.state.value
        assertTrue(state.needsReview.isEmpty(), "dismissing must take it out of review")
        assertEquals(listOf(reviewId), state.dismissed.map { it.id })
    }

    @Test
    fun `undismissing puts a message back into review`() = runTest {
        val (vm, rawDao, _) = newViewModel()
        val reviewId = rawDao.messages.value.single { !it.ignored }.id
        vm.dismiss(reviewId)

        vm.undismiss(reviewId)

        val state = vm.state.value
        assertEquals(listOf(reviewId), state.needsReview.map { it.id })
        assertTrue(state.dismissed.isEmpty())
    }

    @Test
    fun `adding a dismissed message as a transaction drops it from the dismissed list`() = runTest {
        val (vm, rawDao, txnDao) = newViewModel()
        val reviewId = rawDao.messages.value.single { !it.ignored }.id
        vm.dismiss(reviewId)

        vm.createManualTransactionFrom(
            rawId = reviewId,
            amountMinorUnits = 50000L,
            merchant = "Bank",
            remark = "Your statement is ready.",
            categoryId = null,
        )
        rawDao.transactions.value = txnDao.flow.value

        assertTrue(vm.state.value.dismissed.isEmpty())
    }

    @Test
    fun `restoring an ignored message moves it to needs review`() = runTest {
        val (vm, rawDao, _) = newViewModel()
        val ignoredId = rawDao.messages.value.single { it.ignored }.id

        vm.restore(ignoredId)

        val state = vm.state.value
        assertEquals(2, state.needsReview.size)
        assertTrue(state.ignored.isEmpty())
    }

    @Test
    fun `ignored messages preserve their ignore reason`() = runTest {
        val (vm, _, _) = newViewModel()
        val state = vm.state.value
        assertEquals("OTP", state.ignored.single().ignoreReason)
    }
}
