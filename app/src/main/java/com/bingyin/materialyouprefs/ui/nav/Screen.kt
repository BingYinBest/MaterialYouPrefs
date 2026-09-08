package com.bingyin.materialyouprefs.ui.nav

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Feature : Screen("feature")
    data object Settings : Screen("settings")
    data object Detail : Screen("detail/{itemId}") {
        fun createRoute(itemId: String) = "detail/$itemId"
    }
}
