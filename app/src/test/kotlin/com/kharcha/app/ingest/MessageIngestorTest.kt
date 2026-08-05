package com.kharcha.app.ingest

import com.kharcha.data.RawMessage
import com.kharcha.data.RawMessageDao
import com.kharcha.data.RuleDao
import com.kharcha.data.RuleEntity
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.SblAlertRuleset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeRawMessageDao : RawMessageDao {
    val messages = mutableListOf<RawMessage>()
    private var nextId = 1L

    override suspend fun insertIgnoringDuplicates(message: RawMessage): Long {
        if (messages.any { it.contentHash == message.contentHash }) return -1L
        val stored = message.copy(id = nextId++)
        messages += stored
        return stored.id
    }

    override suspend fun markIgnored(id: Long) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = messages[index].copy(ignored = true)
    }

    override suspend fun count(): Int = messages.size

    override suspend fun getAll(): List<RawMessage> = messages.toList()

    override fun observeUnparsed(): Flow<List<RawMessage>> =
        flowOf(messages.filter { !it.ignored && !it.dismissed })

    override suspend fun markDismissed(id: Long) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = messages[index].copy(dismissed = true)
    }
}

private class FakeTransactionDao : TransactionDao {
    val transactions = mutableListOf<TransactionEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())

    override suspend fun insert(transaction: TransactionEntity): Long {
        val stored = transaction.copy(id = nextId++)
        transactions += stored
        flow.value = transactions.toList()
        return stored.id
    }

    override suspend fun update(transaction: TransactionEntity) {
        val index = transactions.indexOfFirst { it.id == transaction.id }
        if (index >= 0) transactions[index] = transaction
    }

    override suspend fun delete(transaction: TransactionEntity) {
        transactions.removeAll { it.id == transaction.id }
    }

    override suspend fun getById(id: Long): TransactionEntity? = transactions.find { it.id == id }

    override suspend fun getByRawMessageId(rawMessageId: Long): TransactionEntity? =
        transactions.find { it.rawMessageId == rawMessageId }

    override fun observeAll(): Flow<List<TransactionEntity>> = flow
}

private class FakeRuleDao(private val rules: List<RuleEntity> = emptyList()) : RuleDao {
    var observeAllCallCount = 0
        private set

    override suspend fun insert(rule: RuleEntity): Long = 0L
    override suspend fun update(rule: RuleEntity) = Unit
    override suspend fun delete(rule: RuleEntity) = Unit
    override suspend fun getById(id: Long): RuleEntity? = rules.find { it.id == id }

    override fun observeAll(): Flow<List<RuleEntity>> {
        observeAllCallCount++
        return flowOf(rules)
    }
}

class MessageIngestorTest {

    private val qrPayment =
        "Dear SUVASH, AC 0###15164761, NPR 2,984.00 withdrawn on 17/07/2026 12:10:01 " +
            "for QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU"

    // Mirrors SeedData: "Food & Dining" wins on the QR payment's merchant name (the
    // ruleset extracts "JAWALAKHEL HANKOOK SARANG RESTAU" as the merchant for a "QR
    // Payment to <merchant>" remark, and Categorizer matches against merchant when
    // present), "Fees" wins on a WTax.Pd remark (matching SeedData.RULES's own
    // WTax.Pd -> Fees rule verbatim).
    private val foodDiningCategoryId = 1L
    private val feesCategoryId = 6L

    private val seededRules = listOf(
        RuleEntity(id = 1L, matchPattern = "HANKOOK", matchesPrefix = false, categoryId = foodDiningCategoryId, priority = 50),
        RuleEntity(id = 2L, matchPattern = "WTax.Pd", matchesPrefix = true, categoryId = feesCategoryId, priority = 100)
    )

    private fun newIngestor(
        transactionDao: FakeTransactionDao = FakeTransactionDao(),
        rules: List<RuleEntity> = seededRules,
        ruleDao: FakeRuleDao = FakeRuleDao(rules)
    ): MessageIngestor =
        MessageIngestor(FakeRawMessageDao(), transactionDao, SblAlertRuleset, ruleDao)

    @Test
    fun `stores a parsed transaction`() = runTest {
        val ingestor = newIngestor()
        assertEquals(IngestOutcome.STORED, ingestor.ingest("SBL_Alert", qrPayment, 1_754_000_000_000L).outcome)
    }

    @Test
    fun `the same message ingested twice is a duplicate`() = runTest {
        val ingestor = newIngestor()
        ingestor.ingest("SBL_Alert", qrPayment, 1_754_000_000_000L)
        assertEquals(IngestOutcome.DUPLICATE, ingestor.ingest("SBL_Alert", qrPayment, 1_754_000_000_000L).outcome)
    }

