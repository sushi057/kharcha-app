package com.kharcha.app.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharcha.app.chart.axisMaxForValues
import com.kharcha.app.ui.components.CardHeader
import com.kharcha.app.ui.components.CardLabel
import com.kharcha.app.ui.components.CategoryTile
import com.kharcha.app.ui.components.DailyBars
import com.kharcha.app.ui.components.IconAction
import com.kharcha.app.ui.components.KharchaAppBar
import com.kharcha.app.ui.components.KharchaCard
import com.kharcha.app.ui.components.KharchaFlushCard
import com.kharcha.app.ui.components.Mini
import com.kharcha.app.ui.components.ProgressTrack
import com.kharcha.app.ui.components.RingGauge
import com.kharcha.app.ui.components.ThemeToggleAction
import com.kharcha.app.ui.components.hairline
import com.kharcha.app.ui.components.hairlineStrong
import com.kharcha.app.ui.theme.CategoryVisuals
import com.kharcha.app.ui.theme.KharchaMoneyTextStyle
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.LocalKharchaIsDark
import com.kharcha.app.ui.theme.formatMajorUnits
import com.kharcha.app.ui.theme.formatMinorUnitsPlain
import com.kharcha.app.ui.theme.formatMoney
import com.kharcha.app.ui.theme.parseAmountMinorUnits
import com.kharcha.parser.Currency
import com.kharcha.parser.Money
import kotlinx.coroutines.launch

/**
 * Thin [BudgetsViewModel]-wired shell. All layout and interaction lives in
 * [BudgetsScreenContent], which takes plain state and callbacks so it is directly
 * Robolectric-testable without a Hilt-backed `hiltViewModel()` call.
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
 * Budgets: one number for the month, then the categories that make it up.
 *
 * Budgeted and unbudgeted categories are separated into two cards rather than
 * interleaved. They answer different questions — "am I on track" versus "should
 * this have a budget at all" — and a list that mixes rows with progress bars and
 * rows without reads as a broken list rather than as two groups.
 *
 * The `LazyColumn` key is `(categoryId, currency)`, not `categoryId` alone — two
 * rows can legitimately share a `categoryId`, and Compose requires unique keys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreenContent(
    state: BudgetsUiState,
    onSetBudget: (categoryId: Long, limitMinorUnits: Long, currency: Currency, alertThresholdPercent: Int) -> Unit,
    onDeleteBudget: (budgetId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingRow by remember { mutableStateOf<BudgetRow?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val budgeted = state.rows.filter { it.limitMinorUnits != null && it.limitMinorUnits > 0 }
    val unbudgeted = state.rows.filter { it.limitMinorUnits == null || it.limitMinorUnits <= 0 }

    // The pace marker, shared by the summary and every category bar: the fraction
    // of the month that has already gone. One value, computed once, so no two bars
    // on the screen can disagree about what day it is.
    val pace = state.summary.today?.let { today ->
        (today.dayOfMonth - 1).toFloat() / getMonthLength(today.year, today.monthNumber)
    }
    val daysLeft = state.summary.today?.let { today ->
        getMonthLength(today.year, today.monthNumber) - today.dayOfMonth
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            KharchaAppBar(
                title = "Budgets",
                actions = {
                    IconAction(
                        icon = Icons.Outlined.Add,
                        contentDescription = "Add a budget",
                        active = true,
                        onClick = { editingRow = unbudgeted.firstOrNull() ?: state.rows.firstOrNull() },
                    )
                    ThemeToggleAction()
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = KharchaSpacing.screenGutter,
                    end = KharchaSpacing.screenGutter,
                    bottom = KharchaSpacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(KharchaSpacing.md),
            ) {
                if (state.summary.totalBudgetedMinorUnits > 0) {
                    item(key = "summary") {
                        BudgetsSummaryCard(
                            summary = state.summary,
                            paceFraction = pace,
                            daysLeft = daysLeft,
                        )
                    }
                }

                if (budgeted.isNotEmpty()) {
                    item(key = "by-category") {
                        KharchaFlushCard {
                            CardHeader(
                                label = "By category",
                                modifier = Modifier.padding(
                                    horizontal = KharchaSpacing.cardPadding,
                                    vertical = 13.dp,
                                ),
                            )
                            budgeted.forEach { row ->
                                HorizontalDivider(color = hairline)
                                BudgetRowItem(
                                    row = row,
                                    paceFraction = pace,
                                    daysLeft = daysLeft,
                                    onClick = { editingRow = row },
                                )
                            }
                        }
                    }

                    // The six-month history of the biggest budget, on the screen
                    // rather than buried in the edit sheet. It answers the question a
                    // budgets screen usually cannot: is this limit realistic, or did
                    // I make it up?
                    val focus = budgeted.maxByOrNull { it.spentMinorUnits }
                    if (focus != null && focus.last6MonthsHistory.any { it > 0 }) {
                        item(key = "history") {
                            CategoryHistoryCard(row = focus)
                        }
                    }
                }

                if (unbudgeted.isNotEmpty()) {
                    item(key = "unbudgeted") {
                        KharchaFlushCard {
                            CardHeader(
                                label = "No budget set",
                                caption = "${unbudgeted.size}",
                                modifier = Modifier.padding(
                                    horizontal = KharchaSpacing.cardPadding,
                                    vertical = 13.dp,
                                ),
                            )
                            unbudgeted.forEach { row ->
                                HorizontalDivider(color = hairline)
                                UnbudgetedRowItem(row = row, onClick = { editingRow = row })
                            }
                        }
                    }
                }

                item(key = "add") {
                    AddBudgetCard(
                        onClick = { editingRow = unbudgeted.firstOrNull() ?: state.rows.firstOrNull() },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val rowBeingEdited = editingRow
    if (rowBeingEdited != null) {
        BudgetEditSheet(
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
                    scope.launch {
                        snackbarHostState.showSnackbar(message = "Budget removed")
                    }
                }
            },
        )
    }
}

/**
 * The month in one card: what has gone, out of what was allowed, against how far
 * through the month you are.
 */
