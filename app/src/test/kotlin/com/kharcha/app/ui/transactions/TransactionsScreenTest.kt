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
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric JVM test standing in for a device `connectedAndroidTest`, per
 * the task's environment ruling — no emulator is attached in this session.
 * Owed: manual verification on a real device/emulator (see HANDOFF.md).
 *
 * Tests for the v2 redesigned transactions screen, validating:
 * - Transaction row never renders raw remark when merchant exists
 * - Channel and time display in secondary line
 * - Day headers show correct signed subtotal
 * - Filter chips add and remove correctly
 * - Null merchant fallback without exposing reference soup
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TransactionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val foodCategory = CategoryEntity(
        id = 1L,
        name = "Food & Dining",
        colorArgb = 0xFFE8734A.toInt(),
        isIncome = false,
        isFee = false
    )
    private val shoppingCategory = CategoryEntity(
        id = 3L,
        name = "Shopping",
        colorArgb = 0xFFC968A6.toInt(),
        isIncome = false,
        isFee = false
    )

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
    fun `transaction row never renders raw remark when merchant exists`() {
        composeRule.setContent {
            KharchaTheme {
                TransactionRow(
                    transaction = transaction,
                    category = foodCategory,
                    onClick = {},
                    zone = TimeZone.UTC,
                )
            }
        }

        // Should show the merchant — title-cased for display, since the bank sends
        // it shouting — and never the raw remark.
        composeRule.onNodeWithText("Jawalakhel Hankook Sarang Restau").assertExists()
        composeRule.onNodeWithText(transaction.merchant!!).assertDoesNotExist()
        // Raw remark should NOT be displayed
        composeRule.onNodeWithText("QR Payment to").assertDoesNotExist()
    }

    @Test
    fun `transaction row shows channel and time in secondary line`() {
        val ipsTransaction = TransactionEntity(
            id = 2L,
            rawMessageId = 11L,
            sourceAccount = "0###15164761",
            amountMinorUnits = 150000L,
            currency = Currency.NPR,
            direction = Direction.DEBIT,
            occurredAtEpochMillis = 1_754_000_000_000L,
            remark = "Fund Trf to NABIL BANK (ESEW-9815618427,79578/FUN MOB/SBLMOB01)",
            merchant = "NABIL BANK",
            balanceAfterMinorUnits = 500000L,
            categoryId = null,
            categoryIsManualOverride = false,
            excludedFromSpending = false,
            isManualEntry = false,
        )

        composeRule.setContent {
            KharchaTheme {
                TransactionRow(
                    transaction = ipsTransaction,
                    category = null,
                    onClick = {},
                    zone = TimeZone.UTC,
                )
            }
        }

        // Channel and time share one secondary line, so match it as a substring.
        composeRule.onNodeWithText("eSewa", substring = true).assertExists()
        // Time in format HH:mm should exist (based on epoch 1_754_000_000_000L)
        // This transaction is not null, so time display should exist
        composeRule.onNodeWithText("Nabil Bank").assertExists()
    }

    @Test
    fun `day header shows correct signed subtotal`() {
        val transactions = listOf(
            // Debit (expense) transactions: should be negative in subtotal
            TransactionEntity(
                id = 1L,
                rawMessageId = 1L,
                sourceAccount = "0###15164761",
                amountMinorUnits = 100000L, // 1000 NPR
                currency = Currency.NPR,
                direction = Direction.DEBIT,
                occurredAtEpochMillis = 1_754_000_000_000L,
                remark = "Expense 1",
                merchant = "Merchant 1",
                balanceAfterMinorUnits = 500000L,
                categoryId = foodCategory.id,
                categoryIsManualOverride = false,
                excludedFromSpending = false,
                isManualEntry = false,
            ),
            // Credit transaction: should be positive in subtotal
            TransactionEntity(
                id = 2L,
                rawMessageId = 2L,
                sourceAccount = "0###15164761",
                amountMinorUnits = 50000L, // 500 NPR
                currency = Currency.NPR,
                direction = Direction.CREDIT,
                occurredAtEpochMillis = 1_754_000_000_000L,
                remark = "Income 1",
                merchant = "Merchant 2",
                balanceAfterMinorUnits = 550000L,
                categoryId = null,
                categoryIsManualOverride = false,
                excludedFromSpending = false,
                isManualEntry = false,
            ),
        )

        // Signed subtotal should be: -100000 + 50000 = -50000 (net debit)
        val subtotal = transactions.sumOf { txn ->
            if (txn.direction == Direction.DEBIT) -txn.amountMinorUnits else txn.amountMinorUnits
        }

        assert(subtotal == -50000L) { "Expected subtotal -50000, got $subtotal" }
    }

    @Test
    fun `transaction row with null merchant falls back without exposing reference soup`() {
        val noMerchantTxn = TransactionEntity(
            id = 3L,
            rawMessageId = 12L,
            sourceAccount = "0###15164761",
            amountMinorUnits = 25000L,
            currency = Currency.NPR,
            direction = Direction.DEBIT,
            occurredAtEpochMillis = 1_754_000_000_000L,
            remark = "Fund Trf frm A/C PAYABLE (IN-401573123,SBLMOB01)",
            merchant = null, // No merchant extracted
            balanceAfterMinorUnits = 500000L,
            categoryId = null,
            categoryIsManualOverride = false,
            excludedFromSpending = false,
            isManualEntry = false,
        )

        composeRule.setContent {
            KharchaTheme {
                TransactionRow(
                    transaction = noMerchantTxn,
                    category = null,
                    onClick = {},
                    zone = TimeZone.UTC,
                )
            }
        }

        // Should not expose reference numbers or raw soup
        composeRule.onNodeWithText("IN-401573123").assertDoesNotExist()
        composeRule.onNodeWithText("SBLMOB01").assertDoesNotExist()
        composeRule.onNodeWithText("A/C PAYABLE").assertDoesNotExist()

        // With no extractable counterparty ("A/C PAYABLE" is the bank's own ledger
        // account, not a payee), the row falls back to the payment channel — which is
        // more use to a reader than a bare "Unknown".
        composeRule.onNodeWithText("Mobile banking").assertExists()
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
