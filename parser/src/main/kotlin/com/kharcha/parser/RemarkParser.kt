package com.kharcha.parser

/**
 * Turns a raw SMS remark tail (e.g. `Fund Trf to NABIL BANK LTD (ESEW-9815618427,79578/FUN MOB/SBLMOB01`)
 * into a clean, display-ready result: a friendly merchant/counterparty name, a channel
 * label, and a coarse transaction [Kind].
 *
 * This object never touches the raw remark stored on [ParsedTransaction] — it only
 * derives display fields from it, so it is safe to call speculatively and independently
 * testable without any SMS-parsing machinery.
 *
 * The rail (HOW the money moved: eSewa, connectIPS, IBFT, ATM, ...) and the counterparty
 * (WHO received/sent it) are two independent axes and are detected independently, then
 * composed — collapsing them would make every eSewa transfer show up as one
 * indistinguishable "eSewa" merchant, silently destroying top-merchants ranking,
 * recurring-charge detection, and per-merchant categorization, all of which key on
 * merchant.
 *
 * Recognition strategy:
 *  1. `QR Payment to X` -> merchant X, channel "QR", kind PURCHASE (legacy behavior).
 *  2. Independently:
 *     - a payment rail/wallet token (eSewa, connectIPS, IBFT, Khalti, IME Pay, FonePay,
 *       ATM, POS, or — as an explicit last resort, since it appears in nearly every
 *       Family D remark regardless of the real rail — the generic mobile banking channel
 *       marker) -> `channel` + a default `kind`.
 *     - the counterparty name after `Fund Trf to `/`Fund Trf frm `, title-cased for
 *       readability -> candidate `merchant`. Finding a counterparty also means this is a
 *       TRANSFER, even when no specific rail token was present.
 *  3. Compose: `merchant` = counterparty if found, else the rail's friendly channel name
 *     (so a bare "cIPS Fund Trf frm ..." with no extractable counterparty still shows
 *     something useful), else — for remarks that look like reference-soup transfers
 *     (contain a parenthetical) — a cleaned-up version of the remark with the soup
 *     stripped out, else null. Reference-number soup (`9815618427,79578`, `IN-401573123`,
 *     `SBLMOB01`, ...) never appears in the extracted counterparty because the
 *     counterparty pattern stops at the first parenthetical or truncated reference stub.
 */
object RemarkParser {

    enum class Kind { TRANSFER, PURCHASE, WITHDRAWAL, UNKNOWN }

    data class Result(val merchant: String?, val channel: String?, val kind: Kind)

    private val qrPattern = Regex("^QR Payment to\\s+(.+)$", RegexOption.IGNORE_CASE)

