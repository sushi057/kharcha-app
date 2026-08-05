package com.kharcha.parser

import kotlinx.datetime.LocalDateTime

/**
 * Parses SMS alerts from Siddhartha Bank's `SBL_Alert` sender ID.
 *
 * Family A: NPR account debit/credit alerts.
 * Family B: USD card purchase alerts.
 * Family C: OTP / purchase-code messages, which are explicitly ignored
 * rather than parsed as transactions.
 *
 * Dispatch order in [parse] is: ignore list -> Family A -> Family B ->
 * [ParseResult.Unrecognized]. The ignore list runs first because some
 * ignorable messages (e.g. purchase codes) contain a date, currency and
 * amount that could otherwise be misread as a transaction.
 */
object SblAlertRuleset : SenderRuleset {

    override val senderId: String = "SBL_Alert"

    private val familyA = Regex(
        "^Dear\\s+\\w+,\\s*AC\\s+(?<account>\\S+),\\s*NPR\\s+(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+" +
            "(?<direction>withdrawn|deposited)\\s+on\\s+(?<date>\\d{2}/\\d{2}/\\d{4})\\s+" +
            "(?<time>\\d{2}:\\d{2}:\\d{2})\\s+for\\s+(?<remark>.+)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val familyB = Regex(
        "^SBL\\s+Card\\s+(?<card>\\S+)\\s+used\\s+at\\s+(?<merchant>.+?)\\s+for\\s+USD\\s+" +
            "(?<amount>[\\d,]+\\.\\d{2})\\s+on\\s+(?<date>\\d{2}\\.\\d{2}\\.\\d{2})\\s+" +
            "(?<time>\\d{2}:\\d{2})\\s+Authid\\s+(?<authid>\\w+)" +
            "(?:\\s+Remaining\\s+Balance\\s+after\\s+txn\\s+USD\\s+(?<balance>[\\d,]+\\.\\d{2}))?",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val otpPattern = Regex("is your OTP", RegexOption.IGNORE_CASE)
    private val purchaseCodePattern = Regex("^Your purchase code at .* is \\d+$")

    override fun parse(body: String): ParseResult {
        ignoreReasonFor(body)?.let { return ParseResult.Ignored(it) }

        parseFamilyA(body)?.let { return it }
        parseFamilyB(body)?.let { return it }

        return ParseResult.Unrecognized
    }

    private fun ignoreReasonFor(body: String): String? {
        if (otpPattern.containsMatchIn(body)) return "otp"
        if (purchaseCodePattern.containsMatchIn(body)) return "purchase code"
        return null
    }

    private fun parseFamilyA(body: String): ParseResult? {
        val match = familyA.matchEntire(body) ?: return null

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

        val occurredAt = parseFamilyADate(dateText, timeText) ?: return ParseResult.Unrecognized

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

    private fun parseFamilyB(body: String): ParseResult? {
        val match = familyB.find(body) ?: return null

        val card = match.groups["card"]!!.value
        val merchant = match.groups["merchant"]!!.value.trim()
        val amountText = match.groups["amount"]!!.value
        val dateText = match.groups["date"]!!.value
        val timeText = match.groups["time"]!!.value
        val balanceText = match.groups["balance"]?.value

        val amount = parseAmount(amountText, Currency.USD) ?: return ParseResult.Unrecognized
        val occurredAt = parseFamilyBDate(dateText, timeText) ?: return ParseResult.Unrecognized
        val balanceAfter = if (balanceText != null) {
            parseAmount(balanceText, Currency.USD) ?: return ParseResult.Unrecognized
        } else {
            null
        }

        return ParseResult.Parsed(
            ParsedTransaction(
                sourceAccount = card,
                amount = amount,
                direction = Direction.DEBIT,
                occurredAt = occurredAt,
                remark = merchant,
                merchant = merchant,
                balanceAfter = balanceAfter,
                remarkTruncated = false
            )
        )
    }

    private fun parseFamilyADate(dateText: String, timeText: String): LocalDateTime? {
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

    private fun parseFamilyBDate(dateText: String, timeText: String): LocalDateTime? {
        val dateParts = dateText.split(".")
        if (dateParts.size != 3) return null
        val day = dateParts[0].toIntOrNull() ?: return null
        val month = dateParts[1].toIntOrNull() ?: return null
        val twoDigitYear = dateParts[2].toIntOrNull() ?: return null
        val year = 2000 + twoDigitYear

        val timeParts = timeText.split(":")
        if (timeParts.size != 2) return null
        val hour = timeParts[0].toIntOrNull() ?: return null
        val minute = timeParts[1].toIntOrNull() ?: return null

        return try {
            LocalDateTime(year, month, day, hour, minute)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
