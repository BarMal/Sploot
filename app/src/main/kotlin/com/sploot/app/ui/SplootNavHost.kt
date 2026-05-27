package com.sploot.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sploot.app.ui.screen.DashboardScreen
import com.sploot.app.ui.screen.GarminImportScreen
import com.sploot.app.ui.screen.SettingsScreen
import com.sploot.app.ui.screen.SleepDetailScreen

object Routes {
    const val DASHBOARD     = "dashboard"
    const val SLEEP_DETAIL  = "sleep/{sessionId}"
    const val GARMIN_IMPORT = "garmin_import"
    const val SETTINGS      = "settings"
}

@Composable
fun SplootNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD)    { DashboardScreen(nav) }
        composable(Routes.SLEEP_DETAIL) { backStack ->
            val sessionId = backStack.arguments?.getString("sessionId")?.toLongOrNull() ?: return@composable
            SleepDetailScreen(nav, sessionId)
        }
        composable(Routes.GARMIN_IMPORT) { GarminImportScreen(nav) }
        composable(Routes.SETTINGS)      { SettingsScreen(nav) }
    }
}
