package com.kharcha.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharcha.app.ui.onboarding.PermissionDeniedBanner
import com.kharcha.app.ui.onboarding.PermissionScreen
import com.kharcha.app.ui.onboarding.PermissionViewModel
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.theme.KharchaThemeController
import com.kharcha.app.ui.theme.ThemeViewModel
import com.kharcha.app.ui.theme.resolveIsDark
import dagger.hilt.android.AndroidEntryPoint

/** Sole activity: hosts [KharchaApp] (onboarding + [KharchaNavHost]) inside [KharchaTheme]. Launcher activity. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The theme is resolved above everything else, including the onboarding
            // screen, so there is no point in the app's life where a composable is
            // drawn against a scheme other than the one the user chose.
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val isDark = themeMode.resolveIsDark()

            // `enableEdgeToEdge` picks the status-bar icon polarity once, from the
            // system setting, at the moment the activity is created. The in-app
            // toggle can disagree with the system a frame later, which leaves dark
            // icons on the dark scheme's near-black bar — invisible. Re-assert it
            // whenever the resolved scheme changes.
            val view = LocalView.current
            SideEffect {
                val window = (view.context as android.app.Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }

            KharchaTheme(
                darkTheme = isDark,
                themeController = KharchaThemeController(
                    mode = themeMode,
                    isDark = isDark,
                    setMode = themeViewModel::setThemeMode,
                ),
            ) {
                KharchaApp()
            }
        }
    }
}

/**
 * Startup decision lives here, driven by [PermissionViewModel.state] whose `showOnboarding`
 * and `showDeniedBanner` flags are computed by the pure, independently-tested predicates
 * [com.kharcha.app.ui.onboarding.shouldShowOnboarding] and
 * [com.kharcha.app.ui.onboarding.shouldShowDeniedBanner] — this composable only reads them.
 * A denied permission never dead-ends the app: it falls through to [KharchaNavHost] with a
 * persistent [PermissionDeniedBanner] offering to re-request (ruling 4).
 */
@Composable
fun KharchaApp() {
    val permissionViewModel: PermissionViewModel = hiltViewModel()
    val state by permissionViewModel.state.collectAsState()
    val context = LocalContext.current

    // Re-checked on every resume rather than once per composition: the user can grant or revoke
    // SMS access from system Settings without the app being recreated, and a LaunchedEffect(Unit)
    // would leave the denied banner up for the rest of the process lifetime.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionViewModel.refreshPermissionState(hasSmsPermission(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // No early return here: `showOnboarding` / `showDeniedBanner` are already false until the
    // state resolves (see PermissionUiState.isResolved), so the app falls through to the nav
    // host for the first frame or two rather than flashing a banner — or, if this bailed out
    // early, rendering nothing at all.
    if (state.showOnboarding) {
        PermissionScreen(onResult = permissionViewModel::onPermissionsResult)
    } else if (state.showDeniedBanner) {
        // The app draws edge-to-edge, so the banner — which sits above the nav host's own
        // Scaffold — has to take the status bar inset itself, then *consume* it. Without the
        // padding the banner text is drawn underneath the system clock; without the consume
        // the Scaffold below applies the same inset a second time and leaves a dead band
        // above every app bar.
        Column(modifier = Modifier.fillMaxSize()) {
            PermissionDeniedBanner(
                onResult = permissionViewModel::onPermissionsResult,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .consumeWindowInsets(WindowInsets.statusBars),
            ) {
                KharchaNavHost()
            }
        }
    } else {
        KharchaNavHost()
    }
}

/** Both SMS permissions, checked together — the app needs read (backfill) and receive (live). */
private fun hasSmsPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS,
    ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