    @Test
    fun `messages from other senders are dropped`() = runTest {
        assertEquals(
            IngestOutcome.WRONG_SENDER,
            newIngestor().ingest("Ncell", qrPayment, 1_754_000_000_000L).outcome
        )
    }

    @Test
    fun `an OTP is ignored and stored as neither transaction nor unparsed`() = runTest {
        val ingestor = newIngestor()
        assertEquals(
            IngestOutcome.IGNORED,
            ingestor.ingest("SBL_Alert", "288388 is your OTP to get CVV for your Virtual eCom Card.", 1L).outcome
        )
    }

    @Test
    fun `an unknown SBL message lands in the unparsed inbox`() = runTest {
        val ingestor = newIngestor()
        assertEquals(
            IngestOutcome.UNPARSED,
            ingestor.ingest("SBL_Alert", "Your statement is ready.", 1L).outcome
        )
    }

    @Test
    fun `a QR payment matching a seeded rule is categorized on ingest`() = runTest {
        val transactionDao = FakeTransactionDao()
        val ingestor = newIngestor(transactionDao = transactionDao)

        assertEquals(IngestOutcome.STORED, ingestor.ingest("SBL_Alert", qrPayment, 1_754_000_000_000L).outcome)

        val stored = transactionDao.transactions.single()
        assertEquals(foodDiningCategoryId, stored.categoryId)
        assertEquals(false, stored.categoryIsManualOverride)
    }

    @Test
    fun `a WTax Pd message is categorized as Fees`() = runTest {
        val wTaxMessage =
            "Dear SUVASH, AC 0###15164761, NPR 10.00 withdrawn on 17/07/2026 12:10:01 " +
                "for WTax.Pd on Interest"
        val transactionDao = FakeTransactionDao()
        val ingestor = newIngestor(transactionDao = transactionDao)

        assertEquals(IngestOutcome.STORED, ingestor.ingest("SBL_Alert", wTaxMessage, 1_754_000_000_000L).outcome)

        val stored = transactionDao.transactions.single()
        assertEquals(feesCategoryId, stored.categoryId)
        assertEquals(false, stored.categoryIsManualOverride)
    }

    @Test
    fun `an unmatched remark is stored uncategorized`() = runTest {
        val unmatched =
            "Dear SUVASH, AC 0###15164761, NPR 500.00 withdrawn on 17/07/2026 12:10:01 " +
                "for Something Nobody Has A Rule For"
        val transactionDao = FakeTransactionDao()
        val ingestor = newIngestor(transactionDao = transactionDao)

        assertEquals(IngestOutcome.STORED, ingestor.ingest("SBL_Alert", unmatched, 1_754_000_000_000L).outcome)

        val stored = transactionDao.transactions.single()
        assertNull(stored.categoryId)
    }

    @Test
    fun `a batch ingested with a pre-built categorizer only queries rules once`() = runTest {
        val transactionDao = FakeTransactionDao()
        val ruleDao = FakeRuleDao(seededRules)
        val ingestor = newIngestor(transactionDao = transactionDao, ruleDao = ruleDao)

        val categorizer = ingestor.loadCategorizer()
        assertEquals(1, ruleDao.observeAllCallCount)

        val wTaxMessage =
            "Dear SUVASH, AC 0###15164761, NPR 10.00 withdrawn on 17/07/2026 12:10:01 " +
                "for WTax.Pd on Interest"
        val otherMessage =
            "Dear SUVASH, AC 0###15164761, NPR 20.00 withdrawn on 17/07/2026 13:10:01 " +
                "for WTax.Pd on Interest again"

        ingestor.ingest("SBL_Alert", qrPayment, 1_754_000_000_000L, categorizer)
        ingestor.ingest("SBL_Alert", wTaxMessage, 1_754_000_100_000L, categorizer)
        ingestor.ingest("SBL_Alert", otherMessage, 1_754_000_200_000L, categorizer)

        // observeAll() was called exactly once — by loadCategorizer() up front — not once
        // per message in the batch that followed.
        assertEquals(1, ruleDao.observeAllCallCount)
        assertEquals(3, transactionDao.transactions.size)
        assertEquals(foodDiningCategoryId, transactionDao.transactions[0].categoryId)
        assertEquals(feesCategoryId, transactionDao.transactions[1].categoryId)
        assertEquals(feesCategoryId, transactionDao.transactions[2].categoryId)
    }
}
