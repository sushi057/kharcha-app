package com.kharcha.app.ui.settings

import com.kharcha.app.export.ExportFileNamer
import com.kharcha.app.export.TransactionExporter
import com.kharcha.data.TransactionDao
import com.kharcha.data.CategoryDao
import com.kharcha.data.CategoryEntity
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeSettingsPreferences : SettingsPreferences {
    private val themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)

    override fun observeThemeMode(): Flow<ThemeMode> = themeModeFlow

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeModeFlow.value = mode
    }
}

private class FakeTransactionDao(seed: List<TransactionEntity> = emptyList()) : TransactionDao {
    private val flow = MutableStateFlow(seed)

    override suspend fun insert(transaction: TransactionEntity): Long = 0L
    override suspend fun update(transaction: TransactionEntity) = Unit
    override suspend fun delete(transaction: TransactionEntity) = Unit
    override suspend fun getById(id: Long): TransactionEntity? = flow.value.find { it.id == id }
    override suspend fun getByRawMessageId(rawMessageId: Long): TransactionEntity? = null
    override fun observeAll(): Flow<List<TransactionEntity>> = flow

    fun setTransactions(transactions: List<TransactionEntity>) {
        flow.value = transactions
    }
}

private class FakeCategoryDao(private val categories: List<CategoryEntity> = emptyList()) : CategoryDao {
    override suspend fun insert(category: CategoryEntity): Long = 0L
    override suspend fun update(category: CategoryEntity) = Unit
    override suspend fun delete(category: CategoryEntity) = Unit
    override suspend fun getById(id: Long): CategoryEntity? = categories.find { it.id == id }
    override fun observeAll(): Flow<List<CategoryEntity>> = MutableStateFlow(categories)
}

class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val zone = TimeZone.UTC
    private val fixedNow = LocalDate(2026, 8, 6).atStartOfDayIn(zone)
    private val fixedClock = FixedClock(fixedNow)

    private lateinit var settingsPreferences: FakeSettingsPreferences
    private lateinit var transactionDao: FakeTransactionDao
    private var categoryDao: FakeCategoryDao = FakeCategoryDao()
    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsPreferences = FakeSettingsPreferences()
        transactionDao = FakeTransactionDao()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            settingsPreferences = settingsPreferences,
            transactionDao = transactionDao,
            categoryDao = categoryDao,
            exporter = TransactionExporter(),
            fileNamer = ExportFileNamer(zone),
            clock = fixedClock,
            zone = zone,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun `theme mode change persists through preferences`() = runTest {
        viewModel = createViewModel()

        viewModel.setThemeMode(ThemeMode.DARK)

        val state = viewModel.state.value
        assertEquals(ThemeMode.DARK, state.themeMode)
    }

    @Test
    fun `export format selection changes state`() = runTest {
        viewModel = createViewModel()

        viewModel.setExportFormat(ExportFormat.JSON)

        assertEquals(ExportFormat.JSON, viewModel.state.value.exportFormat)
    }

    @Test
    fun `this month preset sets correct date range`() = runTest {
        viewModel = createViewModel()

        viewModel.setExportDatePreset(ExportDatePreset.ThisMonth)

        val range = viewModel.state.value.exportDateRange
        assertEquals(LocalDate(2026, 8, 1), range?.startDate)
        assertEquals(LocalDate(2026, 8, 6), range?.endDate)
    }

    @Test
    fun `last month preset sets correct date range`() = runTest {
        viewModel = createViewModel()

        viewModel.setExportDatePreset(ExportDatePreset.LastMonth)

        val range = viewModel.state.value.exportDateRange
        assertEquals(LocalDate(2026, 7, 1), range?.startDate)
        assertEquals(LocalDate(2026, 7, 31), range?.endDate)
    }

    @Test
    fun `export transaction count matches filtered transactions`() = runTest {
        val txn1 = TransactionEntity(
            id = 1,
            rawMessageId = null,
            sourceAccount = "acct",
            amountMinorUnits = 10000,
            currency = Currency.NPR,
            direction = Direction.DEBIT,
            occurredAtEpochMillis = LocalDate(2026, 8, 1).atStartOfDayIn(zone).toEpochMilliseconds(),
            merchant = "Merchant A",
            remark = "",
            balanceAfterMinorUnits = null,
            categoryId = null,
            categoryIsManualOverride = false,
            excludedFromSpending = false,
            isManualEntry = false,
        )
        val txn2 = TransactionEntity(
            id = 2,
            rawMessageId = null,
            sourceAccount = "acct",
            amountMinorUnits = 5000,
            currency = Currency.NPR,
            direction = Direction.DEBIT,
            occurredAtEpochMillis = LocalDate(2026, 8, 5).atStartOfDayIn(zone).toEpochMilliseconds(),
            merchant = "Merchant B",
            remark = "",
            balanceAfterMinorUnits = null,
            categoryId = null,
            categoryIsManualOverride = false,
            excludedFromSpending = false,
            isManualEntry = false,
        )
        val txn3 = TransactionEntity(
            id = 3,
            rawMessageId = null,
            sourceAccount = "acct",
            amountMinorUnits = 2000,
            currency = Currency.NPR,
            direction = Direction.DEBIT,
            occurredAtEpochMillis = LocalDate(2026, 7, 25).atStartOfDayIn(zone).toEpochMilliseconds(),
            merchant = "Merchant C",
            remark = "",
            balanceAfterMinorUnits = null,
            categoryId = null,
            categoryIsManualOverride = false,
            excludedFromSpending = false,
            isManualEntry = false,
        )

        transactionDao.setTransactions(listOf(txn1, txn2, txn3))
        viewModel = createViewModel()

        // This month should count only txn1 and txn2
        viewModel.setExportDatePreset(ExportDatePreset.ThisMonth)

        assertEquals(2, viewModel.state.value.exportTransactionCount)
    }

    @Test
    fun `csv filename format is correct`() = runTest {
        viewModel = createViewModel()

        val filename = viewModel.getExportFilename()

        assertTrue(filename.endsWith(".csv"))
        assertTrue(filename.startsWith("kharcha-"))
    }

    @Test
    fun `json filename format is correct`() = runTest {
        viewModel = createViewModel()
        viewModel.setExportFormat(ExportFormat.JSON)

        val filename = viewModel.getExportFilename()

        assertTrue(filename.endsWith(".json"))
        assertTrue(filename.startsWith("kharcha-"))
    }

    @Test
    fun `export format mime type is correct for csv`() = runTest {
        viewModel = createViewModel()
        viewModel.setExportFormat(ExportFormat.CSV)

        val format = viewModel.state.value.exportFormat
        assertEquals("text/csv", format.mimeType)
    }

    @Test
    fun `export format mime type is correct for json`() = runTest {
        viewModel = createViewModel()
        viewModel.setExportFormat(ExportFormat.JSON)

        val format = viewModel.state.value.exportFormat
        assertEquals("application/json", format.mimeType)
    }

    @Test
    fun `export state starts as idle`() = runTest {
        viewModel = createViewModel()

        assertTrue(viewModel.exportState.value is ExportState.Idle)
    }

    @Test
    fun `clear export state resets to idle`() = runTest {
        viewModel = createViewModel()

        // Simulate an error state
        (viewModel.exportState as MutableStateFlow<ExportState>).value = ExportState.Error("test")

        viewModel.clearExportState()

        assertTrue(viewModel.exportState.value is ExportState.Idle)
    }
}
