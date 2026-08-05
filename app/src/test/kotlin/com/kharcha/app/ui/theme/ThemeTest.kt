package com.kharcha.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertTrue

class ThemeTest {
    @Test
    fun `neutrals are tinted, never pure gray or black`() {
        KharchaNeutrals.all.forEach { argb ->
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            assertTrue(
                !(r == g && g == b),
                "neutral ${argb.toString(16)} is untinted — every neutral must carry a color bias"
            )
        }
    }

    @Test
    fun `debit and credit colors are distinguishable`() {
        assertTrue(KharchaColors.debit != KharchaColors.credit)
    }
}
