package com.sploot.data.repository

import com.sploot.data.dao.AlgorithmEvaluationDao
import com.sploot.data.dao.AlgorithmRevisionDao
import com.sploot.data.entity.AlgorithmEvaluationEntity
import com.sploot.data.entity.AlgorithmRevisionEntity
import com.sploot.domain.model.AlgorithmEvaluation
import com.sploot.domain.model.AlgorithmRevision
import com.sploot.domain.model.AlgorithmStatus
import com.sploot.domain.model.MetricFamily
import com.sploot.domain.model.SleepAlgorithmConfig
import com.sploot.domain.model.SleepScoreParameters
import com.sploot.domain.model.SleepStageThresholds
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlgorithmRepository @Inject constructor(
    private val revisionDao: AlgorithmRevisionDao,
    private val evaluationDao: AlgorithmEvaluationDao,
) {

    suspend fun getOrCreateActiveRevision(family: MetricFamily): AlgorithmRevision {
        revisionDao.getActive(family.name)?.let { return it.toDomain() }

        val createdId = revisionDao.insert(defaultRevisionEntity(family))
        return checkNotNull(
            revisionDao.getActive(family.name) ?: revisionDao.getAll(family.name).firstOrNull { it.id == createdId }
        ) {
            "Failed to initialize default algorithm revision for $family"
        }.toDomain()
    }

    suspend fun listRevisions(family: MetricFamily): List<AlgorithmRevision> =
        revisionDao.getAll(family.name).map { it.toDomain() }

    suspend fun activateRevision(family: MetricFamily, id: Long) {
        revisionDao.activate(family.name, id)
    }

    suspend fun saveEvaluation(evaluation: AlgorithmEvaluation): Long {
        evaluationDao.deleteForPair(
            family = evaluation.family.name,
            algorithmSessionId = evaluation.algorithmSessionId,
            garminSessionId = evaluation.garminSessionId,
        )
        return evaluationDao.insert(evaluation.toEntity())
    }

    suspend fun getEvaluationsForRevision(
        family: MetricFamily,
        algorithmRevisionId: Long,
    ): List<AlgorithmEvaluation> =
        evaluationDao.getByRevision(family.name, algorithmRevisionId).map { it.toDomain() }

    private fun defaultRevisionEntity(family: MetricFamily) = when (family) {
        MetricFamily.SLEEP -> AlgorithmRevisionEntity(
            family = family.name,
            version = 1,
            status = AlgorithmStatus.ACTIVE.name,
            notes = "Initial rule-based baseline",
            targetSleepMinutes = 480,
            durationWeight = 0.25f,
            deepWeight = 0.30f,
            remWeight = 0.25f,
            disturbanceWeight = 0.20f,
            targetStagePercent = 0.225f,
            stageTolerancePercent = 0.025f,
            disturbancePenaltyPerBlock = 0.05f,
            awakeMovement = 0.20f,
            awakeHrHigh = 80f,
            awakeRmssdHigh = 60f,
            deepRmssd = 30f,
            deepHr = 55f,
            deepResp = 14f,
            remMinRmssd = 40f,
            remMaxMovement = 0.02f,
        )
        else -> AlgorithmRevisionEntity(
            family = family.name,
            version = 1,
            status = AlgorithmStatus.ACTIVE.name,
            notes = "Placeholder revision for future implementation",
            targetSleepMinutes = null,
            durationWeight = null,
            deepWeight = null,
            remWeight = null,
            disturbanceWeight = null,
            targetStagePercent = null,
            stageTolerancePercent = null,
            disturbancePenaltyPerBlock = null,
            awakeMovement = null,
            awakeHrHigh = null,
            awakeRmssdHigh = null,
            deepRmssd = null,
            deepHr = null,
            deepResp = null,
            remMinRmssd = null,
            remMaxMovement = null,
        )
    }

    private fun AlgorithmRevisionEntity.toDomain() = AlgorithmRevision(
        id = id,
        family = MetricFamily.valueOf(family),
        version = version,
        status = AlgorithmStatus.valueOf(status),
        createdAtMillis = createdAtMillis,
        notes = notes,
        sleepConfig = if (family == MetricFamily.SLEEP.name) {
            SleepAlgorithmConfig(
                scoreParameters = SleepScoreParameters(
                    targetSleepMinutes = requireNotNull(targetSleepMinutes),
                    durationWeight = requireNotNull(durationWeight),
                    deepWeight = requireNotNull(deepWeight),
                    remWeight = requireNotNull(remWeight),
                    disturbanceWeight = requireNotNull(disturbanceWeight),
                    targetStagePercent = requireNotNull(targetStagePercent),
                    stageTolerancePercent = requireNotNull(stageTolerancePercent),
                    disturbancePenaltyPerBlock = requireNotNull(disturbancePenaltyPerBlock),
                ),
                thresholds = SleepStageThresholds(
                    awakeMovement = requireNotNull(awakeMovement),
                    awakeHrHigh = requireNotNull(awakeHrHigh),
                    awakeRmssdHigh = requireNotNull(awakeRmssdHigh),
                    deepRmssd = requireNotNull(deepRmssd),
                    deepHr = requireNotNull(deepHr),
                    deepResp = requireNotNull(deepResp),
                    remMinRmssd = requireNotNull(remMinRmssd),
                    remMaxMovement = requireNotNull(remMaxMovement),
                ),
            )
        } else {
            null
        },
    )

    private fun AlgorithmEvaluationEntity.toDomain() = AlgorithmEvaluation(
        id = id,
        family = MetricFamily.valueOf(family),
        algorithmRevisionId = algorithmRevisionId,
        algorithmSessionId = algorithmSessionId,
        garminSessionId = garminSessionId,
        garminDate = garminDate,
        epochAccuracy = epochAccuracy,
        cohensKappa = cohensKappa,
        scoreDelta = scoreDelta,
        matchedEpochCount = matchedEpochCount,
        createdAtMillis = createdAtMillis,
    )

    private fun AlgorithmEvaluation.toEntity() = AlgorithmEvaluationEntity(
        id = id,
        family = family.name,
        algorithmRevisionId = algorithmRevisionId,
        algorithmSessionId = algorithmSessionId,
        garminSessionId = garminSessionId,
        garminDate = garminDate,
        epochAccuracy = epochAccuracy,
        cohensKappa = cohensKappa,
        scoreDelta = scoreDelta,
        matchedEpochCount = matchedEpochCount,
        createdAtMillis = createdAtMillis,
    )
}
