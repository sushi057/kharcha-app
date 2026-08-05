package com.kharcha.app.ingest

import com.kharcha.data.RawMessage
import com.kharcha.data.RawMessageDao
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.SblAlertRuleset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

class MessageIngestorTest {

    private val qrPayment =
        "Dear SUVASH, AC 0###15164761, NPR 2,984.00 withdrawn on 17/07/2026 12:10:01 " +
            "for QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU"

    private fun newIngestor(): MessageIngestor =
        MessageIngestor(FakeRawMessageDao(), FakeTransactionDao(), SblAlertRuleset)

    @Test
    fun `stores a parsed transaction`() = runTest {
        val ingestor = newIngestor()
        assertEquals(IngestOutcome.STORED, ingestor.ingest("SBL_Alert", qrPayment, 1_754_000_000_000L))
    }

    @Test
    fun `the same message ingested twice is a duplicate`() = runTest {
        val ingestor = newIngestor()
        ingestor.ingest("SBL_Alert", qrPayment, 1_754_000_000_000L)
        assertEquals(IngestOutcome.DUPLICATE, ingestor.ingest("SBL_Alert", qrPayment, 1_754_000_000_000L))
    }

    @Test
    fun `messages from other senders are dropped`() = runTest {
        assertEquals(
            IngestOutcome.WRONG_SENDER,
            newIngestor().ingest("Ncell", qrPayment, 1_754_000_000_000L)
        )
    }

    @Test
    fun `an OTP is ignored and stored as neither transaction nor unparsed`() = runTest {
        val ingestor = newIngestor()
        assertEquals(
            IngestOutcome.IGNORED,
            ingestor.ingest("SBL_Alert", "288388 is your OTP to get CVV for your Virtual eCom Card.", 1L)
        )
    }

    @Test
    fun `an unknown SBL message lands in the unparsed inbox`() = runTest {
        val ingestor = newIngestor()
        assertEquals(
            IngestOutcome.UNPARSED,
            ingestor.ingest("SBL_Alert", "Your statement is ready.", 1L)
        )
    }
}
