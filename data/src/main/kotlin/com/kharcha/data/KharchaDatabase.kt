package com.kharcha.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RawMessage::class,
        TransactionEntity::class,
        CategoryEntity::class,
        RuleEntity::class,
        BudgetEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class KharchaDatabase : RoomDatabase() {
    abstract fun rawMessageDao(): RawMessageDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ruleDao(): RuleDao
    abstract fun budgetDao(): BudgetDao
}
