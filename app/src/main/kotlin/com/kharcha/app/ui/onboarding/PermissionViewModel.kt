package com.kharcha.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionUiState(
    val hasSmsPermission: Boolean = false,
    val onboardingSeen: Boolean = false,
) {
    /** Startup decision: show the full-screen explainer, per [shouldShowOnboarding]. */
    val showOnboarding: Boolean
        get() = shouldShowOnboarding(hasSmsPermission, onboardingSeen)

    /** Denial keeps the app usable; this drives the persistent re-request banner. */
    val showDeniedBanner: Boolean
        get() = shouldShowDeniedBanner(hasSmsPermission, onboardingSeen)
}

/**
 * Pure predicate for "should [PermissionScreen] be shown" — deliberately not buried inside a
 * composable, so the startup decision is unit-testable without Robolectric/Compose. Onboarding
 * shows exactly when SMS permission is missing and the user has never responded to the
 * explainer yet (grant or deny both count as "responded" — see [shouldShowDeniedBanner] for
 * what happens after a denial).
 */
fun shouldShowOnboarding(hasSmsPermission: Boolean, onboardingSeen: Boolean): Boolean =
    !hasSmsPermission && !onboardingSeen

/**
 * After the user has been through onboarding and denied SMS permission, the app runs in
 * manual-entry-only mode with a persistent banner (ruling 4) instead of dead-ending or
 * re-showing the full explainer on every screen.
 */
fun shouldShowDeniedBanner(hasSmsPermission: Boolean, onboardingSeen: Boolean): Boolean =
    !hasSmsPermission && onboardingSeen

/**
 * Drives [PermissionScreen] and the app-wide denied banner. On grant, enqueues
 * [com.kharcha.app.ingest.BackfillWorker] through [BackfillGate] exactly once ever — gated on
 * [BackfillGate.isComplete] so a later re-grant (e.g. after the user re-enables SMS access in
 * system settings) does not re-scan an inbox that's already been imported.
 */
@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val onboardingState: OnboardingState,
    private val backfillGate: BackfillGate,
) : ViewModel() {

    private val _state = MutableStateFlow(PermissionUiState())
    val state: StateFlow<PermissionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(onboardingSeen = onboardingState.isOnboardingSeen())
        }
    }

    /** Called once with the current permission-check result, e.g. on app startup. */
    fun refreshPermissionState(hasSmsPermission: Boolean) {
        _state.value = _state.value.copy(hasSmsPermission = hasSmsPermission)
    }

    /** Called with the outcome of the system permission dialog (or a re-request from the banner). */
    fun onPermissionsResult(smsGranted: Boolean) {
        _state.value = _state.value.copy(hasSmsPermission = smsGranted)
        viewModelScope.launch {
            onboardingState.markOnboardingSeen()
            _state.value = _state.value.copy(onboardingSeen = true)
            if (smsGranted && !backfillGate.isComplete()) {
                backfillGate.enqueueOnce()
            }
        }
    }
}
