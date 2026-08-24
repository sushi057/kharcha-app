package com.kharcha.app.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharcha.app.ui.components.CardLabel
import com.kharcha.app.ui.components.IconAction
import com.kharcha.app.ui.components.KharchaAppBar
import com.kharcha.app.ui.components.KharchaButton
import com.kharcha.app.ui.components.KharchaButtonStyle
import com.kharcha.app.ui.components.KharchaChip
import com.kharcha.app.ui.theme.KharchaPillShape
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.data.TransactionEntity
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Calendar

/**
 * The ledger.
 *
 * Chrome is deliberately thin: an app bar, a search pill, one row of filter
 * chips, and then nothing between the reader and the list. Filters that are on
 * appear as removable chips rather than as a panel you have to open to find out
 * what is hiding rows from you.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showManualEntry by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedForEdit by remember { mutableStateOf<Long?>(null) }

    val categoriesById = remember(state.categories) { state.categories.associateBy { it.id } }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            KharchaAppBar(
                title = "Transactions",
                actions = {
                    IconAction(
                        icon = Icons.Outlined.Search,
                        contentDescription = "Search transactions",
                        active = showSearch || state.searchQuery.isNotEmpty(),
                        onClick = { showSearch = !showSearch },
                    )
                    IconAction(
                        icon = Icons.Outlined.Tune,
                        contentDescription = "Filters",
                        active = state.hasActiveFilters,
                        onClick = { showFilters = true },
                    )
                },
            )

            Column(
                modifier = Modifier.padding(
                    start = KharchaSpacing.screenGutter,
                    end = KharchaSpacing.screenGutter,
                    bottom = KharchaSpacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showSearch || state.searchQuery.isNotEmpty()) {
                    SearchPill(
                        query = state.searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        onClear = {
                            viewModel.setSearchQuery("")
                            showSearch = false
                        },
                    )
                }

                FilterChipRow(
                    state = state,
                    onClearCategory = { viewModel.setCategoryFilter(null) },
                    onClearSort = { viewModel.setSort(TransactionSort.Newest) },
                    onClearExcludedOnly = { viewModel.setExcludedOnly(false) },
                    onSetDateRange = viewModel::setDateRangeFilter,
                    categoryName = state.categories.find { it.id == state.categoryFilter }?.name,
                )
            }

            // A day-grouped list is a diary; an amount-sorted one is a ranking, and day
            // headers over a ranking would print one header per row. So the ordering
            // decides the shape of the list, not just the order of its rows.
            val rows = state.filteredTransactions
            val grouped = if (state.sort.isChronological) {
                rows.groupBy { dayLabel(it.occurredAtEpochMillis, viewModel.zone) }
            } else {
                emptyMap()
            }

            val onToggleExcluded: (TransactionEntity) -> Unit = { txn ->
                val nowExcluded = !txn.excludedFromSpending
                viewModel.setExcludedFromSpending(txn.id, nowExcluded)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = if (nowExcluded) {
                            "Excluded from spending"
                        } else {
                            "Counted in spending again"
                        },
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.setExcludedFromSpending(txn.id, !nowExcluded)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // The FAB floats over this list; without the trailing space it sits
                // on top of the last row's amount.
                contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
            ) {
                if (state.sort.isChronological) {
                    grouped.forEach { (day, transactions) ->
                        stickyHeader(key = "header-$day") {
                            DayHeader(
                                epochMillis = transactions.first().occurredAtEpochMillis,
                                signedSubtotalMinorUnits = calculateDaySubtotal(transactions),
                                currency = transactions.first().currency,
                                zone = viewModel.zone,
                            )
                        }
                        items(transactions, key = { it.id }) { txn ->
                            SwipeToExcludeRow(
                                isExcluded = txn.excludedFromSpending,
                                onToggleExcluded = { onToggleExcluded(txn) },
                            ) {
                                TransactionRow(
                                    // Opaque: the row slides over the swipe background, so a
                                    // transparent row would let "Exclude" show through on
                                    // every row at rest.
                                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                                    transaction = txn,
                                    category = txn.categoryId?.let { categoriesById[it] },
                                    onClick = { selectedForEdit = txn.id },
                                    zone = viewModel.zone,
                                    categorizedByRule = txn.categoryIsManualOverride,
                                )
                            }
                        }
                    }
                } else {
                    items(rows, key = { it.id }) { txn ->
                        SwipeToExcludeRow(
                            isExcluded = txn.excludedFromSpending,
                            onToggleExcluded = { onToggleExcluded(txn) },
                        ) {
                            TransactionRow(
                                modifier = Modifier.background(MaterialTheme.colorScheme.background),
                                transaction = txn,
                                category = txn.categoryId?.let { categoriesById[it] },
                                onClick = { selectedForEdit = txn.id },
                                zone = viewModel.zone,
                                categorizedByRule = txn.categoryIsManualOverride,
                            )
                        }
                    }
                }
            }
        }

        // A rounded square rather than a circle, matching the card radius scale —
        // and placed clear of the nav bar so it never covers a nav label.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = KharchaSpacing.screenGutter, bottom = 24.dp)
                .size(54.dp)
                .clip(MaterialTheme.shapes.large)
                .background(KharchaSemantics.accent)
                .clickable { showManualEntry = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Add transaction",
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(24.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
                // The message the transaction was read out of, when there is one. The
                // remark is the parser's excerpt; the evidence panel has to show the
                // thing being excerpted or it is not evidence.
                rawSmsBody = txn.rawMessageId?.let { state.rawBodiesById[it] },
            )
        }
    }

    if (showFilters) {
        FilterSheet(
            state = state,
            onDismiss = { showFilters = false },
            onSetSort = viewModel::setSort,
            onSetCategory = viewModel::setCategoryFilter,
            onSetExcludedOnly = viewModel::setExcludedOnly,
            onClearAll = viewModel::clearFilters,
        )
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
 * The search field, as a tonal pill rather than an outlined Material text field.
 * An outlined box with a floating label is a *form* control; this is a filter
 * over a list that is already on screen, and it should read as part of the
 * chrome, not as something to fill in.
 */
