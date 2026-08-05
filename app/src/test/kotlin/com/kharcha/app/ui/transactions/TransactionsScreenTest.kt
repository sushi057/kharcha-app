package com.kharcha.app.ui.transactions

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kharcha.data.CategoryEntity
import com.kharcha.data.TransactionEntity
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.theme.formatMoney
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import com.kharcha.parser.Money
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric JVM test standing in for a device `connectedAndroidTest`, per
 * the task's environment ruling — no emulator is attached in this session.
 * Owed: manual verification on a real device/emulator (see HANDOFF.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TransactionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val foodCategory = CategoryEntity(id = 1L, name = "Food & Dining", colorArgb = 0xFFE8734A.toInt(), isIncome = false, isFee = false)
    private val shoppingCategory = CategoryEntity(id = 3L, name = "Shopping", colorArgb = 0xFFC968A6.toInt(), isIncome = false, isFee = false)

    private val transaction = TransactionEntity(
        id = 1L,
        rawMessageId = 10L,
        sourceAccount = "0###15164761",
        amountMinorUnits = 298400L,
        currency = Currency.NPR,
        direction = Direction.DEBIT,
        occurredAtEpochMillis = 1_754_000_000_000L,
        remark = "QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU",
        merchant = "JAWALAKHEL HANKOOK SARANG RESTAU",
        balanceAfterMinorUnits = 500000L,
        categoryId = foodCategory.id,
        categoryIsManualOverride = false,
        excludedFromSpending = false,
        isManualEntry = false,
    )

    @Test
    fun `transaction row shows merchant, category and formatted amount`() {
        composeRule.setContent {
            KharchaTheme {
                TransactionRow(transaction = transaction, category = foodCategory, onClick = {})
            }
        }

        composeRule.onNodeWithText(transaction.merchant!!).assertExists()
        composeRule.onNodeWithText(foodCategory.name).assertExists()
        composeRule.onNodeWithText(formatMoney(Money(transaction.amountMinorUnits, transaction.currency))).assertExists()
    }

    @Test
    fun `tapping a category in the edit sheet reports it and offers the always-categorize prompt`() {
        var setCategoryCalled: Long? = null
        var alwaysCategorizeMerchant: String? = null
        var alwaysCategorizeCategoryId: Long? = null

        composeRule.setContent {
            KharchaTheme {
                TransactionEditSheetContent(
                    transaction = transaction,
                    categories = listOf(foodCategory, shoppingCategory),
                    onSetCategory = { setCategoryCalled = it },
                    onSetExcluded = {},
                    onSetMerchant = {},
                    onDelete = {},
                    onAlwaysCategorize = { merchant, categoryId ->
                        alwaysCategorizeMerchant = merchant
                        alwaysCategorizeCategoryId = categoryId
                    },
                    onAddManual = { _, _, _, _ -> },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(shoppingCategory.name).assertExists()
        composeRule.onNodeWithText(shoppingCategory.name).performClick()
        composeRule.waitForIdle()
        assert(setCategoryCalled == shoppingCategory.id) { "expected ${shoppingCategory.id}, got $setCategoryCalled" }

        composeRule.onNodeWithText("Always categorize?").assertExists()
        composeRule.onNodeWithText("Yes").performClick()
        composeRule.waitForIdle()
        assert(alwaysCategorizeMerchant == transaction.merchant) {
            "expected ${transaction.merchant}, got $alwaysCategorizeMerchant"
        }
        assert(alwaysCategorizeCategoryId == shoppingCategory.id) {
            "expected ${shoppingCategory.id}, got $alwaysCategorizeCategoryId"
        }
    }
}
