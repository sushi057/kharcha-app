package com.kharcha.app.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

/**
 * Theme mode preference: System, Light, or Dark.
 * System follows the device's system setting (the default).
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun toDisplayName(): String = when (this) {
        SYSTEM -> "System"
        LIGHT -> "Light"
        DARK -> "Dark"
    }

    companion object {
        fun fromString(value: String?): ThemeMode = when (value?.uppercase()) {
            "LIGHT" -> LIGHT
            "DARK" -> DARK
            else -> SYSTEM
        }
    }
}

/**
 * Accessor for app-wide preferences stored in DataStore.
 * Testable through the interface; production uses DataStore.
 */
interface SettingsPreferences {
    fun observeThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}

class DataStoreSettingsPreferences(private val dataStore: DataStore<Preferences>) : SettingsPreferences {
    override fun observeThemeMode(): Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromString(prefs[THEME_MODE_KEY])
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }
}