@Composable
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opening search should let you type, not hand you a field to tap a second time.
    // The pill only exists while search is on, so taking focus on appearance is
    // unambiguous — there is nothing else on the screen that wanted it.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(KharchaPillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = KharchaSpacing.cardPadding, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(15.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search merchant, amount, note",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                ),
                cursorBrush = SolidColor(KharchaSemantics.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        // Clearing a search by backspacing twenty characters is the kind of small
        // friction that makes people stop using search at all.
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Clear search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(KharchaPillShape)
                    .clickable(role = Role.Button, onClick = onClear)
                    .padding(3.dp)
                    .size(15.dp),
            )
        }
    }
}

/**
 * What the app-bar's filter button opens: sort order, category, and the excluded-only
 * switch, in one sheet.
 *
 * Every choice here also appears as a removable chip under the app bar once it is on,
 * so the sheet is where a filter is *set* and the chip row is where it is *seen* — the
 * user never has to open a panel to find out what is hiding rows from them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: TransactionsUiState,
    onDismiss: () -> Unit,
    onSetSort: (TransactionSort) -> Unit,
    onSetCategory: (Long?) -> Unit,
    onSetExcludedOnly: (Boolean) -> Unit,
    onClearAll: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = KharchaSpacing.screenGutter,
                    end = KharchaSpacing.screenGutter,
                    bottom = KharchaSpacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(KharchaSpacing.sm),
        ) {
            CardLabel("Sort")
            TransactionSort.entries.forEach { sort ->
                SheetOptionRow(
                    label = sort.label,
                    selected = state.sort == sort,
                    onClick = { onSetSort(sort) },
                )
            }

            CardLabel("Category")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item(key = "cat-all") {
                    KharchaChip(
                        label = "All",
                        selected = state.categoryFilter == null,
                        onClick = { onSetCategory(null) },
                    )
                }
                items(state.categories, key = { it.id }) { category ->
                    KharchaChip(
                        label = category.name,
                        selected = state.categoryFilter == category.id,
                        onClick = {
                            onSetCategory(if (state.categoryFilter == category.id) null else category.id)
                        },
                    )
                }
            }

            CardLabel("Show")
            SheetOptionRow(
                label = "Only excluded from spending",
                selected = state.excludedOnly,
                onClick = { onSetExcludedOnly(!state.excludedOnly) },
            )

            Row(
                modifier = Modifier.padding(top = KharchaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                KharchaButton(
                    text = "Clear all",
                    style = KharchaButtonStyle.Text,
                    onClick = onClearAll,
                )
                KharchaButton(
                    text = "Done",
                    style = KharchaButtonStyle.Filled,
                    onClick = onDismiss,
                )
            }
        }
    }
}

/** One tappable line in [FilterSheet], with a check on the chosen one. */
@Composable
private fun SheetOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = KharchaSemantics.accent,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/**
 * Date presets and any active filters, in one scrolling row. Active filters carry
 * a ✕ and sit first, so what is currently being hidden from the list is the first
 * thing in the row rather than the last.
 */
