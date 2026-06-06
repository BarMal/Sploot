package com.sploot.app.ui.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sploot.app.service.WhoopRecordingService
import com.sploot.app.service.WhoopRuntimeCoordinator
import com.sploot.app.service.WhoopRuntimeState
import com.sploot.app.settings.AppSettingsRepository
import com.sploot.app.settings.WhoopDoubleTapAction
import com.sploot.data.repository.RecordingRepository
import com.sploot.whoopble.gatt.ConnectionState
import com.sploot.whoopble.gatt.WhoopGattManager
import com.sploot.whoopble.model.TraceDirection
import com.sploot.whoopble.model.WhoopBleTraceEvent
import com.sploot.whoopble.model.WhoopRecord
import com.sploot.whoopble.model.WhoopUnknownObservation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/** UI state snapshot for the debug screen. */
data class DebugUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val runtimeState: WhoopRuntimeState = WhoopRuntimeState.IDLE,
    val hrBpm:           Int     = 0,
    val batteryPercent:  Float?  = null,
    val tempCelsius:     Float?  = null,
    val configuredDoubleTapAction: String = "No action",
    val latestBandEvent: String? = null,
    val hapticsActive:   Boolean = false,
    /** Rolling 500-sample (5 s) window of PPG channel A for the waveform. */
    val ppgChannelA:     List<Float> = emptyList(),
    /** Running count of R10 + R21 packets received this session. */
    val packetCount:     Int     = 0,
    val outgoingPacketCount: Int = 0,
    val incomingPacketCount: Int = 0,
    val realtimeFrameCount: Int = 0,
    val historicalFrameCount: Int = 0,
    val historicalBatchMarkers: Int = 0,
    val historicalCompleteMarkers: Int = 0,
    val traceEvents: List<WhoopBleTraceEvent> = emptyList(),
    val unknownAuditRows: List<UnknownAuditRow> = emptyList(),
)

