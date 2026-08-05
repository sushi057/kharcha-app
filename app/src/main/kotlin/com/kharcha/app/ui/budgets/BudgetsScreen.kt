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
import com.kharcha.app.ui.theme.KharchaColors
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.KharchaTypography
import com.kharcha.app.ui.theme.MoneyText
import com.kharcha.parser.Money

/**
 * One row per spendable category: current-month spend against its budget (if any), with
 * a progress indicator. Over-budget is signalled with an explicit "Over budget" label, not
 * color alone — the debit accent is a reinforcement, not the only signal. No card-in-card
 * nesting: rows are plain, divider-separated, matching [com.kharcha.app.ui.dashboard.DashboardScreen].
 */
@Composable
fun BudgetsScreen(
    modifier: Modifier = Modifier,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var editingRow by remember { mutableStateOf<BudgetRow?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = KharchaSpacing.md, vertical = KharchaSpacing.md),
    ) {
        item {
            Text(
                text = "Budgets",
                style = KharchaTypography.headlineMedium,
                color = KharchaColors.onBackground,
            )
            Spacer(modifier = Modifier.height(KharchaSpacing.md))
        }

        items(state.rows, key = { it.categoryId }) { row ->
            BudgetRowItem(row = row, onClick = { editingRow = row })
            HorizontalDivider(color = KharchaColors.outline)
        }
    }

    val rowBeingEdited = editingRow
    if (rowBeingEdited != null) {
        BudgetEditDialog(
            row = rowBeingEdited,
            onDismiss = { editingRow = null },
            onSave = { limitMinorUnits, thresholdPercent ->
                viewModel.setBudget(rowBeingEdited.categoryId, limitMinorUnits, rowBeingEdited.currency, thresholdPercent)
                editingRow = null
            },
            onRemove = rowBeingEdited.budgetId?.let { budgetId ->
                {
                    viewModel.deleteBudget(budgetId)
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
                    style = KharchaTypography.bodyLarge,
                    color = KharchaColors.onSurface,
                )
                if (row.isOverBudget) {
                    Text(
                        text = "Over budget",
                        style = KharchaTypography.labelMedium,
                        color = KharchaColors.debit,
                    )
                }
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                MoneyText(
                    money = Money(row.spentMinorUnits, row.currency),
                    color = if (row.isOverBudget) KharchaColors.debit else KharchaColors.onSurface,
                )
                if (row.limitMinorUnits != null) {
                    Text(
                        text = "of ${com.kharcha.app.ui.theme.formatMoney(Money(row.limitMinorUnits, row.currency))}",
                        style = KharchaTypography.labelMedium,
                        color = KharchaColors.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "No budget set",
                        style = KharchaTypography.labelMedium,
                        color = KharchaColors.onSurfaceVariant,
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
                color = if (row.isOverBudget) KharchaColors.debit else KharchaColors.credit,
                trackColor = KharchaColors.surfaceVariant,
            )
        }
    }
}

@Composable
private fun BudgetEditDialog(
    row: BudgetRow,
    onDismiss: () -> Unit,
    onSave: (limitMinorUnits: Long, thresholdPercent: Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var limitText by remember(row.categoryId) {
        mutableStateOf(row.limitMinorUnits?.let { (it / 100).toString() } ?: "")
    }
    var thresholdText by remember(row.categoryId) {
        mutableStateOf(row.alertThresholdPercent.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "${row.categoryName} budget") },
        text = {
            Column {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Monthly limit (${row.currency.name})") },
                )
                Spacer(modifier = Modifier.height(KharchaSpacing.sm))
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it },
                    label = { Text("Alert threshold %") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val majorUnits = limitText.toLongOrNull()
                val thresholdPercent = thresholdText.toIntOrNull()
                if (majorUnits != null && majorUnits > 0 && thresholdPercent != null &&
                    thresholdPercent in 1..100
                ) {
                    onSave(majorUnits * 100, thresholdPercent)
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
