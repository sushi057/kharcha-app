package com.kharcha.app.ui.transactions

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.data.CategoryEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression coverage for the reviewer's Important 1: in manual-entry mode
 * (`transaction == null`) the category chips were inert — their `onClick` body was
 * wrapped in `if (transaction != null)` — and Save hardcoded
 * `categories.firstOrNull()?.id`. Every hand-entered transaction was therefore filed
 * under the first seed category (Food & Dining) *and* flagged
 * `categoryIsManualOverride = true`, making it permanently immune to re-parse and to
 * any rule the user later wrote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ManualEntryCategoryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val food = CategoryEntity(id = 1L, name = "Food & Dining", colorArgb = 0xFFE8734A.toInt(), isIncome = false, isFee = false)
    private val transport = CategoryEntity(id = 2L, name = "Transport", colorArgb = 0xFF4A90D8.toInt(), isIncome = false, isFee = false)

    private fun content(onAddManual: (Long, String, String, Long?) -> Unit) {
        composeRule.setContent {
            KharchaTheme {
                TransactionEditSheetContent(
                    transaction = null,
                    categories = listOf(food, transport),
                    onSetCategory = {},
                    onSetExcluded = {},
                    onSetMerchant = {},
                    onDelete = {},
                    onAlwaysCategorize = { _, _ -> },
                    onAddManual = onAddManual,
                    onDismiss = {},
                    initialMerchantText = "Corner Store",
                    initialAmountText = "500",
                )
            }
        }
    }

    @Test
    fun `tapping a category in manual entry mode saves that category, not the first one`() {
        var savedCategoryId: Long? = null
        var savedAmount: Long? = null
        content { amount, _, _, categoryId ->
            savedAmount = amount
            savedCategoryId = categoryId
        }

        composeRule.onNodeWithText("Transport").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        assertEquals(500_00L, savedAmount)
        assertEquals(transport.id, savedCategoryId, "the chip the user tapped must be the category that is saved")
    }

    @Test
    fun `saving without choosing a category reports no category rather than defaulting`() {
        var savedCategoryId: Long? = food.id
        var called = false
        content { _, _, _, categoryId ->
            called = true
            savedCategoryId = categoryId
        }

        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        assert(called) { "Save should have invoked onAddManual" }
        assertNull(
            savedCategoryId,
            "an untouched category picker must not silently pick the first seed category",
        )
    }
}
