package com.kharcha.parser

import kotlinx.datetime.LocalDateTime

/**
 * Parses SMS alerts from Siddhartha Bank's `SBL_Alert` sender ID.
 *
 * Family A: NPR account debit/credit alerts (with time-of-day).
 * Family B: USD card purchase alerts.
 * Family C: OTP / purchase-code / account-notice messages, which are
 * explicitly ignored rather than parsed as transactions.
 * Family D: NPR account debit/credit alerts with no time-of-day component
 * (`SBL AC ... debited|credited by NPR ... on dd/mm/yyyy for <remark>
 * Siddhartha Bank`). These are frequently truncated mid-remark because they
 * run up against the SMS length boundary.
 *
 * Dispatch order in [parse] is: ignore list -> Family A -> Family B ->
 * Family D -> [ParseResult.Unrecognized]. The ignore list runs first because
 * some ignorable messages (e.g. purchase codes) contain a date, currency and
 * amount that could otherwise be misread as a transaction. Family D runs
 * last because its shape (`SBL AC ...`) is distinct enough from Family A
 * (`Dear ..., AC ...`) and Family B (`SBL Card ...`) that it cannot shadow
 * either, but keeping it last documents that it is the newest, most
 * permissive family.
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

    private val familyD = Regex(
        "^SBL\\s+AC\\s+(?<account>\\S+)\\s+(?<direction>debited|credited)\\s+by\\s+NPR\\s+" +
            "(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+on\\s+(?<date>\\d{2}/\\d{2}/\\d{4})\\s+" +
            "for\\s+(?<remark>.+)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val bankNameSuffix = Regex("\\s+Siddhartha\\s+Bank\\s*$", RegexOption.IGNORE_CASE)

    /**
     * One-time passcodes, in either phrasing the bank actually uses: the code-first
     * "483920 is your OTP" and the code-last "Your OTP for Mobile Banking is 483920". Matching
     * only the former left the latter [ParseResult.Unrecognized], which surfaced a live OTP —
     * the one message class that must never be shown — in the review inbox.
     *
     * Still anchored on the possessive phrasing rather than a bare "OTP" keyword, per the
     * conservative-ignore rule above.
     */
    private val otpPattern = Regex(
        "\\b(is|as) your (otp|one[\\s-]?time (password|passcode|pin))\\b" +
            "|\\byour (otp|one[\\s-]?time (password|passcode|pin))\\b",
        RegexOption.IGNORE_CASE,
    )
    private val purchaseCodePattern = Regex("^Your purchase code at .* is \\d+$")

    // Conservative ignore rules. A false ignore silently drops a real transaction,
    // which is far worse than leaving a message Unrecognized, so each pattern below
    // is anchored to specific, confident phrasing rather than loose keyword matches.

    /** Internet/mobile banking password or card PIN changed/reset notices. No amount involved. */
    private val passwordOrPinChangedPattern =
        Regex("\\b(password|pin)\\b.*\\b(has been|was)\\b.*\\b(changed|reset)\\b", RegexOption.IGNORE_CASE)

    /** Balance enquiry / available-balance informational notices (not a debit/credit event). */
    private val balanceEnquiryPattern =
        Regex("\\byour available balance is\\b", RegexOption.IGNORE_CASE)

    /** Card activation/blocking/deactivation status notices, distinct from any spend/withdrawal. */
    private val cardStatusPattern =
        Regex("\\bcard\\b.*\\bhas been\\b.*\\b(activated|blocked|deactivated)\\b", RegexOption.IGNORE_CASE)

    /** Marketing/promotional blasts, recognized by the combination of an offer pitch and T&C boilerplate. */
    private val promotionalPattern =
        Regex("\\b(offer|cashback|discount)\\b", RegexOption.IGNORE_CASE) to
            Regex("T&C Apply", RegexOption.IGNORE_CASE)

    /** Explicit failed/declined transaction notices — no money actually moved. */
    private val failedTransactionPattern =
        Regex("\\btransaction\\b.*\\b(has failed|failed|was declined|declined)\\b", RegexOption.IGNORE_CASE)

    /** Successful login notifications for online/mobile banking. */
    private val loginAlertPattern =
        Regex("\\byou have successfully logged in\\b", RegexOption.IGNORE_CASE)

    override fun parse(body: String): ParseResult {
        ignoreReasonFor(body)?.let { return ParseResult.Ignored(it) }

        parseFamilyA(body)?.let { return it }
        parseFamilyB(body)?.let { return it }
        parseFamilyD(body)?.let { return it }

        return ParseResult.Unrecognized
    }

    private fun ignoreReasonFor(body: String): String? {
        if (otpPattern.containsMatchIn(body)) return "otp"
        if (purchaseCodePattern.containsMatchIn(body)) return "purchase code"
        if (passwordOrPinChangedPattern.containsMatchIn(body)) return "password/pin change"
        if (balanceEnquiryPattern.containsMatchIn(body)) return "balance enquiry"
        if (cardStatusPattern.containsMatchIn(body)) return "card status notice"
        if (promotionalPattern.first.containsMatchIn(body) && promotionalPattern.second.containsMatchIn(body)) {
            return "promotional message"
        }
        if (failedTransactionPattern.containsMatchIn(body)) return "failed transaction"
        if (loginAlertPattern.containsMatchIn(body)) return "login alert"
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

        val remarkResult = RemarkParser.parse(remark)

        val remarkTruncated = body.length >= 155 && !remark.endsWith(".")

        return ParseResult.Parsed(
            ParsedTransaction(
                sourceAccount = account,
                amount = amount,
                direction = direction,
                occurredAt = occurredAt,
                remark = remark,
                merchant = remarkResult.merchant,
                balanceAfter = null,
                remarkTruncated = remarkTruncated,
                channel = remarkResult.channel,
                kind = remarkResult.kind
            )
        )
    }

    private fun parseFamilyD(body: String): ParseResult? {
        val match = familyD.matchEntire(body) ?: return null

        val account = match.groups["account"]!!.value
        val directionText = match.groups["direction"]!!.value
        val amountText = match.groups["amount"]!!.value
        val dateText = match.groups["date"]!!.value
        var remark = match.groups["remark"]!!.value.trim()

        val amount = parseAmount(amountText, Currency.NPR) ?: return ParseResult.Unrecognized

        val direction = when (directionText.lowercase()) {
            "debited" -> Direction.DEBIT
            "credited" -> Direction.CREDIT
            else -> return ParseResult.Unrecognized
        }

        val occurredAt = parseFamilyDDate(dateText) ?: return ParseResult.Unrecognized

        remark = remark.replace(bankNameSuffix, "").trim()

        val openParens = remark.count { it == '(' }
        val closeParens = remark.count { it == ')' }
        val remarkTruncated = openParens != closeParens || body.length >= 155

        val remarkResult = RemarkParser.parse(remark)

        return ParseResult.Parsed(
            ParsedTransaction(
                sourceAccount = account,
                amount = amount,
                direction = direction,
                occurredAt = occurredAt,
                remark = remark,
                merchant = remarkResult.merchant,
                balanceAfter = null,
                remarkTruncated = remarkTruncated,
                channel = remarkResult.channel,
                kind = remarkResult.kind
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

    private fun parseFamilyDDate(dateText: String): LocalDateTime? {
        val dateParts = dateText.split("/")
        if (dateParts.size != 3) return null
        val day = dateParts[0].toIntOrNull() ?: return null
        val month = dateParts[1].toIntOrNull() ?: return null
        val year = dateParts[2].toIntOrNull() ?: return null

        return try {
            LocalDateTime(year, month, day, 0, 0)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
