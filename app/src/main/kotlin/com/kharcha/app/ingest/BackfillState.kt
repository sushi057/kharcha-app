package com.kharcha.app.ingest

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.backfillDataStore: DataStore<Preferences> by preferencesDataStore(name = "sms_backfill")

private val BACKFILL_COMPLETE = booleanPreferencesKey("backfill_complete")

/**
 * Tracks whether [BackfillWorker] has already imported the existing SMS inbox, so it is
 * safe to enqueue the worker again (e.g. on every app start) without re-scanning the
 * inbox every time. The worker is separately idempotent by content hash, so this flag is
 * purely an optimization, not a correctness requirement.
 */
class BackfillState(private val dataStore: DataStore<Preferences>) {
    suspend fun isComplete(): Boolean = dataStore.data.first()[BACKFILL_COMPLETE] ?: false

    suspend fun markComplete() {
        dataStore.edit { prefs -> prefs[BACKFILL_COMPLETE] = true }
    }
}
