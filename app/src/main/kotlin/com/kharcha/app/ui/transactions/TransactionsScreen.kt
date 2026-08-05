package com.kharcha.app.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharcha.app.ui.theme.KharchaSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showManualEntry by remember { mutableStateOf(false) }
    var selectedForEdit by remember { mutableStateOf<Long?>(null) }

    val categoriesById = remember(state.categories) { state.categories.associateBy { it.id } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showManualEntry = true }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Column {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth(),
                )
                val grouped = state.filteredTransactions.groupBy { dayLabel(it.occurredAtEpochMillis) }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    grouped.forEach { (day, transactions) ->
                        item(key = "header-$day") {
                            Text(
                                text = day,
                                modifier = Modifier.padding(
                                    horizontal = KharchaSpacing.md,
                                    vertical = KharchaSpacing.xs,
                                ),
                            )
                        }
                        items(transactions, key = { it.id }) { txn ->
                            TransactionRow(
                                transaction = txn,
                                category = txn.categoryId?.let { categoriesById[it] },
                                onClick = { selectedForEdit = txn.id },
                            )
                        }
                    }
                }
            }
        }
    }

    val editingId = selectedForEdit
    if (editingId != null) {
        val txn = state.transactions.find { it.id == editingId }
        if (txn != null) {
            TransactionEditSheet(
                transaction = txn,
                categories = state.categories,
                onDismiss = { selectedForEdit = null },
                onSetCategory = { categoryId -> viewModel.setCategory(txn.id, categoryId) },
                onSetExcluded = { excluded -> viewModel.setExcludedFromSpending(txn.id, excluded) },
                onSetMerchant = { merchant -> viewModel.setMerchant(txn.id, merchant) },
                onDelete = {
                    viewModel.deleteTransaction(txn.id)
                    selectedForEdit = null
                },
                onAlwaysCategorize = { merchant, categoryId ->
                    viewModel.confirmAlwaysCategorize(merchant, categoryId)
                },
                onAddManual = { _, _, _, _ -> },
            )
        }
    }

    if (showManualEntry) {
        TransactionEditSheet(
            transaction = null,
            categories = state.categories,
            onDismiss = { showManualEntry = false },
            onSetCategory = {},
            onSetExcluded = {},
            onSetMerchant = {},
            onDelete = {},
            onAlwaysCategorize = { _, _ -> },
            onAddManual = { amountMinorUnits, merchant, remark, categoryId ->
                viewModel.addManualTransaction(
                    amountMinorUnits = amountMinorUnits,
                    currency = com.kharcha.parser.Currency.NPR,
                    direction = com.kharcha.parser.Direction.DEBIT,
                    occurredAtEpochMillis = System.currentTimeMillis(),
                    remark = remark,
                    merchant = merchant,
                    categoryId = categoryId,
                )
                showManualEntry = false
            },
        )
    }
}

private fun dayLabel(epochMillis: Long): String {
    val formatter = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    formatter.timeZone = TimeZone.getDefault()
    return formatter.format(Date(epochMillis))
}
