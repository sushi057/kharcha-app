package com.kharcha.app.ui.unparsed

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharcha.app.ui.components.CardLabel
import com.kharcha.app.ui.settings.ExportState
import com.kharcha.app.ui.components.CategoryTile
import com.kharcha.app.ui.components.IconAction
import com.kharcha.app.ui.components.KharchaAppBar
import com.kharcha.app.ui.components.KharchaButton
import com.kharcha.app.ui.components.KharchaButtonStyle
import com.kharcha.app.ui.components.KharchaCard
import com.kharcha.app.ui.components.Mini
import com.kharcha.app.ui.components.SegmentedControl
import com.kharcha.app.ui.components.ThemeToggleAction
import com.kharcha.app.ui.components.hairlineStrong
import com.kharcha.app.ui.theme.CategoryVisuals
import com.kharcha.app.ui.theme.KharchaMoneyDisplayTextStyle
import com.kharcha.app.ui.theme.KharchaMonoSmallTextStyle
import com.kharcha.app.ui.theme.KharchaPillShape
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.LocalKharchaIsDark
import com.kharcha.app.ui.theme.parseAmountMinorUnits
import com.kharcha.app.ui.transactions.TransactionEditSheet
import com.kharcha.app.ui.transactions.toDisplayName
import com.kharcha.data.RawMessage
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The inbox: everything the parser could not place, split by whether it is worth
 * your attention.
 *
 * "Needs review" is genuinely ambiguous — a real transaction the parser could not
 * pin down. "Ignored" is OTPs, password changes and promos: never transactions,
 * kept only so a misclassification is visible and reversible rather than silently
 * dropped.
 *
 * Each review card leads with what was *extracted* — amount, date, counterparty —
 * and keeps the raw SMS collapsed underneath as evidence. Leading with the raw
 * message, as v1 did, made every card a wall of reference numbers and forced the
 * reader to do the parser's job by eye.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnparsedScreen(
    modifier: Modifier = Modifier,
    viewModel: UnparsedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    var selectedForManualEntry by remember { mutableStateOf<RawMessage?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Storage Access Framework: the user picks where the dump lands, so it survives the
    // app being uninstalled and can be pulled off the device for parser debugging.
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri -> viewModel.exportAllMessagesTo(context, uri) }
        }
    }

    LaunchedEffect(exportState) {
        when (val current = exportState) {
            is ExportState.Success -> {
                snackbarHostState.showSnackbar(current.message, duration = SnackbarDuration.Short)
                viewModel.clearExportState()
            }

            is ExportState.Error -> {
                snackbarHostState.showSnackbar(current.message, duration = SnackbarDuration.Long)
                viewModel.clearExportState()
            }

            else -> {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            KharchaAppBar(
                title = "Inbox",
                actions = {
                    if (state.isSyncing) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = KharchaSemantics.accent,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    } else {
                        IconAction(
                            icon = Icons.Outlined.Refresh,
                            contentDescription = "Sync now",
                            active = true,
                            onClick = viewModel::sync,
                        )
                    }
                    IconAction(
                        icon = Icons.Outlined.IosShare,
                        contentDescription = "Export all messages",
                        enabled = exportState !is ExportState.InProgress,
                        onClick = {
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                                putExtra(Intent.EXTRA_TITLE, viewModel.exportFilename())
                            }
                            exportLauncher.launch(intent)
                        },
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
                item(key = "segments") {
                    SegmentedControl(
                        options = listOf(
                            "Review · ${state.needsReview.size}",
                            "Ignored · ${state.ignored.size}",
                            "Dismissed · ${state.dismissed.size}",
                        ),
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it },
                    )
                }

                if (selectedTab == 0) {
                    if (state.needsReview.isEmpty()) {
                        item(key = "empty-review") {
                            EmptyState(
                                title = "All caught up",
                                detail = "New alerts Kharcha is unsure about will show up here.",
                            )
                        }
                    } else {
                        items(state.needsReview, key = { it.id }) { message ->
                            NeedsReviewCard(
                                message = message,
                                onAddAsTransaction = { selectedForManualEntry = message },
                                onDismiss = { viewModel.dismiss(message.id) },
                            )
                        }
                    }
                } else if (selectedTab == 1) {
                    if (state.ignored.isEmpty()) {
                        item(key = "empty-ignored") {
                            EmptyState(
                                title = "Nothing ignored",
                                detail = "OTPs, password changes and promos will collect here.",
                            )
                        }
                    } else {
                        item(key = "ignored-label") {
                            CardLabel("Ignored · not transactions")
                        }
                        items(state.ignored, key = { it.id }) { message ->
                            IgnoredRow(
                                message = message,
                                onRestore = { viewModel.restore(message.id) },
                            )
                        }
                        item(key = "ignored-hint") {
                            Mini(
                                text = "Tap any to restore",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    if (state.dismissed.isEmpty()) {
                        item(key = "empty-dismissed") {
                            EmptyState(
                                title = "Nothing dismissed",
                                detail = "Messages you wave away from Review collect here.",
                            )
                        }
                    } else {
                        item(key = "dismissed-label") {
                            CardLabel("Dismissed · waved away by you")
                        }
                        items(state.dismissed, key = { it.id }) { message ->
                            DismissedCard(
                                message = message,
                                onAddAsTransaction = { selectedForManualEntry = message },
                                onRestore = { viewModel.undismiss(message.id) },
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val prefillFrom = selectedForManualEntry
    if (prefillFrom != null) {
        TransactionEditSheet(
            transaction = null,
            categories = state.categories,
            onDismiss = { selectedForManualEntry = null },
            onSetCategory = {},
            onSetExcluded = {},
            onSetMerchant = {},
            onDelete = {},
            onAlwaysCategorize = { _, _ -> },
            onAddManual = { amountMinorUnits, merchant, remark, categoryId ->
                viewModel.createManualTransactionFrom(
                    rawId = prefillFrom.id,
                    amountMinorUnits = amountMinorUnits,
                    merchant = merchant,
                    remark = remark,
                    categoryId = categoryId,
                )
                selectedForManualEntry = null
            },
            initialMerchantText = merchantHintFrom(prefillFrom.body),
            // The amount the message states, already in the field. Reading it off the SMS
            // and retyping it is the single most error-prone step in triaging the inbox.
            initialAmountText = extractAmountForEntry(prefillFrom.body).orEmpty(),
        )
    }
}

/**
 * One ambiguous message: what Kharcha managed to read, then the message itself.
 */
@Composable
private fun NeedsReviewCard(
    message: RawMessage,
    onAddAsTransaction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Dismiss",
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isDark = LocalKharchaIsDark.current
    val extracted = remember(message.body) { extractFields(message.body) }

    KharchaCard(modifier = modifier, padding = 13.dp) {
        if (extracted.amountText != null) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(KharchaSpacing.sm),
            ) {
                Text(
                    text = extracted.amountText,
                    style = KharchaMoneyDisplayTextStyle,
                    color = if (extracted.isCredit) KharchaSemantics.credit else KharchaSemantics.debit,
                )
                Mini(
                    text = "NPR · ${formatMessageDate(message.receivedAtEpochMillis)}",
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        } else {
            Mini(text = "NPR · ${formatMessageDate(message.receivedAtEpochMillis)}")
        }

        if (extracted.counterparty != null) {
            Row(
                modifier = Modifier.padding(top = KharchaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CategoryTile(
                    color = CategoryVisuals.colorOrFallback("Other", isDark),
                    icon = CategoryVisuals.iconOrFallback("Other"),
                    contentDescription = null,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extracted.counterparty,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Mini(text = "Counterparty unconfirmed", modifier = Modifier.padding(top = 1.dp))
                }
            }
        }

        // The raw SMS: collapsed to two lines by default. It is evidence for the
        // extraction above, not the thing being read, so it is set in the data face
        // at the smallest size in the app and given a tonal well of its own.
        Text(
            text = message.body,
            style = KharchaMonoSmallTextStyle.copy(letterSpacing = 0.sp),
            color = MaterialTheme.colorScheme.outline,
            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 9.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .heightIn(min = 0.dp)
                .padding(horizontal = 9.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier.padding(top = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KharchaButton(
                text = "Add",
                style = KharchaButtonStyle.Filled,
                onClick = onAddAsTransaction,
            )
            KharchaButton(
                text = if (isExpanded) "Collapse" else "Expand",
                style = KharchaButtonStyle.Tonal,
                onClick = { isExpanded = !isExpanded },
            )
            KharchaButton(
                text = dismissLabel,
                style = KharchaButtonStyle.Text,
                onClick = onDismiss,
            )
        }
    }
}

/**
 * A message the user dismissed. Shown with the same extraction as a review card — the
 * point of this list is to spot the one you dismissed by mistake, which you cannot do
 * from a truncated body alone — and offering the two ways out: add it, or put it back.
 */
@Composable
private fun DismissedCard(
    message: RawMessage,
    onAddAsTransaction: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeedsReviewCard(
        message = message,
        onAddAsTransaction = onAddAsTransaction,
        onDismiss = onRestore,
        dismissLabel = "Restore",
        modifier = modifier,
    )
}

/**
 * An ignored message: one line of it, and the reason it was ignored. The whole
 * row restores it, because the only reason to look at this list is to catch one
 * that should not be here.
 */
@Composable
private fun IgnoredRow(
    message: RawMessage,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KharchaCard(modifier = modifier, padding = 11.dp, onClick = onRestore) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KharchaSpacing.sm),
        ) {
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = ignoreReasonPill(message.ignoreReason),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .border(1.dp, hairlineStrong, KharchaPillShape)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(KharchaSpacing.sm))
        Mini(text = detail)
    }
}

/** What could be read out of a message without fully parsing it. */
internal data class ExtractedFields(
    val amountText: String?,
    val isCredit: Boolean,
    val counterparty: String?,
    /**
     * The same amount as a bare number — "1234.56", no separators and no currency —
     * ready to prefill the Add sheet's amount field. Kept separate from [amountText],
     * which carries a sign and thousands separators and would not parse back.
     */
    val amountForEntry: String? = null,
)

/**
 * Every way the bank writes money in an alert: `NPR 1,234.56`, `Rs. 500`, `RS.500`,
 * `NRs 1,200.00`, `INR 900`. Matched case-insensitively, but only at a word boundary,
 * so the "rs" inside "hrs" or a reference number is not read as a currency marker.
 */
private val AMOUNT_REGEX = Regex(
    """(?<![A-Za-z0-9])(?:NPR|NRS|RS|INR|₹|रु)\.?\s*([\d,]+(?:\.\d{1,2})?)""",
    RegexOption.IGNORE_CASE,
)

/**
 * The transacted amount in [body], as `1234.56`, or null if none is written.
 *
 * A bank alert usually carries two amounts — what moved, and the balance left behind.
 * The first one written is the transaction, so any match introduced by a balance word
 * ("Bal", "Balance", "Avl") is skipped rather than being offered as the amount to add.
 */
internal fun extractAmountForEntry(body: String): String? {
    val match = AMOUNT_REGEX.findAll(body).firstOrNull { !isBalanceContext(body, it.range.first) }
        ?: return null
    return match.groupValues[1].replace(",", "").takeIf { parseAmountMinorUnits(it) != null }
}

/** True when the characters just before [index] name a balance rather than a movement. */
private fun isBalanceContext(body: String, index: Int): Boolean {
    val lead = body.substring((index - BALANCE_LOOKBEHIND).coerceAtLeast(0), index).lowercase()
    return lead.contains("bal") || lead.contains("avl") || lead.contains("limit")
}

private const val BALANCE_LOOKBEHIND = 14

/**
 * A best-effort read of an unparsed message, for display only.
 *
 * This is deliberately not the parser. The parser rejected this message; if it
 * could extract these fields reliably the message would not be in this list. What
 * this does is give the reader a head start — an amount and a name to recognise —
 * while leaving the real decision, and the real extraction, to the Add sheet.
 */
internal fun extractFields(body: String): ExtractedFields {
    val amountForEntry = extractAmountForEntry(body)
    val isCredit = body.contains("credited", ignoreCase = true) ||
        body.contains("received", ignoreCase = true)
    val amountText = amountForEntry?.let {
        (if (isCredit) "+" else "−") + formatWithSeparators(it)
    }

    // "Fund Trf to NABIL BANK LTD (ESEW-981…" — take what follows the preposition
    // and stop at the first bracket or slash, which is where the reference soup
    // reliably starts.
    val counterparty = Regex("""(?:to|frm|from)\s+([A-Za-z][A-Za-z .&-]{2,40})""")
        .find(body)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.trimEnd('.', '-')
        ?.takeIf { it.length >= 3 }
        ?.toDisplayName()

    return ExtractedFields(
        amountText = amountText,
        isCredit = isCredit,
        counterparty = counterparty,
        amountForEntry = amountForEntry,
    )
}

/** "1234.5" -> "1,234.50": the display form, grouped and always two decimals. */
private fun formatWithSeparators(plain: String): String {
    val minorUnits = parseAmountMinorUnits(plain) ?: return plain
    val major = (minorUnits / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val fraction = (minorUnits % 100).toString().padStart(2, '0')
    return "$major.$fraction"
}

/** "2 Dec 2024" — the date the message arrived. */
private fun formatMessageDate(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "${date.dayOfMonth} $month ${date.year}"
}

private fun ignoreReasonPill(reason: String?): String = when (reason?.uppercase()) {
    "OTP" -> "OTP"
    "SECURITY", "PASSWORD" -> "SECURITY"
    "PROMO" -> "PROMO"
    "LOGIN" -> "LOGIN"
    "BALANCE" -> "BALANCE"
    "DECLINED" -> "DECLINED"
    else -> reason?.uppercase() ?: "OTHER"
}

/**
 * Turns a raw SMS remark tail into something sane to prefill the Merchant field with.
 * The whole body is never appropriate: it is multi-line, up to 160+ characters,
 * and would be written verbatim into both `merchant` and `remark`.
 */
internal fun merchantHintFrom(body: String): String {
    val singleLine = body.trim().replace(Regex("\\s+"), " ")
    if (singleLine.length <= MERCHANT_HINT_MAX_LENGTH) return singleLine
    // Prefer a word boundary so the hint reads as words, not a mid-word cut.
    val window = singleLine.take(MERCHANT_HINT_MAX_LENGTH)
    val lastSpace = window.lastIndexOf(' ')
    return if (lastSpace >= MERCHANT_HINT_MAX_LENGTH / 2) window.take(lastSpace) else window
}

private const val MERCHANT_HINT_MAX_LENGTH = 40
