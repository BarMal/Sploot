package com.sploot.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.sploot.app.ui.Routes

/**
 * Dashboard screen — Phase 2 implementation.
 *
 * Phase 1 version: navigation hub to the debug live feed, Garmin import, and settings.
 *
 * Phase 2 will add:
 *   - Recovery score ring (colour-coded green/yellow/red)
 *   - Last night's sleep summary (stacked bar: deep/REM/light/awake)
 *   - 7-day RMSSD sparkline
 *   - Whoop connection status chip
 */
@Composable
fun DashboardScreen(nav: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Sploot") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Phase 1",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                letterSpacing = 2.sp,
            )
            Text(
                text = "Ready to stream",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Connect your WHOOP 4.0 to see\nlive heart rate and PPG waveform.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            // ── Primary action: open the live debug feed ──────────────────────
            Button(
                onClick = { nav.navigate(Routes.DEBUG) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Live Debug Feed")
            }

            // ── Secondary actions ─────────────────────────────────────────────
            OutlinedButton(
                onClick = { nav.navigate(Routes.GARMIN_IMPORT) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import Garmin Export")
            }

            OutlinedButton(
                onClick = { nav.navigate(Routes.SETTINGS) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Settings")
            }
        }
    }
}
