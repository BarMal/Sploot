package com.sploot.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sploot.app.ui.debug.DebugScreen
import com.sploot.app.ui.screen.ActivityReviewScreen
import com.sploot.app.ui.screen.DashboardScreen
import com.sploot.app.ui.screen.GarminDashboardScreen
import com.sploot.app.ui.screen.GarminImportScreen
import com.sploot.app.ui.screen.OnboardingScreen
import com.sploot.app.ui.screen.SettingsScreen
import com.sploot.app.ui.screen.SleepDetailScreen
import com.sploot.app.ui.screen.TrainingDatasetScreen
import com.sploot.app.ui.screen.WhoopDevicesScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val SLEEP_DETAIL = "sleep/{sessionId}"
    const val GARMIN_DASHBOARD = "garmin_dashboard"
    const val GARMIN_IMPORT = "garmin_import"
    const val ACTIVITY_REVIEW = "activity_review"
    const val TRAINING_DATASET = "training_dataset"
    const val WHOOP_DEVICES = "whoop_devices"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
}

@Composable
fun SplootNavHost(
    shellVm: AppShellViewModel = hiltViewModel(),
) {
    val nav = rememberNavController()
    val settings by shellVm.settings.collectAsStateWithLifecycle()
    val startDestination = if (settings.hasCompletedOnboarding) {
        Routes.DASHBOARD
    } else {
        Routes.ONBOARDING
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.ONBOARDING) { OnboardingScreen(nav) }
        composable(Routes.DASHBOARD) { DashboardScreen(nav) }
        composable(Routes.GARMIN_DASHBOARD) { GarminDashboardScreen(nav) }
        composable(Routes.SLEEP_DETAIL) { backStack ->
            val sessionId = backStack.arguments?.getString("sessionId")?.toLongOrNull() ?: return@composable
            SleepDetailScreen(nav, sessionId)
        }
        composable(Routes.GARMIN_IMPORT) { GarminImportScreen(nav) }
        composable(Routes.ACTIVITY_REVIEW) { ActivityReviewScreen(nav) }
        composable(Routes.TRAINING_DATASET) { TrainingDatasetScreen(nav) }
        composable(Routes.WHOOP_DEVICES) { WhoopDevicesScreen(nav) }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(Routes.DEBUG) { DebugScreen(nav) }
    }
}
