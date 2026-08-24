package com.kharcha.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.kharcha.data.CategoryEntity
import com.kharcha.data.TransactionEntity
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.parseAmountMinorUnits
import com.kharcha.parser.RemarkParser

/**
 * Editing sheet for an existing transaction, and (when [transaction] is
 * null) manual-entry mode reached from the FAB. Thin [ModalBottomSheet]
 * wrapper around [TransactionEditSheetContent] — the wrapper is not
 * Robolectric-testable (its Popup window doesn't map click coordinates
 * correctly under Robolectric), so tests exercise the content directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditSheet(
    transaction: TransactionEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSetCategory: (categoryId: Long) -> Unit,
    onSetExcluded: (Boolean) -> Unit,
    onSetMerchant: (String) -> Unit,
    onDelete: () -> Unit,
    onAlwaysCategorize: (merchant: String, categoryId: Long) -> Unit,
    onAddManual: (
        amountMinorUnits: Long,
        merchant: String,
        remark: String,
        categoryId: Long?,
    ) -> Unit,
    initialMerchantText: String = "",
    initialAmountText: String = "",
    rawSmsBody: String? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        TransactionEditSheetContent(
            transaction = transaction,
            categories = categories,
            onSetCategory = onSetCategory,
            onSetExcluded = onSetExcluded,
            onSetMerchant = onSetMerchant,
            onDelete = onDelete,
            onAlwaysCategorize = onAlwaysCategorize,
            onAddManual = onAddManual,
            onDismiss = onDismiss,
            initialMerchantText = initialMerchantText,
            initialAmountText = initialAmountText,
            rawSmsBody = rawSmsBody,
        )
    }
}

@Composable
fun TransactionEditSheetContent(
    transaction: TransactionEntity?,
    categories: List<CategoryEntity>,
    onSetCategory: (categoryId: Long) -> Unit,
    onSetExcluded: (Boolean) -> Unit,
    onSetMerchant: (String) -> Unit,
    onDelete: () -> Unit,
    onAlwaysCategorize: (merchant: String, categoryId: Long) -> Unit,
    onAddManual: (
        amountMinorUnits: Long,
        merchant: String,
        remark: String,
        categoryId: Long?,
    ) -> Unit,
    onDismiss: () -> Unit,
    initialMerchantText: String = "",
    initialAmountText: String = "",
    rawSmsBody: String? = null,
) {
    var merchantText by remember(transaction?.id) {
        mutableStateOf(transaction?.merchant ?: initialMerchantText)
    }
    var amountText by remember(transaction?.id) { mutableStateOf(initialAmountText) }
    var pendingCategoryPrompt by remember(transaction?.id) {
        mutableStateOf<CategoryEntity?>(null)
    }
    var rawSmsExpanded by remember(transaction?.id) { mutableStateOf(false) }
    // Manual-entry mode only. Deliberately starts null rather than defaulting to the
    // first category: "a wrong transaction is worse than a missing one", and a
    // defaulted category must never be reported as a manual override (which would make
    // the row permanently immune to ReparseService and to every future rule).
    var selectedCategoryId by remember(transaction?.id) { mutableStateOf<Long?>(null) }

    val parsed = transaction?.let { RemarkParser.parse(it.remark) }

    // Scrollable: an expanded raw SMS runs several lines longer than the sheet, and
    // without this the delete button and the save button fall off the bottom.
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(KharchaSpacing.md),
    ) {
        Text(text = if (transaction == null) "Add transaction" else "Edit transaction")
        Spacer(modifier = Modifier.height(KharchaSpacing.sm))

        // Show parsed fields prominently for existing transactions
        if (transaction != null && parsed != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(KharchaSpacing.md)) {
                    Text(
                        text = "Parsed transaction details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(KharchaSpacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Channel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = parsed.channel ?: "—",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Column {
                            Text(
                                text = "Type",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = parsed.kind.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(KharchaSpacing.md))

            // Raw SMS collapsible section
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { rawSmsExpanded = !rawSmsExpanded }
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Column(modifier = Modifier.padding(KharchaSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Raw SMS (evidence)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = if (rawSmsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (rawSmsExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (rawSmsExpanded) {
                        Spacer(modifier = Modifier.height(KharchaSpacing.sm))
                        Text(
                            // The whole message, never an ellipsis: the reason to open
                            // this panel is to check a detail the parsed fields left out,
                            // and a truncated message cannot answer that. `remark` is the
                            // fallback for manual entries, which have no SMS behind them.
                            text = rawSmsBody ?: transaction.remark,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (rawSmsBody == null) {
                            Spacer(modifier = Modifier.height(KharchaSpacing.xs))
                            Text(
                                text = "No original message — entered by hand.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(KharchaSpacing.md))
        }

        if (transaction == null) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(KharchaSpacing.sm))
        }

        OutlinedTextField(
            value = merchantText,
            onValueChange = {
                merchantText = it
                if (transaction != null) onSetMerchant(it)
            },
            label = { Text("Merchant") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(KharchaSpacing.sm))

        Text(text = "Category")
        LazyRow {
            items(categories) { category ->
                FilterChip(
                    selected = if (transaction != null) {
                        transaction.categoryId == category.id
                    } else {
                        selectedCategoryId == category.id
                    },
                    onClick = {
                        if (transaction != null) {
                            onSetCategory(category.id)
                            if (!merchantText.isBlank()) {
                                pendingCategoryPrompt = category
                            }
                        } else {
                            // Tapping the selected chip again clears it, so the user can
                            // get back to "no category" rather than being stuck with one.
                            selectedCategoryId =
                                if (selectedCategoryId == category.id) null else category.id
                        }
                    },
                    label = { Text(category.name) },
                    modifier = Modifier.padding(end = KharchaSpacing.xs),
                )
            }
        }
        Spacer(modifier = Modifier.height(KharchaSpacing.sm))

        if (transaction != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "Exclude from spending")
                Switch(
                    checked = transaction.excludedFromSpending,
                    onCheckedChange = onSetExcluded,
                )
            }
            Spacer(modifier = Modifier.height(KharchaSpacing.sm))
            TextButton(onClick = onDelete) {
                Text(text = "Delete")
            }
        } else {
            Button(
                onClick = {
                    val amountMinorUnits = parseAmountMinorUnits(amountText) ?: return@Button
                    onAddManual(
                        amountMinorUnits,
                        merchantText,
                        merchantText,
                        // null when the user never touched the picker — the caller turns
                        // that into an uncategorized, non-overridden transaction that
                        // rules and re-parse can still improve later.
                        selectedCategoryId,
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Save")
            }
        }
    }

    val prompt = pendingCategoryPrompt
    if (prompt != null && transaction != null) {
        AlertDialog(
            onDismissRequest = { pendingCategoryPrompt = null },
            title = { Text(text = "Always categorize?") },
            text = {
                Text(
                    text = "Always categorize '${merchantText}' as ${prompt.name}?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAlwaysCategorize(merchantText, prompt.id)
                    pendingCategoryPrompt = null
                }) {
                    Text(text = "Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryPrompt = null }) {
                    Text(text = "Not now")
                }
            },
        )
    }
}
