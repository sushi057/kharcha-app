package com.kharcha.app.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.MoneyText
import com.kharcha.app.ui.theme.formatMinorUnitsPlain
import com.kharcha.app.ui.theme.parseAmountMinorUnits
import com.kharcha.parser.Currency
import com.kharcha.parser.Money

/**
 * Thin [BudgetsViewModel]-wired shell. All layout and interaction lives in
 * [BudgetsScreenContent], which takes plain state and callbacks so it is directly
 * Robolectric-testable without a Hilt-backed `hiltViewModel()` call — see [BudgetsScreenTest]
 * (matching [com.kharcha.app.ui.transactions.TransactionEditSheetContent]'s split).
 */
@Composable
fun BudgetsScreen(
    modifier: Modifier = Modifier,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    BudgetsScreenContent(
        state = state,
        onSetBudget = viewModel::setBudget,
        onDeleteBudget = viewModel::deleteBudget,
        modifier = modifier,
    )
}

/**
 * One row per (category, currency) — a category with spend or a budget in both NPR and
 * USD gets two independent rows, never merged or summed. Over-budget is signalled with an
 * explicit "Over budget" label, not color alone — the debit accent is a reinforcement, not
 * the only signal. No card-in-card nesting: rows are plain, divider-separated, matching
 * [com.kharcha.app.ui.dashboard.DashboardScreen].
 *
 * The `LazyColumn` key is `(categoryId, currency)`, not `categoryId` alone — two rows can
 * legitimately share a `categoryId` now, and Compose requires unique keys per item.
 * Because each [BudgetRow] itself carries its own `currency`, tapping a row and saving
 * always targets that exact (category, currency) pair — see [BudgetsViewModel.setBudget]
 * for the matching fix on the persistence side (it used to match by `categoryId` alone,
 * which could clobber a different currency's budget for the same category).
 */
@Composable
fun BudgetsScreenContent(
    state: BudgetsUiState,
    onSetBudget: (categoryId: Long, limitMinorUnits: Long, currency: Currency, alertThresholdPercent: Int) -> Unit,
    onDeleteBudget: (budgetId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingRow by remember { mutableStateOf<BudgetRow?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = KharchaSpacing.md, vertical = KharchaSpacing.md),
    ) {
        item {
            Text(
                text = "Budgets",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(KharchaSpacing.md))
        }

        items(state.rows, key = { "${it.categoryId}-${it.currency}" }) { row ->
            BudgetRowItem(row = row, onClick = { editingRow = row })
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }

    val rowBeingEdited = editingRow
    if (rowBeingEdited != null) {
        BudgetEditDialog(
            row = rowBeingEdited,
            onDismiss = { editingRow = null },
            onSave = { limitMinorUnits, thresholdPercent ->
                onSetBudget(rowBeingEdited.categoryId, limitMinorUnits, rowBeingEdited.currency, thresholdPercent)
                editingRow = null
            },
            onRemove = rowBeingEdited.budgetId?.let { budgetId ->
                {
                    onDeleteBudget(budgetId)
                    editingRow = null
                }
            },
        )
    }
}

@Composable
private fun BudgetRowItem(row: BudgetRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KharchaSpacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = row.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (row.isOverBudget) {
                    Text(
                        text = "Over budget",
                        style = MaterialTheme.typography.labelMedium,
                        color = KharchaSemantics.debit,
                    )
                }
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                MoneyText(
                    money = Money(row.spentMinorUnits, row.currency),
                    color = if (row.isOverBudget) KharchaSemantics.debit else MaterialTheme.colorScheme.onSurface,
                )
                if (row.limitMinorUnits != null) {
                    Text(
                        text = "of ${com.kharcha.app.ui.theme.formatMoney(Money(row.limitMinorUnits, row.currency))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "No budget set",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (row.limitMinorUnits != null && row.limitMinorUnits > 0) {
            Spacer(modifier = Modifier.height(KharchaSpacing.xs))
            val progress = (row.spentMinorUnits.toFloat() / row.limitMinorUnits.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (row.isOverBudget) KharchaSemantics.debit else KharchaSemantics.credit,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

/** Shown when Save is pressed with input the dialog cannot turn into a budget. */
const val BUDGET_INVALID_INPUT_MESSAGE = "Enter a valid amount and a threshold between 1 and 100"

@Composable
private fun BudgetEditDialog(
    row: BudgetRow,
    onDismiss: () -> Unit,
    onSave: (limitMinorUnits: Long, thresholdPercent: Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    // Keyed on (categoryId, currency), not categoryId alone — switching between two rows
    // for the same category (e.g. its NPR row and its USD row) must reset these fields.
    //
    // The limit is rendered with formatMinorUnitsPlain, not `it / 100`: integer division
    // dropped the minor units, so an NPR 500.50 budget showed as "500" and Save quietly
    // rewrote it to NPR 500.00. Money keeps its minor units through the whole round trip.
    var limitText by remember(row.categoryId, row.currency) {
        mutableStateOf(row.limitMinorUnits?.let { formatMinorUnitsPlain(it) } ?: "")
    }
    var thresholdText by remember(row.categoryId, row.currency) {
        mutableStateOf(row.alertThresholdPercent.toString())
    }
    var showError by remember(row.categoryId, row.currency) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "${row.categoryName} budget") },
        text = {
            Column {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = {
                        limitText = it
                        showError = false
                    },
                    isError = showError,
                    label = { Text("Monthly limit (${row.currency.name})") },
                )
                Spacer(modifier = Modifier.height(KharchaSpacing.sm))
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = {
                        thresholdText = it
                        showError = false
                    },
                    isError = showError,
                    label = { Text("Alert threshold %") },
                )
                if (showError) {
                    Spacer(modifier = Modifier.height(KharchaSpacing.xs))
                    // Save used to be a silent no-op on invalid input: the dialog just sat
                    // there with no indication of why nothing happened.
                    Text(
                        text = BUDGET_INVALID_INPUT_MESSAGE,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val limitMinorUnits = parseAmountMinorUnits(limitText)
                val thresholdPercent = thresholdText.toIntOrNull()
                if (limitMinorUnits != null && limitMinorUnits > 0 && thresholdPercent != null &&
                    thresholdPercent in 1..100
                ) {
                    onSave(limitMinorUnits, thresholdPercent)
                } else {
                    showError = true
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text("Remove")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}
