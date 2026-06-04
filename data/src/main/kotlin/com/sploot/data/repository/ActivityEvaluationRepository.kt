package com.sploot.data.repository

import com.sploot.data.dao.ActivityEvaluationDao
import com.sploot.data.entity.ActivityEvaluationEntity
import com.sploot.domain.model.ActivityEvaluation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityEvaluationRepository @Inject constructor(
    private val activityEvaluationDao: ActivityEvaluationDao,
) {

    suspend fun saveEvaluation(evaluation: ActivityEvaluation): Long {
        activityEvaluationDao.deleteForPair(
            algorithmRevisionId = evaluation.algorithmRevisionId,
            algorithmActivityNaturalKey = evaluation.algorithmActivityNaturalKey,
            garminActivityNaturalKey = evaluation.garminActivityNaturalKey,
        )
        return activityEvaluationDao.insert(evaluation.toEntity())
    }

    suspend fun getEvaluationsForRevision(algorithmRevisionId: Long): List<ActivityEvaluation> =
        activityEvaluationDao.getByRevision(algorithmRevisionId).map { it.toDomain() }

    private fun ActivityEvaluation.toEntity() = ActivityEvaluationEntity(
        id = id,
        algorithmRevisionId = algorithmRevisionId,
        algorithmActivityNaturalKey = algorithmActivityNaturalKey,
        garminActivityNaturalKey = garminActivityNaturalKey,
        garminDate = garminDate,
        overlapSeconds = overlapSeconds,
        durationDeltaSeconds = durationDeltaSeconds,
        avgHrDelta = avgHrDelta,
        maxHrDelta = maxHrDelta,
        caloriesDelta = caloriesDelta,
        distanceDeltaMeters = distanceDeltaMeters,
        createdAtMillis = createdAtMillis,
    )

    private fun ActivityEvaluationEntity.toDomain() = ActivityEvaluation(
        id = id,
        algorithmRevisionId = algorithmRevisionId,
        algorithmActivityNaturalKey = algorithmActivityNaturalKey,
        garminActivityNaturalKey = garminActivityNaturalKey,
        garminDate = garminDate,
        overlapSeconds = overlapSeconds,
        durationDeltaSeconds = durationDeltaSeconds,
        avgHrDelta = avgHrDelta,
        maxHrDelta = maxHrDelta,
        caloriesDelta = caloriesDelta,
        distanceDeltaMeters = distanceDeltaMeters,
        createdAtMillis = createdAtMillis,
    )
}
