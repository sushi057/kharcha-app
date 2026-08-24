@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kharcha.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharcha.app.ui.components.CardLabel
import com.kharcha.app.ui.components.IconAction
import com.kharcha.app.ui.components.KharchaAppBar
import com.kharcha.app.ui.components.KharchaButton
import com.kharcha.app.ui.components.KharchaButtonStyle
import com.kharcha.app.ui.components.KharchaCard
import com.kharcha.app.ui.components.KharchaChip
import com.kharcha.app.ui.components.Mini
import com.kharcha.app.ui.components.SegmentedControl
import com.kharcha.app.ui.theme.KharchaSpacing

/**
 * Settings: export, appearance and about. Reached from the gear in the Dashboard
 * app bar rather than from the bottom bar — it is a place you visit occasionally,
 * and a fifth tab would crowd the four you visit daily.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri -> viewModel.performExportToUri(context, uri) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = modifier.fillMaxSize().padding(innerPadding)) {
            KharchaAppBar(
                title = "Settings",
                leading = {
                    IconAction(
                        icon = Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                    )
                },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = KharchaSpacing.screenGutter),
                contentPadding = PaddingValues(bottom = KharchaSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(KharchaSpacing.md),
            ) {
                item(key = "export-header") { CardLabel("Export") }
                item(key = "export-section") {
                    ExportCard(
                        state = state,
                        exportState = exportState,
                        onPresetSelect = viewModel::setExportDatePreset,
                        onFormatChange = viewModel::setExportFormat,
                        onExportClick = {
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = state.exportFormat.mimeType
                                putExtra(Intent.EXTRA_TITLE, viewModel.getExportFilename())
                            }
                            filePickerLauncher.launch(intent)
                        },
                    )
                }

                item(key = "appearance-header") { CardLabel("Appearance") }
                item(key = "appearance-section") {
                    AppearanceCard(
                        themeMode = state.themeMode,
                        onThemeModeChange = viewModel::setThemeMode,
                    )
                }

                item(key = "about-header") { CardLabel("About") }
                item(key = "about-section") { AboutCard() }
            }
        }
    }
}

@Composable
private fun ExportCard(
    state: SettingsUiState,
    exportState: ExportState,
    onPresetSelect: (ExportDatePreset) -> Unit,
    onFormatChange: (ExportFormat) -> Unit,
    onExportClick: () -> Unit,
) {
    KharchaCard {
        Column(verticalArrangement = Arrangement.spacedBy(KharchaSpacing.md)) {
            Mini("Date range")
            // A wrapping row of chips rather than a five-high stack of buttons: the
            // presets are alternatives to each other, and only one is ever chosen.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(KharchaSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(KharchaSpacing.xs),
            ) {
                ALL_EXPORT_PRESETS.forEach { preset ->
                    KharchaChip(
                        label = preset.label,
                        selected = state.exportPreset == preset,
                        onClick = { onPresetSelect(preset) },
                    )
                }
            }

            state.exportDateRange?.let { range ->
                Text(
                    text = "${state.exportTransactionCount} " +
                        (if (state.exportTransactionCount == 1) "transaction" else "transactions") +
                        " · ${range.formatDisplay()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Mini("Format")
            SegmentedControl(
                options = ExportFormat.entries.map { it.name },
                selectedIndex = ExportFormat.entries.indexOf(state.exportFormat),
                onSelect = { onFormatChange(ExportFormat.entries[it]) },
            )

            val running = exportState is ExportState.InProgress
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KharchaSpacing.sm),
            ) {
                KharchaButton(
                    text = if (running) "Exporting…" else "Export",
                    style = KharchaButtonStyle.Filled,
                    enabled = !running && state.exportTransactionCount > 0,
                    onClick = onExportClick,
                )
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    KharchaCard {
        Column(verticalArrangement = Arrangement.spacedBy(KharchaSpacing.md)) {
            Mini("Theme")
            SegmentedControl(
                options = ThemeMode.entries.map { it.toDisplayName() },
                selectedIndex = ThemeMode.entries.indexOf(themeMode),
                onSelect = { onThemeModeChange(ThemeMode.entries[it]) },
            )
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    KharchaCard {
        Column(verticalArrangement = Arrangement.spacedBy(KharchaSpacing.sm)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Kharcha",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Mini(if (versionName.isEmpty()) "On-device only" else "Version $versionName · on-device only")
                }
            }
            Text(
                text = "Fonts under the SIL Open Font License 1.1: Calistoga, Inter, " +
                    "JetBrains Mono. Licence texts ship with the source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
