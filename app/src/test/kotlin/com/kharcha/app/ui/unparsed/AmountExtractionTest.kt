package com.kharcha.app.ui.unparsed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The amount the Add sheet is prefilled with, read straight off an unparsed alert.
 *
 * The bank does not write money one way. `NPR 1,234.56` is the format the parser was
 * built for, but the messages that end up in the inbox — the ones the parser could not
 * place — are exactly the ones that write it some other way, and `Rs.` is by far the
 * most common of those.
 */
class AmountExtractionTest {

    @Test
    fun `reads the NPR form`() {
        assertEquals("1234.56", extractAmountForEntry("Your acct debited NPR 1,234.56 on 02/12"))
    }

    @Test
    fun `reads Rs with a dot and no space`() {
        assertEquals("500", extractAmountForEntry("Payment of Rs.500 made to ESEWA"))
    }

    @Test
    fun `reads RS uppercase with a space`() {
        assertEquals("2500.75", extractAmountForEntry("RS 2,500.75 transferred successfully"))
    }

    @Test
    fun `reads the NRs and INR forms`() {
        assertEquals("900", extractAmountForEntry("NRs. 900 debited"))
        assertEquals("1200.00", extractAmountForEntry("INR 1,200.00 spent on card"))
    }

    @Test
    fun `prefers the transacted amount over the balance that follows it`() {
        val body = "Rs.350 debited from your acct. Bal: NPR 12,400.00"
        assertEquals("350", extractAmountForEntry(body))
    }

    @Test
    fun `skips a balance when it is the only amount written`() {
        assertNull(extractAmountForEntry("Your available balance is NPR 12,400.00"))
    }

    @Test
    fun `does not read rs inside a word as a currency marker`() {
        assertNull(extractAmountForEntry("Valid for 24hrs 500 only"))
    }

    @Test
    fun `returns null when there is no amount at all`() {
        assertNull(extractAmountForEntry("Your password was changed successfully."))
    }

    @Test
    fun `the prefill round-trips through the amount field parser`() {
        // Whatever this returns is typed straight into the Add sheet, so it must be a
        // value that sheet can parse — no separators, no currency, at most two decimals.
        val prefill = extractAmountForEntry("Rs. 1,234.5 debited")
        assertEquals("1234.5", prefill)
        assertEquals(123450L, com.kharcha.app.ui.theme.parseAmountMinorUnits(prefill!!))
    }

    @Test
    fun `the display form keeps the sign and the separators`() {
        val credited = extractFields("NPR 1,234.56 credited to your account")
        assertEquals("+1,234.56", credited.amountText)
        assertEquals("1234.56", credited.amountForEntry)

        val debited = extractFields("Rs.500 debited")
        assertEquals("−500.00", debited.amountText)
    }
}
