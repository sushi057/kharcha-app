package com.kharcha.app.ui.transactions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A day the user asked to see the transactions for.
 *
 * Carried as a calendar date rather than an epoch range because only
 * [TransactionsViewModel] knows the app's single injected timezone; resolving the
 * day's boundaries anywhere else would be a second definition of "which day is this".
 */
data class OpenDayRequest(val date: LocalDate)

/**
 * The one-way channel from "tapped a bar on the dashboard's daily-spend chart" to
 * "the ledger is now filtered to that day".
 *
 * Dashboard and Transactions are sibling bottom-nav destinations with independent
 * ViewModels, so the alternative is a nav argument on the Transactions route — which
 * would give that tab two routes and lose its saved back-stack state on every jump.
 * A request is consumed once, so returning to the tab later does not silently re-apply
 * a filter the user has since cleared.
 */
@Singleton
class OpenDayRequests @Inject constructor() {
    private val _requests = MutableStateFlow<OpenDayRequest?>(null)
    val requests: StateFlow<OpenDayRequest?> = _requests.asStateFlow()

    fun request(date: LocalDate) {
        _requests.value = OpenDayRequest(date)
    }

    fun consume() {
        _requests.value = null
    }
}
