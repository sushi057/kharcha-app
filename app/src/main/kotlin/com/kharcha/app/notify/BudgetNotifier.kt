package com.kharcha.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kharcha.app.dashboard.DashboardAggregator
import com.kharcha.data.BudgetAlertStateDao
import com.kharcha.data.BudgetAlertStateEntity
import com.kharcha.data.BudgetDao
import com.kharcha.data.CategoryDao
import com.kharcha.data.TransactionDao
import com.kharcha.parser.Currency
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/** Fires at most once each, per (category, month) — see [BudgetNotifier] and ruling 1. */
enum class BudgetAlert { THRESHOLD_CROSSED, EXCEEDED }

/**
 * Posts the actual Android notification for a [BudgetAlert]. Kept as a separate interface
 * (ruling 4) so [BudgetNotifier]'s decision logic — whether an alert should fire — is
 * testable without a [NotificationManager] or [android.content.Context] in the loop.
 */
fun interface NotificationPoster {
    fun post(categoryName: String, alert: BudgetAlert, spentMinorUnits: Long, limitMinorUnits: Long, currency: Currency)
}

/**
 * Real [NotificationPoster]: posts an Android notification, degrading safely — silently
 * skipping the post, never crashing — if `POST_NOTIFICATIONS` was denied or is simply
 * unanswered (ruling: "never assume the permission was granted").
 */
class AndroidNotificationPoster(private val context: Context) : NotificationPoster {

    override fun post(categoryName: String, alert: BudgetAlert, spentMinorUnits: Long, limitMinorUnits: Long, currency: Currency) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel()

        val title = when (alert) {
            BudgetAlert.THRESHOLD_CROSSED -> "Approaching your $categoryName budget"
            BudgetAlert.EXCEEDED -> "Over your $categoryName budget"
        }
        val spent = formatMinor(spentMinorUnits, currency)
        val limit = formatMinor(limitMinorUnits, currency)
        val text = "Spent $spent of $limit this month"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        // A stable, non-clobbering id per (category, alert kind) so THRESHOLD_CROSSED and
        // a later EXCEEDED for the same category both show, rather than one overwriting
        // the other.
        val notificationId = categoryName.hashCode() * 2 + alert.ordinal
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun formatMinor(minorUnits: Long, currency: Currency): String {
        val major = minorUnits / 100
        val fraction = (minorUnits % 100).toString().padStart(2, '0')
        return "${currency.name} $major.$fraction"
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Budget alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "budget_alerts"
    }
}

/**
 * Decides whether a per-category monthly budget has just crossed its alert threshold or
 * been exceeded, persists that fact so it fires at most once per (category, month) —
 * surviving process death (ruling 1) — and posts the notification as a side effect
 * (ruling 4: the decision itself is the pure, testable part; [poster] is the only piece
 * that touches Android).
 *
 * Reuses [DashboardAggregator] (Task 10) for the month-to-date spend figure rather than
 * reimplementing the excluded/credit/income/fee filtering rules.
 */
class BudgetNotifier @Inject constructor(
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val alertStateDao: BudgetAlertStateDao,
    private val clock: Clock,
    private val zone: TimeZone,
    private val poster: NotificationPoster,
) {
    suspend fun checkAndNotify(categoryId: Long): BudgetAlert? {
        val budget = budgetDao.observeAll().first().firstOrNull { it.categoryId == categoryId } ?: return null
        val categories = categoryDao.observeAll().first()
        val category = categories.firstOrNull { it.id == categoryId } ?: return null
        val transactions = transactionDao.observeAll().first()

        val today = clock.now().toLocalDateTime(zone).date
        val monthStart = LocalDate(today.year, today.monthNumber, 1)
        val monthEndExclusive = monthStart.plus(1, DateTimeUnit.MONTH)
        val yearMonth = "%04d-%02d".format(today.year, today.monthNumber)

        // A budget whose currency has no spend this month is simply at zero (ruling 3) —
        // DashboardAggregator.byCategory just won't have an entry for it, and the sum
        // below is 0 rather than an error.
        val aggregate = DashboardAggregator.aggregate(
            transactions = transactions,
            categories = categories,
            monthStartEpochMillis = monthStart.atStartOfDayIn(zone).toEpochMilliseconds(),
            monthEndExclusiveEpochMillis = monthEndExclusive.atStartOfDayIn(zone).toEpochMilliseconds(),
            zone = zone,
        )
        val spentMinorUnits = aggregate.byCategory
            .filter { it.categoryId == categoryId && it.currency == budget.currency }
            .sumOf { it.total.minorUnits }

        val priorState = alertStateDao.get(categoryId, budget.currency, yearMonth)
        val alert = decide(
            spentMinorUnits = spentMinorUnits,
            limitMinorUnits = budget.monthlyLimitMinorUnits,
            thresholdPercent = budget.alertThresholdPercent,
            thresholdAlreadyFired = priorState?.thresholdFired ?: false,
            exceededAlreadyFired = priorState?.exceededFired ?: false,
        ) ?: return null

        alertStateDao.upsert(
            BudgetAlertStateEntity(
                id = priorState?.id ?: 0,
                categoryId = categoryId,
                currency = budget.currency,
                yearMonth = yearMonth,
                // Exceeding implies the threshold was crossed too (thresholdPercent <= 100),
                // so EXCEEDED marks both flags — otherwise a later call could spuriously
                // report THRESHOLD_CROSSED after the budget is already blown.
                thresholdFired = true,
                exceededFired = (priorState?.exceededFired ?: false) || alert == BudgetAlert.EXCEEDED,
            )
        )

        poster.post(category.name, alert, spentMinorUnits, budget.monthlyLimitMinorUnits, budget.currency)
        return alert
    }

    companion object {
        /**
         * Pure decision function, deliberately free of DAOs/Android (ruling 4). Integer-only
         * threshold comparison: `spent * 100 >= limit * thresholdPercent`, never a Double ratio.
         */
        internal fun decide(
            spentMinorUnits: Long,
            limitMinorUnits: Long,
            thresholdPercent: Int,
            thresholdAlreadyFired: Boolean,
            exceededAlreadyFired: Boolean,
        ): BudgetAlert? {
            if (limitMinorUnits <= 0) return null

            val exceeded = spentMinorUnits >= limitMinorUnits
            if (exceeded && !exceededAlreadyFired) return BudgetAlert.EXCEEDED

            val crossedThreshold = spentMinorUnits * 100 >= limitMinorUnits * thresholdPercent
            if (crossedThreshold && !thresholdAlreadyFired) return BudgetAlert.THRESHOLD_CROSSED

            return null
        }
    }
}
