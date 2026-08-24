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
    /** True once a real `checkSelfPermission` result has arrived — see [isResolved]. */
    val permissionChecked: Boolean = false,
    /** True once the persisted onboarding flag has been read back — see [isResolved]. */
    val onboardingLoaded: Boolean = false,
) {
    /**
     * Both inputs default to `false`, which is indistinguishable from a genuine
     * "no permission, onboarding already seen" — the exact combination that shows the denied
     * banner. Without this gate every cold start flashes the banner (and briefly the whole
     * onboarding screen) before the asynchronous checks land, however the permission actually
     * stands. Nothing is decided until both answers are real.
     */
    val isResolved: Boolean
        get() = permissionChecked && onboardingLoaded

    /** Startup decision: show the full-screen explainer, per [shouldShowOnboarding]. */
    val showOnboarding: Boolean
        get() = isResolved && shouldShowOnboarding(hasSmsPermission, onboardingSeen)

    /** Denial keeps the app usable; this drives the persistent re-request banner. */
    val showDeniedBanner: Boolean
        get() = isResolved && shouldShowDeniedBanner(hasSmsPermission, onboardingSeen)
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
            _state.value = _state.value.copy(
                onboardingSeen = onboardingState.isOnboardingSeen(),
                onboardingLoaded = true,
            )
        }
    }

    /**
     * Called with the current permission-check result. This must run on every resume, not once
     * at startup: SMS access can be granted or revoked in system Settings while the app sits in
     * the background, and a one-shot check leaves the banner asserting "SMS access is off" long
     * after the user turned it on.
     */
    fun refreshPermissionState(hasSmsPermission: Boolean) {
        _state.value = _state.value.copy(
            hasSmsPermission = hasSmsPermission,
            permissionChecked = true,
        )
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
