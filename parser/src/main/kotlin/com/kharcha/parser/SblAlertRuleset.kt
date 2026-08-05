package com.kharcha.parser

import kotlinx.datetime.LocalDateTime

/**
 * Parses SMS alerts from Siddhartha Bank's `SBL_Alert` sender ID.
 *
 * Family A: NPR account debit/credit alerts (this task).
 * Family B (USD card messages) and Family C (OTP/purchase-code ignores)
 * are intentionally not handled here yet — they fall through to
 * [ParseResult.Unrecognized] until Task 4 slots them in.
 */
object SblAlertRuleset : SenderRuleset {

    override val senderId: String = "SBL_Alert"

    private val familyA = Regex(
        "^Dear\\s+\\w+,\\s*AC\\s+(?<account>\\S+),\\s*NPR\\s+(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+" +
            "(?<direction>withdrawn|deposited)\\s+on\\s+(?<date>\\d{2}/\\d{2}/\\d{4})\\s+" +
            "(?<time>\\d{2}:\\d{2}:\\d{2})\\s+for\\s+(?<remark>.+)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    override fun parse(body: String): ParseResult {
        val match = familyA.matchEntire(body) ?: return ParseResult.Unrecognized

        val account = match.groups["account"]!!.value
        val amountText = match.groups["amount"]!!.value
        val directionText = match.groups["direction"]!!.value
        val dateText = match.groups["date"]!!.value
        val timeText = match.groups["time"]!!.value
        val remark = match.groups["remark"]!!.value.trim()

        val amount = parseAmount(amountText, Currency.NPR) ?: return ParseResult.Unrecognized

        val direction = when (directionText.lowercase()) {
            "withdrawn" -> Direction.DEBIT
            "deposited" -> Direction.CREDIT
            else -> return ParseResult.Unrecognized
        }

        val occurredAt = parseOccurredAt(dateText, timeText) ?: return ParseResult.Unrecognized

        val merchant = if (remark.startsWith("QR Payment to ", ignoreCase = true)) {
            remark.substring("QR Payment to ".length)
        } else {
            null
        }

        val remarkTruncated = body.length >= 155 && !remark.endsWith(".")

        return ParseResult.Parsed(
            ParsedTransaction(
                sourceAccount = account,
                amount = amount,
                direction = direction,
                occurredAt = occurredAt,
                remark = remark,
                merchant = merchant,
                balanceAfter = null,
                remarkTruncated = remarkTruncated
            )
        )
    }

    private fun parseOccurredAt(dateText: String, timeText: String): LocalDateTime? {
        val dateParts = dateText.split("/")
        if (dateParts.size != 3) return null
        val day = dateParts[0].toIntOrNull() ?: return null
        val month = dateParts[1].toIntOrNull() ?: return null
        val year = dateParts[2].toIntOrNull() ?: return null

        val timeParts = timeText.split(":")
        if (timeParts.size != 3) return null
        val hour = timeParts[0].toIntOrNull() ?: return null
        val minute = timeParts[1].toIntOrNull() ?: return null
        val second = timeParts[2].toIntOrNull() ?: return null

        return try {
            LocalDateTime(year, month, day, hour, minute, second)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
