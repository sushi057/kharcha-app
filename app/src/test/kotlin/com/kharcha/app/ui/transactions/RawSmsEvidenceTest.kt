package com.kharcha.app.ui.transactions

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The evidence panel has to show the SMS, not the parser's excerpt of it.
 *
 * `remark` is what the parser pulled out — "QR Payment to X" — so a panel labelled
 * "Raw SMS (evidence)" that renders `remark` is showing the conclusion as if it were
 * the evidence. The full body is the only thing that can settle "is this reference
 * number the one on my receipt?", which is the reason to open the panel at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RawSmsEvidenceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fullBody =
        "Dear Customer, your acct 0###15164761 is debited by NPR 2,984.00 on 01-08-2025 " +
            "for QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU. Ref: 987654321012. " +
            "Bal: NPR 41,233.19. -Siddhartha Bank"

    private val transaction = TransactionEntity(
        id = 1L,
        rawMessageId = 7L,
        sourceAccount = "0###15164761",
        amountMinorUnits = 298_400L,
        currency = Currency.NPR,
        direction = Direction.DEBIT,
        occurredAtEpochMillis = 1_754_000_000_000L,
        remark = "QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU",
        merchant = "JAWALAKHEL HANKOOK SARANG RESTAU",
        balanceAfterMinorUnits = 4_123_319L,
        categoryId = 1L,
        categoryIsManualOverride = false,
        excludedFromSpending = false,
        isManualEntry = false,
    )

    private fun content(rawSmsBody: String?) {
        composeRule.setContent {
            KharchaTheme {
                TransactionEditSheetContent(
                    transaction = transaction,
                    categories = emptyList(),
                    onSetCategory = {},
                    onSetExcluded = {},
                    onSetMerchant = {},
                    onDelete = {},
                    onAlwaysCategorize = { _, _ -> },
                    onAddManual = { _, _, _, _ -> },
                    onDismiss = {},
                    rawSmsBody = rawSmsBody,
                )
            }
        }
    }

    @Test
    fun `expanding the evidence panel shows the whole SMS, not the parsed remark`() {
        content(rawSmsBody = fullBody)

        composeRule.onNodeWithText("Raw SMS (evidence)").performClick()

        composeRule.onNodeWithText(fullBody, substring = false).assertExists()
    }

    @Test
    fun `a hand-entered transaction says so instead of showing a message it never had`() {
        content(rawSmsBody = null)

        composeRule.onNodeWithText("Raw SMS (evidence)").performClick()

        composeRule.onNodeWithText("No original message — entered by hand.").assertExists()
    }
}
