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
import com.kharcha.data.BudgetDao
import com.kharcha.data.CategoryDao
import com.kharcha.data.KharchaDatabase
import com.kharcha.data.RawMessageDao
import com.kharcha.data.RuleDao
import com.kharcha.data.TransactionDao
import com.kharcha.parser.SblAlertRuleset
import com.kharcha.parser.SenderRuleset
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KharchaDatabase =
        Room.databaseBuilder(context, KharchaDatabase::class.java, "kharcha.db")
            .addCallback(KharchaDatabase.seedCallback)
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
}