@Composable
private fun BudgetsSummaryCard(
    summary: BudgetsSummary,
    paceFraction: Float?,
    daysLeft: Int?,
    modifier: Modifier = Modifier,
) {
    if (summary.totalBudgetedMinorUnits <= 0 || summary.today == null || summary.monthStart == null) return

    val fraction = summary.totalSpentMinorUnits.toFloat() / summary.totalBudgetedMinorUnits
    val paceText = computePace(
        summary.today,
        summary.monthStart,
        summary.totalSpentMinorUnits,
        summary.totalBudgetedMinorUnits,
    )

    // Gold while on track, clay once over. The bar itself is not an alarm until
    // the number behind it is.
    val barColor = if (summary.totalSpentMinorUnits >= summary.totalBudgetedMinorUnits) {
        KharchaSemantics.debit
    } else {
        KharchaSemantics.accent
    }

    KharchaCard(modifier = modifier) {
        CardHeader(
            label = "All budgets",
            caption = daysLeft?.let { "$it days left" },
        )

        Row(
            modifier = Modifier.padding(top = KharchaSpacing.sm, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = formatMajorUnits(Money(summary.totalSpentMinorUnits, Currency.NPR)),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Mini(
                text = "of ${formatMajorUnits(Money(summary.totalBudgetedMinorUnits, Currency.NPR))}",
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }

        ProgressTrack(
            fraction = fraction,
            color = barColor,
            height = 9.dp,
            paceFraction = paceFraction,
        )

        if (paceText != null) {
            Mini(text = paceText, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

/**
 * One budgeted category: who it is, how far through, and how that compares with
 * how far through the month is.
 */
@Composable
private fun BudgetRowItem(
    row: BudgetRow,
    paceFraction: Float?,
    daysLeft: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val limit = row.limitMinorUnits ?: return
    val isDark = LocalKharchaIsDark.current
    val categoryColor = CategoryVisuals.getColor(row.categoryName, isDark)?.let(::Color)
        ?: Color(row.colorArgb)

    val fraction = row.spentMinorUnits.toFloat() / limit
    val percent = (fraction * 100).toInt()
    val status = classifyBudgetStatus(row.spentMinorUnits, limit, row.alertThresholdPercent)
    val statusColor = when (status) {
        BudgetStatus.NORMAL -> categoryColor
        BudgetStatus.NEAR_LIMIT -> KharchaSemantics.accent
        BudgetStatus.OVER_BUDGET -> KharchaSemantics.debit
        null -> categoryColor
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = KharchaSpacing.cardPadding, vertical = KharchaSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryTile(
                color = categoryColor,
                icon = CategoryVisuals.iconOrFallback(row.categoryName),
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.categoryName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Mini(
                    text = when (status) {
                        BudgetStatus.OVER_BUDGET -> "Over budget"
                        BudgetStatus.NEAR_LIMIT -> "$percent% used · approaching limit"
                        else -> "$percent% used" + (daysLeft?.let { " · $it days left" } ?: "")
                    },
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            RingGauge(fraction = fraction, color = statusColor)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Currency is spelled out here, unlike in the transactions list. A
            // category can have one row per currency, and two rows that differ only
            // by currency would otherwise both read "300".
            Text(
                text = formatMoney(Money(row.spentMinorUnits, row.currency)),
                style = KharchaMoneyTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Mini(text = "of ${formatMoney(Money(limit, row.currency))}")
        }

        ProgressTrack(
            fraction = fraction,
            color = statusColor,
            paceFraction = paceFraction,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * A category with spend but no budget. It still shows the amount — rendering only
 * "No budget set" hid it entirely, so a category you had spent real money in
 * looked identical to one you had never touched.
 */
@Composable
private fun UnbudgetedRowItem(
    row: BudgetRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalKharchaIsDark.current
    val categoryColor = CategoryVisuals.getColor(row.categoryName, isDark)?.let(::Color)
        ?: Color(row.colorArgb)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = KharchaSpacing.cardPadding, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryTile(
            color = categoryColor,
            icon = CategoryVisuals.iconOrFallback(row.categoryName),
            contentDescription = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.categoryName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Mini(text = "No budget set · tap to add one", modifier = Modifier.padding(top = 1.dp))
        }
        Text(
            text = formatMoney(Money(row.spentMinorUnits, row.currency)),
            style = KharchaMoneyTextStyle,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Six months of one category's spend, so a limit can be judged against reality. */
@Composable
private fun CategoryHistoryCard(row: BudgetRow, modifier: Modifier = Modifier) {
    val isDark = LocalKharchaIsDark.current
    val history = row.last6MonthsHistory
    val average = history.filter { it > 0 }.let { months ->
        if (months.isEmpty()) 0L else months.sum() / months.size
    }

    KharchaCard(modifier = modifier) {
        CardHeader(
            label = "${row.categoryName} · 6 months",
            caption = "avg ${formatMajorUnits(Money(average, row.currency))}",
        )
        DailyBars(
            values = history,
            axisMax = axisMaxForValues(history),
            barColor = CategoryVisuals.getColor(row.categoryName, isDark)?.let(::Color)
                ?: Color(row.colorArgb),
            height = 90.dp,
            modifier = Modifier.padding(top = 10.dp),
        )
        Mini(text = "6 months ago → last month", modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * The affordance for a category that has no row yet. Dashed rather than filled,
 * because it is an empty slot, not a card with content in it.
 */
@Composable
private fun AddBudgetCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, hairlineStrong, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(KharchaSpacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                .padding(7.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = KharchaSemantics.accent,
                modifier = Modifier.height(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Add a budget",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Mini(
                text = "Or let Kharcha suggest one from your last 3 months",
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

/** Bottom sheet for adding, editing, or removing a budget. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditSheet(
    row: BudgetRow,
    onDismiss: () -> Unit,
    onSave: (limitMinorUnits: Long, thresholdPercent: Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        BudgetEditSheetContent(
            row = row,
            onDismiss = onDismiss,
            onSave = onSave,
            onRemove = onRemove,
        )
    }
}

/**
 * The edit sheet's body, deliberately separate from the [ModalBottomSheet] that hosts it.
 *
 * The validation and save behaviour here is worth testing directly, and a modal sheet is
 * a hostile thing to drive from a test: it renders into its own window and settles
 * asynchronously, so a click on a control below the fold silently does nothing. Keeping
 * the body a plain composable means those rules can be exercised without a modal window
 * at all.
 */
@Composable
internal fun BudgetEditSheetContent(
    row: BudgetRow,
    onDismiss: () -> Unit,
    onSave: (limitMinorUnits: Long, thresholdPercent: Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var limitText by remember(row.categoryId, row.currency) {
        mutableStateOf(row.limitMinorUnits?.let { formatMinorUnitsPlain(it) } ?: "")
    }
    var thresholdText by remember(row.categoryId, row.currency) {
        mutableStateOf(row.alertThresholdPercent.toString())
    }
    var showError by remember(row.categoryId, row.currency) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KharchaSpacing.lg)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "${row.categoryName} budget",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(KharchaSpacing.md))

        OutlinedTextField(
            value = limitText,
            onValueChange = {
                limitText = it
                showError = false
            },
            isError = showError,
            label = { Text("Monthly limit (${row.currency.name})") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(KharchaSpacing.md))

        OutlinedTextField(
            value = thresholdText,
            onValueChange = {
                thresholdText = it
                showError = false
            },
            isError = showError,
            label = { Text("Alert threshold %") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (showError) {
            Spacer(modifier = Modifier.height(KharchaSpacing.sm))
            Text(
                text = BUDGET_INVALID_INPUT_MESSAGE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (row.last6MonthsHistory.any { it > 0 }) {
            Spacer(modifier = Modifier.height(KharchaSpacing.lg))
            CardLabel("6-month history")
            Spacer(modifier = Modifier.height(KharchaSpacing.sm))
            DailyBars(
                values = row.last6MonthsHistory,
                axisMax = axisMaxForValues(row.last6MonthsHistory),
                barColor = KharchaSemantics.accent,
                height = 90.dp,
            )
        }

        Spacer(modifier = Modifier.height(KharchaSpacing.lg))

        val suggestedAmount = suggestBudgetAmount(row.last6MonthsHistory.takeLast(3))
        if (suggestedAmount != null && suggestedAmount > 0) {
            TextButton(
                onClick = {
                    limitText = formatMinorUnitsPlain(suggestedAmount)
                    showError = false
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Suggest: ${formatMoney(Money(suggestedAmount, row.currency))}",
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(KharchaSpacing.md))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (onRemove != null) {
                TextButton(onClick = onRemove) { Text("Remove") }
            }
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Button(
                onClick = {
                    val limitMinorUnits = parseAmountMinorUnits(limitText)
                    val thresholdPercent = thresholdText.toIntOrNull()
                    if (limitMinorUnits != null && limitMinorUnits > 0 && thresholdPercent != null &&
                        thresholdPercent in 1..100
                    ) {
                        onSave(limitMinorUnits, thresholdPercent)
                    } else {
                        showError = true
                    }
                },
            ) {
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(KharchaSpacing.lg))
    }
}

/** Shown when Save is pressed with input the dialog cannot turn into a budget. */
const val BUDGET_INVALID_INPUT_MESSAGE = "Enter a valid amount and a threshold between 1 and 100"
