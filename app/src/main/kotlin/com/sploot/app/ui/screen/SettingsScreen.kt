package com.sploot.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sploot.app.settings.WhoopDoubleTapAction
import com.sploot.app.ui.Routes
import com.sploot.app.ui.debug.DebugViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nav: NavController,
    debugVm: DebugViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
) {
    val debugState by debugVm.uiState.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Sploot now defaults to daily-wear behavior: app-open sync, fixed daily sync, and no always-on live WHOOP session. The live debug view is the place where on-demand streaming starts. Stream toggles change what the strap is asked to send during a sync or debug session, and interval controls change how often Sploot keeps and processes the data it receives.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }

            item { SectionHeader("WHOOP Recording") }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Preferred WHOOP device", style = MaterialTheme.typography.titleSmall)
                        Text(
                            settings.preferredWhoopDeviceName?.let {
                                "$it\n${settings.preferredWhoopDeviceAddress.orEmpty()}"
                            } ?: "No preferred WHOOP selected yet.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.WHOOP_DEVICES) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Manage WHOOP Devices")
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConnectionStatusCard(
                        status = when {
                            debugState.runtimeState == com.sploot.app.service.WhoopRuntimeState.HISTORY ||
                                debugState.runtimeState == com.sploot.app.service.WhoopRuntimeState.STARTING_HISTORY ||
                                debugState.runtimeState == com.sploot.app.service.WhoopRuntimeState.SWITCHING_TO_HISTORY -> "Syncing"
                            debugState.connectionState == com.sploot.whoopble.gatt.ConnectionState.READY ||
                                debugState.runtimeState == com.sploot.app.service.WhoopRuntimeState.LIVE ||
                                debugState.runtimeState == com.sploot.app.service.WhoopRuntimeState.STARTING_LIVE ||
                                debugState.runtimeState == com.sploot.app.service.WhoopRuntimeState.SWITCHING_TO_LIVE -> "Connected"
                            else -> "Disconnected"
                        },
                        batteryPercent = debugState.batteryPercent,
                    )
                    OutlinedButton(
                        onClick = { nav.navigate(Routes.DEBUG) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open Live Debug Feed")
                    }
                }
            }

            item {
                WarningCard(
                    title = "Source-level WHOOP stream toggles",
                    body = "These switches attempt to enable or disable HR, IMU, and optical PPG at the strap itself when Sploot syncs or opens a live debug session. Turning a stream off saves radio, parsing, storage, and processing cost, but it removes that signal entirely until you re-enable it.",
                )
            }

            item {
                ToggleRow(
                    title = "Enable WHOOP heart-rate stream",
                    subtitle = "Disabling removes HR telemetry during sync/debug sessions and weakens activity, strain, calories, and comparison work. In this build, HR is observed through the R10 path, so IMU off effectively means no usable live HR.",
                    checked = settings.enableWhoopHrStream,
                    onCheckedChange = settingsVm::setEnableWhoopHrStream,
                )
            }

            item {
                ToggleRow(
                    title = "Enable WHOOP IMU stream",
                    subtitle = "Disabling removes accelerometer and gyroscope data, which significantly hurts activity detection and motion-derived analysis.",
                    checked = settings.enableWhoopImuStream,
                    onCheckedChange = settingsVm::setEnableWhoopImuStream,
                )
            }

            item {
                ToggleRow(
                    title = "Enable WHOOP optical PPG stream",
                    subtitle = "Disabling removes raw optical waveform data, which hurts sleep, IBI extraction, HRV, and future recovery work the most.",
                    checked = settings.enableWhoopPpgStream,
                    onCheckedChange = settingsVm::setEnableWhoopPpgStream,
                )
            }

            item {
                WarningCard(
                    title = "System Battery Saver integration",
                    body = "When enabled, Sploot watches Android Battery Saver and applies a second, lower-power WHOOP profile while Battery Saver is on. This lets you keep a high-fidelity default profile while still protecting the phone when the system enters power-saving mode.",
                )
            }

            item {
                ToggleRow(
                    title = "Follow Android Battery Saver",
                    subtitle = "Apply the lower-power profile below whenever system Battery Saver is active during a sync or debug session.",
                    checked = settings.followSystemBatterySaver,
                    onCheckedChange = settingsVm::setFollowSystemBatterySaver,
                )
            }

            item {
                WarningCard(
                    title = "Daily WHOOP sync",
                    body = "WHOOP appears to keep a rolling historical buffer, and Sploot now reconnects on a fixed daily cadence to pull that backlog instead of relying on a continuous live session. Pick a preferred WHOOP device first.",
                )
            }

            item {
                ToggleRow(
                    title = "Enable daily WHOOP sync",
                    subtitle = "Schedules one recurring reconnect per day to pull stored WHOOP history from your preferred device.",
                    checked = settings.enablePeriodicHistoricalSync,
                    onCheckedChange = settingsVm::setEnablePeriodicHistoricalSync,
                )
            }

            item {
                ToggleRow(
                    title = "Battery Saver disables WHOOP heart-rate stream",
                    subtitle = "Strongest data loss for activity, calories, strain, and comparisons. Usually leave this off unless you want the most aggressive battery savings.",
                    checked = settings.batterySaverDisableWhoopHrStream,
                    onCheckedChange = settingsVm::setBatterySaverDisableWhoopHrStream,
                )
            }

            item {
                ToggleRow(
                    title = "Battery Saver disables WHOOP IMU stream",
                    subtitle = "Cuts motion sensing while Battery Saver is on. This also makes live HR less useful in the current build because HR is observed through the R10 path.",
                    checked = settings.batterySaverDisableWhoopImuStream,
                    onCheckedChange = settingsVm::setBatterySaverDisableWhoopImuStream,
                )
            }

            item {
                ToggleRow(
                    title = "Battery Saver disables WHOOP optical PPG stream",
                    subtitle = "Largest accuracy hit for sleep, IBI extraction, HRV, and recovery research, but often the best battery-saving switch.",
                    checked = settings.batterySaverDisableWhoopPpgStream,
                    onCheckedChange = settingsVm::setBatterySaverDisableWhoopPpgStream,
                )
            }

            item {
                ToggleRow(
                    title = "Battery Saver forces a global capture interval",
                    subtitle = "When enabled, Battery Saver overrides per-stream persistence intervals with one lower-power interval.",
                    checked = settings.batterySaverForceGlobalWhoopInterval,
                    onCheckedChange = settingsVm::setBatterySaverForceGlobalWhoopInterval,
                )
            }

            item {
                IntervalSliderRow(
                    title = "Battery Saver global capture interval",
                    subtitle = "Higher values keep fewer rows while Battery Saver is active.",
                    value = settings.batterySaverGlobalWhoopIntervalSeconds,
                    unitLabel = "seconds",
                    enabled = settings.followSystemBatterySaver && settings.batterySaverForceGlobalWhoopInterval,
                    onValueChange = settingsVm::setBatterySaverGlobalWhoopInterval,
                    disabledMessage = if (!settings.followSystemBatterySaver) {
                        "Enable Android Battery Saver integration to use this slider"
                    } else {
                        "Enable Battery Saver global interval override to use this slider"
                    },
                )
            }

            item {
                WarningCard(
                    title = "Global WHOOP capture override",
                    body = "When enabled, the single interval below overrides IMU, optical PPG, and HR persistence intervals during sync/debug sessions. This lowers storage and processing cost, but it also reduces raw signal resolution and can degrade sleep, HRV, and activity accuracy.",
                )
            }

            item {
                ToggleRow(
                    title = "Enable global WHOOP capture interval",
                    subtitle = "Quick access for downsampling all continuous WHOOP streams together.",
                    checked = settings.globalWhoopCaptureIntervalEnabled,
                    onCheckedChange = settingsVm::setGlobalWhoopCaptureEnabled,
                )
            }

            item {
                IntervalSliderRow(
                    title = "Global WHOOP capture interval",
                    subtitle = "Higher values save fewer records and reduce post-processing cost.",
                    value = settings.globalWhoopCaptureIntervalSeconds,
                    unitLabel = "seconds",
                    enabled = settings.globalWhoopCaptureIntervalEnabled,
                    onValueChange = settingsVm::setGlobalWhoopCaptureInterval,
                    disabledMessage = "Enable the global WHOOP override to use this slider",
                )
            }

            item {
                WarningCard(
                    title = "Per-stream capture intervals",
                    body = "IMU primarily affects movement and activity segmentation. PPG primarily affects optical fidelity, IBI extraction, and sleep/HRV work. HR primarily affects activity, comparison, and strain proxies. These controls are disabled when the global WHOOP override is on, and they are also meaningless when the corresponding stream is disabled at the strap.",
                )
            }

            item {
                IntervalSliderRow(
                    title = "IMU persist interval",
                    subtitle = "Impacts activity segmentation and motion-derived load estimates.",
                    value = settings.imuCaptureIntervalSeconds,
                    unitLabel = "seconds",
                    enabled = !settings.globalWhoopCaptureIntervalEnabled && settings.enableWhoopImuStream,
                    onValueChange = settingsVm::setImuInterval,
                    disabledMessage = if (!settings.enableWhoopImuStream) "IMU stream disabled at strap" else "Controlled by global WHOOP interval",
                )
            }

            item {
                IntervalSliderRow(
                    title = "PPG persist interval",
                    subtitle = "Impacts optical waveform quality, IBI extraction, and future HRV/sleep research.",
                    value = settings.ppgCaptureIntervalSeconds,
                    unitLabel = "seconds",
                    enabled = !settings.globalWhoopCaptureIntervalEnabled && settings.enableWhoopPpgStream,
                    onValueChange = settingsVm::setPpgInterval,
                    disabledMessage = if (!settings.enableWhoopPpgStream) "PPG stream disabled at strap" else "Controlled by global WHOOP interval",
                )
            }

            item {
                IntervalSliderRow(
                    title = "HR persist interval",
                    subtitle = "Impacts derived activities, comparisons, and strain estimates.",
                    value = settings.hrCaptureIntervalSeconds,
                    unitLabel = "seconds",
                    enabled = !settings.globalWhoopCaptureIntervalEnabled && settings.enableWhoopHrStream,
                    onValueChange = settingsVm::setHrInterval,
                    disabledMessage = if (!settings.enableWhoopHrStream) "HR stream disabled at strap" else "Controlled by global WHOOP interval",
                )
            }

            item {
                ToggleRow(
                    title = "Capture WHOOP events",
                    subtitle = "Battery, temperature, wrist on/off, and touch-related events. Disabling reduces metadata visibility.",
                    checked = settings.captureWhoopEvents,
                    onCheckedChange = settingsVm::setCaptureWhoopEvents,
                )
            }

            item {
                ActionSelectionRow(
                    title = "WHOOP double-tap action",
                    subtitle = "Applies while Sploot is actively connected in a live debug session. Daily syncs stay passive, so this is for intentional strap-side experiments while the app is open.",
                    selected = settings.whoopDoubleTapAction,
                    onSelected = settingsVm::setWhoopDoubleTapAction,
                )
            }

            item {
                ToggleRow(
                    title = "Prompt to tag unknown WHOOP packets",
                    subtitle = "Shows an optional inline-reply notification when a new unknown packet signature appears during a WHOOP session. Sploot still audits unknown packets when this is off, and prompts are suppressed while Android Do Not Disturb is active.",
                    checked = settings.enableWhoopUnknownTagPrompts,
                    onCheckedChange = settingsVm::setEnableWhoopUnknownTagPrompts,
                )
            }

            item { HorizontalDivider() }
            item { SectionHeader("Post-Processing") }

            item {
                WarningCard(
                    title = "Derived processing toggles",
                    body = "Disabling these saves CPU and battery after a sync or debug capture ends, but Sploot will not automatically refresh those derived outputs.",
                )
            }

            item {
                ToggleRow(
                    title = "Run sleep processing after recording",
                    subtitle = "Turning this off disables WHOOP-derived sleep sessions, epochs, HRV windows, and sleep review updates.",
                    checked = settings.runSleepProcessing,
                    onCheckedChange = settingsVm::setRunSleepProcessing,
                )
            }

            item {
                ToggleRow(
                    title = "Run activity processing after recording",
                    subtitle = "Turning this off disables WHOOP-derived activities and Garmin-vs-WHOOP activity comparisons.",
                    checked = settings.runActivityProcessing,
                    onCheckedChange = settingsVm::setRunActivityProcessing,
                )
            }

            item { HorizontalDivider() }
            item { SectionHeader("Garmin Data") }

            item {
                OutlinedButton(
                    onClick = { nav.navigate(Routes.GARMIN_IMPORT) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Garmin Import / Health Connect Sync")
                }
            }

            item {
                WarningCard(
                    title = "Health Connect import controls",
                    body = "These affect what Sploot reads from Garmin-origin Health Connect data during a sync. Narrowing the lookback window reduces read cost and duplicate processing, but limits backfill and historical calibration.",
                )
            }

            item {
                ToggleRow(
                    title = "Import Garmin sleep from Health Connect",
                    subtitle = "Disabling means no Garmin sleep ground truth arrives from Health Connect.",
                    checked = settings.importHealthConnectSleep,
                    onCheckedChange = settingsVm::setImportHealthConnectSleep,
                )
            }

            item {
                ToggleRow(
                    title = "Import Garmin activities from Health Connect",
                    subtitle = "Disabling means no Garmin activity sessions or laps arrive from Health Connect.",
                    checked = settings.importHealthConnectActivities,
                    onCheckedChange = settingsVm::setImportHealthConnectActivities,
                )
            }

            item {
                ToggleRow(
                    title = "Import Garmin heart rate from Health Connect",
                    subtitle = "Disabling means no Garmin HR stream arrives from Health Connect for calibration.",
                    checked = settings.importHealthConnectHeartRate,
                    onCheckedChange = settingsVm::setImportHealthConnectHeartRate,
                )
            }

            item {
                IntervalSliderRow(
                    title = "Health Connect lookback window",
                    subtitle = "How far back each Garmin sync reads.",
                    value = settings.healthConnectLookbackDays,
                    unitLabel = "days",
                    enabled = true,
                    onValueChange = settingsVm::setHealthConnectLookbackDays,
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}

@Composable
private fun WarningCard(
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    status: String,
    batteryPercent: Float?,
) {
    val accent = when (status) {
        "Connected" -> Color(0xFF57E6B1)
        "Syncing" -> Color(0xFF7AC7FF)
        else -> Color(0xFFB0B7C3)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(accent, androidx.compose.foundation.shape.CircleShape)
                )
                Text("WHOOP $status", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                batteryPercent?.let { "${it.toInt()}% battery" } ?: "Battery --",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun IntervalSliderRow(
    title: String,
    subtitle: String,
    value: Int,
    unitLabel: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    disabledMessage: String = "Controlled by global WHOOP interval",
    range: ClosedFloatingPointRange<Float> = 1f..30f,
    steps: Int = 28,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            Text(
                if (enabled) "$value $unitLabel"
                else disabledMessage,
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = range,
                steps = steps,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ActionSelectionRow(
    title: String,
    subtitle: String,
    selected: WhoopDoubleTapAction,
    onSelected: (WhoopDoubleTapAction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            Text(
                "Current: ${selected.toDisplayLabel()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            listOf(
                WhoopDoubleTapAction.NONE to "No action",
                WhoopDoubleTapAction.START_HAPTICS to "Start haptics",
                WhoopDoubleTapAction.STOP_HAPTICS to "Stop haptics",
                WhoopDoubleTapAction.TOGGLE_HAPTICS to "Toggle haptics",
            ).forEach { (action, label) ->
                val isSelected = selected == action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(action) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelected(action) },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        if (isSelected) {
                            Text(
                                "Selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun WhoopDoubleTapAction.toDisplayLabel(): String =
    when (this) {
        WhoopDoubleTapAction.NONE -> "No action"
        WhoopDoubleTapAction.START_HAPTICS -> "Start haptics"
        WhoopDoubleTapAction.STOP_HAPTICS -> "Stop haptics"
        WhoopDoubleTapAction.TOGGLE_HAPTICS -> "Toggle haptics"
    }
