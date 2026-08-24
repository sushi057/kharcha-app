package com.kharcha.app.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.app.ui.settings.SettingsPreferences
import com.kharcha.app.ui.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the app-wide theme preference, above the nav host, so that switching it
 * recolours every screen at once instead of only the one that owns the toggle.
 *
 * This is a separate ViewModel from `SettingsViewModel` even though both read the
 * same [SettingsPreferences] key: `SettingsViewModel` also pulls in the DAO and
 * the exporter, and the activity would then be constructing an export pipeline
 * on every cold start just to find out which colour scheme to use.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    /**
     * Starts at [ThemeMode.SYSTEM] rather than at a stored value, because the
     * first emission from DataStore is asynchronous. SYSTEM is the right thing to
     * show for that one frame: it matches whatever the device is already doing,
     * so a user with a stored DARK preference on a dark device sees no flash at
     * all, and one with a stored override sees at worst a single frame of the
     * system's answer instead of a white flash.
     */
    val themeMode: StateFlow<ThemeMode> = settingsPreferences.observeThemeMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.SYSTEM,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsPreferences.setThemeMode(mode) }
    }
}
