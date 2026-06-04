package com.sploot.app.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoopDevicesScreen(
    nav: NavController,
    vm: WhoopDevicesViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            vm.startScan()
            vm.refreshBondedDevices()
        }
    }

    DisposableEffect(Unit) {
        vm.refreshBondedDevices()
        onDispose { vm.stopScan() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WHOOP Devices") },
                navigationIcon = {
                    IconButton(onClick = nav::popBackStack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Pick a preferred WHOOP device for live recording and scheduled historical syncs. Bonded devices are shown first; nearby devices appear when scanned.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { permissionLauncher.launch(blePermissions()) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Scan Nearby") }
                    OutlinedButton(
                        onClick = vm::refreshBondedDevices,
                        modifier = Modifier.weight(1f),
                    ) { Text("Refresh Bonded") }
                }
            }

            if (state.devices.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No WHOOP devices found yet. Try Scan Nearby with your strap awake, or Refresh Bonded if you've already paired it in Android settings.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            items(state.devices, key = { it.address }) { device ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(device.name, style = MaterialTheme.typography.titleSmall)
                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                        Text(
                            buildString {
                                append(if (device.isBonded) "Bonded" else "Not bonded")
                                append(" • ")
                                append(if (device.isNearby) "Nearby" else "Not currently seen")
                                if (state.preferredAddress == device.address) append(" • Preferred")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!device.isBonded) {
                                OutlinedButton(
                                    onClick = { vm.pair(device.address) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Pair") }
                            }
                            Button(
                                onClick = { vm.selectPreferred(device) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (state.preferredAddress == device.address) "Preferred" else "Use This Device")
                            }
                        }
                    }
                }
            }
        }
    }
}

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
