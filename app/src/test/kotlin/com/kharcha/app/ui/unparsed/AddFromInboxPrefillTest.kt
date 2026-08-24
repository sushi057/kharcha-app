package com.kharcha.app.ui.unparsed

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.transactions.TransactionEditSheetContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * End to end for "press Add on an inbox card and the rupees are already in the field":
 * the extracted string has to survive being handed to the sheet and saved, not merely
 * be correct in isolation. A prefill the amount field cannot parse is worse than none —
 * Save silently does nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddFromInboxPrefillTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun addFrom(body: String): Long? {
        var saved: Long? = null
        composeRule.setContent {
            KharchaTheme {
                TransactionEditSheetContent(
                    transaction = null,
                    categories = emptyList(),
                    onSetCategory = {},
                    onSetExcluded = {},
                    onSetMerchant = {},
                    onDelete = {},
                    onAlwaysCategorize = { _, _ -> },
                    onAddManual = { amount, _, _, _ -> saved = amount },
                    onDismiss = {},
                    initialMerchantText = merchantHintFrom(body),
                    initialAmountText = extractAmountForEntry(body).orEmpty(),
                )
            }
        }
        composeRule.onNodeWithText("Save").performClick()
        return saved
    }

    @Test
    fun `an Rs amount arrives in the sheet and saves without being retyped`() {
        assertEquals(50_000L, addFrom("Payment of Rs.500 made to ESEWA on 02/12"))
    }

    @Test
    fun `an NPR amount with separators and paisa saves exactly`() {
        assertEquals(123_456L, addFrom("Your acct debited NPR 1,234.56 on 02/12"))
    }

    @Test
    fun `a message with no amount leaves the field empty rather than guessing`() {
        assertEquals(null, addFrom("Your password was changed successfully."))
    }
}
