package com.kharcha.app.ui.transactions

import com.kharcha.data.CategoryDao
import com.kharcha.data.CategoryEntity
import com.kharcha.data.RawMessage
import com.kharcha.data.RawMessageDao
import com.kharcha.data.RuleDao
import com.kharcha.data.RuleEntity
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ReparseFakeRawMessageDao(seed: List<RawMessage>) : RawMessageDao {
    private val messages = seed.toMutableList()
    private var nextId = (seed.maxOfOrNull { it.id } ?: 0L) + 1

    override suspend fun insertIgnoringDuplicates(message: RawMessage): Long {
        if (messages.any { it.contentHash == message.contentHash }) return -1L
        val stored = message.copy(id = nextId++)
        messages += stored
        return stored.id
    }

    override suspend fun findNearDuplicate(sender: String, body: String, fromEpochMillis: Long, toEpochMillis: Long): RawMessage? =
        messages.firstOrNull {
            it.sender == sender && it.body == body &&
                it.receivedAtEpochMillis in fromEpochMillis..toEpochMillis
        }

    override suspend fun markIgnored(id: Long, reason: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = messages[index].copy(ignored = true, ignoreReason = reason)
    }

    override fun observeIgnored(): Flow<List<RawMessage>> =
        MutableStateFlow(messages.filter { it.ignored })

    override suspend fun restore(id: Long) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = messages[index].copy(ignored = false, ignoreReason = null)
    }

    override suspend fun count(): Int = messages.size
    override suspend fun getAll(): List<RawMessage> = messages.toList()
    override fun observeUnparsed(): Flow<List<RawMessage>> = flowOf(emptyList())
    override suspend fun markDismissed(id: Long) = Unit
    override fun observeDismissed(): Flow<List<RawMessage>> = flowOf(emptyList())
    override suspend fun undismiss(id: Long) = Unit
    override suspend fun getById(id: Long): RawMessage? = messages.firstOrNull { it.id == id }
    override fun observeAll(): Flow<List<RawMessage>> = flowOf(messages.toList())
}

private class ReparseFakeTransactionDao(seed: List<TransactionEntity>) : TransactionDao {
    val flow = MutableStateFlow(seed)
    private var nextId = (seed.maxOfOrNull { it.id } ?: 0L) + 1

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

private class ReparseFakeCategoryDao(private val categories: List<CategoryEntity>) : CategoryDao {
    override suspend fun insert(category: CategoryEntity): Long = 0L
    override suspend fun update(category: CategoryEntity) = Unit
    override suspend fun delete(category: CategoryEntity) = Unit
    override suspend fun getById(id: Long): CategoryEntity? = categories.find { it.id == id }
    override fun observeAll(): Flow<List<CategoryEntity>> = MutableStateFlow(categories)
}

/** Unlike the other fakes in this module, inserted rules are visible to `observeAll()`. */
private class ReparseFakeRuleDao : RuleDao {
    private val rules = MutableStateFlow<List<RuleEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(rule: RuleEntity): Long {
        val stored = rule.copy(id = nextId++)
        rules.value = rules.value + stored
        return stored.id
    }

    override suspend fun update(rule: RuleEntity) = Unit
    override suspend fun delete(rule: RuleEntity) = Unit
    override suspend fun getById(id: Long): RuleEntity? = rules.value.find { it.id == id }
    override fun observeAll(): Flow<List<RuleEntity>> = rules
}

/**
 * Reviewer's Important 2: `ReparseService` was dead code — never provided by `DataModule`,
 * never injected, `reparseAll()` never called from `:app`.
 * [TransactionsViewModel.confirmAlwaysCategorize] inserted the rule and stopped, so the
 * user's 40 historical transactions from that merchant stayed Uncategorized forever and
 * spec success criterion 4 ("Improving a rule re-categorizes history without losing manual
 * overrides") was unreachable.
 */
class ReparseOnRuleChangeTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val merchant = "JAWALAKHEL HANKOOK SARANG RESTAU"
    private val body = "Dear SUVASH, AC 0###15164761, NPR 250.00 withdrawn on 03/08/2026 11:32:05 " +
        "for QR Payment to $merchant"

    private val food = CategoryEntity(id = 1L, name = "Food & Dining", colorArgb = 0xFFE8734A.toInt(), isIncome = false, isFee = false)
    private val shopping = CategoryEntity(id = 3L, name = "Shopping", colorArgb = 0xFFC968A6.toInt(), isIncome = false, isFee = false)

    private fun rawMessage(id: Long, receivedAt: Long) = RawMessage(
        id = id,
        sender = "SBL_Alert",
        body = body,
        receivedAtEpochMillis = receivedAt,
        contentHash = "hash-$id",
    )

    private fun transaction(id: Long, rawMessageId: Long, categoryId: Long?, manualOverride: Boolean) =
        TransactionEntity(
            id = id,
            rawMessageId = rawMessageId,
            sourceAccount = "0###15164761",
            amountMinorUnits = 25000L,
            currency = Currency.NPR,
            direction = Direction.DEBIT,
            occurredAtEpochMillis = 1_754_000_000_000L,
            remark = "QR Payment to $merchant",
            merchant = merchant,
            balanceAfterMinorUnits = null,
            categoryId = categoryId,
            categoryIsManualOverride = manualOverride,
            excludedFromSpending = false,
            isManualEntry = false,
        )

    @Test
    fun `accepting always-categorize re-categorizes history and preserves manual overrides`() = runTest {
        val rawDao = ReparseFakeRawMessageDao(
            listOf(rawMessage(1L, 1_754_000_000_000L), rawMessage(2L, 1_754_000_100_000L)),
        )
        val txnDao = ReparseFakeTransactionDao(
            listOf(
                // Historical, never hand-touched: must pick up the new rule.
                transaction(id = 1L, rawMessageId = 1L, categoryId = null, manualOverride = false),
                // The user hand-filed this one under Food & Dining: must survive untouched.
                transaction(id = 2L, rawMessageId = 2L, categoryId = food.id, manualOverride = true),
            ),
        )
        val ruleDao = ReparseFakeRuleDao()
        val reparseService = com.kharcha.data.ReparseService(
            rawMessageDao = rawDao,
            transactionDao = txnDao,
            ruleset = com.kharcha.parser.SblAlertRuleset,
            categorizerFactory = {
                com.kharcha.data.Categorizer(ruleDao.observeAll().first())
            },
        )

        val vm = TransactionsViewModel(
            transactionDao = txnDao,
            categoryDao = ReparseFakeCategoryDao(listOf(food, shopping)),
            rawMessageDao = rawDao,
            ruleDao = ruleDao,
            openDayRequests = OpenDayRequests(),
            zone = TimeZone.UTC,
            reparseService = reparseService,
            ioDispatcher = testDispatcher,
        )

        vm.confirmAlwaysCategorize(merchant = merchant, categoryId = shopping.id)

        assertEquals(
            shopping.id,
            txnDao.get(1L).categoryId,
            "the historical transaction from this merchant must be re-categorized by the new rule",
        )
        val overridden = txnDao.get(2L)
        assertEquals(food.id, overridden.categoryId, "a manual override must survive re-parse")
        assertTrue(overridden.categoryIsManualOverride)
        assertEquals(2, txnDao.flow.value.size, "re-parse must never duplicate transactions")
    }
}
