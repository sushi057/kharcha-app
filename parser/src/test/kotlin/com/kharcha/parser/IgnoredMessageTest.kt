package com.kharcha.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
    fun `an unknown message is unrecognized, not ignored`() {
        assertEquals(
            ParseResult.Unrecognized,
            SblAlertRuleset.parse("Your account statement is ready for collection.")
        )
    }
}
