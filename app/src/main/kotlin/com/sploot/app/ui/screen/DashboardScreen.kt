package com.sploot.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/**
 * Dashboard screen — Phase 2 implementation.
 *
 * Will show:
 *   - Recovery score ring (colour-coded green/yellow/red)
 *   - Last night's sleep summary (stacked bar: deep/REM/light/awake)
 *   - 7-day RMSSD sparkline
 *   - Whoop connection status chip
 *   - Start/Stop recording FAB
 */
@Composable
fun DashboardScreen(nav: NavController) {
    // TODO Phase 2
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Dashboard — coming in Phase 2")
    }
}
