package com.kharcha.app.export

import com.kharcha.data.TransactionEntity
import com.kharcha.parser.RemarkParser
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Exports transactions to CSV or JSON formats.
 *
 * All amounts are formatted from minor units (integer) without floating point.
 * Transactions are ordered deterministically: date descending, then ID ascending.
 * Date range filtering is inclusive on both ends.
 */
class TransactionExporter {

    /**
     * Exports transactions to CSV format.
     *
     * @param transactions List of transactions to export
     * @param zone TimeZone for date conversion
     * @param startEpochMillis Optional inclusive start date; null means no lower bound
     * @param endEpochMillis Optional inclusive end date; null means no upper bound
     * @return CSV string with header row and one row per transaction
     */
    fun toCsv(
        transactions: List<TransactionEntity>,
        zone: TimeZone,
        startEpochMillis: Long? = null,
        endEpochMillis: Long? = null,
        categoryNames: Map<Long, String> = emptyMap(),
    ): String {
        val filtered = filterByDateRange(transactions, startEpochMillis, endEpochMillis)
        val sorted = sortDeterministically(filtered)

        val header = csvHeader()
        val rows = sorted.map { txn -> csvRow(txn, zone, categoryNames) }

        return (listOf(header) + rows).joinToString("\n")
    }

    /**
     * Exports transactions to JSON format.
     *
     * @param transactions List of transactions to export
     * @param zone TimeZone for date conversion
     * @param startEpochMillis Optional inclusive start date; null means no lower bound
     * @param endEpochMillis Optional inclusive end date; null means no upper bound
     * @return JSON string with metadata and transaction array
     */
    fun toJson(
        transactions: List<TransactionEntity>,
        zone: TimeZone,
        startEpochMillis: Long? = null,
        endEpochMillis: Long? = null,
        categoryNames: Map<Long, String> = emptyMap(),
    ): String {
        val filtered = filterByDateRange(transactions, startEpochMillis, endEpochMillis)
        val sorted = sortDeterministically(filtered)

        val exportedAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            .toLocalDateTime(zone)
            .toString()

        val rangeStart = if (startEpochMillis != null) {
            epochMillisToIsoDate(startEpochMillis, zone)
        } else {
            "null"
        }

        val rangeEnd = if (endEpochMillis != null) {
            epochMillisToIsoDate(endEpochMillis, zone)
        } else {
            "null"
        }

        val transactionLines = sorted.map { txn -> jsonTransaction(txn, zone, categoryNames) }
        val transactionsJson = transactionLines.joinToString(",\n    ")

        return buildString {
            append("{\n")
            append("  \"exported_at\": \"$exportedAt\",\n")
            append("  \"range_start\": \"$rangeStart\",\n")
            append("  \"range_end\": \"$rangeEnd\",\n")
            append("  \"count\": ${sorted.size},\n")
            append("  \"transactions\": [\n")
            if (transactionLines.isNotEmpty()) {
                append("    $transactionsJson\n")
            }
            append("  ]\n")
            append("}\n")
        }
    }

    // ===== Private Helpers =====

    private fun filterByDateRange(
        transactions: List<TransactionEntity>,
        startEpochMillis: Long?,
        endEpochMillis: Long?,
    ): List<TransactionEntity> {
        return transactions.filter { txn ->
            val afterStart = startEpochMillis == null || txn.occurredAtEpochMillis >= startEpochMillis
            val beforeEnd = endEpochMillis == null || txn.occurredAtEpochMillis <= endEpochMillis
            afterStart && beforeEnd
        }
    }

    private fun sortDeterministically(transactions: List<TransactionEntity>): List<TransactionEntity> {
        return transactions.sortedWith(compareBy<TransactionEntity>({ -it.occurredAtEpochMillis }, { it.id }))
    }

    private fun csvHeader(): String {
        return "date,time,direction,amount,currency,merchant,channel,category,account,excluded_from_spending,raw_remark"
    }

    /**
     * The rail money moved over — eSewa, connectIPS, Mobile banking — read out of the
     * remark, the same way the transactions list reads it. It used to be a second copy of
     * `account`, which made the column pure noise: every row's channel equalled its
     * account, and the rail the user actually wanted to see was nowhere in the file.
     */
    private fun channelOf(txn: TransactionEntity): String =
        RemarkParser.parse(txn.remark).channel ?: ""