data class UnknownAuditRow(
    val signature: String,
    val count: Int,
    val latestHexPreview: String,
    val latestNote: String?,
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val gattManager: WhoopGattManager,
    private val whoopRuntimeCoordinator: WhoopRuntimeCoordinator,
    private val settingsRepository: AppSettingsRepository,
    private val recordingRepository: RecordingRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    /** Mirror the GattManager's connection state directly. */
    val connectionState: StateFlow<ConnectionState> = gattManager.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.DISCONNECTED)

    init {
        // Sync connectionState changes into uiState
        viewModelScope.launch {
            gattManager.state.collect { cs ->
                _uiState.update { it.copy(connectionState = cs) }
                // Clear stale data on disconnect
                if (cs == ConnectionState.DISCONNECTED && shouldClearOnDisconnect(_uiState.value.runtimeState)) {
                    _uiState.update {
                        it.copy(
                            hrBpm = 0,
                            ppgChannelA = emptyList(),
                            hapticsActive = false,
                        packetCount = 0,
                        outgoingPacketCount = 0,
                        incomingPacketCount = 0,
                        realtimeFrameCount = 0,
                        historicalFrameCount = 0,
                        historicalBatchMarkers = 0,
                        historicalCompleteMarkers = 0,
                        traceEvents = emptyList(),
                        unknownAuditRows = emptyList(),
                    )
                    }
                }
            }
        }
        viewModelScope.launch {
            whoopRuntimeCoordinator.state.collect { runtimeState ->
                _uiState.update { it.copy(runtimeState = runtimeState) }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(configuredDoubleTapAction = settings.whoopDoubleTapAction.toDisplayLabel())
                }
            }
        }
        viewModelScope.launch {
            gattManager.traceEvents.collect { trace ->
                _uiState.update { state ->
                    state.copy(
                        outgoingPacketCount = state.outgoingPacketCount + if (trace.direction == TraceDirection.OUTGOING) 1 else 0,
                        incomingPacketCount = state.incomingPacketCount + if (trace.direction == TraceDirection.INCOMING) 1 else 0,
                        realtimeFrameCount = state.realtimeFrameCount + if (trace.summary.contains("raw-realtime")) 1 else 0,
                        historicalFrameCount = state.historicalFrameCount + if (trace.summary.contains("historical-data")) 1 else 0,
                        historicalBatchMarkers = state.historicalBatchMarkers + if (trace.summary.contains("Historical batch marker")) 1 else 0,
                        historicalCompleteMarkers = state.historicalCompleteMarkers + if (trace.summary.contains("Historical sync complete")) 1 else 0,
                        traceEvents = (listOf(trace) + state.traceEvents).take(80),
                    )
                }
            }
        }
        viewModelScope.launch {
            gattManager.unknownObservations.collect { observation ->
                _uiState.update { state ->
                    state.copy(
                        unknownAuditRows = mergeUnknownObservation(
                            current = state.unknownAuditRows,
                            incoming = observation,
                        ),
                    )
                }
            }
        }
        // Collect records
        viewModelScope.launch {
            gattManager.records.collect { record ->
                when (record) {
                    is WhoopRecord.Imu -> {
                        _uiState.update { s ->
                            val updatedHr = record.hrBpm.takeIf { it in PLAUSIBLE_HR_RANGE } ?: s.hrBpm
                            s.copy(
                                hrBpm       = updatedHr,
                                packetCount = s.packetCount + 1,
                            )
                        }
                    }

                    is WhoopRecord.HeartRate -> {
                        _uiState.update { s ->
                            val updatedHr = record.hrBpm.takeIf { it in PLAUSIBLE_HR_RANGE } ?: s.hrBpm
                            s.copy(
                                hrBpm = updatedHr,
                                packetCount = s.packetCount + 1,
                            )
                        }
                    }

                    is WhoopRecord.Ppg -> {
                        val newSamples = record.channelA.map { it.toFloat() }
                        _uiState.update { s ->
                            val updated = (s.ppgChannelA + newSamples).takeLast(PPG_WINDOW_SAMPLES)
                            s.copy(ppgChannelA = updated, packetCount = s.packetCount + 1)
                        }
                    }

                    is WhoopRecord.Battery ->
                        _uiState.update { state ->
                            state.copy(batteryPercent = mergeBatteryPercent(state.batteryPercent, record))
                        }

                    is WhoopRecord.Temperature ->
                        _uiState.update { it.copy(tempCelsius = record.celsius) }

                    is WhoopRecord.DoubleTap ->
                        handleDoubleTap()

                    is WhoopRecord.HapticsFired ->
                        _uiState.update {
                            it.copy(
                                latestBandEvent = "Haptics fired${record.patternId?.let { id -> " (pattern $id)" } ?: ""}",
                                hapticsActive = true,
                            )
                        }

                    is WhoopRecord.HapticsTerminated ->
                        _uiState.update {
                            it.copy(
                                latestBandEvent = "Haptics stopped${record.reasonCode?.let { code -> " (reason $code)" } ?: ""}",
                                hapticsActive = false,
                            )
                        }

                    is WhoopRecord.CapTouchAutoThreshold ->
                        _uiState.update {
                            val summary = record.payloadHex.take(23).ifBlank { "no payload" }
                            it.copy(latestBandEvent = "Cap-touch calibration ($summary)")
                        }

                    else -> Unit
                }
            }
        }
    }

    // ── Service control ───────────────────────────────────────────────────────

    fun stopRecording() {
        context.startService(WhoopRecordingService.stopIntent(context))
    }

    fun startHistoricalBackfill() {
        val settings = settingsRepository.current()
        if (settings.preferredWhoopDeviceAddress == null) {
            _uiState.update { it.copy(latestBandEvent = "Pick a WHOOP device before backfill") }
            return
        }
        context.startForegroundService(WhoopRecordingService.startHistoricalSyncIntent(context))
        _uiState.update { it.copy(latestBandEvent = "Requested historical backfill") }
    }

    fun onScreenVisible() {
        val settings = settingsRepository.current()
        if (settings.preferredWhoopDeviceAddress == null) return

        val runtimeState = whoopRuntimeCoordinator.state.value
        if (runtimeState != WhoopRuntimeState.IDLE &&
            runtimeState != WhoopRuntimeState.ERROR &&
            runtimeState != WhoopRuntimeState.STOPPING
        ) {
            return
        }
        if (gattManager.state.value != ConnectionState.DISCONNECTED) return

        context.startForegroundService(WhoopRecordingService.startIntent(context))
    }

    fun onScreenHidden() {
        val runtimeState = whoopRuntimeCoordinator.state.value
        if (runtimeState == WhoopRuntimeState.LIVE ||
            runtimeState == WhoopRuntimeState.STARTING_LIVE ||
            runtimeState == WhoopRuntimeState.SWITCHING_TO_LIVE
        ) {
            context.startService(WhoopRecordingService.stopIntent(context))
        }
    }

    fun startHaptics() {
        viewModelScope.launch {
            gattManager.runHapticsPattern()
            _uiState.update { it.copy(latestBandEvent = "Requested haptics start") }
        }
    }

    fun stopHaptics() {
        viewModelScope.launch {
            gattManager.stopHaptics()
            _uiState.update { it.copy(latestBandEvent = "Requested haptics stop") }
        }
    }

    fun toggleHaptics() {
        if (_uiState.value.hapticsActive) stopHaptics() else startHaptics()
    }

    private fun shouldClearOnDisconnect(runtimeState: WhoopRuntimeState): Boolean =
        runtimeState == WhoopRuntimeState.IDLE ||
            runtimeState == WhoopRuntimeState.STOPPING ||
            runtimeState == WhoopRuntimeState.ERROR

    private fun handleDoubleTap() {
        val action = settingsRepository.current().whoopDoubleTapAction
        val actionSummary = when (action) {
            WhoopDoubleTapAction.NONE -> "No action"
            WhoopDoubleTapAction.START_HAPTICS -> "Start haptics"
            WhoopDoubleTapAction.STOP_HAPTICS -> "Stop haptics"
            WhoopDoubleTapAction.TOGGLE_HAPTICS -> "Toggle haptics"
        }
        _uiState.update { it.copy(latestBandEvent = "Double tap · $actionSummary") }
        when (action) {
            WhoopDoubleTapAction.NONE -> Unit
            WhoopDoubleTapAction.START_HAPTICS -> startHaptics()
            WhoopDoubleTapAction.STOP_HAPTICS -> stopHaptics()
            WhoopDoubleTapAction.TOGGLE_HAPTICS -> toggleHaptics()
        }
    }

    companion object {
        /** Number of PPG samples to keep in the rolling waveform buffer (5 s at 100 Hz). */
        private const val PPG_WINDOW_SAMPLES = 500
        private val PLAUSIBLE_HR_RANGE = 30..240
    }
}

