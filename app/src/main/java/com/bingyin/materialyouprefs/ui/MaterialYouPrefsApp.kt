package com.bingyin.materialyouprefs.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.bingyin.materialyouprefs.ui.nav.Screen
import com.bingyin.materialyouprefs.ui.screens.DetailScreen
import com.bingyin.materialyouprefs.ui.screens.FeatureScreen
import com.bingyin.materialyouprefs.ui.screens.HomeScreen
import com.bingyin.materialyouprefs.ui.screens.SettingsScreen
import com.bingyin.materialyouprefs.ui.theme.MaterialYouPrefsTheme

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MaterialYouPrefsApp() {
    MaterialYouPrefsTheme {
        val navController = rememberNavController()

        val bottomNavItems = listOf(
            BottomNavItem(Screen.Home, "首页", Icons.Outlined.Home),
            BottomNavItem(Screen.Feature, "功能", Icons.Outlined.Apps),
            BottomNavItem(Screen.Settings, "设置", Icons.Outlined.Settings),
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
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
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onItemClicked = { itemId ->
                            navController.navigate(Screen.Detail.createRoute(itemId))
                        },
                        contentPadding = innerPadding,
                    )
                }
                composable(Screen.Feature.route) {
                    FeatureScreen(
                        onItemClicked = { itemId ->
                            navController.navigate(Screen.Detail.createRoute(itemId))
                        },
                        contentPadding = innerPadding,
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onItemClicked = { itemId ->
                            navController.navigate(Screen.Detail.createRoute(itemId))
                        },
                        contentPadding = innerPadding,
                    )
                }
                composable(
                    route = Screen.Detail.route,
                    arguments = listOf(
                        navArgument("itemId") { type = NavType.StringType }
                    ),
                ) { backStackEntry ->
                    val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                    DetailScreen(
                        itemId = itemId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
