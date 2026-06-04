package com.sploot.app.ui.debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import com.sploot.app.service.WhoopRuntimeState
import com.sploot.whoopble.model.TraceDirection
import com.sploot.whoopble.model.WhoopBleTraceEvent
import com.sploot.whoopble.gatt.ConnectionState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    nav: NavController,
    vm: DebugViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        vm.onScreenVisible()
        onDispose {
            vm.onScreenHidden()
        }
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
            ConnectionChip(
                connectionState = state.connectionState,
                runtimeState = state.runtimeState,
            )

            Spacer(Modifier.height(28.dp))

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

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.batteryPercent?.let { pct ->
                    MetricLabel(label = "Battery", value = "${pct.toInt()}%")
                }
                state.tempCelsius?.let { temp ->
                    MetricLabel(label = "Skin temp", value = "${"%.1f".format(temp)} C")
                }
                if (state.packetCount > 0) {
                    MetricLabel(label = "Packets", value = "${state.packetCount}")
                }
                if (state.incomingPacketCount > 0 || state.outgoingPacketCount > 0) {
                    MetricLabel(label = "BLE I/O", value = "${state.outgoingPacketCount}/${state.incomingPacketCount}")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Double-tap action: ${state.configuredDoubleTapAction}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            state.latestBandEvent?.let { latestEvent ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Latest band event: $latestEvent",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }

            Spacer(Modifier.height(24.dp))

            PpgWaveform(
                samples = state.ppgChannelA,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                    ),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "PPG channel A (Green 1) - 5 s window",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Opening this debug view starts live WHOOP streaming. Leaving it stops live capture again so daily use can stay battery-light.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = vm::toggleHaptics,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.hapticsActive) "Stop Haptics" else "Start Haptics")
                }
                OutlinedButton(
                    onClick = vm::stopHaptics,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Force Stop")
                }
            }

            Spacer(Modifier.height(12.dp))

            UnknownAuditPanel(
                rows = state.unknownAuditRows,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            TracePanel(
                events = state.traceEvents,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun UnknownAuditPanel(
    rows: List<UnknownAuditRow>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Unknown Packet Audit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (rows.isEmpty()) {
                    "No unknown WHOOP events or undecoded data frames seen in this session."
                } else {
                    "Grouped signatures help us reverse-engineer packet families by frequency, size, and sample payload."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            if (rows.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    RoundedCornerShape(14.dp),
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "${row.count}x  ${row.signature}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            row.latestNote?.let { note ->
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                )
                            }
                            Text(
                                text = row.latestHexPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionChip(
    connectionState: ConnectionState,
    runtimeState: WhoopRuntimeState,
) {
    val (label, color) = when (runtimeState) {
        WhoopRuntimeState.STARTING_LIVE -> "Starting live stream..." to Color(0xFFFFC107)
        WhoopRuntimeState.LIVE -> "Streaming" to Color(0xFF4CAF50)
        WhoopRuntimeState.STARTING_HISTORY -> "Starting sync..." to Color(0xFFFFC107)
        WhoopRuntimeState.HISTORY -> "Syncing history..." to Color(0xFF2196F3)
        WhoopRuntimeState.SWITCHING_TO_LIVE -> "Switching to live..." to Color(0xFFFFC107)
        WhoopRuntimeState.SWITCHING_TO_HISTORY -> "Switching to sync..." to Color(0xFFFFC107)
        WhoopRuntimeState.STOPPING -> "Stopping..." to Color(0xFF757575)
        WhoopRuntimeState.ERROR -> "Connection failed" to Color(0xFFE53935)
        WhoopRuntimeState.IDLE -> when (connectionState) {
            ConnectionState.DISCONNECTED -> "Disconnected" to Color(0xFF757575)
            ConnectionState.CONNECTING -> "Connecting..." to Color(0xFFFFC107)
            ConnectionState.CONNECTED -> "Connected" to Color(0xFF2196F3)
            ConnectionState.READY -> "Streaming" to Color(0xFF4CAF50)
        }
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
            drawLine(
                color = lineColor.copy(alpha = 0.3f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1.dp.toPx(),
            )
            return@Canvas
        }

        val w = size.width
        val h = size.height
        val min = samples.min()
        val max = samples.max()
        val range = (max - min).coerceAtLeast(1f)
        val xStep = w / (samples.size - 1)

        val path = Path()
        samples.forEachIndexed { i, v ->
            val x = i * xStep
            val y = h * (1f - (v - min) / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

@Composable
private fun TracePanel(
    events: List<WhoopBleTraceEvent>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "BLE Trace",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (events.isEmpty()) {
                    "No packets captured yet. After the handshake, this should show outgoing commands and incoming notifications."
                } else {
                    "Newest packets first. Outgoing/incoming/internal events help confirm whether the strap is actually talking to the phone."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events) { event ->
                    TraceRow(event = event)
                }
            }
        }
    }
}

@Composable
private fun TraceRow(event: WhoopBleTraceEvent) {
    val tint = when (event.direction) {
        TraceDirection.OUTGOING -> Color(0xFFFFA24C)
        TraceDirection.INCOMING -> Color(0xFF57E6B1)
        TraceDirection.INTERNAL -> Color(0xFF7AC7FF)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${event.direction.name.lowercase()} · ${event.channel}",
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
            Text(
                text = formatTraceTime(event),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
            )
        }
        Text(
            text = event.summary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = if (event.sizeBytes > 0) "${event.sizeBytes} bytes" else "internal state",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
        if (event.hexPreview.isNotBlank()) {
            Text(
                text = event.hexPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
        }
    }
}

private fun formatTraceTime(event: WhoopBleTraceEvent): String =
    DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(event.timestamp)
