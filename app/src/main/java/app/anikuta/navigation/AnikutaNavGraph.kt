package app.anikuta.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
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
                // Floating pill-style bottom nav — matches the top bar aesthetic.
                // A rounded Surface floats above the content with padding, holding
                // all 5 nav icons. The selected icon gets a secondaryContainer
                // background pill + spring scale animation.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            bottomNavScreens.forEach { screen ->
                                val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.15f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                    label = "nav_icon_scale",
                                )

                                // Each nav item is a clickable Surface that gets
                                // a secondaryContainer background when selected.
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = screen.label,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .scale(iconScale),
                                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (isSelected) {
                                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(4.dp))
                                            Text(
                                                screen.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
                app.anikuta.ui.settings.ExtensionsSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onManageRepos = { navController.navigate("extension_repos") },
                    onOpenExtensionDetails = { pkgName ->
                        navController.navigate("extension_details/$pkgName")
                    },
                )
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
            // --- Phase 7: Extension management routes ---
            composable("extension_repos") {
                app.anikuta.ui.settings.ExtensionReposScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "extension_details/{pkgName}",
                arguments = listOf(navArgument("pkgName") { type = NavType.StringType }),
            ) { backStackEntry ->
                val pkgName = backStackEntry.arguments?.getString("pkgName") ?: ""
                app.anikuta.ui.settings.ExtensionDetailsScreen(
                    pkgName = pkgName,
                    onBack = { navController.popBackStack() },
                    onOpenSourcePreferences = { sourceId ->
                        navController.navigate("source_preferences/$sourceId")
                    },
                )
            }
            composable(
                route = "source_preferences/{sourceId}",
                arguments = listOf(navArgument("sourceId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val sourceId = backStackEntry.arguments?.getLong("sourceId") ?: 0L
                app.anikuta.ui.settings.SourcePreferencesScreen(
                    sourceId = sourceId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
