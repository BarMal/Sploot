package com.sploot.data.repository

import com.sploot.domain.model.ActivityEvaluation
import com.sploot.domain.model.MetricFamily
import com.sploot.domain.model.TrainingExample
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class ActivityReviewRepository @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val activityEvaluationRepository: ActivityEvaluationRepository,
    private val trainingExampleRepository: TrainingExampleRepository,
) {

    suspend fun evaluateAgainstGarmin(
        algorithmActivityNaturalKey: String,
        algorithmRevisionId: Long,
    ) {
        val algorithmActivity = activityRepository.getActivityByNaturalKey(algorithmActivityNaturalKey) ?: return
        if (algorithmActivity.source != WHOOP_ALGO_SOURCE) return

        val garminActivity = activityRepository.getActivitiesInRange(
            algorithmActivity.startEpochSeconds,
            algorithmActivity.endEpochSeconds,
        ).asSequence()
            .filter { it.source == GARMIN_SOURCE }
            .map { candidate ->
                candidate to overlapSeconds(
                    algorithmActivity.startEpochSeconds,
                    algorithmActivity.endEpochSeconds,
                    candidate.startEpochSeconds,
                    candidate.endEpochSeconds,
                )
            }
            .filter { (_, overlap) -> overlap >= MIN_OVERLAP_SECONDS }
            .maxByOrNull { it.second }
            ?.first
            ?: return

        val garminDate = Instant.ofEpochSecond(garminActivity.startEpochSeconds)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .toString()
        val evaluation = ActivityEvaluation(
            id = 0L,
            algorithmRevisionId = algorithmRevisionId,
            algorithmActivityNaturalKey = algorithmActivity.naturalKey,
            garminActivityNaturalKey = garminActivity.naturalKey,
            garminDate = garminDate,
            overlapSeconds = overlapSeconds(
                algorithmActivity.startEpochSeconds,
                algorithmActivity.endEpochSeconds,
                garminActivity.startEpochSeconds,
                garminActivity.endEpochSeconds,
            ),
            durationDeltaSeconds = (
                (algorithmActivity.endEpochSeconds - algorithmActivity.startEpochSeconds) -
                    (garminActivity.endEpochSeconds - garminActivity.startEpochSeconds)
                ).toInt(),
            avgHrDelta = pairedDelta(algorithmActivity.avgHrBpm, garminActivity.avgHrBpm),
            maxHrDelta = pairedDelta(algorithmActivity.maxHrBpm, garminActivity.maxHrBpm),
            caloriesDelta = pairedDelta(algorithmActivity.caloriesKcal, garminActivity.caloriesKcal),
            distanceDeltaMeters = pairedDelta(algorithmActivity.distanceMeters, garminActivity.distanceMeters),
            createdAtMillis = System.currentTimeMillis(),
        )
        activityEvaluationRepository.saveEvaluation(evaluation)

        val garminLaps = activityRepository.getLapsForActivity(garminActivity.naturalKey)
        val garminTrackPoints = activityRepository.getTrackPointsForActivity(garminActivity.naturalKey)
        val now = System.currentTimeMillis()
        trainingExampleRepository.save(
            TrainingExample(
                id = 0L,
                exampleKey = "ACTIVITY:${algorithmRevisionId}:${algorithmActivity.naturalKey}:${garminActivity.naturalKey}",
                family = MetricFamily.ACTIVITY,
                algorithmRevisionId = algorithmRevisionId,
                algorithmReference = algorithmActivity.naturalKey,
                garminReference = garminActivity.naturalKey,
                exampleDate = garminDate,
                featureJson = buildActivityFeatureJson(algorithmActivity),
                labelJson = buildActivityLabelJson(garminActivity, garminLaps, garminTrackPoints),
                evaluationJson = buildActivityEvaluationJson(evaluation),
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        )
    }

    private fun buildActivityFeatureJson(
        activity: com.sploot.domain.model.ActivitySession,
    ): String = JSONObject()
        .put("algorithmActivity", JSONObject()
            .put("naturalKey", activity.naturalKey)
            .put("source", activity.source)
            .put("activityType", activity.activityType)
            .put("title", activity.title)
            .put("startEpochSeconds", activity.startEpochSeconds)
            .put("endEpochSeconds", activity.endEpochSeconds)
            .put("durationSeconds", activity.endEpochSeconds - activity.startEpochSeconds)
            .put("avgHrBpm", activity.avgHrBpm)
            .put("maxHrBpm", activity.maxHrBpm)
            .put("caloriesKcal", activity.caloriesKcal)
            .put("distanceMeters", activity.distanceMeters)
        )
        .toString()

    private fun buildActivityLabelJson(
        activity: com.sploot.domain.model.ActivitySession,
        laps: List<com.sploot.domain.model.ActivityLap>,
        trackPoints: List<com.sploot.domain.model.ActivityTrackPoint>,
    ): String {
        val lapArray = JSONArray().apply {
            laps.forEach { lap ->
                put(
                    JSONObject()
                        .put("lapIndex", lap.lapIndex)
                        .put("activityType", lap.activityType)
                        .put("startEpochSeconds", lap.startEpochSeconds)
                        .put("endEpochSeconds", lap.endEpochSeconds)
                        .put("distanceMeters", lap.distanceMeters)
                        .put("caloriesKcal", lap.caloriesKcal)
                        .put("avgHrBpm", lap.avgHrBpm)
                        .put("maxHrBpm", lap.maxHrBpm)
                        .put("avgSpeedMetersPerSecond", lap.avgSpeedMetersPerSecond)
                        .put("maxSpeedMetersPerSecond", lap.maxSpeedMetersPerSecond)
                )
            }
        }
        return JSONObject()
            .put("garminActivity", JSONObject()
                .put("naturalKey", activity.naturalKey)
                .put("source", activity.source)
                .put("activityType", activity.activityType)
                .put("title", activity.title)
                .put("startEpochSeconds", activity.startEpochSeconds)
                .put("endEpochSeconds", activity.endEpochSeconds)
                .put("durationSeconds", activity.endEpochSeconds - activity.startEpochSeconds)
                .put("avgHrBpm", activity.avgHrBpm)
                .put("maxHrBpm", activity.maxHrBpm)
                .put("caloriesKcal", activity.caloriesKcal)
                .put("distanceMeters", activity.distanceMeters)
            )
            .put("laps", lapArray)
            .put("trackPointCount", trackPoints.size)
            .toString()
    }

    private fun buildActivityEvaluationJson(evaluation: ActivityEvaluation): String =
        JSONObject()
            .put("overlapSeconds", evaluation.overlapSeconds)
            .put("durationDeltaSeconds", evaluation.durationDeltaSeconds)
            .put("avgHrDelta", evaluation.avgHrDelta)
            .put("maxHrDelta", evaluation.maxHrDelta)
            .put("caloriesDelta", evaluation.caloriesDelta)
            .put("distanceDeltaMeters", evaluation.distanceDeltaMeters)
            .toString()

    private fun overlapSeconds(
        startA: Long,
        endA: Long,
        startB: Long,
        endB: Long,
    ): Long = (minOf(endA, endB) - maxOf(startA, startB)).coerceAtLeast(0L)

    private fun pairedDelta(left: Float?, right: Float?): Float? =
        if (left == null || right == null) null else left - right

    companion object {
        private const val GARMIN_SOURCE = "GARMIN"
        private const val WHOOP_ALGO_SOURCE = "WHOOP_ALGO"
        private const val MIN_OVERLAP_SECONDS = 300L
    }
}