@Composable
private fun FilterChipRow(
    state: TransactionsUiState,
    categoryName: String?,
    onClearCategory: () -> Unit,
    onClearSort: () -> Unit,
    onClearExcludedOnly: () -> Unit,
    onSetDateRange: (Long?, Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDateFilter = state.dateRangeStartEpochMillis != null || state.dateRangeEndEpochMillis != null
    val activePreset = datePresetFor(
        state.dateRangeStartEpochMillis,
        state.dateRangeEndEpochMillis,
    )

    // Active-filter chips are *prepended* to this row. A LazyRow anchors on the item that
    // was first before the insert, so without this the newly added chip lands half
    // off-screen to the left — the user turns on "Highest amount" and sees "mount ✕".
    val listState = rememberLazyListState()
    val leadingChipCount = listOf(
        categoryName != null,
        state.sort != TransactionSort.Newest,
        state.excludedOnly,
        hasDateFilter && activePreset == null,
    ).count { it }
    LaunchedEffect(leadingChipCount) {
        if (leadingChipCount > 0) listState.scrollToItem(0)
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (categoryName != null) {
            item(key = "chip-category") {
                KharchaChip(
                    label = categoryName,
                    selected = true,
                    removable = true,
                    onClick = onClearCategory,
                )
            }
        }
        if (state.sort != TransactionSort.Newest) {
            item(key = "chip-sort") {
                KharchaChip(
                    label = state.sort.label,
                    selected = true,
                    removable = true,
                    onClick = onClearSort,
                )
            }
        }
        if (state.excludedOnly) {
            item(key = "chip-excluded") {
                KharchaChip(
                    label = "Excluded only",
                    selected = true,
                    removable = true,
                    onClick = onClearExcludedOnly,
                )
            }
        }
        // Only a range that is *not* one of the presets needs its own chip. When the user
        // taps "Today", the thing that is on is Today — saying "Date range" instead names
        // the mechanism rather than the choice, and leaves every preset looking unselected.
        if (hasDateFilter && activePreset == null) {
            item(key = "chip-dates") {
                KharchaChip(
                    label = "Date range",
                    selected = true,
                    removable = true,
                    onClick = { onSetDateRange(null, null) },
                )
            }
        }
        item(key = "chip-all") {
            KharchaChip(
                label = "All time",
                selected = !hasDateFilter,
                onClick = { onSetDateRange(null, null) },
            )
        }
        DatePreset.entries.forEach { preset ->
            item(key = "chip-${preset.name}") {
                KharchaChip(
                    label = preset.label,
                    selected = activePreset == preset,
                    onClick = {
                        val (start, end) = preset.range()
                        onSetDateRange(start, end)
                    },
                )
            }
        }
    }
}

/**
 * The date shortcuts under the app bar. Each one is a window ending at the end of today,
 * so they nest: Today ⊂ Last 7 days ⊂ Last 30 days.
 */
internal enum class DatePreset(val label: String, private val daysBack: Int) {
    Today("Today", 0),
    Week("Last 7 days", 6),
    Month("Last 30 days", 29),
    ;

    fun range(): Pair<Long, Long> = startOfDay(-daysBack) to endOfDay(0)
}

/**
 * Which preset, if any, a filter range corresponds to.
 *
 * Derived by comparison rather than stored alongside the range, so a range that arrives
 * from somewhere else entirely — the dashboard's daily-spend chart, say — still lights up
 * "Today" when that is what it happens to be. A range matching no preset returns null and
 * is shown as its own "Date range" chip.
 */
internal fun datePresetFor(startEpochMillis: Long?, endEpochMillis: Long?): DatePreset? {
    if (startEpochMillis == null || endEpochMillis == null) return null
    return DatePreset.entries.firstOrNull { it.range() == (startEpochMillis to endEpochMillis) }
}

/**
 * Which calendar day a transaction falls on, in the app's single injected [zone] — the same
 * one Dashboard, Budgets and the notifier use. This used to resolve the day with
 * `SimpleDateFormat` + `TimeZone.getDefault()`, a second, independent definition of "today"
 * that could disagree with every other screen.
 */
internal fun dayLabel(epochMillis: Long, zone: TimeZone): String {
    val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date
    val dayOfWeek = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$dayOfWeek, ${date.dayOfMonth} $month ${date.year}"
}

/** Signed day subtotal: debits negative, credits positive. */
private fun calculateDaySubtotal(transactions: List<TransactionEntity>): Long =
    transactions.sumOf { txn ->
        if (txn.direction == com.kharcha.parser.Direction.DEBIT) -txn.amountMinorUnits else txn.amountMinorUnits
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

/** Space reserved at the bottom of the list so the floating action button never covers content. */
private val FAB_CLEARANCE = 96.dp

/**
 * Wraps a transaction row in a swipe gesture that excludes it from spending totals — the quick
 * path for the one-off outlier (a salary, a transfer to yourself, a reimbursed purchase) that
 * otherwise distorts every chart and budget on the dashboard.
 *
 * Excluding is deliberately what the swipe does, not deleting: the transaction stays in the
 * ledger and keeps its link to the original SMS, so the action is reversible and the evidence
 * trail survives. Permanent deletion stays behind the edit sheet, where it takes a deliberate
 * tap. Swiping an already-excluded row puts it back into the totals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToExcludeRow(
    isExcluded: Boolean,
    onToggleExcluded: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onToggleExcluded()
            }
            // Never let the box settle into a dismissed state: the row is not going away, it is
            // changing a flag, so it must spring back under the caller's new `isExcluded`.
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = KharchaSpacing.lg),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Text(
                    text = if (isExcluded) "Count again" else "Exclude",
                    style = MaterialTheme.typography.labelLarge,
                    color = KharchaSemantics.accent,
                )
            }
        },
        content = { content() },
    )
}
