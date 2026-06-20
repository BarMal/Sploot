package com.sploot.app.ui.screen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sploot.app.service.WhoopRecordingService
import com.sploot.app.service.WhoopRuntimeCoordinator
import com.sploot.app.service.WhoopRuntimeState
import com.sploot.app.settings.AppSettingsRepository
import com.sploot.app.sync.WhoopSyncCoordinator
import com.sploot.data.repository.ActivityRepository
import com.sploot.data.repository.RecordingRepository
import com.sploot.data.repository.SleepRepository
import com.sploot.domain.model.PersonalBaseline
import com.sploot.domain.model.SleepSession
import com.sploot.domain.model.SleepSource
import com.sploot.domain.scorer.RecoveryScorer
import com.sploot.whoopble.gatt.ConnectionState
import com.sploot.whoopble.gatt.WhoopGattManager
import com.sploot.whoopble.model.WhoopRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.sqrt

enum class DashboardRange(val label: String, val days: Int) {
    WEEK("7D", 7),
    MONTH("30D", 30),
    QUARTER("90D", 90),
}

data class DashboardTrendPoint(
    val label: String,
    val value: Float?,
)

data class DashboardUiState(
    val selectedRange: DashboardRange = DashboardRange.WEEK,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val hasPreferredWhoopDevice: Boolean = false,
    val preferredWhoopDeviceName: String? = null,
    val latestWhoopDataTimestampSeconds: Long? = null,
    val isWhoopSyncing: Boolean = false,
    val liveHrBpm: Int? = null,
    val liveBatteryPercent: Float? = null,
    val liveTempCelsius: Float? = null,
    val spotSpo2Percent: Float? = null,
    val spotSpo2Status: String = "Not measured",
    val isSpotSpo2Reading: Boolean = false,
    val spotSpo2SampleCount: Int = 0,
    val latestSleep: SleepSession? = null,
    val todaySleep: SleepSession? = null,
    val latestRecoveryScore: Int? = null,
    val latestRmssd: Float? = null,
    val todayRmssd: Float? = null,
    val averageSleepScore: Float? = null,
    val averageSleepHours: Float? = null,
    val sleepEfficiencyAverage: Float? = null,
    val todayActivityCount: Int = 0,
    val todayActivityHours: Float = 0f,
    val todayCalories: Float = 0f,
    val todayHeartRateTrend: List<DashboardTrendPoint> = emptyList(),
    val todayRespRateTrend: List<DashboardTrendPoint> = emptyList(),
    val todayTempTrend: List<DashboardTrendPoint> = emptyList(),
    val activityCount: Int = 0,
    val totalActivityHours: Float = 0f,
    val totalCalories: Float = 0f,
    val sleepScoreTrend: List<DashboardTrendPoint> = emptyList(),
    val hrvTrend: List<DashboardTrendPoint> = emptyList(),
    val activityTrend: List<DashboardTrendPoint> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val gattManager: WhoopGattManager,
    private val sleepRepo: SleepRepository,
    private val activityRepo: ActivityRepository,
    private val recordingRepo: RecordingRepository,
    private val settingsRepository: AppSettingsRepository,
    private val whoopSyncCoordinator: WhoopSyncCoordinator,
    private val whoopRuntimeCoordinator: WhoopRuntimeCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var sawActiveWhoopConnection = false
    private val spotSpo2Ratios = mutableListOf<Float>()
    private var spotSpo2Job: Job? = null
    private var spotLiveNonOpticalFrameCount = 0
    private var spotPpgFrameCount = 0
    private var spotInvalidPpgFrameCount = 0

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        hasPreferredWhoopDevice = settings.preferredWhoopDeviceAddress != null,
                        preferredWhoopDeviceName = settings.preferredWhoopDeviceName,
                    )
                }
            }
        }

        viewModelScope.launch {
            gattManager.state.collect { state ->
                if (state != ConnectionState.DISCONNECTED) {
                    sawActiveWhoopConnection = true
                } else if (sawActiveWhoopConnection) {
                    sawActiveWhoopConnection = false
                    refreshWhoopHistoryState()
                }

                _uiState.update {
                    it.copy(
                        connectionState = state,
                    )
                }
            }
        }

        viewModelScope.launch {
            whoopRuntimeCoordinator.state.collect { }
        }

        viewModelScope.launch {
            whoopSyncCoordinator.isSyncing.collect { syncing ->
                _uiState.update { it.copy(isWhoopSyncing = syncing) }
            }
        }

        viewModelScope.launch {
            gattManager.records.collect { record ->
                when (record) {
                    is WhoopRecord.Imu -> {
                        _uiState.update { state ->
                            state.copy(liveHrBpm = record.hrBpm.takeIf { bpm -> bpm in PLAUSIBLE_HR_RANGE } ?: state.liveHrBpm)
                        }
                        handleSpotLiveNonOpticalFrame()
                    }
                    is WhoopRecord.HeartRate -> {
                        _uiState.update { state ->
                            state.copy(liveHrBpm = record.hrBpm.takeIf { bpm -> bpm in PLAUSIBLE_HR_RANGE } ?: state.liveHrBpm)
                        }
                        handleSpotLiveNonOpticalFrame()
                    }
                    is WhoopRecord.Battery -> _uiState.update { state ->
                        state.copy(liveBatteryPercent = mergeBatteryPercent(state.liveBatteryPercent, record))
                    }
                    is WhoopRecord.Temperature -> _uiState.update { it.copy(liveTempCelsius = record.celsius) }
                    is WhoopRecord.Ppg -> handleSpotSpo2Frame(record)
                    else -> Unit
                }
            }
        }

        viewModelScope.launch {
            sleepRepo.sleepSessionsFlow().collect {
                refreshAnalytics()
            }
        }

        refreshWhoopHistoryState()
        refreshAnalytics()
    }

    fun selectRange(range: DashboardRange) {
        if (_uiState.value.selectedRange == range) return
        _uiState.update { it.copy(selectedRange = range, isLoading = true) }
        refreshAnalytics()
    }

    fun refreshWhoopHistory() {
        val settings = settingsRepository.current()
        if (settings.preferredWhoopDeviceAddress == null) return

        _uiState.update { it.copy(isWhoopSyncing = true) }
        runCatching {
            appContext.startForegroundService(WhoopRecordingService.startHistoricalSyncIntent(appContext))
        }.onFailure {
            _uiState.update { state -> state.copy(isWhoopSyncing = false) }
        }
    }

    fun startSpotSpo2Reading() {
        val settings = settingsRepository.current()
        if (settings.preferredWhoopDeviceAddress == null) {
            _uiState.update { it.copy(spotSpo2Status = "Select a WHOOP first") }
            return
        }
        if (!settings.enableWhoopPpgStream) {
            _uiState.update { it.copy(spotSpo2Status = "Enable WHOOP optical PPG first") }
            return
        }
        if (_uiState.value.isWhoopSyncing) {
            _uiState.update { it.copy(spotSpo2Status = "Wait for sync to finish") }
            return
        }
        if (_uiState.value.isSpotSpo2Reading) return

        spotSpo2Ratios.clear()
        spotLiveNonOpticalFrameCount = 0
        spotPpgFrameCount = 0
        spotInvalidPpgFrameCount = 0
        _uiState.update {
            it.copy(
                isSpotSpo2Reading = true,
                spotSpo2SampleCount = 0,
                spotSpo2Status = "Starting WHOOP optical session...",
            )
        }

        runCatching {
            appContext.startForegroundService(WhoopRecordingService.startIntent(appContext))
        }.onFailure {
            _uiState.update {
                it.copy(
                    isSpotSpo2Reading = false,
                    spotSpo2Status = "Could not start WHOOP live session",
                )
            }
            return
        }

        viewModelScope.launch {
            delay(3_000L)
            if (_uiState.value.isSpotSpo2Reading && spotSpo2Ratios.isEmpty()) {
                val status = if (spotLiveNonOpticalFrameCount > 0) {
                    "Live HR/IMU active; waiting for R21 optical frames..."
                } else {
                    "Connecting; waiting for WHOOP live frames..."
                }
                _uiState.update { it.copy(spotSpo2Status = status) }
            }
        }

        spotSpo2Job?.cancel()
        spotSpo2Job = viewModelScope.launch {
            delay(SPOT_SPO2_TIMEOUT_MS)
            if (_uiState.value.isSpotSpo2Reading) {
                finishSpotSpo2Reading(stopLiveSession = true)
            }
        }
    }

    private fun refreshWhoopHistoryState() {
        viewModelScope.launch {
            val repairedHrSamples = recordingRepo.repairMissingHrSamplesFromRawImu()
            val latestWhoopTimestamp = recordingRepo.getLatestStoredTimestamp()
            _uiState.update {
                it.copy(
                    latestWhoopDataTimestampSeconds = latestWhoopTimestamp,
                )
            }
            if (repairedHrSamples > 0) {
                refreshAnalytics()
            }
        }
    }

    private fun refreshAnalytics() {
        viewModelScope.launch {
            val state = _uiState.value
            val nowSeconds = Instant.now().epochSecond
            val fromSeconds = nowSeconds - state.selectedRange.days * 86_400L
            val zone = ZoneId.systemDefault()
            val startOfToday = Instant.now()
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toEpochSecond()

            val allSessionsInRange = sleepRepo.getSessionsInRange(fromSeconds, nowSeconds)
            val sessionsInRange = allSessionsInRange
                .filter { it.source == SleepSource.ALGO }
                .filter { it.isPlausibleSleepSession() }
                .sortedBy { it.startEpochSeconds }
            val garminSessionsInRange = allSessionsInRange
                .filter { it.source == SleepSource.GARMIN }
                .filter { it.isPlausibleSleepSession() }
                .sortedBy { it.startEpochSeconds }
            val sleepDurationSessionsInRange = sessionsInRange.ifEmpty { garminSessionsInRange }
            val allTodaysSessions = sleepRepo.getSessionsInRange(startOfToday, nowSeconds)
            val todaysSessions = allTodaysSessions
                .filter { it.source == SleepSource.ALGO }
                .filter { it.isPlausibleSleepSession() }
                .sortedBy { it.startEpochSeconds }
            val latestAlgoSleep = sessionsInRange.lastOrNull()
            val todaySleep = todaysSessions.lastOrNull()
            val latestHrv = latestAlgoSleep?.let { sleepRepo.getHrvWindows(it.id).lastOrNull() }
                ?: sleepRepo.getLatestHrvWindow()
            val todayHrv = sleepRepo.getHrvWindowsSince(startOfToday).lastOrNull()
            val latestRecovery = if (latestAlgoSleep != null && latestHrv != null && latestHrv.meanRrMs > 0f) {
                RecoveryScorer(PersonalBaseline.EMPTY).score(
                    overnightHrv = latestHrv,
                    restingHrBpm = 60_000f / latestHrv.meanRrMs,
                    sleepSession = latestAlgoSleep,
                )
            } else {
                null
            }

            val averageSleepScore = sessionsInRange.mapNotNull { it.totalScore }.averageNumberOrNull()
            val averageSleepHours = sleepDurationSessionsInRange
                .map { (it.endEpochSeconds - it.startEpochSeconds).coerceAtLeast(0L) / 3600f }
                .averageNumberOrNull()
            val averageSleepEfficiency = sleepDurationSessionsInRange.mapNotNull { it.efficiencyPercent }.averageNumberOrNull()

            val hrvWindows = sleepRepo.getHrvWindowsSince(fromSeconds)
            val activities = activityRepo.getActivitiesInRange(fromSeconds, nowSeconds)
                .sortedBy { it.startEpochSeconds }
            val todaysActivities = activityRepo.getActivitiesInRange(startOfToday, nowSeconds)
                .sortedBy { it.startEpochSeconds }
            val todaysHrSamples = recordingRepo.getHrSamplesSince(startOfToday)
            val todaysTempEvents = recordingRepo.getEventsByTypeSince(startOfToday, TEMP_EVENT_TYPE)
            val todaysRespEpochs = sleepRepo.getEpochsSince(startOfToday, SleepSource.ALGO)

            _uiState.update {
                it.copy(
                    latestSleep = latestAlgoSleep,
                    todaySleep = todaySleep,
                    latestRecoveryScore = latestRecovery,
                    latestRmssd = latestHrv?.rmssd,
                    todayRmssd = todayHrv?.rmssd,
                    averageSleepScore = averageSleepScore,
                    averageSleepHours = averageSleepHours,
                    sleepEfficiencyAverage = averageSleepEfficiency,
                    todayActivityCount = todaysActivities.size,
                    todayActivityHours = todaysActivities.sumOf {
                        ((it.endEpochSeconds - it.startEpochSeconds).coerceAtLeast(0L) / 3600.0)
                    }.toFloat(),
                    todayCalories = todaysActivities.sumOf { activity -> activity.caloriesKcal?.toDouble() ?: 0.0 }.toFloat(),
                    todayHeartRateTrend = buildIntradayTrend(
                        startOfToday = startOfToday,
                        nowSeconds = nowSeconds,
                    ) { bucketStart, bucketEnd ->
                        todaysHrSamples
                            .filter { sample -> sample.tsSeconds in bucketStart until bucketEnd }
                            .map { sample -> sample.hrBpm.toFloat() }
                            .averageNumberOrNull()
                    },
                    todayRespRateTrend = buildIntradayTrend(
                        startOfToday = startOfToday,
                        nowSeconds = nowSeconds,
                    ) { bucketStart, bucketEnd ->
                        todaysRespEpochs
                            .filter { epoch -> epoch.epochStartSeconds in bucketStart until bucketEnd }
                            .mapNotNull { epoch -> epoch.respRate }
                            .averageNumberOrNull()
                    },
                    todayTempTrend = buildIntradayTrend(
                        startOfToday = startOfToday,
                        nowSeconds = nowSeconds,
                    ) { bucketStart, bucketEnd ->
                        todaysTempEvents
                            .filter { event -> event.tsSeconds in bucketStart until bucketEnd }
                            .mapNotNull { event -> event.valueFloat }
                            .averageNumberOrNull()
                    },
                    activityCount = activities.size,
                    totalActivityHours = activities.sumOf {
                        ((it.endEpochSeconds - it.startEpochSeconds).coerceAtLeast(0L) / 3600.0)
                    }.toFloat(),
                    totalCalories = activities.sumOf { it.caloriesKcal?.toDouble() ?: 0.0 }.toFloat(),
                    sleepScoreTrend = buildDailyTrend(
                        days = it.selectedRange.days,
                        fromSeconds = fromSeconds,
                        zone = zone,
                    ) { dayStart, dayEnd ->
                        sessionsInRange
                            .filter { it.startEpochSeconds in dayStart until dayEnd }
                            .mapNotNull { sleep -> sleep.totalScore?.toFloat() }
                            .averageNumberOrNull()
                    },
                    hrvTrend = buildDailyTrend(
                        days = it.selectedRange.days,
                        fromSeconds = fromSeconds,
                        zone = zone,
                    ) { dayStart, dayEnd ->
                        hrvWindows
                            .filter { window -> window.windowStartSeconds in dayStart until dayEnd }
                            .map { window -> window.rmssd.toFloat() }
                            .averageNumberOrNull()
                    },
                    activityTrend = buildDailyTrend(
                        days = it.selectedRange.days,
                        fromSeconds = fromSeconds,
                        zone = zone,
                    ) { dayStart, dayEnd ->
                        activities
                            .filter { activity -> activity.startEpochSeconds in dayStart until dayEnd }
                            .map { activity -> ((activity.endEpochSeconds - activity.startEpochSeconds) / 60f).coerceAtLeast(0f) }
                            .sum()
                            .takeIf { sum -> sum > 0f }
                    },
                    isLoading = false,
                )
            }
        }
    }

    private fun handleSpotSpo2Frame(record: WhoopRecord.Ppg) {
        if (!_uiState.value.isSpotSpo2Reading) return

        spotPpgFrameCount += 1
        val ratio = estimateRedIrRatio(record.channelF, record.channelC)
        if (ratio == null) {
            spotInvalidPpgFrameCount += 1
            if (spotInvalidPpgFrameCount == 1 || spotInvalidPpgFrameCount % 3 == 0) {
                _uiState.update {
                    it.copy(spotSpo2Status = "R21 optical seen; red/IR not stable yet")
                }
            }
            return
        }

        spotSpo2Ratios += ratio
        _uiState.update {
            it.copy(
                spotSpo2SampleCount = spotSpo2Ratios.size,
                spotSpo2Status = "Sampling red/IR signal (${spotSpo2Ratios.size}/$SPOT_SPO2_REQUIRED_FRAMES)",
            )
        }

        if (spotSpo2Ratios.size >= SPOT_SPO2_REQUIRED_FRAMES) {
            finishSpotSpo2Reading(stopLiveSession = true)
        }
    }

    private fun handleSpotLiveNonOpticalFrame() {
        if (!_uiState.value.isSpotSpo2Reading) return
        if (spotPpgFrameCount > 0 || spotSpo2Ratios.isNotEmpty()) return

        spotLiveNonOpticalFrameCount += 1
        if (spotLiveNonOpticalFrameCount == 1 || spotLiveNonOpticalFrameCount % 5 == 0) {
            _uiState.update {
                it.copy(spotSpo2Status = "Live HR/IMU active; waiting for R21 optical frames...")
            }
        }
    }

    private fun finishSpotSpo2Reading(stopLiveSession: Boolean) {
        spotSpo2Job?.cancel()
        spotSpo2Job = null

        val ratios = spotSpo2Ratios.toList()
        val ppgFrames = spotPpgFrameCount
        val liveFrames = spotLiveNonOpticalFrameCount
        val invalidPpgFrames = spotInvalidPpgFrameCount
        spotSpo2Ratios.clear()
        spotPpgFrameCount = 0
        spotLiveNonOpticalFrameCount = 0
        spotInvalidPpgFrameCount = 0

        val percent = ratios.takeIf { it.size >= SPOT_SPO2_MIN_FRAMES }
            ?.average()
            ?.toFloat()
            ?.let(::ratioToSpo2Percent)

        _uiState.update {
            it.copy(
                spotSpo2Percent = percent ?: it.spotSpo2Percent,
                isSpotSpo2Reading = false,
                spotSpo2SampleCount = ratios.size,
                spotSpo2Status = when {
                    percent != null && ratios.size >= SPOT_SPO2_REQUIRED_FRAMES -> "Experimental spot estimate"
                    percent != null -> "Low-confidence spot estimate"
                    ppgFrames == 0 && liveFrames > 0 -> "No R21 optical frames received"
                    ppgFrames == 0 -> "No WHOOP live frames received"
                    invalidPpgFrames > 0 -> "R21 seen; red/IR not stable"
                    else -> "No stable optical reading"
                },
            )
        }

        if (stopLiveSession) {
            appContext.startService(WhoopRecordingService.stopIntent(appContext))
        }
    }

    private fun buildDailyTrend(
        days: Int,
        fromSeconds: Long,
        zone: ZoneId,
        valueForDay: (Long, Long) -> Float?,
    ): List<DashboardTrendPoint> =
        (0 until days).map { index ->
            val dayStart = fromSeconds + index * 86_400L
            val dayEnd = dayStart + 86_400L
            val label = Instant.ofEpochSecond(dayStart)
                .atZone(zone)
                .toLocalDate()
                .let { date ->
                    if (days <= 7) {
                        date.dayOfWeek.name.take(3)
                    } else {
                        "${date.monthValue}/${date.dayOfMonth}"
                    }
                }

            DashboardTrendPoint(
                label = label,
                value = valueForDay(dayStart, dayEnd),
            )
        }

    private fun buildIntradayTrend(
        startOfToday: Long,
        nowSeconds: Long,
        bucketSeconds: Long = 5 * 60L,
        valueForBucket: (Long, Long) -> Float?,
    ): List<DashboardTrendPoint> {
        val bucketCount = ((24 * 3600L) / bucketSeconds).toInt()
        return (0 until bucketCount).map { index ->
            val bucketStart = startOfToday + index * bucketSeconds
            val bucketEnd = bucketStart + bucketSeconds
            val label = if (index % 12 == 0) {
                Instant.ofEpochSecond(bucketStart)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .let { time -> String.format("%02d:%02d", time.hour, time.minute) }
            } else {
                ""
            }
            DashboardTrendPoint(
                label = label,
                value = if (bucketStart <= nowSeconds) valueForBucket(bucketStart, bucketEnd) else null,
            )
        }
    }
}

