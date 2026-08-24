package com.kharcha.app.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsPreferencesTest {

    @Test
    fun `theme mode enum parses from string correctly`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString("SYSTEM"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromString("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromString("DARK"))
    }

    @Test
    fun `theme mode to display name is correct`() {
        assertEquals("System", ThemeMode.SYSTEM.toDisplayName())
        assertEquals("Light", ThemeMode.LIGHT.toDisplayName())
        assertEquals("Dark", ThemeMode.DARK.toDisplayName())
    }

    @Test
    fun `theme mode round trips through string conversion`() {
        val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

        modes.forEach { mode ->
            val parsed = ThemeMode.fromString(mode.name)
            assertEquals(mode, parsed)
        }
    }
}