    // Captures the counterparty after "Fund Trf to|frm ", stopping at the first
    // parenthetical reference block, or at a lone-letter-dash reference stub
    // (e.g. " I-/FUN...") that is not part of a real word like "E-PAYMENT".
    private val fundTrfPattern = Regex(
        "Fund Trf (?:to|frm|from)\\s+(.+?)(?:\\s+[A-Za-z]-(?=/|\\s|$)|\\s*\\(|\\s*$)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Specific rail/wallet tokens, checked in order and independent of counterparty
     * extraction. The first token found anywhere in the remark wins.
     */
    private val specificRails: List<Triple<Regex, String, Kind>> = listOf(
        Triple(Regex("\\bESEW\\b", RegexOption.IGNORE_CASE), "eSewa", Kind.TRANSFER),
        Triple(Regex("\\bKHALTI\\b", RegexOption.IGNORE_CASE), "Khalti", Kind.TRANSFER),
        Triple(Regex("\\bIMEPAY\\b", RegexOption.IGNORE_CASE), "IME Pay", Kind.TRANSFER),
        Triple(Regex("\\bFONEPAY\\b", RegexOption.IGNORE_CASE), "FonePay", Kind.TRANSFER),
        Triple(
            Regex("\\bcIPS\\b|\\bSBLIPS\\d*\\b|\\bFUN IPS\\b", RegexOption.IGNORE_CASE),
            "connectIPS",
            Kind.TRANSFER
        ),
        Triple(Regex("\\bIBFT\\b", RegexOption.IGNORE_CASE), "IBFT", Kind.TRANSFER),
        Triple(Regex("\\bATM\\b", RegexOption.IGNORE_CASE), "ATM", Kind.WITHDRAWAL),
        Triple(Regex("\\bPOS\\b", RegexOption.IGNORE_CASE), "POS", Kind.PURCHASE)
    )

    // Generic mobile-banking channel marker. Only ever consulted as a LAST resort — it
    // appears in essentially every Family D remark regardless of which rail actually
    // moved the money, so it must never win over a specific rail like ESEW or IBFT.
    private val mobileBankingFallback = Regex("\\bSBLMOB\\d*\\b|\\bFUN MOB\\b", RegexOption.IGNORE_CASE)

    fun parse(remark: String): Result {
        val text = remark.trim()

        qrPattern.find(text)?.let { match ->
            return Result(match.groupValues[1].trim(), "QR", Kind.PURCHASE)
        }

        var channel: String? = null
        var kind = Kind.UNKNOWN
        for ((pattern, label, railKind) in specificRails) {
            if (pattern.containsMatchIn(text)) {
                channel = label
                kind = railKind
                break
            }
        }
        if (channel == null && mobileBankingFallback.containsMatchIn(text)) {
            channel = "Mobile banking"
            kind = Kind.TRANSFER
        }

        val counterparty = fundTrfPattern.find(text)
            ?.groupValues?.get(1)
            ?.trim()
            ?.let(::stripTrailingRailTokens)
            ?.takeIf { !isGenericLedgerName(it) }
            ?.let { titleCase(it) }
            ?.takeIf { it.isNotBlank() }

        if (counterparty != null && kind == Kind.UNKNOWN) {
            kind = Kind.TRANSFER
        }

        // Last-resort merchant fallback: only attempt to salvage a name out of
        // reference-soup-looking remarks (recognizable by a parenthetical reference
        // block). Plain narrative remarks like "Int.Pd:14-04-2026 to 16-07-2026" are
        // left alone so existing non-transfer Family A behavior is unchanged.
        val soupFallback = if (text.contains("(")) {
            stripReferenceSoup(text).takeIf { it.isNotBlank() }
        } else {
            null
        }

        val merchant = counterparty ?: channel ?: soupFallback

        return Result(merchant, channel, kind)
    }

    /**
     * The rail token trails the payee in the counterparty position — `Fund Trf to NEA
     * ELECTRICITY ESEW` names NEA, not "Nea Electricity Esew". The rail is already
     * captured as the channel, so leaving it on the merchant both misnames the payee and
     * splits one merchant into several: the same shop reached over eSewa and over
     * connectIPS would otherwise be two different names in top-merchants and two
     * different keys for per-merchant rules.
     *
     * Only a *trailing* token is stripped. A token in the middle of a name is left alone,
     * because there the name is what is unusual, not the token.
     */
    private val trailingRailToken = Regex(
        "\\s+(?:ESEW|KHALTI|IMEPAY|FONEPAY|cIPS|SBLIPS\\d*|IBFT|ATM|POS|SBLMOB\\d*|FUN (?:IPS|MOB))$",
        RegexOption.IGNORE_CASE,
    )

    private fun stripTrailingRailTokens(counterparty: String): String {
        var result = counterparty.trim()
        while (true) {
            val stripped = trailingRailToken.replace(result, "").trim()
            if (stripped == result) return result
            result = stripped
        }
    }

    /**
     * The bank's own internal ledger accounts, which appear in the counterparty position
     * but name no real payee. `Fund Trf to A/C PAYABLE IBFT (...)` is not a transfer to
     * someone called "A/c Payable Ibft" — it is an IBFT transfer whose recipient the SMS
     * simply never states.
     *
     * Rejecting these lets composition fall through to the rail's friendly name, so the
     * row reads "IBFT" rather than an internal accounting term the user has never seen.
     * Matching is on the raw pre-title-cased text, since these always arrive upper-case.
     */
    private val genericLedgerNames = listOf(
        Regex("^A/C\\s+PAYABLE\\b", RegexOption.IGNORE_CASE),
        Regex("^A/C\\s+RECEIVABLE\\b", RegexOption.IGNORE_CASE),
        Regex("^SUNDRY\\b", RegexOption.IGNORE_CASE),
        Regex("^SUSPENSE\\b", RegexOption.IGNORE_CASE),
    )

    private fun isGenericLedgerName(text: String): Boolean =
        genericLedgerNames.any { it.containsMatchIn(text.trim()) }

    private fun stripReferenceSoup(text: String): String {
        var cleaned = text
        cleaned = cleaned.replace(Regex("\\([^)]*\\)?"), " ") // parenthetical, balanced or not
        cleaned = cleaned.replace(Regex("\\b[A-Za-z]{1,4}-\\d+\\b"), " ") // e.g. IN-401573123
        cleaned = cleaned.replace(Regex("\\b\\d[\\d,]{3,}\\b"), " ") // long reference numbers
        cleaned = cleaned.replace(Regex("/[A-Za-z ]+\\d*"), " ") // /FUN MOB/SBLMOB01
        cleaned = cleaned.replace(Regex("\\s{2,}"), " ").trim()
        return cleaned
    }

    private fun titleCase(text: String): String {
        return text.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}
