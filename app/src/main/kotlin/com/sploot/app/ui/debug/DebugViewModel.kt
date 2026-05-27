package com.sploot.app.ui.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sploot.app.service.WhoopRecordingService
import com.sploot.whoopble.gatt.ConnectionState
import com.sploot.whoopble.gatt.WhoopGattManager
import com.sploot.whoopble.model.WhoopRecord
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

/** UI state snapshot for the debug screen. */
data class DebugUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val hrBpm:           Int     = 0,
    val batteryPercent:  Float?  = null,
    val tempCelsius:     Float?  = null,
    /** Rolling 500-sample (5 s) window of PPG channel A for the waveform. */
    val ppgChannelA:     List<Float> = emptyList(),
    /** Running count of R10 + R21 packets received this session. */
    val packetCount:     Int     = 0,
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val gattManager: WhoopGattManager,
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
                if (cs == ConnectionState.DISCONNECTED) {
                    _uiState.update { it.copy(hrBpm = 0, ppgChannelA = emptyList(), packetCount = 0) }
                }
            }
        }
        // Collect records
        viewModelScope.launch {
            gattManager.records.collect { record ->
                when (record) {
                    is WhoopRecord.Imu -> {
                        _uiState.update { s ->
                            s.copy(
                                hrBpm       = record.hrBpm,
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
                        _uiState.update { it.copy(batteryPercent = record.percent) }

                    is WhoopRecord.Temperature ->
                        _uiState.update { it.copy(tempCelsius = record.celsius) }

                    else -> Unit
                }
            }
        }
    }

    // ── Service control ───────────────────────────────────────────────────────

    fun startRecording() {
        context.startForegroundService(WhoopRecordingService.startIntent(context))
    }

    fun stopRecording() {
        context.startService(WhoopRecordingService.stopIntent(context))
    }

    companion object {
        /** Number of PPG samples to keep in the rolling waveform buffer (5 s at 100 Hz). */
        private const val PPG_WINDOW_SAMPLES = 500
    }
}
