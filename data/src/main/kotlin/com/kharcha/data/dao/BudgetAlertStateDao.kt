package com.kharcha.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kharcha.parser.Currency

@Dao
interface BudgetAlertStateDao {
    @Query("SELECT * FROM budget_alert_states WHERE categoryId = :categoryId AND currency = :currency AND yearMonth = :yearMonth")
    suspend fun get(categoryId: Long, currency: Currency, yearMonth: String): BudgetAlertStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: BudgetAlertStateEntity): Long
}