private fun com.sploot.domain.model.SleepSession.isPlausibleSleepSession(): Boolean {
    val durationHours = (endEpochSeconds - startEpochSeconds).coerceAtLeast(0L) / 3600f
    return durationHours in MIN_PLAUSIBLE_SLEEP_HOURS..MAX_PLAUSIBLE_SLEEP_HOURS
}

private const val MIN_PLAUSIBLE_SLEEP_HOURS = 2f
private const val MAX_PLAUSIBLE_SLEEP_HOURS = 14f

private fun estimateRedIrRatio(
    red: IntArray,
    infrared: IntArray,
): Float? {
    if (red.size < SPOT_SPO2_MIN_SAMPLES || infrared.size < SPOT_SPO2_MIN_SAMPLES) return null

    val redDc = red.average()
    val irDc = infrared.average()
    if (redDc <= 0.0 || irDc <= 0.0) return null

    val redAc = red.rmsAround(redDc)
    val irAc = infrared.rmsAround(irDc)
    if (redAc <= 0.0 || irAc <= 0.0) return null

    val ratio = (redAc / redDc) / (irAc / irDc)
    return ratio.toFloat().takeIf { it in 0.2f..2.0f }
}

private fun ratioToSpo2Percent(ratio: Float): Float =
    (110f - 25f * ratio).coerceIn(70f, 100f)

private fun IntArray.rmsAround(mean: Double): Double {
    val variance = sumOf { value ->
        val delta = value - mean
        delta * delta
    } / size
    return sqrt(variance)
}

private fun mergeBatteryPercent(
    current: Float?,
    incoming: WhoopRecord.Battery,
): Float =
    when {
        current == null -> incoming.percent
        incoming.source.startsWith("cmd:") -> incoming.percent
        abs(incoming.percent - current) > BATTERY_EVENT_JUMP_THRESHOLD_PERCENT -> current
        else -> incoming.percent
    }

private fun Iterable<Number>.averageNumberOrNull(): Float? {
    val values = toList()
    if (values.isEmpty()) return null
    return values.map { it.toDouble() }.average().toFloat()
}

private const val BATTERY_EVENT_JUMP_THRESHOLD_PERCENT = 5f
private val PLAUSIBLE_HR_RANGE = 30..240
private const val TEMP_EVENT_TYPE = "TEMP"
private const val SPOT_SPO2_REQUIRED_FRAMES = 8
private const val SPOT_SPO2_MIN_FRAMES = 3
private const val SPOT_SPO2_TIMEOUT_MS = 60_000L
private const val SPOT_SPO2_MIN_SAMPLES = 30