    private fun csvRow(txn: TransactionEntity, zone: TimeZone, categoryNames: Map<Long, String>): String {
        val localDateTime = Instant.fromEpochMilliseconds(txn.occurredAtEpochMillis)
            .toLocalDateTime(zone)
        val date = localDateTime.date.toString() // ISO-8601: yyyy-MM-dd
        val time = localDateTime.time.toString() // HH:mm:ss.sss format, but we'll use HH:mm

        val timeFormatted = localDateTime.time.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }

        val direction = txn.direction.name
        val amount = formatAmount(txn.amountMinorUnits)
        val currency = txn.currency.name
        val merchant = escapeCsvField(txn.merchant ?: "")
        val channel = escapeCsvField(channelOf(txn))
        val category = escapeCsvField(txn.categoryId?.let(categoryNames::get) ?: "")
        val account = escapeCsvField(txn.sourceAccount)
        val excludedFromSpending = txn.excludedFromSpending.toString()
        val remark = escapeCsvField(txn.remark)

        return "$date,$timeFormatted,$direction,$amount,$currency,$merchant,$channel,$category,$account,$excludedFromSpending,$remark"
    }

    private fun jsonTransaction(txn: TransactionEntity, zone: TimeZone, categoryNames: Map<Long, String>): String {
        val localDateTime = Instant.fromEpochMilliseconds(txn.occurredAtEpochMillis)
            .toLocalDateTime(zone)
        val date = localDateTime.date.toString() // ISO-8601
        val timeFormatted = localDateTime.time.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }

        val direction = txn.direction.name
        val amount = formatAmount(txn.amountMinorUnits)
        val currency = txn.currency.name
        val merchant = jsonEscape(txn.merchant ?: "")
        val channel = jsonEscape(channelOf(txn))
        val category = jsonEscape(txn.categoryId?.let(categoryNames::get) ?: "")
        val account = jsonEscape(txn.sourceAccount)
        val excludedFromSpending = txn.excludedFromSpending
        val remark = jsonEscape(txn.remark)

        return buildString {
            append("{\n")
            append("      \"date\": \"$date\",\n")
            append("      \"time\": \"$timeFormatted\",\n")
            append("      \"direction\": \"$direction\",\n")
            append("      \"amount\": \"$amount\",\n")
            append("      \"currency\": \"$currency\",\n")
            append("      \"merchant\": \"$merchant\",\n")
            append("      \"channel\": \"$channel\",\n")
            append("      \"category\": \"$category\",\n")
            append("      \"account\": \"$account\",\n")
            append("      \"excluded_from_spending\": $excludedFromSpending,\n")
            append("      \"raw_remark\": \"$remark\"\n")
            append("    }")
        }
    }

    /**
     * Format amount from minor units to decimal string.
     * E.g., 100000 -> "1000.00", 1 -> "0.01"
     */
    private fun formatAmount(minorUnits: Long): String {
        val major = minorUnits / 100
        val minor = minorUnits % 100
        return "$major.${minor.toString().padStart(2, '0')}"
    }

    /**
     * Escape a field for CSV output (RFC 4180 quoting, plus formula-injection defence).
     *
     * Quoting alone does NOT neutralise a formula: spreadsheet apps strip the surrounding
     * quotes while importing and then evaluate the cell, so `"=1+1"` still runs. The only
     * reliable defence is to change the character the cell *starts* with, so a leading
     * `=`, `+`, `-` or `@` is prefixed with an apostrophe — the conventional
     * "treat as literal text" marker — before the field is quoted.
     *
     * A merchant name legitimately beginning with `-` is vanishingly rare, and rendering
     * it as `'-FOO` in a spreadsheet is a far better failure than executing it.
     */
    private fun escapeCsvField(field: String): String {
        val neutralised = if (field.isNotEmpty() && field[0] in FORMULA_TRIGGERS) {
            "'$field"
        } else {
            field
        }

        val needsQuoting = neutralised.contains(',') ||
            neutralised.contains('"') ||
            neutralised.contains('\n') ||
            neutralised.contains('\r') ||
            neutralised !== field

        if (!needsQuoting) return neutralised

        // Escape internal quotes by doubling them.
        return "\"${neutralised.replace("\"", "\"\"")}\""
    }

    /**
     * Escape a string for JSON output.
     * Handles quotes, newlines, tabs, backslashes, etc.
     */
    private fun jsonEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Convert epoch millis to ISO-8601 date string (YYYY-MM-DD).
     */
    private fun epochMillisToIsoDate(epochMillis: Long, zone: TimeZone): String {
        return Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(zone)
            .date
            .toString()
    }

    private companion object {
        /** Leading characters a spreadsheet treats as the start of a formula. */
        val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@')
    }
}
