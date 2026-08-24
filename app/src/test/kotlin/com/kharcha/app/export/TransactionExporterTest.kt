package com.kharcha.app.export

import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class TransactionExporterTest {

    private val zone = TimeZone.of("UTC")

    private fun epochOf(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long {
        val date = LocalDate(year, month, day)
        val time = LocalTime(hour, minute, 0, 0)
        val dateTime = LocalDateTime(date, time)
        val instant = dateTime.toInstant(zone)
        return instant.toEpochMilliseconds()
    }

    private fun createTxn(
        id: Long = 1L,
        sourceAccount: String = "Acc001",
        amountMinorUnits: Long = 100000L,
        currency: Currency = Currency.NPR,
        direction: Direction = Direction.DEBIT,
        occurredAtEpochMillis: Long = epochOf(2026, 1, 15, 10, 30),
        remark: String = "Test transaction",
        merchant: String? = "Merchant A",
        excludedFromSpending: Boolean = false,
        categoryId: Long? = null,
    ): TransactionEntity = TransactionEntity(
        id = id,
        rawMessageId = null,
        sourceAccount = sourceAccount,
        amountMinorUnits = amountMinorUnits,
        currency = currency,
        direction = direction,
        occurredAtEpochMillis = occurredAtEpochMillis,
        remark = remark,
        merchant = merchant,
        balanceAfterMinorUnits = null,
        categoryId = categoryId,
        categoryIsManualOverride = false,
        excludedFromSpending = excludedFromSpending,
        isManualEntry = false
    )

    @Test
    fun `CSV has header row`() {
        val txns = listOf(createTxn())
        val exporter = TransactionExporter()
        val csv = exporter.toCsv(txns, zone)

        val lines = csv.split("\n")
        assertEquals(2, lines.size)
        val header = lines[0]
        assertTrue(header.contains("date"))
        assertTrue(header.contains("time"))
        assertTrue(header.contains("direction"))
        assertTrue(header.contains("amount"))
        assertTrue(header.contains("currency"))
        assertTrue(header.contains("merchant"))
        assertTrue(header.contains("channel"))
        assertTrue(header.contains("category"))
        assertTrue(header.contains("account"))
        assertTrue(header.contains("excluded_from_spending"))
        assertTrue(header.contains("raw_remark"))
    }

    @Test
    fun `CSV exports single transaction with correct format`() {
        val txn = createTxn(
            id = 1L,
            amountMinorUnits = 50000L,
            currency = Currency.NPR,
            direction = Direction.DEBIT,
            occurredAtEpochMillis = epochOf(2026, 1, 15, 14, 30),
            merchant = "Coffee Shop",
            remark = "Morning coffee"
        )
        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txn), zone)

        val lines = csv.split("\n")
        assertEquals(2, lines.size)

        val dataLine = lines[1]
        assertTrue(dataLine.contains("2026-01-15"))
        assertTrue(dataLine.contains("14:30"))
        assertTrue(dataLine.contains("DEBIT"))
        assertTrue(dataLine.contains("500.00"))
        assertTrue(dataLine.contains("NPR"))
        assertTrue(dataLine.contains("Coffee Shop"))
        assertTrue(dataLine.contains("false"))
    }

    @Test
    fun `CSV correctly formats amount from minor units without floating point`() {
        val testCases = listOf(
            100000L to "1000.00",
            1L to "0.01",
            50L to "0.50",
            100L to "1.00",
            123456L to "1234.56",
        )

        val exporter = TransactionExporter()
        for ((minorUnits, expected) in testCases) {
            val txn = createTxn(amountMinorUnits = minorUnits)
            val csv = exporter.toCsv(listOf(txn), zone)
            val lines = csv.split("\n")
            assertTrue(lines[1].contains(expected))
        }
    }

    @Test
    fun `CSV escapes commas in fields`() {
        val txn = createTxn(
            merchant = "Merchant, Inc.",
            remark = "Payment, split, invoice"
        )
        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txn), zone)

        val lines = csv.split("\n")
        val dataLine = lines[1]
        assertTrue(dataLine.contains("\"Merchant, Inc.\""))
        assertTrue(dataLine.contains("\"Payment, split, invoice\""))
    }

    @Test
    fun `CSV escapes double quotes in fields`() {
        val txn = createTxn(
            merchant = "Vendor \"Premium\" Store",
            remark = "Invoice #123 \"Special\""
        )
        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txn), zone)

        val lines = csv.split("\n")
        val dataLine = lines[1]
        assertTrue(dataLine.contains("\"Vendor \"\"Premium\"\" Store\""))
        assertTrue(dataLine.contains("\"Invoice #123 \"\"Special\"\"\""))
    }

    @Test
    fun `CSV escapes newlines in fields`() {
        val txn = createTxn(
            remark = "Line 1\nLine 2\nLine 3"
        )
        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txn), zone)

        assertTrue(csv.contains("\"Line 1\nLine 2\nLine 3\""))
    }

    @Test
    fun `CSV prevents CSV injection with leading equals`() {
        val txn = createTxn(merchant = "=SUM(1+1)")
        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txn), zone)

        val lines = csv.split("\n")
        val dataLine = lines[1]
        assertTrue(dataLine.contains("\"'=SUM(1+1)\""))
    }

    @Test
    fun `CSV prevents CSV injection with leading plus`() {
        val txn = createTxn(merchant = "+1234567890")
        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txn), zone)

        val lines = csv.split("\n")
        val dataLine = lines[1]
        assertTrue(dataLine.contains("\"'+1234567890\""))
    }

    @Test
    fun `CSV prevents CSV injection with leading minus`() {
        val txn = createTxn(merchant = "-2000")
        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txn), zone)

        val lines = csv.split("\n")
        val dataLine = lines[1]
        assertTrue(dataLine.contains("\"'-2000\""))
    }

    @Test
    fun `CSV prevents CSV injection with leading at sign`() {
        val txn = createTxn(merchant = "@SUM(A1+A2)")
        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txn), zone)

        val lines = csv.split("\n")
        val dataLine = lines[1]
        assertTrue(dataLine.contains("\"'@SUM(A1+A2)\""))
    }

    @Test
    fun `CSV sorts by date descending then id ascending`() {
        val date1 = epochOf(2026, 1, 10)
        val date2 = epochOf(2026, 1, 15)

        val txns = listOf(
            createTxn(id = 1L, occurredAtEpochMillis = date1),
            createTxn(id = 3L, occurredAtEpochMillis = date2),
            createTxn(id = 2L, occurredAtEpochMillis = date2),
        )

        val exporter = TransactionExporter()
        val csv = exporter.toCsv(txns, zone)
        val lines = csv.split("\n")

        val row1 = lines[1]
        val row2 = lines[2]
        val row3 = lines[3]

        assertTrue(row1.contains("2026-01-15"))
        assertTrue(row2.contains("2026-01-15"))
        assertTrue(row3.contains("2026-01-10"))
    }

    @Test
    fun `CSV filters transactions by date range (inclusive)`() {
        val before = epochOf(2026, 1, 1)
        val start = epochOf(2026, 1, 15)
        val middle = epochOf(2026, 1, 20)
        val end = epochOf(2026, 1, 31)
        val after = epochOf(2026, 2, 1)

        val txns = listOf(
            createTxn(id = 1L, occurredAtEpochMillis = before),
            createTxn(id = 2L, occurredAtEpochMillis = start),
            createTxn(id = 3L, occurredAtEpochMillis = middle),
            createTxn(id = 4L, occurredAtEpochMillis = end),
            createTxn(id = 5L, occurredAtEpochMillis = after),
        )

        val exporter = TransactionExporter()
        val csv = exporter.toCsv(txns, zone, start, end)
        val lines = csv.split("\n")

        assertEquals(4, lines.size)
        assertTrue(csv.contains("2026-01-15"))
        assertTrue(csv.contains("2026-01-20"))
        assertTrue(csv.contains("2026-01-31"))
        val allLines = csv.split("\n").drop(1)
        val hasFebruary = allLines.any { it.contains("2026-02-") }
        val hasJanuaryFirst = allLines.any { it.contains("2026-01-01") }
        assertEquals(false, hasFebruary)
        assertEquals(false, hasJanuaryFirst)
    }

    @Test
    fun `CSV handles nullable merchant gracefully`() {
        val txn = createTxn(merchant = null)
        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txn), zone)

        val lines = csv.split("\n")
        assertEquals(2, lines.size)
    }

    @Test
    fun `CSV handles all currencies`() {
        val txnNpr = createTxn(currency = Currency.NPR)
        val txnUsd = createTxn(currency = Currency.USD)

        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txnNpr, txnUsd), zone)

        assertTrue(csv.contains("NPR"))
        assertTrue(csv.contains("USD"))
    }

    @Test
    fun `CSV handles credit direction`() {
        val txn = createTxn(direction = Direction.CREDIT)
        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txn), zone)

        assertTrue(csv.contains("CREDIT"))
    }

    @Test
    fun `CSV exports excluded_from_spending flag`() {
        val txnIncluded = createTxn(id = 1L, excludedFromSpending = false)
        val txnExcluded = createTxn(id = 2L, excludedFromSpending = true)

        val exporter = TransactionExporter()
        val csv = exporter.toCsv(listOf(txnIncluded, txnExcluded), zone)

        assertEquals(true, csv.contains("false"))
        assertEquals(true, csv.contains("true"))
    }

    @Test
    fun `JSON has required top-level fields`() {
        val txns = listOf(createTxn())
        val exporter = TransactionExporter()
        val json = exporter.toJson(txns, zone)

        assertTrue(json.contains("exported_at"))
        assertTrue(json.contains("range_start"))
        assertTrue(json.contains("range_end"))
        assertTrue(json.contains("count"))
        assertTrue(json.contains("transactions"))
    }

    @Test
    fun `JSON exports single transaction`() {
        val txn = createTxn(
            id = 1L,
            amountMinorUnits = 50000L,
            currency = Currency.NPR,
            direction = Direction.DEBIT,
            occurredAtEpochMillis = epochOf(2026, 1, 15, 14, 30),
            merchant = "Coffee Shop",
            remark = "Morning coffee"
        )
        val exporter = TransactionExporter()
        val json = exporter.toJson(listOf(txn), zone)

        assertTrue(json.contains("2026-01-15"))
        assertTrue(json.contains("14:30"))
        assertTrue(json.contains("DEBIT"))
        assertTrue(json.contains("500.00"))
        assertTrue(json.contains("NPR"))
        assertTrue(json.contains("Coffee Shop"))
        assertTrue(json.contains("Morning coffee"))
    }

    @Test
    fun `JSON count field matches transaction count`() {
        val txns = listOf(
            createTxn(id = 1L),
            createTxn(id = 2L),
            createTxn(id = 3L),
        )
        val exporter = TransactionExporter()
        val json = exporter.toJson(txns, zone)

        assertTrue(json.contains("\"count\": 3"))
    }

    @Test
    fun `JSON filters by date range`() {
        val start = epochOf(2026, 1, 15)
        val end = epochOf(2026, 1, 31)
        val before = epochOf(2026, 1, 1)
        val after = epochOf(2026, 2, 1)

        val txns = listOf(
            createTxn(id = 1L, occurredAtEpochMillis = before),
            createTxn(id = 2L, occurredAtEpochMillis = start),
            createTxn(id = 3L, occurredAtEpochMillis = after),
        )

        val exporter = TransactionExporter()
        val json = exporter.toJson(txns, zone, start, end)

        assertTrue(json.contains("\"count\": 1"))
        assertTrue(json.contains("2026-01-15"))
        val linesAfterTransactions = json.substringAfter("\"transactions\"")
        assertEquals(
            false,
            linesAfterTransactions.contains("2026-02-01")
        )
    }

    @Test
    fun `JSON has range_start and range_end in ISO-8601 format`() {
        val start = epochOf(2026, 1, 15)
        val end = epochOf(2026, 1, 31)
        val txns = listOf(createTxn(occurredAtEpochMillis = start))

        val exporter = TransactionExporter()
        val json = exporter.toJson(txns, zone, start, end)

        assertTrue(json.contains("\"range_start\": \"2026-01-15\""))
        assertTrue(json.contains("\"range_end\": \"2026-01-31\""))
    }

    @Test
    fun `JSON sorts transactions by date descending then id ascending`() {
        val date1 = epochOf(2026, 1, 10)
        val date2 = epochOf(2026, 1, 15)

        val txns = listOf(
            createTxn(id = 1L, occurredAtEpochMillis = date1),
            createTxn(id = 3L, occurredAtEpochMillis = date2),
            createTxn(id = 2L, occurredAtEpochMillis = date2),
        )

        val exporter = TransactionExporter()
        val json = exporter.toJson(txns, zone)

        val transactionsSection = json.substringAfter("\"transactions\": [")
        assertTrue(transactionsSection.contains("2026-01-15"))
        val idx15 = json.indexOf("2026-01-15")
        val idx10 = json.indexOf("2026-01-10")
        assertEquals(true, idx15 < idx10)
    }

    @Test
    fun `the channel column carries the rail, not a second copy of the account`() {
        val txn = createTxn(
            sourceAccount = "0###15164761",
            remark = "Fund Trf to BHATBHATENI SUPERMARKET ESEW",
        )
        val csv = TransactionExporter().toCsv(listOf(txn), zone)
        val fields = csv.split("\n")[1].split(",")
        val header = csv.split("\n")[0].split(",")

        val channel = fields[header.indexOf("channel")]
        assertTrue(channel.isNotBlank(), "expected a rail in the channel column, got: $csv")
        assertTrue(
            channel != "0###15164761",
            "channel duplicated the account instead of naming the rail: $csv",
        )
    }

    @Test
    fun `the category column carries the category name`() {
        val txn = createTxn(categoryId = 7L)
        val csv = TransactionExporter().toCsv(
            listOf(txn),
            zone,
            categoryNames = mapOf(7L to "Food & Dining"),
        )

        assertTrue(csv.contains("Food & Dining"), "category name missing from: $csv")
    }

    @Test
    fun `a transaction with no category exports an empty category, not a stray name`() {
        val txn = createTxn(categoryId = null)
        val csv = TransactionExporter().toCsv(
            listOf(txn),
            zone,
            categoryNames = mapOf(7L to "Food & Dining"),
        )

        assertTrue(!csv.contains("Food & Dining"), "uncategorised row picked up a name: $csv")
    }
}
