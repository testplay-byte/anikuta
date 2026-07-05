package app.anikuta.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.anikuta.ui.home.HomeScreen
import app.anikuta.ui.library.LibraryScreen
import app.anikuta.ui.history.HistoryScreen
import app.anikuta.ui.search.SearchScreen
import app.anikuta.ui.settings.MoreScreen

/**
 * Bottom navigation destinations.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    data object History : Screen("history", "History", Icons.Default.History)
    data object Search : Screen("search", "Search", Icons.Default.Search)
    data object More : Screen("more", "More", Icons.Default.MoreHoriz)
}

private val screens = listOf(Screen.Home, Screen.Library, Screen.History, Screen.Search, Screen.More)

/**
 * Main app navigation — bottom nav with 5 tabs.
 */
@Composable
fun AnikutaNavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Library.route) { LibraryScreen() }
            composable(Screen.History.route) { HistoryScreen() }
            composable(Screen.Search.route) { SearchScreen() }
            composable(Screen.More.route) { MoreScreen() }
        }
    }
}
