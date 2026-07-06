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
                // M3 Expressive: truly floating bottom bar — no background, just rounded pill
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp,
                    shadowElevation = 12.dp,
                ) {
                    NavigationBar(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0),
                    ) {
                        bottomNavScreens.forEach { screen ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                            // Spring-based icon scale — selected icon is slightly larger
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
                                // Only show label for the SELECTED item — unselected items are icon-only
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
            composable(Screen.Library.route) { LibraryScreen() }
            composable(Screen.History.route) { HistoryScreen() }
            composable(Screen.Search.route) { SearchScreen() }
            composable(Screen.More.route) { MoreScreen() }
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
        }
    }
}
