package com.sploot.data.repository

import com.sploot.data.dao.ActivitySessionDao
import com.sploot.data.dao.ActivityLapDao
import com.sploot.data.dao.ActivityTrackPointDao
import com.sploot.data.dao.DailyMetricSummaryDao
import com.sploot.data.dao.ExternalHeartRateSampleDao
import com.sploot.data.dao.ImportedArtifactDao
import com.sploot.data.entity.ActivitySessionEntity
import com.sploot.data.entity.ActivityLapEntity
import com.sploot.data.entity.ActivityTrackPointEntity
import com.sploot.data.entity.DailyMetricSummaryEntity
import com.sploot.data.entity.ExternalHeartRateSampleEntity
import com.sploot.data.entity.ImportedArtifactEntity
import com.sploot.domain.model.ActivitySession
import com.sploot.domain.model.ActivityLap
import com.sploot.domain.model.ActivityTrackPoint
import com.sploot.domain.model.DailyMetricSummary
import com.sploot.domain.model.ExternalHeartRateSample
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanonicalImportRepository @Inject constructor(
    private val artifactDao: ImportedArtifactDao,
    private val activityDao: ActivitySessionDao,
    private val activityLapDao: ActivityLapDao,
    private val activityTrackPointDao: ActivityTrackPointDao,
    private val heartRateDao: ExternalHeartRateSampleDao,
    private val dailyMetricDao: DailyMetricSummaryDao,
) {

    data class UpsertResult(
        val inserted: Int,
        val updated: Int,
    )

    suspend fun getImportedArtifact(fingerprint: String): ImportedArtifactEntity? =
        artifactDao.getByFingerprint(fingerprint)

    suspend fun upsertImportedArtifact(artifact: ImportedArtifactEntity) {
        artifactDao.upsert(artifact)
    }

    suspend fun getLatestActivitySessions(limit: Int = 20): List<ActivitySession> =
        activityDao.getLatest(limit).map { it.toDomain() }

    suspend fun getActivitySession(naturalKey: String): ActivitySession? =
        activityDao.getByNaturalKey(naturalKey)?.toDomain()

    suspend fun getActivitySessionsInRange(
        fromSeconds: Long,
        toSeconds: Long,
    ): List<ActivitySession> = activityDao.getInRange(fromSeconds, toSeconds).map { it.toDomain() }

    suspend fun getActivityTrackPoints(activityNaturalKey: String): List<ActivityTrackPoint> =
        activityTrackPointDao.getForActivity(activityNaturalKey).map { it.toDomain() }

    suspend fun getActivityLaps(activityNaturalKey: String): List<ActivityLap> =
        activityLapDao.getForActivity(activityNaturalKey).map { it.toDomain() }

    suspend fun getActivityLapsInRange(
        fromSeconds: Long,
        toSeconds: Long,
    ): List<ActivityLap> = activityLapDao.getInRange(fromSeconds, toSeconds).map { it.toDomain() }

    suspend fun getActivityTrackPointsInRange(
        fromSeconds: Long,
        toSeconds: Long,
    ): List<ActivityTrackPoint> = activityTrackPointDao.getInRange(fromSeconds, toSeconds).map { it.toDomain() }

    suspend fun getExternalHeartRateSamplesInRange(
        fromSeconds: Long,
        toSeconds: Long,
    ): List<ExternalHeartRateSample> = heartRateDao.getInRange(fromSeconds, toSeconds).map { it.toDomain() }

    suspend fun getLatestDailyMetrics(
        metricType: String,
        limit: Int = 30,
    ): List<DailyMetricSummary> = dailyMetricDao.getLatestByMetricType(metricType, limit).map { it.toDomain() }

    suspend fun getDailyMetricsInDateRange(
        fromDate: String,
        toDate: String,
    ): List<DailyMetricSummary> = dailyMetricDao.getInDateRange(fromDate, toDate).map { it.toDomain() }

    suspend fun upsertActivitySessions(sessions: List<ActivitySessionEntity>): UpsertResult =
        upsertByNaturalKey(
            items = sessions,
            keyOf = { it.naturalKey },
            existingKeys = { activityDao.getExistingKeys(it) },
            persist = { activityDao.upsertAll(it) },
        )

    suspend fun upsertExternalHeartRateSamples(
        samples: List<ExternalHeartRateSampleEntity>,
    ): UpsertResult = upsertByNaturalKey(
        items = samples,
        keyOf = { it.naturalKey },
        existingKeys = { heartRateDao.getExistingKeys(it) },
        persist = { heartRateDao.upsertAll(it) },
    )

    suspend fun upsertActivityTrackPoints(
        points: List<ActivityTrackPointEntity>,
    ): UpsertResult = upsertByNaturalKey(
        items = points,
        keyOf = { it.naturalKey },
        existingKeys = { activityTrackPointDao.getExistingKeys(it) },
        persist = { activityTrackPointDao.upsertAll(it) },
    )

    suspend fun upsertActivityLaps(
        laps: List<ActivityLapEntity>,
    ): UpsertResult = upsertByNaturalKey(
        items = laps,
        keyOf = { it.naturalKey },
        existingKeys = { activityLapDao.getExistingKeys(it) },
        persist = { activityLapDao.upsertAll(it) },
    )

    suspend fun upsertDailyMetricSummaries(
        metrics: List<DailyMetricSummaryEntity>,
    ): UpsertResult = upsertByNaturalKey(
        items = metrics,
        keyOf = { it.naturalKey },
        existingKeys = { dailyMetricDao.getExistingKeys(it) },
        persist = { dailyMetricDao.upsertAll(it) },
    )

    private suspend fun <T> upsertByNaturalKey(
        items: List<T>,
        keyOf: (T) -> String,
        existingKeys: suspend (List<String>) -> List<String>,
        persist: suspend (List<T>) -> Unit,
    ): UpsertResult {
        if (items.isEmpty()) return UpsertResult(inserted = 0, updated = 0)
        val keys = items.map(keyOf)
        val existing = existingKeys(keys).toSet()
        persist(items)
        val inserted = keys.count { it !in existing }
        return UpsertResult(
            inserted = inserted,
            updated = items.size - inserted,
        )
    }

    private fun ActivitySessionEntity.toDomain() = ActivitySession(
        naturalKey = naturalKey,
        source = source,
        externalId = externalId,
        activityType = activityType,
        title = title,
        startEpochSeconds = startEpochSeconds,
        endEpochSeconds = endEpochSeconds,
        avgHrBpm = avgHrBpm,
        maxHrBpm = maxHrBpm,
        caloriesKcal = caloriesKcal,
        distanceMeters = distanceMeters,
    )

    private fun ExternalHeartRateSampleEntity.toDomain() = ExternalHeartRateSample(
        naturalKey = naturalKey,
        source = source,
        tsSeconds = tsSeconds,
        hrBpm = hrBpm,
    )

    private fun ActivityTrackPointEntity.toDomain() = ActivityTrackPoint(
        naturalKey = naturalKey,
        source = source,
        activityNaturalKey = activityNaturalKey,
        tsSeconds = tsSeconds,
        latitudeDegrees = latitudeDegrees,
        longitudeDegrees = longitudeDegrees,
        altitudeMeters = altitudeMeters,
        distanceMeters = distanceMeters,
        speedMetersPerSecond = speedMetersPerSecond,
    )

    private fun ActivityLapEntity.toDomain() = ActivityLap(
        naturalKey = naturalKey,
        source = source,
        activityNaturalKey = activityNaturalKey,
        lapIndex = lapIndex,
        activityType = activityType,
        startEpochSeconds = startEpochSeconds,
        endEpochSeconds = endEpochSeconds,
        distanceMeters = distanceMeters,
        caloriesKcal = caloriesKcal,
        avgHrBpm = avgHrBpm,
        maxHrBpm = maxHrBpm,
        avgSpeedMetersPerSecond = avgSpeedMetersPerSecond,
        maxSpeedMetersPerSecond = maxSpeedMetersPerSecond,
    )

    private fun DailyMetricSummaryEntity.toDomain() = DailyMetricSummary(
        naturalKey = naturalKey,
        source = source,
        date = date,
        metricType = metricType,
        numericValue = numericValue,
        textValue = textValue,
        unit = unit,
    )
}
