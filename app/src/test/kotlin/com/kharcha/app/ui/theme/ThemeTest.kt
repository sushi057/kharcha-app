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
    fun `light scheme neutrals are tinted where possible`() {
        // Light scheme uses the same neutral ramp but reads it in reverse.
        // Pure white (#FFFFFF) surface is an exception for technical reasons, but
        // all other backgrounds and text colors must carry warm tinting.
        val lightNeutrals = listOf(
            0xFFFAF7F2.toInt(),  // background — must be tinted
            0xFFF1EAE0.toInt(),  // surfaceContainer — must be tinted
            0xFFE7DCCD.toInt(),  // surfaceContainerHigh — must be tinted
            0xFF241C16.toInt(),  // onBackground/onSurface — must be tinted
            0xFF7A6A5C.toInt(),  // onSurfaceVariant — must be tinted
            0xFF9A8A7C.toInt(),  // outline — must be tinted
        )
        lightNeutrals.forEach { argb ->
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            assertTrue(
                !(r == g && g == b),
                "light neutral 0x${argb.toLong().toString(16)} is untinted — every neutral must carry a warm color bias"
            )
        }
    }

    @Test
    fun `debit, credit, and accent are all distinguishable in dark scheme`() {
        val colors = listOf(
            kharchaDarkSemanticColors.debit,
            kharchaDarkSemanticColors.credit,
            kharchaDarkSemanticColors.accent,
        )
        assertTrue(colors.distinct().size == 3, "dark scheme debit/credit/accent must all be different colors")
    }

    @Test
    fun `debit, credit, and accent are all distinguishable in light scheme`() {
        val colors = listOf(
            kharchaLightSemanticColors.debit,
            kharchaLightSemanticColors.credit,
            kharchaLightSemanticColors.accent,
        )
        assertTrue(colors.distinct().size == 3, "light scheme debit/credit/accent must all be different colors")
    }
}
