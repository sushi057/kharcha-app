package com.kharcha.app.ui.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "permission_onboarding")

private val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")

/**
 * Tracks whether the user has already been shown [PermissionScreen] and responded (grant or
 * deny) once. Separate from [com.kharcha.app.ingest.BackfillState], which tracks a different
 * fact (whether the historical SMS scan has completed) — conflating the two would make a
 * denial look like "backfill still pending" instead of "user chose manual-entry-only".
 */
interface OnboardingState {
    suspend fun isOnboardingSeen(): Boolean
    suspend fun markOnboardingSeen()
}

class DataStoreOnboardingState(private val dataStore: DataStore<Preferences>) : OnboardingState {
    override suspend fun isOnboardingSeen(): Boolean = dataStore.data.first()[ONBOARDING_SEEN] ?: false

    override suspend fun markOnboardingSeen() {
        dataStore.edit { prefs -> prefs[ONBOARDING_SEEN] = true }
    }
}
