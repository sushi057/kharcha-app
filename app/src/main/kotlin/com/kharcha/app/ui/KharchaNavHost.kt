package com.kharcha.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kharcha.app.ui.dashboard.DashboardScreen
import com.kharcha.app.ui.transactions.TransactionsScreen
import com.kharcha.app.ui.unparsed.UnparsedScreen
import com.kharcha.app.ui.unparsed.UnparsedViewModel

/**
 * A bottom-nav destination. Tasks 10 (Dashboard) and 11 (Budgets) each add
 * one more of these to [kharchaDestinations] and one `composable(route) { … }`
 * branch in [KharchaNavHost] — nothing else about the nav structure needs to
 * change.
 */
sealed class KharchaDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : KharchaDestination("dashboard", "Dashboard", Icons.Filled.Home)
    data object Transactions : KharchaDestination("transactions", "Transactions", Icons.Filled.List)
    data object Unparsed : KharchaDestination("unparsed", "Unparsed", Icons.Filled.MailOutline)
}

private val kharchaDestinations = listOf<KharchaDestination>(
    KharchaDestination.Dashboard,
    KharchaDestination.Transactions,
    KharchaDestination.Unparsed,
)

@Composable
fun KharchaNavHost(navController: NavHostController = rememberNavController()) {
    // Shared for the app's lifetime (scoped to the hosting Activity, not the nav
    // back-stack entry), so the unparsed-count badge stays live no matter which
    // destination is on screen.
    val unparsedViewModel: UnparsedViewModel = hiltViewModel()
    val unparsedState by unparsedViewModel.state.collectAsState()
    val unparsedCount = unparsedState.messages.size

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                kharchaDestinations.forEach { destination ->
                    NavigationBarItem(
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
                            if (destination == KharchaDestination.Unparsed && unparsedCount > 0) {
                                BadgedBox(badge = { Badge { Text(text = unparsedCount.toString()) } }) {
                                    Icon(imageVector = destination.icon, contentDescription = destination.label)
                                }
                            } else {
                                Icon(imageVector = destination.icon, contentDescription = destination.label)
                            }
                        },
                        label = { Text(text = destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = KharchaDestination.Transactions.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(KharchaDestination.Dashboard.route) {
                DashboardScreen(modifier = Modifier)
            }
            composable(KharchaDestination.Transactions.route) {
                TransactionsScreen(modifier = Modifier)
            }
            composable(KharchaDestination.Unparsed.route) {
                UnparsedScreen(modifier = Modifier, viewModel = unparsedViewModel)
            }
        }
    }
}
