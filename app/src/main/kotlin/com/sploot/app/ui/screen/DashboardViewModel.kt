package com.sploot.app.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sploot.data.repository.SleepRepository
import com.sploot.domain.model.PersonalBaseline
import com.sploot.domain.model.SleepSession
import com.sploot.domain.model.SleepSource
import com.sploot.domain.scorer.RecoveryScorer
import com.sploot.whoopble.gatt.ConnectionState
import com.sploot.whoopble.gatt.WhoopGattManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DashboardUiState(
    val connectionState: ConnectionState  = ConnectionState.DISCONNECTED,
    val latestSession:   SleepSession?    = null,
    val recoveryScore:   Int?             = null,
    /** Latest overnight RMSSD (ms) */
    val latestRmssd:     Float?           = null,
    /** One average RMSSD value per day for the last 7 days (oldest → newest, null = no data). */
    val sparklineRmssd:  List<Float?>     = emptyList(),
    val isLoading:       Boolean          = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val gattManager: WhoopGattManager,
    private val sleepRepo:   SleepRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gattManager.state.collect { cs ->
                _uiState.update { it.copy(connectionState = cs) }
            }
        }

        viewModelScope.launch {
            sleepRepo.sleepSessionsFlow().collect { sessions ->
                val latest = sessions.firstOrNull { it.source == SleepSource.ALGO }
                if (latest != null) {
                    val hrvWindows = sleepRepo.getHrvWindows(latest.id)
                    val latestWindow = hrvWindows.lastOrNull()
                    val recovery = if (latestWindow != null) {
                        val restingHr = if (latestWindow.meanRrMs > 0f)
                            60_000f / latestWindow.meanRrMs else null
                        if (restingHr != null)
                            RecoveryScorer(PersonalBaseline.EMPTY)
                                .score(latestWindow, restingHr, latest)
                        else null
                    } else null

                    _uiState.update {
                        it.copy(
                            latestSession = latest,
                            recoveryScore = recovery,
                            latestRmssd   = latestWindow?.rmssd,
                            isLoading     = false,
                        )
                    }
                } else {
                    _uiState.update { it.copy(latestSession = null, recoveryScore = null, isLoading = false) }
                }

                // Always refresh sparkline
                _uiState.update { it.copy(sparklineRmssd = buildSparkline()) }
            }
        }
    }

    private suspend fun buildSparkline(): List<Float?> {
        val nowSec  = Instant.now().epochSecond
        val weekAgo = nowSec - 7 * 86_400L
        val windows = sleepRepo.getHrvWindowsSince(weekAgo)

        return (0 until 7).map { daysBack ->
            // day 0 = oldest, day 6 = today
            val dayStart = weekAgo + daysBack.toLong() * 86_400L
            val dayEnd   = dayStart + 86_400L
            windows.filter { it.windowStartSeconds in dayStart until dayEnd }
                   .map { it.rmssd }
                   .takeIf { it.isNotEmpty() }
                   ?.average()?.toFloat()
        }
    }
}
