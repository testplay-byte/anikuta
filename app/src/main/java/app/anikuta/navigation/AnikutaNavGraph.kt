package app.anikuta.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.anikuta.ui.detail.DetailScreen
import app.anikuta.ui.home.HomeScreen
import app.anikuta.ui.library.LibraryScreen
import app.anikuta.ui.history.HistoryScreen
import app.anikuta.ui.search.SearchScreen
import app.anikuta.ui.settings.MoreScreen

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Library : Screen("library", "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
    data object History : Screen("history", "History", Icons.Filled.History, Icons.Outlined.History)
    data object Search : Screen("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)
    data object More : Screen("more", "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
    data object Detail : Screen("detail/{anilistId}", "Detail", Icons.Filled.Home, Icons.Outlined.Home)
}

private val bottomNavScreens = listOf(Screen.Home, Screen.Library, Screen.History, Screen.Search, Screen.More)

@Composable
fun AnikutaNavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomNavScreens.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                // Full-width bottom nav. Removed the previous floating-pill
                // treatment (horizontal = 48.dp + vertical = 8.dp) which made
                // the bar too narrow AND too tall. Now it spans the full width
                // and uses the standard M3 NavigationBar height (80dp). We
                // still keep the transparent container + spring icon scale
                // (M3 Expressive) but drop the extra container.
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0),
                ) {
                    bottomNavScreens.forEach { screen ->
                        val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.2f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "nav_icon_scale",
                        )

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.label,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .scale(iconScale),
                                )
                            },
                            label = {
                                if (isSelected) {
                                    Text(
                                        screen.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            // Only apply bottom padding (for nav bar height) — NOT top, so home + detail can be edge-to-edge
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onAnimeClick = { anilistId ->
                        navController.navigate("detail/$anilistId")
                    },
                )
            }
            composable(Screen.Library.route) {
                LibraryScreen(
                    onAnimeClick = { anilistId ->
                        navController.navigate("detail/$anilistId")
                    },
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onResume = { anilistId, _, _ ->
                        // Navigate to detail — the player will resume from the
                        // saved position when the user taps the episode.
                        navController.navigate("detail/$anilistId")
                    },
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onAnimeClick = { anilistId ->
                        navController.navigate("detail/$anilistId")
                    },
                )
            }
            composable(Screen.More.route) {
                MoreScreen(
                    onOpenDebug = { navController.navigate("debug") },
                    onNavigate = { route -> navController.navigate(route) },
                )
            }
            composable(
                route = "detail/{anilistId}",
                arguments = listOf(navArgument("anilistId") { type = NavType.IntType }),
            ) { backStackEntry ->
                val anilistId = backStackEntry.arguments?.getInt("anilistId") ?: 0
                DetailScreen(
                    anilistId = anilistId,
                    onBack = { navController.popBackStack() },
                )
            }
            // Hidden debug screen (Phase 5 task 5.1). Accessible via long-press
            // on the version number in About settings. Easily removable: delete this
            // composable + the DebugScreen file + the long-press handler.
            composable(route = "debug") {
                app.anikuta.ui.debug.DebugScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            // --- Settings subpages (Phase 6 task 6.17-6.24) ---
            composable("settings/general") {
                app.anikuta.ui.settings.GeneralSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/player") {
                app.anikuta.ui.settings.PlayerSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/extensions") {
                app.anikuta.ui.settings.ExtensionsSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/downloads") {
                app.anikuta.ui.settings.DownloadsSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/tracking") {
                app.anikuta.ui.settings.TrackingSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/about") {
                app.anikuta.ui.settings.AboutSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDebug = { navController.navigate("debug") },
                )
            }
        }
    }
}
