package com.kharcha.app.ui

import androidx.lifecycle.ViewModel
import com.kharcha.app.ui.transactions.OpenDayRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * The nav shell's own dependencies. [KharchaNavHost] is a composable, so it cannot be
 * injected directly; this exists purely to hand it the app-scoped singletons it needs to
 * route a cross-screen action — currently just "show me that day's transactions".
 */
@HiltViewModel
class NavShellViewModel @Inject constructor(
    val openDayRequests: OpenDayRequests,
) : ViewModel()
