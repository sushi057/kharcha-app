package com.kharcha.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IgnoredMessageTest {
    @Test
    fun `ignores OTP messages`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse(
                "288388 is your OTP to get CVV for your Virtual eCom Card. " +
                    "Please do not share this OTP with others."
            )
        )
    }

    @Test
    fun `ignores purchase code messages`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse("Your purchase code at 02/08/2026 23:27:46 of 1.98 USD is 338558")
        )
    }

    @Test
    fun `ignores password changed messages`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse("Dear Customer, your Internet Banking password has been changed successfully.")
        )
    }

    @Test
    fun `ignores PIN reset messages`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse("Dear Customer, your Debit Card PIN has been reset successfully.")
        )
    }

    @Test
    fun `ignores balance enquiry messages`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse("Dear SUVASH, AC 0###15164761, your available balance is NPR 12,345.00 as of 06/08/2026.")
        )
    }

    @Test
    fun `ignores card activation messages`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse("Dear Customer, your Debit Card ***5367 has been activated successfully.")
        )
    }

    @Test
    fun `ignores card blocked messages`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse("Dear Customer, your Debit Card ***5367 has been blocked as per your request.")
        )
    }

    @Test
    fun `ignores promotional messages`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse(
                "Dear Customer, enjoy 20% cashback offer on all POS transactions this Dashain! T&C Apply."
            )
        )
    }

    @Test
    fun `ignores failed transaction messages`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse("Dear Customer, your transaction of NPR 500.00 has failed due to insufficient balance.")
        )
    }

    @Test
    fun `ignores declined transaction messages`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse("Dear Customer, your card transaction of USD 10.00 was declined.")
        )
    }

    @Test
    fun `ignores login alert messages`() {
        assertIs<ParseResult.Ignored>(
            SblAlertRuleset.parse("Dear Customer, you have successfully logged in to SBL Mobile Banking just now.")
        )
    }

    @Test
    fun `an unknown message is unrecognized, not ignored`() {
        assertEquals(
            ParseResult.Unrecognized,
            SblAlertRuleset.parse("Your account statement is ready for collection.")
        )
    }

    @Test
    fun `a real debit message is not accidentally ignored by the new ignore rules`() {
        val result = SblAlertRuleset.parse(
            "Dear SUVASH, AC 0###15164761, NPR 599.00 withdrawn on 17/07/2026 12:26:23 " +
                "for QR Payment to MinisoJK - Sales;Sales"
        )
        assertIs<ParseResult.Parsed>(result)
    }

    @Test
    fun `code-last OTP phrasing is ignored, not left for review`() {
        val result = SblAlertRuleset.parse(
            "Your OTP for Siddhartha Bank Mobile Banking is 483920. Do not share this with anyone."
        )
        assertTrue(result is ParseResult.Ignored, "expected Ignored but was $result")
        assertEquals("otp", (result as ParseResult.Ignored).reason)
    }

    @Test
    fun `code-first OTP phrasing keeps working`() {
        val result = SblAlertRuleset.parse("483920 is your OTP for fund transfer. Valid for 5 minutes.")
        assertTrue(result is ParseResult.Ignored, "expected Ignored but was $result")
        assertEquals("otp", (result as ParseResult.Ignored).reason)
    }

    @Test
    fun `one-time password spelled out is ignored`() {
        val result = SblAlertRuleset.parse("Your one time password is 118822. Do not share it.")
        assertTrue(result is ParseResult.Ignored, "expected Ignored but was $result")
    }

    @Test
    fun `a real debit alert is never mistaken for an OTP`() {
        val result = SblAlertRuleset.parse(
            "Dear SUVASH, AC 0###15164761, NPR 275.00 withdrawn on 06/08/2026 08:14:55 " +
                "for QR Payment to HIMALAYAN JAVA COFFEE"
        )
        assertTrue(result is ParseResult.Parsed, "expected Parsed but was $result")
    }
}
