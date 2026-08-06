package com.kharcha.app.di

import android.content.Context
import androidx.room.Room
import com.kharcha.app.ingest.BackfillState
import com.kharcha.app.ingest.MessageIngestor
import com.kharcha.app.ingest.backfillDataStore
import com.kharcha.app.ui.onboarding.BackfillGate
import com.kharcha.app.ui.onboarding.DataStoreOnboardingState
import com.kharcha.app.ui.onboarding.OnboardingState
import com.kharcha.app.ui.onboarding.WorkManagerBackfillGate
import com.kharcha.app.ui.onboarding.onboardingDataStore
import com.kharcha.app.notify.AndroidNotificationPoster
import com.kharcha.app.notify.BudgetNotifier
import com.kharcha.app.notify.NotificationPoster
import com.kharcha.data.BudgetAlertStateDao
import com.kharcha.data.BudgetDao
import com.kharcha.data.CategoryDao
import com.kharcha.data.Categorizer
import com.kharcha.data.KharchaDatabase
import com.kharcha.data.RawMessageDao
import com.kharcha.data.ReparseService
import com.kharcha.data.RuleDao
import com.kharcha.data.TransactionDao
import com.kharcha.parser.SblAlertRuleset
import com.kharcha.parser.SenderRuleset
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the app's IO dispatcher, so a ViewModel can move DB work off the main thread. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KharchaDatabase =
        Room.databaseBuilder(context, KharchaDatabase::class.java, "kharcha.db")
            .addCallback(KharchaDatabase.seedCallback)
            .addMigrations(KharchaDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideRawMessageDao(database: KharchaDatabase): RawMessageDao = database.rawMessageDao()

    @Provides
    fun provideTransactionDao(database: KharchaDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideRuleDao(database: KharchaDatabase): RuleDao = database.ruleDao()

    @Provides
    fun provideCategoryDao(database: KharchaDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideBudgetDao(database: KharchaDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun provideBudgetAlertStateDao(database: KharchaDatabase): BudgetAlertStateDao = database.budgetAlertStateDao()

    @Provides
    fun provideNotificationPoster(@ApplicationContext context: Context): NotificationPoster =
        AndroidNotificationPoster(context)

    @Provides
    fun provideSenderRuleset(): SenderRuleset = SblAlertRuleset

    @Provides
    fun provideMessageIngestor(
        rawMessageDao: RawMessageDao,
        transactionDao: TransactionDao,
        ruleset: SenderRuleset,
        ruleDao: RuleDao
    ): MessageIngestor = MessageIngestor(rawMessageDao, transactionDao, ruleset, ruleDao)

    @Provides
    @Singleton
    fun provideBackfillState(@ApplicationContext context: Context): BackfillState =
        BackfillState(context.backfillDataStore)

    @Provides
    @Singleton
    fun provideOnboardingState(@ApplicationContext context: Context): OnboardingState =
        DataStoreOnboardingState(context.onboardingDataStore)

    @Provides
    @Singleton
    fun provideBackfillGate(
        @ApplicationContext context: Context,
        backfillState: BackfillState,
    ): BackfillGate = WorkManagerBackfillGate(context, backfillState)

    /**
     * Task 6's [com.kharcha.data.ReparseService] was never wired up: not provided here, not
     * injected anywhere, `reparseAll()` never called from `:app`. That made spec success
     * criterion 4 ("Improving a rule re-categorizes history without losing manual
     * overrides") unreachable and left the "keep every raw message forever" storage cost
     * buying nothing. The categorizer is built per run from the *current* rule set — see
     * [ReparseService.categorizerFactory].
     */
    @Provides
    @Singleton
    fun provideReparseService(
        rawMessageDao: RawMessageDao,
        transactionDao: TransactionDao,
        ruleset: SenderRuleset,
        ruleDao: RuleDao,
    ): ReparseService = ReparseService(
        rawMessageDao = rawMessageDao,
        transactionDao = transactionDao,
        ruleset = ruleset,
        categorizerFactory = { Categorizer(ruleDao.observeAll().first()) },
    )

    /** Where re-parse and other DB-heavy fan-outs run — never the UI thread. */
    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.System

    @Provides
    @Singleton
    fun provideTimeZone(): TimeZone = TimeZone.currentSystemDefault()
}
