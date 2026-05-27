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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * Garmin export import screen.
 *
 * The user taps "Import ZIP", the SAF file picker opens, they select their
 * Garmin Connect health export archive, and we parse it in the background.
 *
 * Phase 3: wire up the GarminZipParser ViewModel + show import progress + result summary.
 */
@Composable
fun GarminImportScreen(nav: NavController) {
    val context  = LocalContext.current
    var statusText by remember { mutableStateOf("No import yet.") }

    val pickZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            statusText = "Selected: $uri\nPhase 3: parsing will start here."
            // TODO Phase 3: launch GarminImportViewModel.import(uri)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Import Garmin Health Export")
        Spacer(Modifier.height(16.dp))
        Text("Export your data from garmin.com → Account → Data Export,\nthen share or open the ZIP here.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = { pickZipLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
            Text("Choose ZIP file")
        }
        Spacer(Modifier.height(16.dp))
        Text(statusText)
    }
}
