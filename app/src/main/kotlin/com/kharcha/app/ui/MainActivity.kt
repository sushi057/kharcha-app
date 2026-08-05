package com.kharcha.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharcha.app.ui.onboarding.PermissionDeniedBanner
import com.kharcha.app.ui.onboarding.PermissionScreen
import com.kharcha.app.ui.onboarding.PermissionViewModel
import com.kharcha.app.ui.theme.KharchaTheme
import dagger.hilt.android.AndroidEntryPoint

/** Sole activity: hosts [KharchaApp] (onboarding + [KharchaNavHost]) inside [KharchaTheme]. Launcher activity. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KharchaTheme {
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

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS,
            ) == PackageManager.PERMISSION_GRANTED
        permissionViewModel.refreshPermissionState(granted)
    }

    if (state.showOnboarding) {
        PermissionScreen(onResult = permissionViewModel::onPermissionsResult)
    } else {
        Column {
            if (state.showDeniedBanner) {
                PermissionDeniedBanner(onResult = permissionViewModel::onPermissionsResult)
            }
            KharchaNavHost()
        }
    }
}
