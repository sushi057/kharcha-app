package com.kharcha.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.app.di.IoDispatcher
import com.kharcha.app.export.ExportFileNamer
import com.kharcha.app.export.ExportWriter
import com.kharcha.app.export.TransactionExporter
import com.kharcha.data.CategoryDao
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import javax.inject.Inject

/**
 * Export progress/result state.
 */
sealed class ExportState {
    data object Idle : ExportState()
    data object InProgress : ExportState()
    data class Success(val message: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

/**
 * A selected date range and format for export.
 */
data class ExportSelection(
    val dateRange: ExportDateRange,
    val format: ExportFormat,
)

enum class ExportFormat(val mimeType: String, val extension: String) {
    CSV("text/csv", "csv"),
    JSON("application/json", "json"),
}

/**
 * Settings screen UI state.
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val exportDateRange: ExportDateRange? = null,
    /**
     * Which preset produced [exportDateRange]. Held explicitly rather than recovered by
     * comparing the range against every preset's freshly-computed range: "This month" and
     * "This year" coincide every January, and a range set by hand matches no preset at all.
     */
    val exportPreset: ExportDatePreset = ExportDatePreset.ThisMonth,
    val exportFormat: ExportFormat = ExportFormat.CSV,
    val exportTransactionCount: Int = 0,
    val exportState: ExportState = ExportState.Idle,
)

/**
 * Drives the Settings screen: theme preferences, export functionality,
 * and other settings. Date ranges and export formatting are computed
 * deterministically from the current date and persisted format selection.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsPreferences: SettingsPreferences,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val exporter: TransactionExporter,
    private val fileNamer: ExportFileNamer,
    private val clock: Clock,
    private val zone: TimeZone,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    // UI state
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    private val _exportDateRange = MutableStateFlow<ExportDateRange?>(null)
    private val _exportPreset = MutableStateFlow<ExportDatePreset>(ExportDatePreset.ThisMonth)
    private val _exportFormat = MutableStateFlow(ExportFormat.CSV)
    private val _exportTransactionCount = MutableStateFlow(0)

    val state: StateFlow<SettingsUiState> = combine(
        settingsPreferences.observeThemeMode(),
        _exportDateRange,
        _exportPreset,
        _exportFormat,
        combine(_exportTransactionCount, _exportState, ::Pair),
    ) { themeMode, dateRange, preset, format, (count, state) ->
        SettingsUiState(
            themeMode = themeMode,
            exportDateRange = dateRange,
            exportPreset = preset,
            exportFormat = format,
            exportTransactionCount = count,
            exportState = state,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(),
    )

    init {
        // Initialize export date range to "This month"
        _exportDateRange.value = ExportDatePreset.ThisMonth.toDateRange(clock, zone)
        refreshExportCount()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsPreferences.setThemeMode(mode)
        }
    }

    fun setExportDatePreset(preset: ExportDatePreset) {
        _exportPreset.value = preset
        _exportDateRange.value = preset.toDateRange(clock, zone)
        refreshExportCount()
    }

    fun setCustomDateRange(range: ExportDateRange) {
        _exportDateRange.value = range
        refreshExportCount()
    }

    fun setExportFormat(format: ExportFormat) {
        _exportFormat.value = format
    }

    /**
     * Perform the export: generate the file content and write it to the given URI
     * on a background dispatcher. Updates [exportState] to reflect progress/result.
     */
    fun performExportToUri(
        context: Context,
        targetUri: Uri,
    ) {
        val range = _exportDateRange.value ?: return
        val format = _exportFormat.value

        _exportState.value = ExportState.InProgress

        viewModelScope.launch {
            try {
                val content = withContext(ioDispatcher) {
                    val txnList = transactionDao.observeAll().first()
                    val categoryNames = categoryNames()

                    when (format) {
                        ExportFormat.CSV -> exporter.toCsv(
                            transactions = txnList,
                            zone = zone,
                            startEpochMillis = range.startEpochMillis,
                            endEpochMillis = range.endEpochMillis,
                            categoryNames = categoryNames,
                        )
                        ExportFormat.JSON -> exporter.toJson(
                            transactions = txnList,
                            zone = zone,
                            startEpochMillis = range.startEpochMillis,
                            endEpochMillis = range.endEpochMillis,
                            categoryNames = categoryNames,
                        )
                    }
                }

                withContext(ioDispatcher) {
                    val writer = ExportWriter(context)
                    writer.write(targetUri, content, format.mimeType)
                }

                _exportState.value = ExportState.Success("Export saved successfully")
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(
                    "Export failed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    /**
     * Generate export content without writing to a file.
     * Used for testing and previews.
     */
    suspend fun generateExportContent(format: ExportFormat): String {
        val range = _exportDateRange.value ?: return ""
        val txnList = transactionDao.observeAll().first()
        val categoryNames = categoryNames()

        return when (format) {
            ExportFormat.CSV -> exporter.toCsv(
                transactions = txnList,
                zone = zone,
                startEpochMillis = range.startEpochMillis,
                endEpochMillis = range.endEpochMillis,
                categoryNames = categoryNames,
            )
            ExportFormat.JSON -> exporter.toJson(
                transactions = txnList,
                zone = zone,
                startEpochMillis = range.startEpochMillis,
                endEpochMillis = range.endEpochMillis,
                categoryNames = categoryNames,
            )
        }
    }

    /**
     * Category id to name, so the export's `category` column carries the thing the user
     * spent the most effort on — their own categorisation. It shipped as an empty column
     * with a TODO next to it.
     */
    private suspend fun categoryNames(): Map<Long, String> =
        categoryDao.observeAll().first().associate { it.id to it.name }

    fun getExportFilename(): String {
        val range = _exportDateRange.value ?: return "kharcha.${_exportFormat.value.extension}"
        val format = _exportFormat.value

        return when (format) {
            ExportFormat.CSV -> fileNamer.csvFilename(range.startEpochMillis, range.endEpochMillis)
            ExportFormat.JSON -> fileNamer.jsonFilename(range.startEpochMillis, range.endEpochMillis)
        }
    }

    private fun refreshExportCount() {
        val range = _exportDateRange.value ?: return

        viewModelScope.launch {
            val count = withContext(ioDispatcher) {
                val txnList = transactionDao.observeAll().first()
                txnList.count { txn ->
                    txn.occurredAtEpochMillis >= range.startEpochMillis &&
                    txn.occurredAtEpochMillis <= range.endEpochMillis
                }
            }
            _exportTransactionCount.value = count
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }
}
