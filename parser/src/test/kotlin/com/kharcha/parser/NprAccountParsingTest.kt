package com.kharcha.parser

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NprAccountParsingTest {

    private fun parsed(body: String): ParsedTransaction {
        val result = SblAlertRuleset.parse(body)
        assertIs<ParseResult.Parsed>(result)
        return result.transaction
    }

    @Test
    fun `parses a QR payment debit`() {
        val txn = parsed(
            "Dear SUVASH, AC 0###15164761, NPR 2,984.00 withdrawn on 17/07/2026 12:10:01 " +
                "for QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU"
        )
        assertEquals("0###15164761", txn.sourceAccount)
        assertEquals(Money(298400L, Currency.NPR), txn.amount)
        assertEquals(Direction.DEBIT, txn.direction)
        assertEquals(LocalDateTime(2026, 7, 17, 12, 10, 1), txn.occurredAt)
        assertEquals("QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU", txn.remark)
        assertEquals("JAWALAKHEL HANKOOK SARANG RESTAU", txn.merchant)
    }

    @Test
    fun `parses an interest credit`() {
        val txn = parsed(
            "Dear SUVASH, AC 0###15164761, NPR 24,920.44 deposited on 17/07/2026 05:41:30 " +
                "for Int.Pd:14-04-2026 to 16-07-2026"
        )
        assertEquals(Direction.CREDIT, txn.direction)
        assertEquals(Money(2492044L, Currency.NPR), txn.amount)
        assertEquals("Int.Pd:14-04-2026 to 16-07-2026", txn.remark)
    }

    @Test
    fun `parses a withholding tax debit`() {
        val txn = parsed(
            "Dear SUVASH, AC 0###15164761, NPR 1,495.23 withdrawn on 17/07/2026 05:41:26 " +
                "for WTax.Pd:14-04-2026to 16-07-2026"
        )
        assertEquals(Direction.DEBIT, txn.direction)
        assertEquals(Money(149523L, Currency.NPR), txn.amount)
    }

    @Test
    fun `parses a transfer charge`() {
        val txn = parsed(
            "Dear SUVASH, AC 0###15164761, NPR 8.00 withdrawn on 03/08/2026 11:32:05 " +
                "for cIPS Fund Trf Charge"
        )
        assertEquals(Money(800L, Currency.NPR), txn.amount)
        assertEquals("cIPS Fund Trf Charge", txn.remark)
    }

    @Test
    fun `parses a large outgoing transfer`() {
        val txn = parsed(
            "Dear SUVASH, AC 0###15164761, NPR 1,015,625.00 withdrawn on 03/08/2026 11:32:05 " +
                "for GLOBAL /Shambhu Nath/"
        )
        assertEquals(Money(101562500L, Currency.NPR), txn.amount)
        assertEquals("GLOBAL /Shambhu Nath/", txn.remark)
    }

    @Test
    fun `flags a truncated remark`() {
        val txn = parsed(
            "Dear SUVASH, AC 0###15164761, NPR 599.00 withdrawn on 17/07/2026 12:26:23 " +
                "for QR Payment to MinisoJK - Sales;Sales"
        )
        assertEquals("MinisoJK - Sales;Sales", txn.merchant)
    }

    @Test
    fun `rejects a matching shape with a bad amount`() {
        assertEquals(
            ParseResult.Unrecognized,
            SblAlertRuleset.parse(
                "Dear SUVASH, AC 0###15164761, NPR ??? withdrawn on 17/07/2026 12:10:01 for QR Payment to X"
            )
        )
    }

    @Test
    fun `rejects a matching shape with an impossible date`() {
        assertEquals(
            ParseResult.Unrecognized,
            SblAlertRuleset.parse(
                "Dear SUVASH, AC 0###15164761, NPR 100.00 withdrawn on 45/45/2026 12:10:01 for QR Payment to X"
            )
        )
    }
}
