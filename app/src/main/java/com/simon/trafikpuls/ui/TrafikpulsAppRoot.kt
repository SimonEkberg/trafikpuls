package com.simon.trafikpuls.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.simon.trafikpuls.domain.model.TimetableEntry
import com.simon.trafikpuls.ui.alerts.AlertsScreen
import com.simon.trafikpuls.ui.dashboard.DashboardScreen
import com.simon.trafikpuls.ui.navigation.AlertsRoute
import com.simon.trafikpuls.ui.navigation.DashboardRoute
import com.simon.trafikpuls.ui.navigation.SearchRoute
import com.simon.trafikpuls.ui.navigation.SettingsRoute
import com.simon.trafikpuls.ui.navigation.StopBoardRoute
import com.simon.trafikpuls.ui.navigation.TripRoute
import com.simon.trafikpuls.ui.search.SearchScreen
import com.simon.trafikpuls.ui.settings.SettingsScreen
import com.simon.trafikpuls.ui.stop.StopBoardScreen
import com.simon.trafikpuls.ui.trip.TripScreen

private enum class TopLevel(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    OVERVIEW(DashboardRoute, "Översikt", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    ALERTS(AlertsRoute, "Störningar", Icons.Outlined.Campaign, Icons.Filled.Campaign),
    SEARCH(SearchRoute, "Sök", Icons.Outlined.Search, Icons.Filled.Search),
}

@Composable
fun TrafikpulsAppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTopLevel = TopLevel.entries.firstOrNull { topLevel ->
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(topLevel.route::class) } == true
    }

    val openStop: (String, String) -> Unit = { areaId, name -> navController.navigate(StopBoardRoute(areaId, name)) }
    val openTrip: (TimetableEntry) -> Unit = { entry ->
        navController.navigate(TripRoute(entry.tripId, entry.startDate.toString(), entry.stopId))
    }
    val openSettings: () -> Unit = { navController.navigate(SettingsRoute) }

    Scaffold(
        bottomBar = {
            if (currentTopLevel != null) {
                NavigationBar {
                    TopLevel.entries.forEach { topLevel ->
                        val selected = topLevel == currentTopLevel
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(topLevel.route) },
                            icon = { Icon(if (selected) topLevel.selectedIcon else topLevel.icon, contentDescription = null) },
                            label = { Text(topLevel.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Each screen draws its own top bar (and the status bar inset); only the bottom is padded here.
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable<DashboardRoute> {
                DashboardScreen(
                    onOpenStop = openStop,
                    onOpenTrip = openTrip,
                    onOpenSettings = openSettings,
                    onOpenSearch = { navController.navigateTopLevel(SearchRoute) },
                )
            }
            composable<AlertsRoute> {
                AlertsScreen(onOpenStop = openStop, onOpenSettings = openSettings)
            }
            composable<SearchRoute> {
                SearchScreen(onOpenStop = openStop)
            }
            composable<SettingsRoute> {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable<StopBoardRoute> { entry ->
                val route = entry.toRoute<StopBoardRoute>()
                StopBoardScreen(
                    areaId = route.areaId,
                    name = route.name,
                    onBack = { navController.popBackStack() },
                    onOpenTrip = openTrip,
                    onOpenSettings = openSettings,
                )
            }
            composable<TripRoute> { entry ->
                val route = entry.toRoute<TripRoute>()
                TripScreen(
                    tripId = route.tripId,
                    startDate = route.startDate,
                    highlightStopId = route.stopId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
