package com.kharcha.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kharcha.app.ui.theme.KharchaSpacing

/**
 * Requests `RECEIVE_SMS`/`READ_SMS` (and `POST_NOTIFICATIONS` on API 33+, per ruling 2 — the
 * manifest declares it unconditionally, which is harmless below API 33 since the platform
 * ignores permissions it doesn't define) after explaining why the app needs SMS access.
 * [onResult] reports the SMS grant outcome to [PermissionViewModel.onPermissionsResult], which
 * owns the "enqueue backfill exactly once" decision — this composable never touches
 * WorkManager directly.
 */
@Composable
fun PermissionScreen(
    onResult: (smsGranted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requestPermissions = rememberSmsPermissionLauncher(onResult = onResult)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(KharchaSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            Text(
                text = "Read your bank SMS automatically",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(KharchaSpacing.sm))
            Text(
                text = "Kharcha turns Siddhartha Bank's SMS alerts into categorized " +
                    "transactions automatically, entirely on this device — nothing is sent " +
                    "anywhere. Grant SMS access to import your history and keep new " +
                    "transactions up to date without opening the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(KharchaSpacing.lg))
            Button(
                onClick = requestPermissions,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Grant SMS access")
            }
            Spacer(modifier = Modifier.height(KharchaSpacing.sm))
            TextButton(
                onClick = { onResult(false) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Not now — I'll add transactions manually")
            }
        }
    }
}

/**
 * Persistent banner shown when the user is running in manual-entry-only mode (denied SMS
 * access, or chose "not now"). Never dead-ends the screen it's attached to — it only offers to
 * re-request, via the same [PermissionScreen] flow.
 */
@Composable
fun PermissionDeniedBanner(
    onResult: (smsGranted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requestPermissions = rememberSmsPermissionLauncher(onResult = onResult)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KharchaSpacing.md, vertical = KharchaSpacing.sm),
    ) {
        Column {
            Text(
                text = "SMS access is off — add transactions manually, or turn it back on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = requestPermissions) {
                Text(text = "Grant SMS access")
            }
        }
    }
}

/** The permission set requested together: SMS always, notifications only on API 33+ (ruling 2). */
private fun smsAndNotificationPermissions(): List<String> = buildList {
    add(Manifest.permission.RECEIVE_SMS)
    add(Manifest.permission.READ_SMS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Shared by [PermissionScreen] and [PermissionDeniedBanner] so "grant SMS access" is one
 * launcher, not two independent copies. Returns a callback that launches the system dialog;
 * [onResult] fires with whether both SMS permissions ended up granted.
 */
@Composable
private fun rememberSmsPermissionLauncher(onResult: (smsGranted: Boolean) -> Unit): () -> Unit {
    val permissions = smsAndNotificationPermissions()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val smsGranted = results[Manifest.permission.RECEIVE_SMS] == true &&
            results[Manifest.permission.READ_SMS] == true
        onResult(smsGranted)
    }
    return { launcher.launch(permissions.toTypedArray()) }
}
