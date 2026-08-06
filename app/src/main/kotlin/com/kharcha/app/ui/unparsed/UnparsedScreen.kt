package com.kharcha.app.ui.unparsed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.kharcha.app.ui.transactions.TransactionEditSheet
import com.kharcha.data.RawMessage

/**
 * The unparsed inbox: raw `SBL_Alert` messages the parser didn't recognize. This is the
 * user's early warning that the bank changed its SMS format, so it deliberately never shows
 * `ignored` (OTP/purchase-code) messages — see [com.kharcha.data.RawMessageDao.observeUnparsed].
 * "Add as transaction" reuses [TransactionEditSheet]'s manual-entry mode, prefilled from the
 * raw body, rather than building a second entry form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnparsedScreen(
    modifier: Modifier = Modifier,
    viewModel: UnparsedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selectedForManualEntry by remember { mutableStateOf<RawMessage?>(null) }

    Scaffold(modifier = modifier) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (state.messages.isEmpty()) {
                Text(
                    text = "No unparsed messages. New SMS the parser can't read will show up here.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(KharchaSpacing.lg),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.messages, key = { it.id }) { message ->
                        UnparsedMessageCard(
                            message = message,
                            onAddAsTransaction = { selectedForManualEntry = message },
                            onDismiss = { viewModel.dismiss(message.id) },
                        )
                    }
                }
            }
        }
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
        )
    }
}

/**
 * Turns a raw SMS body into something sane to prefill the Merchant field with.
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

@Composable
private fun UnparsedMessageCard(
    message: RawMessage,
    onAddAsTransaction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KharchaSpacing.md, vertical = KharchaSpacing.xs),
    ) {
        Column(modifier = Modifier.padding(KharchaSpacing.md)) {
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(KharchaSpacing.sm))
            Row {
                TextButton(onClick = onAddAsTransaction) {
                    Text(text = "Add as transaction")
                }
                TextButton(onClick = onDismiss) {
                    Text(text = "Dismiss")
                }
            }
        }
    }
}
