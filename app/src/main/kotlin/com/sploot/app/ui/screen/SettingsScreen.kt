package com.sploot.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/**
 * Settings screen — Phase 1 stub.
 *
 * Will contain:
 *   - BLE scan + pair WHOOP device
 *   - Personal data (age, resting HR override)
 *   - Raw data retention policy (7 / 14 / 30 days)
 *   - Navigate to Garmin Import
 */
@Composable
fun SettingsScreen(nav: NavController) {
    // TODO Phase 1 (debug pairing screen first)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Settings — coming soon")
    }
}
