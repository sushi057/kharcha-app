package com.kharcha.app.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.kharcha.app.ui.theme.KharchaSpacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = KharchaSpacing.xs),
                ) {
                    item(key = "category-all") {
                        FilterChip(
                            selected = state.categoryFilter == null,
                            onClick = { viewModel.setCategoryFilter(null) },
                            label = { Text("All categories") },
                            modifier = Modifier.padding(horizontal = KharchaSpacing.xs),
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = state.categoryFilter == category.id,
                            onClick = {
                                viewModel.setCategoryFilter(
                                    if (state.categoryFilter == category.id) null else category.id
                                )
                            },
                            label = { Text(category.name) },
                            modifier = Modifier.padding(horizontal = KharchaSpacing.xs),
                        )
                    }
                }

                DateRangeFilterRow(
                    startEpochMillis = state.dateRangeStartEpochMillis,
                    endEpochMillis = state.dateRangeEndEpochMillis,
                    onSetDateRange = viewModel::setDateRangeFilter,
                )

                val grouped = state.filteredTransactions.groupBy {
                    dayLabel(it.occurredAtEpochMillis, viewModel.zone)
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    grouped.forEach { (day, transactions) ->
                        stickyHeader(key = "header-$day") {
                            Surface(color = MaterialTheme.colorScheme.background) {
                                Text(
                                    text = day,
                                    modifier = Modifier.padding(
                                        horizontal = KharchaSpacing.md,
                                        vertical = KharchaSpacing.xs,
                                    ),
                                )
                            }
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

/**
 * Which calendar day a transaction falls on, in the app's single injected [zone] — the same
 * one Dashboard, Budgets and the notifier use. This used to resolve the day with
 * `SimpleDateFormat` + `TimeZone.getDefault()`, a second, independent definition of "today"
 * that could disagree with every other screen.
 *
 * Formatted from a [kotlinx.datetime.LocalDate] rather than a [Date] so the zone applied to
 * the instant and the zone the label is written in cannot drift apart.
 */
internal fun dayLabel(epochMillis: Long, zone: TimeZone): String {
    val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date
    val dayOfWeek = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$dayOfWeek, ${date.dayOfMonth} $month ${date.year}"
}

/**
 * Quick date-range presets across the top, satisfying the brief's "filter by
 * … date range across the top". A full calendar picker is out of scope for
 * this task; these presets cover the common cases and clear back to "All time".
 */
@Composable
private fun DateRangeFilterRow(
    startEpochMillis: Long?,
    endEpochMillis: Long?,
    onSetDateRange: (start: Long?, end: Long?) -> Unit,
) {
    val hasFilter = startEpochMillis != null || endEpochMillis != null

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KharchaSpacing.xs),
    ) {
        item(key = "date-all") {
            FilterChip(
                selected = !hasFilter,
                onClick = { onSetDateRange(null, null) },
                label = { Text("All time") },
                modifier = Modifier.padding(horizontal = KharchaSpacing.xs),
            )
        }
        item(key = "date-today") {
            FilterChip(
                selected = false,
                onClick = { onSetDateRange(startOfDay(0), endOfDay(0)) },
                label = { Text("Today") },
                modifier = Modifier.padding(horizontal = KharchaSpacing.xs),
            )
        }
        item(key = "date-7d") {
            FilterChip(
                selected = false,
                onClick = { onSetDateRange(startOfDay(-6), endOfDay(0)) },
                label = { Text("Last 7 days") },
                modifier = Modifier.padding(horizontal = KharchaSpacing.xs),
            )
        }
        item(key = "date-30d") {
            FilterChip(
                selected = false,
                onClick = { onSetDateRange(startOfDay(-29), endOfDay(0)) },
                label = { Text("Last 30 days") },
                modifier = Modifier.padding(horizontal = KharchaSpacing.xs),
            )
        }
        if (hasFilter) {
            item(key = "date-clear") {
                TextButton(onClick = { onSetDateRange(null, null) }) {
                    Text("Clear dates")
                }
            }
        }
    }
}

private fun startOfDay(daysAgo: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, daysAgo)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private fun endOfDay(daysAgo: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, daysAgo)
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
    return calendar.timeInMillis
}
