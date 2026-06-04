package com.sploot.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.sploot.app.health.HealthConnectGarminImporter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@Composable
fun GarminImportScreen(
    nav: NavController,
    vm: GarminImportViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.import(uri)
        }
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(
            HealthConnectGarminImporter.HEALTH_CONNECT_PROVIDER_PACKAGE
        )
    ) { granted ->
        if (granted.containsAll(vm.requiredHealthConnectPermissions())) {
            vm.refreshHealthConnectState()
            vm.syncHealthConnect()
        } else {
            vm.refreshHealthConnectState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Import Garmin Data")
        Spacer(Modifier.height(16.dp))
        Text(
            "Use Health Connect for automatic Garmin sync on Android, or upload Garmin ZIP, CSV, or FIT files manually. " +
                "Repeated uploads are deduplicated, and overlapping data is upserted by natural key."
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    state.healthConnectPermissionsGranted -> vm.syncHealthConnect()
                    state.healthConnectAvailable -> healthPermissionLauncher.launch(vm.requiredHealthConnectPermissions())
                    else -> vm.refreshHealthConnectState()
                }
            },
            enabled = !state.isImporting && (state.healthConnectAvailable || state.healthConnectNeedsUpdate),
        ) {
            Text(
                when {
                    state.isImporting -> "Syncing..."
                    state.healthConnectPermissionsGranted -> "Sync Garmin via Health Connect"
                    state.healthConnectAvailable -> "Grant Health Connect Access"
                    state.healthConnectNeedsUpdate -> "Health Connect Needs Update"
                    else -> "Health Connect Unavailable"
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
            enabled = !state.isImporting,
        ) {
            Text(if (state.isImporting) "Importing..." else "Choose Garmin file")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = vm::refreshHealthConnectState, enabled = !state.isImporting) {
            Text("Refresh Health Connect State")
        }
        Spacer(Modifier.height(16.dp))
        Text(state.statusText)
    }
}
