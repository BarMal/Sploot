package com.sploot.data.repository

import com.sploot.domain.model.ActivitySession
import com.sploot.domain.model.ActivityLap
import com.sploot.domain.model.ActivityTrackPoint
import com.sploot.domain.model.DailyMetricSummary
import com.sploot.domain.model.ExternalHeartRateSample
import com.sploot.domain.model.MetricFamily
import com.sploot.domain.scorer.StrainCalculator
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepository @Inject constructor(
    private val canonicalImportRepository: CanonicalImportRepository,
    private val algorithmRepository: AlgorithmRepository,
) {

    data class ActivityLoadEstimate(
        val activity: ActivitySession,
        val estimatedStrain: Float?,
        val estimatedAverageHr: Float?,
    )

    suspend fun getLatestActivities(limit: Int = 20): List<ActivitySession> =
        canonicalImportRepository.getLatestActivitySessions(limit)

    suspend fun getActivityByNaturalKey(naturalKey: String): ActivitySession? =
        canonicalImportRepository.getActivitySession(naturalKey)

    suspend fun getActivitiesInRange(fromSeconds: Long, toSeconds: Long): List<ActivitySession> =
        canonicalImportRepository.getActivitySessionsInRange(fromSeconds, toSeconds)

    suspend fun getTrackPointsForActivity(activityNaturalKey: String): List<ActivityTrackPoint> =
        canonicalImportRepository.getActivityTrackPoints(activityNaturalKey)

    suspend fun getLapsForActivity(activityNaturalKey: String): List<ActivityLap> =
        canonicalImportRepository.getActivityLaps(activityNaturalKey)

    suspend fun getTrackPointsInRange(
        fromSeconds: Long,
        toSeconds: Long,
    ): List<ActivityTrackPoint> =
        canonicalImportRepository.getActivityTrackPointsInRange(fromSeconds, toSeconds)

    suspend fun getHeartRateSamplesInRange(
        fromSeconds: Long,
        toSeconds: Long,
    ): List<ExternalHeartRateSample> =
        canonicalImportRepository.getExternalHeartRateSamplesInRange(fromSeconds, toSeconds)

    suspend fun getLatestDailyMetrics(
        metricType: String,
        limit: Int = 30,
    ): List<DailyMetricSummary> = canonicalImportRepository.getLatestDailyMetrics(metricType, limit)

    suspend fun estimateRecentActivityLoads(
        maxHrBpm: Int,
        restHrBpm: Int,
        limit: Int = 10,
    ): List<ActivityLoadEstimate> {
        algorithmRepository.getOrCreateActiveRevision(MetricFamily.ACTIVITY)
        return getLatestActivities(limit).map { activity ->
            val samples = getHeartRateSamplesInRange(activity.startEpochSeconds, activity.endEpochSeconds)
            val strain = if (samples.isNotEmpty()) {
                val minutesPerSample = inferMinutesPerSample(samples)
                StrainCalculator(maxHrBpm = maxHrBpm).calculate(
                    samples = samples.map {
                        StrainCalculator.HrSample(
                            durationMinutes = minutesPerSample,
                            hrBpm = it.hrBpm,
                        )
                    },
                    restHrBpm = restHrBpm,
                )
            } else {
                activity.avgHrBpm?.let { avg ->
                    val durationMinutes =
                        ((activity.endEpochSeconds - activity.startEpochSeconds).coerceAtLeast(60L) / 60f)
                    StrainCalculator(maxHrBpm = maxHrBpm).calculate(
                        samples = listOf(StrainCalculator.HrSample(durationMinutes, avg.toInt())),
                        restHrBpm = restHrBpm,
                    )
                }
            }

            ActivityLoadEstimate(
                activity = activity,
                estimatedStrain = strain,
                estimatedAverageHr = samplesAverage(samples = samples).takeIf { it > 0f } ?: activity.avgHrBpm,
            )
        }
    }

    suspend fun getDateRangeDailyMetrics(days: Int): List<DailyMetricSummary> {
        val today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate()
        val fromDate = today.minusDays((days - 1).toLong()).toString()
        return canonicalImportRepository.getDailyMetricsInDateRange(fromDate, today.toString())
    }

    private fun inferMinutesPerSample(samples: List<ExternalHeartRateSample>): Float {
        if (samples.size < 2) return 1f
        val avgSpacingSeconds = samples.zipWithNext { left, right -> right.tsSeconds - left.tsSeconds }
            .average()
            .coerceAtLeast(1.0)
        return (avgSpacingSeconds / 60.0).toFloat()
    }

    private fun samplesAverage(samples: List<ExternalHeartRateSample>): Float =
        if (samples.isEmpty()) 0f else samples.map { it.hrBpm }.average().toFloat()
}
