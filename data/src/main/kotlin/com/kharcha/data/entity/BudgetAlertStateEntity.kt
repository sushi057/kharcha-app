package com.kharcha.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kharcha.parser.Currency

/**
 * Persists whether a budget alert has already fired for a (category, currency, month) so
 * [com.kharcha.app.notify.BudgetNotifier] can enforce "each alert fires at most once per
 * month" (ruling 1) across process death — this cannot live in memory. `yearMonth` is
 * "YYYY-MM" in the injected [kotlinx.datetime.TimeZone], matching the dashboard's
 * month-to-date definition (ruling 2). THRESHOLD_CROSSED and EXCEEDED are independent
 * gates: crossing the threshold fires once, exceeding the limit fires once more, later,
 * in the same month.
 */
@Entity(
    tableName = "budget_alert_states",
    indices = [Index(value = ["categoryId", "currency", "yearMonth"], unique = true)]
)
data class BudgetAlertStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val currency: Currency,
    val yearMonth: String,
    val thresholdFired: Boolean,
    val exceededFired: Boolean
)
