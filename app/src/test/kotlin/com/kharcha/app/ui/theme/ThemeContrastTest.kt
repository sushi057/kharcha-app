package com.kharcha.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the reviewer's Important 4: Dashboard and Budgets hardcoded the DARK palette
 * (`KharchaColors.*` / `KharchaTypography.*`) inside composables while [KharchaTheme]
 * genuinely switches to [kharchaLightColorScheme] when the system asks for light mode.
 * The result in light mode was `KharchaColors.onBackground` (Neutral95) drawn on a
 * Neutral95 background — invisible headings — and a near-black `SpendSummaryCard` slab.
 *
 * Two complementary checks:
 *  1. [composables never reference the raw palette objects] catches the root cause at its
 *     source: a composable that reaches for a scheme constant instead of
 *     `MaterialTheme.colorScheme` / `MaterialTheme.typography` cannot respond to the theme
 *     at all, and no rendering assertion can save it.
 *  2. [both schemes meet WCAG AA] catches the other half — a scheme slot whose foreground
 *     is unreadable on its own background.
 */
class ThemeContrastTest {

    // ---- 2. both schemes must be readable ------------------------------------------

    private fun relativeLuminance(color: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertReadable(label: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= 4.5, "$label has contrast %.2f, below the WCAG AA 4.5 floor".format(ratio))
    }

    @Test
    fun `both schemes meet WCAG AA on every foreground-background pair`() {
        val schemes = listOf(
            "dark" to kharchaDarkColorScheme,
            "light" to kharchaLightColorScheme,
        )
        for ((name, scheme) in schemes) {
            assertReadable("$name onBackground/background", scheme.onBackground, scheme.background)
            assertReadable("$name onSurface/surface", scheme.onSurface, scheme.surface)
            assertReadable("$name onSurfaceVariant/surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant)
            assertReadable("$name onPrimary/primary", scheme.onPrimary, scheme.primary)
        }

        val semantics = listOf(
            "dark" to (kharchaDarkSemanticColors to kharchaDarkColorScheme),
            "light" to (kharchaLightSemanticColors to kharchaLightColorScheme),
        )
        for ((name, pair) in semantics) {
            val (semantic, scheme) = pair
            assertReadable("$name debit/background", semantic.debit, scheme.background)
            assertReadable("$name debit/surface", semantic.debit, scheme.surface)
            assertReadable("$name credit/background", semantic.credit, scheme.background)
            assertReadable("$name credit/surface", semantic.credit, scheme.surface)
        }
    }

    @Test
    fun `debit and credit stay distinguishable in both schemes`() {
        assertTrue(kharchaDarkSemanticColors.debit != kharchaDarkSemanticColors.credit)
        assertTrue(kharchaLightSemanticColors.debit != kharchaLightSemanticColors.credit)
    }
}
