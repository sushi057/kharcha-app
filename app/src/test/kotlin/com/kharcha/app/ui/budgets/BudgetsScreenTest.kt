package com.kharcha.app.ui.budgets

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.theme.formatMoney
import com.kharcha.parser.Currency
import com.kharcha.parser.Money
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Robolectric JVM test standing in for a device `connectedAndroidTest`, per the task's
 * environment ruling — no emulator is attached in this session.
 *
 * Regression coverage for the reviewer's Critical finding: [BudgetsViewModel] can now
 * legitimately emit two [BudgetRow]s that share a `categoryId` (one per currency with
 * spend or a budget). Before the fix, `LazyColumn`'s `key = { it.categoryId }` collided on
 * exactly that scenario and Compose threw `IllegalArgumentException("Key ... was already
 * used")` at runtime — a crash that never showed up in [BudgetsViewModelTest] because that
 * suite never renders Compose UI. This test renders [BudgetsScreenContent] directly (no
 * Hilt `hiltViewModel()` call) with a category that has both an NPR and a USD row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BudgetsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val nprRow = BudgetRow(
        categoryId = 1L,
        categoryName = "Food & Dining",
        colorArgb = 0xFFE8734A.toInt(),
        currency = Currency.NPR,
        budgetId = 1L,
        limitMinorUnits = 500_00L,
        spentMinorUnits = 300_00L,
        alertThresholdPercent = 80,
    )

    private val usdRow = BudgetRow(
        categoryId = 1L,
        categoryName = "Food & Dining",
        colorArgb = 0xFFE8734A.toInt(),
        currency = Currency.USD,
        budgetId = null,
        limitMinorUnits = null,
        spentMinorUnits = 75_00L,
        alertThresholdPercent = BudgetsViewModel.DEFAULT_THRESHOLD_PERCENT,
    )

    @Test
    fun `a category with both an NPR and a USD row renders both without crashing`() {
        composeRule.setContent {
            KharchaTheme {
                BudgetsScreenContent(
                    state = BudgetsUiState(rows = listOf(nprRow, usdRow)),
                    onSetBudget = { _, _, _, _ -> },
                    onDeleteBudget = {},
                )
            }
        }
        composeRule.waitForIdle()

        // The category name legitimately appears twice — once per currency row — proving
        // both rows actually rendered rather than one silently replacing the other.
        composeRule.onAllNodesWithText("Food & Dining").assertCountEquals(2)

        composeRule.onNodeWithText(formatMoney(Money(nprRow.spentMinorUnits, Currency.NPR))).assertExists()
        composeRule.onNodeWithText(formatMoney(Money(usdRow.spentMinorUnits, Currency.USD))).assertExists()

        // The NPR row's budget must never leak onto the USD row.
        composeRule.onNodeWithText("of ${formatMoney(Money(nprRow.limitMinorUnits!!, Currency.NPR))}").assertExists()
        composeRule.onNodeWithText("No budget set").assertExists()
    }

    /**
     * Reviewer's cheap finding on `BudgetsScreen.kt:188`: the edit dialog rendered an
     * existing limit as `(it / 100)`, so an NPR 500.50 limit displayed as "500" and Save
     * silently rewrote it to NPR 500.00 — money losing its minor units on a round trip
     * through the UI.
     */
    @Test
    fun `the edit dialog shows a sub-rupee limit without truncating it`() {
        val subRupeeRow = nprRow.copy(limitMinorUnits = 500_50L)
        composeRule.setContent {
            KharchaTheme {
                BudgetsScreenContent(
                    state = BudgetsUiState(rows = listOf(subRupeeRow)),
                    onSetBudget = { _, _, _, _ -> },
                    onDeleteBudget = {},
                )
            }
        }
        composeRule.onNodeWithText(subRupeeRow.categoryName).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("500.50").assertExists()
    }

    @Test
    fun `saving a sub-rupee limit round-trips the minor units`() {
        var savedLimit: Long? = null
        val subRupeeRow = nprRow.copy(limitMinorUnits = 500_50L)
        composeRule.setContent {
            KharchaTheme {
                BudgetsScreenContent(
                    state = BudgetsUiState(rows = listOf(subRupeeRow)),
                    onSetBudget = { _, limit, _, _ -> savedLimit = limit },
                    onDeleteBudget = {},
                )
            }
        }
        composeRule.onNodeWithText(subRupeeRow.categoryName).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        assertEquals(500_50L, savedLimit, "re-saving an untouched limit must not change it")
    }

    /**
     * Reviewer's cheap finding on `BudgetsScreen.kt:213-220`: Save with invalid input was a
     * silent no-op — the dialog stayed open with no explanation of why nothing happened.
     */
    @Test
    fun `saving invalid input shows an error instead of doing nothing`() {
        var saveCalled = false
        composeRule.setContent {
            KharchaTheme {
                BudgetsScreenContent(
                    state = BudgetsUiState(rows = listOf(nprRow)),
                    onSetBudget = { _, _, _, _ -> saveCalled = true },
                    onDeleteBudget = {},
                )
            }
        }
        composeRule.onNodeWithText(nprRow.categoryName).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("500").performTextClearance()
        composeRule.onNodeWithText("Monthly limit (NPR)").performTextInput("not a number")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        assertFalse(saveCalled, "an invalid limit must not be saved")
        composeRule.onNodeWithText(BUDGET_INVALID_INPUT_MESSAGE).assertExists()
    }
}
