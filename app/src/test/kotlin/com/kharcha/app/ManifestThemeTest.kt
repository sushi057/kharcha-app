package com.kharcha.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Reviewer's cheap finding on `AndroidManifest.xml:15, 20`: the application and launcher
 * activity declared `@android:style/Theme.Material.Light.NoActionBar` in a dark-first app
 * that calls `enableEdgeToEdge()` — a white launch flash before the first frame, and light
 * system bars over a dark UI.
 *
 * Consistent with the Important 4 decision (light mode is genuinely supported, so the
 * composables were moved onto `MaterialTheme.colorScheme`), the manifest theme is a
 * day/night theme of our own: light values in `res/values/themes.xml`, dark ones in
 * `res/values-night/themes.xml`, so the launch window matches whichever scheme
 * `KharchaTheme` is about to select.
 */
class ManifestThemeTest {

    private fun appModuleRoot(): File =
        listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }
            ?: fail("could not locate the app main source set from ${File(".").absolutePath}")

    @Test
    fun `the manifest does not hardcode a light platform theme`() {
        val manifest = File(appModuleRoot(), "AndroidManifest.xml").readText()
        assertTrue(
            !manifest.contains("Theme.Material.Light"),
            "a dark-first, edge-to-edge app must not declare a light-only platform theme:\n$manifest",
        )
        assertTrue(
            manifest.contains("@style/Theme.Kharcha"),
            "the manifest should use the app's own day/night launch theme",
        )
    }

    @Test
    fun `the launch theme is defined for both light and dark`() {
        val res = File(appModuleRoot(), "res")
        assertTrue(File(res, "values/themes.xml").isFile, "missing res/values/themes.xml")
        assertTrue(File(res, "values-night/themes.xml").isFile, "missing res/values-night/themes.xml")
    }
}
