package com.sploot.app.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.HealthConnectClient
import com.sploot.app.health.HealthConnectGarminImporter
import com.sploot.data.repository.ActivityRepository
import com.sploot.data.repository.SleepRepository
import com.sploot.domain.model.ActivitySession
import com.sploot.domain.model.DailyMetricSummary
import com.sploot.domain.model.ExternalHeartRateSample
import com.sploot.domain.model.SleepSession
import com.sploot.domain.model.SleepSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class GarminDashboardUiState(
    val isLoading: Boolean = true,
    val syncStatus: String? = null,
    val latestSleep: SleepSession? = null,
    val recentSleeps: List<SleepSession> = emptyList(),
    val latestActivity: ActivitySession? = null,
    val recentActivities: List<ActivitySession> = emptyList(),
    val latestHeartRateSample: ExternalHeartRateSample? = null,
    val recentDailyMetrics: List<DailyMetricSummary> = emptyList(),
    val latestGarminTimestampSeconds: Long? = null,
    val garminSleepCount: Int = 0,
    val garminActivityCount: Int = 0,
    val garminHeartRateSampleCount: Int = 0,
)

@HiltViewModel
class GarminDashboardViewModel @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val activityRepository: ActivityRepository,
    private val healthConnectGarminImporter: HealthConnectGarminImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GarminDashboardUiState())
    val uiState: StateFlow<GarminDashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val syncStatus = syncFromHealthConnectIfAvailable()

            val now = Instant.now().epochSecond
            val from90Days = now - DAYS_90
            val from30Days = now - DAYS_30
            val from7Days = now - DAYS_7

            val garminSleeps = sleepRepository.getSessionsInRange(from90Days, now)
                .filter { it.source == SleepSource.GARMIN }
                .sortedByDescending { it.startEpochSeconds }

            val garminActivities = activityRepository.getActivitiesInRange(from90Days, now)
                .filter { it.source == GARMIN_SOURCE }
                .sortedByDescending { it.startEpochSeconds }

            val garminHrSamples = activityRepository.getHeartRateSamplesInRange(from7Days, now)
                .filter { it.source == GARMIN_SOURCE }
                .sortedByDescending { it.tsSeconds }

            val recentDailyMetrics = activityRepository.getDateRangeDailyMetrics(30)
                .filter { it.source == GARMIN_SOURCE }
                .groupBy { it.metricType }
                .values
                .mapNotNull { metricsForType ->
                    metricsForType.maxByOrNull { it.date }
                }
                .sortedByDescending { it.date }
                .take(8)

            val latestTimestamp = listOfNotNull(
                garminSleeps.firstOrNull()?.endEpochSeconds,
                garminActivities.firstOrNull()?.endEpochSeconds,
                garminHrSamples.firstOrNull()?.tsSeconds,
                recentDailyMetrics.maxOfOrNull { runCatching { Instant.parse("${it.date}T00:00:00Z").epochSecond }.getOrNull() ?: 0L },
            ).maxOrNull()

            _uiState.value = GarminDashboardUiState(
                isLoading = false,
                syncStatus = syncStatus,
                latestSleep = garminSleeps.firstOrNull(),
                recentSleeps = garminSleeps.take(7),
                latestActivity = garminActivities.firstOrNull(),
                recentActivities = garminActivities.take(8),
                latestHeartRateSample = garminHrSamples.firstOrNull(),
                recentDailyMetrics = recentDailyMetrics,
                latestGarminTimestampSeconds = latestTimestamp,
                garminSleepCount = garminSleeps.count { it.startEpochSeconds >= from30Days },
                garminActivityCount = garminActivities.count { it.startEpochSeconds >= from30Days },
                garminHeartRateSampleCount = garminHrSamples.size,
            )
        }
    }

    private suspend fun syncFromHealthConnectIfAvailable(): String? {
        val availability = healthConnectGarminImporter.availabilityStatus()
        if (availability != HealthConnectClient.SDK_AVAILABLE) {
            return "Health Connect is not available on this device."
        }

        val hasPermissions = runCatching { healthConnectGarminImporter.hasAllPermissions() }
            .getOrDefault(false)
        if (!hasPermissions) {
            return "Garmin data will appear here after you grant Health Connect access."
        }

        return runCatching { healthConnectGarminImporter.syncLast30Days() }
            .fold(
                onSuccess = { result ->
                    "Synced Garmin via Health Connect: sleep ${result.importedSleepSessions}, activities ${result.importedActivities}, HR ${result.importedHeartRateSamples}."
                },
                onFailure = { error ->
                    "Garmin Health Connect sync failed: ${error.message ?: "unknown error"}."
                },
            )
    }

    fun formatTimestamp(timestampSeconds: Long): String =
        TIMESTAMP_FORMATTER.format(Instant.ofEpochSecond(timestampSeconds))

    fun formatDuration(startEpochSeconds: Long, endEpochSeconds: Long): String {
        val durationMinutes = ((endEpochSeconds - startEpochSeconds).coerceAtLeast(0L) / 60.0).roundToInt()
        val hours = durationMinutes / 60
        val minutes = durationMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun metricDisplay(metric: DailyMetricSummary): String =
        metric.numericValue?.let { value ->
            val rounded = if (value % 1f == 0f) value.roundToInt().toString() else String.format("%.1f", value)
            metric.unit?.let { "$rounded $it" } ?: rounded
        } ?: (metric.textValue ?: "--")

    fun metricLabel(metric: DailyMetricSummary): String =
        metric.metricType
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replace(Regex("([a-z])([0-9])"), "$1 $2")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> token.replaceFirstChar { char -> char.uppercase() } }

    companion object {
        private const val GARMIN_SOURCE = "GARMIN"
        private const val DAYS_7 = 7L * 86_400L
        private const val DAYS_30 = 30L * 86_400L
        private const val DAYS_90 = 90L * 86_400L
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault())
    }
}