private fun WhoopDoubleTapAction.toDisplayLabel(): String =
    when (this) {
        WhoopDoubleTapAction.NONE -> "No action"
        WhoopDoubleTapAction.START_HAPTICS -> "Start haptics"
        WhoopDoubleTapAction.STOP_HAPTICS -> "Stop haptics"
        WhoopDoubleTapAction.TOGGLE_HAPTICS -> "Toggle haptics"
    }

private fun mergeBatteryPercent(
    current: Float?,
    incoming: WhoopRecord.Battery,
): Float =
    when {
        current == null -> incoming.percent
        incoming.source.startsWith("cmd:") -> incoming.percent
        abs(incoming.percent - current) > DEBUG_BATTERY_EVENT_JUMP_THRESHOLD_PERCENT -> current
        else -> incoming.percent
    }

private fun mergeUnknownObservation(
    current: List<UnknownAuditRow>,
    incoming: WhoopUnknownObservation,
): List<UnknownAuditRow> {
    val signature = "${incoming.category.name}:${incoming.packetTypeName}:${incoming.identifierLabel}:${incoming.frameSizeBytes}B"
    val updated = current.toMutableList()
    val existingIndex = updated.indexOfFirst { it.signature == signature }
    if (existingIndex >= 0) {
        val existing = updated[existingIndex]
        updated[existingIndex] = existing.copy(
            count = existing.count + 1,
            latestHexPreview = incoming.hexPreview,
            latestNote = incoming.note,
        )
    } else {
        updated.add(
            UnknownAuditRow(
                signature = signature,
                count = 1,
                latestHexPreview = incoming.hexPreview,
                latestNote = incoming.note,
            ),
        )
    }
    return updated
        .sortedByDescending { it.count }
        .take(12)
}

private const val DEBUG_BATTERY_EVENT_JUMP_THRESHOLD_PERCENT = 5f
