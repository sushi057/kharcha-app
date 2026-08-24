package com.kharcha.app.ui.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeOnboardingState : OnboardingState {
    var seen = false
    override suspend fun isOnboardingSeen(): Boolean = seen
    override suspend fun markOnboardingSeen() {
        seen = true
    }
}

private class FakeBackfillGate : BackfillGate {
    var complete = false
    var enqueueCount = 0
    override suspend fun isComplete(): Boolean = complete
    override fun enqueueOnce() {
        enqueueCount++
    }

    var rescanCount = 0
    override suspend fun rescan() {
        rescanCount++
    }
}

class PermissionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `granting permission enqueues backfill exactly once when it has never run`() = runTest {
        val backfillGate = FakeBackfillGate()
        val onboardingState = FakeOnboardingState()
        val vm = PermissionViewModel(onboardingState, backfillGate)

        vm.onPermissionsResult(smsGranted = true)

        assertEquals(1, backfillGate.enqueueCount)
        assertTrue(onboardingState.seen)
        assertTrue(vm.state.value.hasSmsPermission)
    }

    @Test
    fun `granting permission again after backfill already ran does not re-enqueue`() = runTest {
        val backfillGate = FakeBackfillGate().apply { complete = true }
        val onboardingState = FakeOnboardingState()
        val vm = PermissionViewModel(onboardingState, backfillGate)

        vm.onPermissionsResult(smsGranted = true)

        assertEquals(0, backfillGate.enqueueCount)
    }

    @Test
    fun `denying permission marks onboarding seen but never enqueues backfill`() = runTest {
        val backfillGate = FakeBackfillGate()
        val onboardingState = FakeOnboardingState()
        val vm = PermissionViewModel(onboardingState, backfillGate)

        vm.onPermissionsResult(smsGranted = false)

        assertEquals(0, backfillGate.enqueueCount)
        assertTrue(onboardingState.seen)
        assertFalse(vm.state.value.hasSmsPermission)
    }

    @Test
    fun `nothing is decided until both the permission check and the onboarding flag have landed`() {
        // The all-defaults state is exactly "no permission, onboarding seen" — the banner
        // combination — so an ungated state would flash the banner on every cold start.
        val unresolved = PermissionUiState(hasSmsPermission = false, onboardingSeen = true)
        assertFalse(unresolved.isResolved)
        assertFalse(unresolved.showDeniedBanner)
        assertFalse(unresolved.showOnboarding)

        val halfResolved = unresolved.copy(permissionChecked = true)
        assertFalse(halfResolved.isResolved)
        assertFalse(halfResolved.showDeniedBanner)

        val resolved = halfResolved.copy(onboardingLoaded = true)
        assertTrue(resolved.isResolved)
        assertTrue(resolved.showDeniedBanner)
    }

    @Test
    fun `a granted permission clears the banner once resolved`() {
        val resolved = PermissionUiState(
            hasSmsPermission = true,
            onboardingSeen = true,
            permissionChecked = true,
            onboardingLoaded = true,
        )
        assertFalse(resolved.showDeniedBanner)
        assertFalse(resolved.showOnboarding)
    }

    @Test
    fun `refreshing the permission state marks it checked, so a later resume can flip it back`() = runTest {
        val vm = PermissionViewModel(FakeOnboardingState(), FakeBackfillGate())
        assertFalse(vm.state.value.permissionChecked)

        vm.refreshPermissionState(hasSmsPermission = true)
        assertTrue(vm.state.value.permissionChecked)
        assertTrue(vm.state.value.hasSmsPermission)

        // Revoked from system Settings while backgrounded, re-checked on the next resume.
        vm.refreshPermissionState(hasSmsPermission = false)
        assertFalse(vm.state.value.hasSmsPermission)
        assertTrue(vm.state.value.permissionChecked)
    }

    @Test
    fun `onboarding is shown only when permission is missing and not yet acknowledged`() {
        assertTrue(shouldShowOnboarding(hasSmsPermission = false, onboardingSeen = false))
        assertFalse(shouldShowOnboarding(hasSmsPermission = false, onboardingSeen = true))
        assertFalse(shouldShowOnboarding(hasSmsPermission = true, onboardingSeen = false))
        assertFalse(shouldShowOnboarding(hasSmsPermission = true, onboardingSeen = true))
    }

    @Test
    fun `denied banner shows only after onboarding has been acknowledged without permission`() {
        assertTrue(shouldShowDeniedBanner(hasSmsPermission = false, onboardingSeen = true))
        assertFalse(shouldShowDeniedBanner(hasSmsPermission = false, onboardingSeen = false))
        assertFalse(shouldShowDeniedBanner(hasSmsPermission = true, onboardingSeen = true))
    }
}
