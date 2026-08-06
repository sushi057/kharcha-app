package com.kharcha.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kharcha.data.CategoryEntity
import com.kharcha.data.TransactionEntity
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.parseAmountMinorUnits

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
) {
    var merchantText by remember(transaction?.id) {
        mutableStateOf(transaction?.merchant ?: initialMerchantText)
    }
    var amountText by remember(transaction?.id) { mutableStateOf(initialAmountText) }
    var pendingCategoryPrompt by remember(transaction?.id) {
        mutableStateOf<CategoryEntity?>(null)
    }
    // Manual-entry mode only. Deliberately starts null rather than defaulting to the
    // first category: "a wrong transaction is worse than a missing one", and a
    // defaulted category must never be reported as a manual override (which would make
    // the row permanently immune to ReparseService and to every future rule).
    var selectedCategoryId by remember(transaction?.id) { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.padding(KharchaSpacing.md)) {
        Text(text = if (transaction == null) "Add transaction" else "Edit transaction")
        Spacer(modifier = Modifier.height(KharchaSpacing.sm))

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
