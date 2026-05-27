package com.sploot.data.repository

import com.sploot.data.dao.HrvWindowDao
import com.sploot.data.dao.SleepEpochDao
import com.sploot.data.dao.SleepSessionDao
import com.sploot.data.entity.HrvWindowEntity
import com.sploot.data.entity.SleepEpochEntity
import com.sploot.data.entity.SleepSessionEntity
import com.sploot.domain.model.HrvWindow
import com.sploot.domain.model.SleepEpoch
import com.sploot.domain.model.SleepSession
import com.sploot.domain.model.SleepSource
import com.sploot.domain.model.SleepStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepository @Inject constructor(
    private val sessionDao: SleepSessionDao,
    private val epochDao:   SleepEpochDao,
    private val hrvDao:     HrvWindowDao,
) {

    fun sleepSessionsFlow(): Flow<List<SleepSession>> =
        sessionDao.getAllFlow().map { entities -> entities.map { it.toDomain() } }

    suspend fun saveSession(session: SleepSession): Long =
        sessionDao.insert(session.toEntity())

    suspend fun saveEpochs(epochs: List<SleepEpoch>) =
        epochDao.insertAll(epochs.map { it.toEntity() })

    suspend fun saveHrvWindows(windows: List<HrvWindow>) =
        hrvDao.insertAll(windows.map { it.toEntity() })

    suspend fun getEpochs(sessionId: Long, source: SleepSource): List<SleepEpoch> =
        epochDao.getBySessionAndSource(sessionId, source.name).map { it.toDomain() }

    suspend fun getLatestHrvWindow(): HrvWindow? = hrvDao.getLatest()?.toDomain()

    suspend fun getAverageRmssdSince(fromEpochSeconds: Long): Float? =
        hrvDao.averageRmssdSince(fromEpochSeconds)

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun SleepSessionEntity.toDomain() = SleepSession(
        id = id, startEpochSeconds = startSeconds, endEpochSeconds = endSeconds,
        source = SleepSource.valueOf(source), totalScore = totalScore,
        deepMinutes = deepMinutes, lightMinutes = lightMinutes,
        remMinutes = remMinutes, awakeMinutes = awakeMinutes,
        latencyMinutes = latencyMinutes, efficiencyPercent = efficiencyPercent,
    )

    private fun SleepSession.toEntity() = SleepSessionEntity(
        id = id, startSeconds = startEpochSeconds, endSeconds = endEpochSeconds,
        source = source.name, totalScore = totalScore,
        deepMinutes = deepMinutes, lightMinutes = lightMinutes,
        remMinutes = remMinutes, awakeMinutes = awakeMinutes,
        latencyMinutes = latencyMinutes, efficiencyPercent = efficiencyPercent,
    )

    private fun SleepEpochEntity.toDomain() = SleepEpoch(
        epochStartSeconds = epochStartSeconds, sessionId = sessionId,
        stage = SleepStage.valueOf(stage), source = SleepSource.valueOf(source),
        rmssd = rmssd, meanHr = meanHr,
        movementIntensity = movementIntensity, respRate = respRate,
    )

    private fun SleepEpoch.toEntity() = SleepEpochEntity(
        epochStartSeconds = epochStartSeconds, sessionId = sessionId,
        stage = stage.name, source = source.name,
        rmssd = rmssd, meanHr = meanHr,
        movementIntensity = movementIntensity, respRate = respRate,
    )

    private fun HrvWindowEntity.toDomain() = HrvWindow(
        windowStartSeconds = windowStartSeconds, windowEndSeconds = windowEndSeconds,
        sessionId = sessionId, rmssd = rmssd, sdnn = sdnn, pnn50 = pnn50,
        lfPower = lfPower, hfPower = hfPower, lfHfRatio = lfHfRatio, meanRrMs = meanRrMs,
    )

    private fun HrvWindow.toEntity() = HrvWindowEntity(
        windowStartSeconds = windowStartSeconds, windowEndSeconds = windowEndSeconds,
        sessionId = sessionId, rmssd = rmssd, sdnn = sdnn, pnn50 = pnn50,
        lfPower = lfPower, hfPower = hfPower, lfHfRatio = lfHfRatio, meanRrMs = meanRrMs,
    )
}
