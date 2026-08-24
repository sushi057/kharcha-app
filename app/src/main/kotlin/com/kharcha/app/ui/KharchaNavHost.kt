package com.kharcha.app.ui

import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DonutSmall
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.kharcha.app.ui.components.hairline
import com.kharcha.app.ui.theme.KharchaSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kharcha.app.ui.budgets.BudgetsScreen
import com.kharcha.app.ui.dashboard.DashboardScreen
import com.kharcha.app.ui.settings.SettingsScreen
import com.kharcha.app.ui.transactions.TransactionsScreen
import com.kharcha.app.ui.unparsed.UnparsedScreen
import com.kharcha.app.ui.unparsed.UnparsedViewModel

/**
 * A bottom-nav destination. Adding one means adding an entry to
 * [kharchaDestinations] and a `composable(route) { … }` branch in [KharchaNavHost] —
 * nothing else about the nav structure needs to change.
 *
 * Destinations reached from an app bar rather than the bottom bar (Settings) are
 * registered in the [NavHost] without appearing in [kharchaDestinations].
 */
sealed class KharchaDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : KharchaDestination("dashboard", "Dashboard", Icons.Outlined.Home)
    data object Budgets : KharchaDestination("budgets", "Budgets", Icons.Outlined.DonutSmall)
    data object Transactions : KharchaDestination("transactions", "Transactions", Icons.Outlined.Reorder)

    /**
     * Route stays `unparsed` so saved back-stack state survives the rename; only the
     * user-facing label changes. "Unparsed" described the app's internal parse outcome
     * rather than the user's job, which is triaging messages the parser could not place.
     */
    data object Inbox : KharchaDestination("unparsed", "Inbox", Icons.Outlined.MailOutline)
}

/**
 * Settings is reached from the Dashboard app bar, not the bottom bar, so it is a plain
 * route rather than a [KharchaDestination] — it must never light up a bottom-bar tab.
 */
internal const val SETTINGS_ROUTE = "settings"

/**
 * Bottom-bar order. Budgets sits second because it is checked far more often than the
 * transaction list — the list is where you go to correct something, the budget is where
 * you go to decide something.
 */
internal val kharchaDestinations = listOf<KharchaDestination>(
    KharchaDestination.Dashboard,
    KharchaDestination.Budgets,
    KharchaDestination.Transactions,
    KharchaDestination.Inbox,
)

/**
 * The destination the app opens on. Must be the same destination as the first
 * entry of [kharchaDestinations] — otherwise the app launches on a tab that is
 * not the one the bottom bar highlights first. Enforced by
 * [com.kharcha.app.ui.KharchaNavHostTest].
 */
internal val kharchaStartDestination: KharchaDestination = KharchaDestination.Dashboard

@Composable
fun KharchaNavHost(navController: NavHostController = rememberNavController()) {
    // Shared for the app's lifetime (scoped to the hosting Activity, not the nav
    // back-stack entry), so the unparsed-count badge stays live no matter which
    // destination is on screen.
    val unparsedViewModel: UnparsedViewModel = hiltViewModel()
    val shellViewModel: NavShellViewModel = hiltViewModel()
    val unparsedState by unparsedViewModel.state.collectAsState()
    val unparsedCount = unparsedState.messages.size

    Scaffold(
        bottomBar = {
            // `surface`, not the Material default `surfaceContainer`: the bar is the
            // same plane as the cards above it, separated by a hairline rather than by
            // a tonal step. A darker bar reads as a floating slab under the content.
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.drawTopHairline(),
            ) {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                kharchaDestinations.forEach { destination ->
                    NavigationBarItem(
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = KharchaSemantics.accent,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.outline,
                            unselectedTextColor = MaterialTheme.colorScheme.outline,
                        ),
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (destination == KharchaDestination.Inbox && unparsedCount > 0) {
                                BadgedBox(badge = { Badge { Text(text = unparsedCount.toString()) } }) {
                                    Icon(imageVector = destination.icon, contentDescription = destination.label)
                                }
                            } else {
                                Icon(imageVector = destination.icon, contentDescription = destination.label)
                            }
                        },
                        label = {
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = kharchaStartDestination.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(KharchaDestination.Dashboard.route) {
                DashboardScreen(
                    modifier = Modifier,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                    onOpenDay = { date ->
                        shellViewModel.openDayRequests.request(date)
                        navController.navigate(KharchaDestination.Transactions.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(KharchaDestination.Transactions.route) {
                TransactionsScreen(modifier = Modifier)
            }
            composable(KharchaDestination.Inbox.route) {
                UnparsedScreen(modifier = Modifier, viewModel = unparsedViewModel)
            }
            composable(KharchaDestination.Budgets.route) {
                BudgetsScreen(modifier = Modifier)
            }
            // Pushed on top of the current tab rather than swapping it, so Back returns
            // to the tab the user opened Settings from with its state intact.
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    modifier = Modifier,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Draws the single hairline separating the nav bar from the content above it.
 * `Modifier.border` would outline all four sides; the design wants one line on
 * top and nothing else, because the bar's own background already ends the page.
 */
@Composable
private fun Modifier.drawTopHairline(): Modifier {
    val color = hairline
    return this.drawBehind {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1f,
        )
    }
}
