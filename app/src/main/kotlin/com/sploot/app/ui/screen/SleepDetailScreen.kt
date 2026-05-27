package com.sploot.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/**
 * Sleep detail screen — Phase 2 implementation.
 *
 * Will show:
 *   - Stage timeline (Vico chart, stacked colour bands)
 *   - If Garmin data available: Garmin vs Algo overlay on same timeline
 *   - Key metrics: latency, efficiency, RMSSD, SpO₂, respiration rate
 *   - Comparison screen link if ground-truth data exists for this night
 */
@Composable
fun SleepDetailScreen(nav: NavController, sessionId: Long) {
    // TODO Phase 2
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Sleep Detail (session $sessionId) — coming in Phase 2")
    }
}
