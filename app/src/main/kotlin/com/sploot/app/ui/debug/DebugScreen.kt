package com.sploot.app.ui.debug

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sploot.whoopble.gatt.ConnectionState

@Composable
fun DebugScreen(
    nav: NavController,
    vm: DebugViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    // BLE permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) vm.startRecording()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Live Debug Feed") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Connection state chip ─────────────────────────────────────────
            ConnectionChip(state.connectionState)

            Spacer(Modifier.height(28.dp))

            // ── HR number ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (state.hrBpm > 0) "${state.hrBpm}" else "--",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = " bpm",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            // ── Auxiliary readings ───────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.batteryPercent?.let { pct ->
                    MetricLabel(label = "Battery", value = "${pct.toInt()}%")
                }
                state.tempCelsius?.let { temp ->
                    MetricLabel(label = "Skin temp", value = "${"%.1f".format(temp)} °C")
                }
                if (state.packetCount > 0) {
                    MetricLabel(label = "Packets", value = "${state.packetCount}")
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── PPG channel A waveform ───────────────────────────────────────
            PpgWaveform(
                samples   = state.ppgChannelA,
                modifier  = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                    ),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "PPG channel A (Green 1) — 5 s window",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )

            Spacer(Modifier.weight(1f))

            // ── Start / Stop buttons ─────────────────────────────────────────
            val isActive = state.connectionState != ConnectionState.DISCONNECTED

            if (!isActive) {
                Button(
                    onClick = {
                        val permissions = blePermissions()
                        permissionLauncher.launch(permissions)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start Recording")
                }
            } else {
                OutlinedButton(
                    onClick  = vm::stopRecording,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Stop Recording")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun ConnectionChip(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.DISCONNECTED -> "Disconnected" to Color(0xFF757575)
        ConnectionState.CONNECTING   -> "Connecting…"  to Color(0xFFFFC107)
        ConnectionState.CONNECTED    -> "Connected"     to Color(0xFF2196F3)
        ConnectionState.READY        -> "Streaming"     to Color(0xFF4CAF50)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun MetricLabel(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PpgWaveform(samples: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.padding(8.dp)) {
        if (samples.size < 2) {
            // Empty state: draw a flat centre line
            drawLine(
                color = lineColor.copy(alpha = 0.3f),
                start = Offset(0f, size.height / 2),
                end   = Offset(size.width, size.height / 2),
                strokeWidth = 1.dp.toPx(),
            )
            return@Canvas
        }

        val w = size.width
        val h = size.height
        val min    = samples.min()
        val max    = samples.max()
        val range  = (max - min).coerceAtLeast(1f)
        val xStep  = w / (samples.size - 1)

        val path = Path()
        samples.forEachIndexed { i, v ->
            val x = i * xStep
            val y = h * (1f - (v - min) / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path        = path,
            color       = lineColor,
            style       = Stroke(width = 1.5.dp.toPx()),
        )
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
