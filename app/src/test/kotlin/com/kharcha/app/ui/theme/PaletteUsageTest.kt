package com.kharcha.app.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

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
class PaletteUsageTest {

    // ---- 1. no composable may hardcode a scheme's constants -------------------------

    private val allowedPaletteFiles = setOf("Color.kt", "Theme.kt", "Type.kt", "MoneyText.kt")

    private fun mainSourceRoot(): File {
        val candidates = listOf(
            File("src/main/kotlin/com/kharcha/app"),
            File("app/src/main/kotlin/com/kharcha/app"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: fail("could not locate the app main source root from ${File(".").absolutePath}")
    }

    @Test
    fun `composables never reference the raw palette objects`() {
        val offenders = mainSourceRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name !in allowedPaletteFiles }
            .mapNotNull { file ->
                val hits = file.readLines()
                    .withIndex()
                    .filter { (_, line) ->
                        line.contains("KharchaColors.") || line.contains("KharchaTypography.")
                    }
                    .map { (index, line) -> "  ${file.name}:${index + 1}  ${line.trim()}" }
                if (hits.isEmpty()) null else hits
            }
            .flatten()
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "these composables hardcode a single scheme's constants instead of reading " +
                "MaterialTheme.colorScheme / MaterialTheme.typography, so they render the " +
                "dark palette in light mode:\n" + offenders.joinToString("\n"),
        )
    }
}
