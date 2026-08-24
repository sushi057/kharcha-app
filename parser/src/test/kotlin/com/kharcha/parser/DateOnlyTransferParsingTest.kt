package com.kharcha.parser

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Family D: date-only "SBL AC ... debited/credited by NPR ... on dd/mm/yyyy for <remark>
 * Siddhartha Bank" alerts. These have no time component and are frequently truncated
 * mid-remark because they run up against the SMS length boundary.
 */
class DateOnlyTransferParsingTest {

    private fun parsed(body: String): ParsedTransaction {
        val result = SblAlertRuleset.parse(body)
        assertIs<ParseResult.Parsed>(result)
        return result.transaction
    }

    @Test
    fun `parses a debited fund transfer with eSewa rail`() {
        val txn = parsed(
            "SBL AC 0###15164761 debited by NPR 260.00 on 02/12/2024 for Fund Trf to NABIL BANK LTD " +
                "(ESEW-9815618427,79578/FUN MOB/SBLMOB01 Siddhartha Bank"
        )
        assertEquals("0###15164761", txn.sourceAccount)
        assertEquals(Money(26000L, Currency.NPR), txn.amount)
        assertEquals(Direction.DEBIT, txn.direction)
        assertEquals(LocalDateTime(2024, 12, 2, 0, 0), txn.occurredAt)
        assertEquals("Nabil Bank Ltd", txn.merchant)
        assertEquals("eSewa", txn.channel)
        assertTrue(txn.remarkTruncated)
        assertTrue(!txn.remark.contains("Siddhartha Bank"))
    }

    @Test
    fun `parses a credited connectIPS fund transfer with thousands separator`() {
        val txn = parsed(
            "SBL AC 0###15164761 credited by NPR 50,000.00 on 02/12/2024 for cIPS Fund Trf frm IPS " +
                "E-PAYMENT I-/FUN IPS/SBLIPS02 Siddhartha Bank"
        )
        assertEquals(Direction.CREDIT, txn.direction)
        assertEquals(Money(5000000L, Currency.NPR), txn.amount)
        assertEquals("Ips E-payment", txn.merchant)
        assertEquals("connectIPS", txn.channel)
    }

    @Test
    fun `parses a debited IBFT transfer and strips bank suffix`() {
        val txn = parsed(
            "SBL AC 0###15164761 debited by NPR 150.00 on 01/12/2024 for Fund Trf to A/C PAYABLE IBFT " +
                "(IN-401573123,Mobile/FUN MOB/SBLMOB01 Siddhartha Bank"
        )
        assertEquals(Direction.DEBIT, txn.direction)
        assertEquals(Money(15000L, Currency.NPR), txn.amount)
        assertEquals("IBFT", txn.merchant)
        assertEquals("IBFT", txn.channel)
        assertTrue(txn.remarkTruncated)
    }

    @Test
    fun `rejects a family D shape with an impossible date`() {
        assertEquals(
            ParseResult.Unrecognized,
            SblAlertRuleset.parse(
                "SBL AC 0###15164761 debited by NPR 100.00 on 45/45/2024 for Fund Trf to X Siddhartha Bank"
            )
        )
    }

    // --- Regression: existing families still parse correctly ---

    @Test
    fun `family A QR payment still parses`() {
        val txn = parsed(
            "Dear SUVASH, AC 0###15164761, NPR 2,984.00 withdrawn on 17/07/2026 12:10:01 " +
                "for QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU"
        )
        assertEquals("JAWALAKHEL HANKOOK SARANG RESTAU", txn.merchant)
        assertEquals(Direction.DEBIT, txn.direction)
    }

    @Test
    fun `family B USD card purchase still parses`() {
        val result = SblAlertRuleset.parse(
            "SBL Card ***5367 used at SPACESHIP.COM* NRXD3L, US for USD 1.98 on 02.08.26 23:28 " +
                "Authid 512208 Remaining Balance after txn USD 241.22. INFO 015970020"
        )
        assertIs<ParseResult.Parsed>(result)
        assertEquals("SPACESHIP.COM* NRXD3L, US", result.transaction.merchant)
    }

    @Test
    fun `family D does not shadow family A dear-customer message`() {
        val result = SblAlertRuleset.parse(
            "Dear SUVASH, AC 0###15164761, NPR 8.00 withdrawn on 03/08/2026 11:32:05 for cIPS Fund Trf Charge"
        )
        assertIs<ParseResult.Parsed>(result)
    }

    @Test
    fun `OTP is still ignored ahead of family D`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse(
                "288388 is your OTP to get CVV for your Virtual eCom Card. " +
                    "Please do not share this OTP with others."
            )
        )
    }
}
