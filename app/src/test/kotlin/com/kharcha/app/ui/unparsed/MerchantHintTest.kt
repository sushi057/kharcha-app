package com.kharcha.app.ui.unparsed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `UnparsedScreen` prefilled the Merchant field with the *entire* raw SMS body, which
 * was then written verbatim to both `merchant` and `remark` (reviewer's cheap finding on
 * `UnparsedScreen.kt:91`).
 */
class MerchantHintTest {

    private val body = "Dear SUVASH, AC 0###15164761, NPR 2984.00 withdrawn on 03/08/2026 11:32:05 " +
        "for QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU"

    @Test
    fun `a raw SBL alert body is trimmed to a short single-line hint`() {
        val hint = merchantHintFrom(body)
        assertTrue(hint.length <= 40, "merchant hint was ${hint.length} chars: '$hint'")
        assertTrue(!hint.contains('\n'), "merchant hint must be single-line")
        assertTrue(hint.isNotBlank())
    }

    @Test
    fun `whitespace is collapsed and a short body is passed through unchanged`() {
        assertEquals("Corner Store", merchantHintFrom("  Corner\n  Store  "))
    }
}
