package com.sploot.app.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sploot.app.ui.debug.DebugViewModel
import com.sploot.app.ui.Routes
import com.sploot.whoopble.gatt.ConnectionState

/**
 * Settings screen — Phase 1.
 *
 * Provides:
 *   - Start / Stop recording directly (without navigating to the debug feed)
 *   - Link to the live debug feed
 *   - Link to Garmin import
 *
 * Phase 2 will add:
 *   - Personal data (age, resting HR override)
 *   - Raw data retention policy (7 / 14 / 30 days)
 */
@Composable
fun SettingsScreen(
    nav: NavController,
    vm: DebugViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) vm.startRecording()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Recording section ─────────────────────────────────────────────
            Text("WHOOP Recording", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

            val isActive = state.connectionState != ConnectionState.DISCONNECTED

            if (!isActive) {
                Button(
                    onClick = {
                        val perms = blePermissions()
                        permissionLauncher.launch(perms)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start Recording")
                }
            } else {
                OutlinedButton(
                    onClick = vm::stopRecording,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Stop Recording")
                }
            }

            OutlinedButton(
                onClick = { nav.navigate(Routes.DEBUG) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Live Debug Feed")
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ── Data section ──────────────────────────────────────────────────
            Text("Data", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

            OutlinedButton(
                onClick = { nav.navigate(Routes.GARMIN_IMPORT) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import Garmin Export")
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ── Placeholder for Phase 2 settings ─────────────────────────────
            Text(
                "Personal data, retention policy, and algorithm tuning options coming in Phase 2.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun blePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
