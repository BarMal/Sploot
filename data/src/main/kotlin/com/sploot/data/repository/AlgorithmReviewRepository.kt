package com.sploot.data.repository

import com.sploot.domain.model.AlgorithmEvaluation
import com.sploot.domain.model.MetricFamily
import com.sploot.domain.model.SleepSession
import com.sploot.domain.model.SleepSource
import com.sploot.domain.scorer.AlgorithmComparator
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlgorithmReviewRepository @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val algorithmRepository: AlgorithmRepository,
) {
    private val comparator = AlgorithmComparator()

    suspend fun evaluateAgainstGarmin(algorithmSessionId: Long) {
        val algorithmSession = sleepRepository.getSessionById(algorithmSessionId) ?: return
        val revisionId = algorithmSession.algorithmRevisionId ?: return
        if (algorithmSession.source != SleepSource.ALGO) return

        val overlapping = sleepRepository.getSessionsInRange(
            fromSeconds = algorithmSession.startEpochSeconds,
            toSeconds = algorithmSession.endEpochSeconds,
        )
        val garminSession = overlapping.firstOrNull { it.source == SleepSource.GARMIN } ?: return

        val algoEpochs = sleepRepository.getEpochs(algorithmSession.id, SleepSource.ALGO)
        val garminEpochs = sleepRepository.getEpochs(garminSession.id, SleepSource.GARMIN)
        if (algoEpochs.isEmpty() || garminEpochs.isEmpty()) return

        val result = comparator.compare(algoEpochs, garminEpochs, algorithmSession, garminSession)
        algorithmRepository.saveEvaluation(
            AlgorithmEvaluation(
                id = 0L,
                family = MetricFamily.SLEEP,
                algorithmRevisionId = revisionId,
                algorithmSessionId = algorithmSession.id,
                garminSessionId = garminSession.id,
                garminDate = Instant.ofEpochSecond(garminSession.startEpochSeconds)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .toString(),
                epochAccuracy = result.epochAccuracy,
                cohensKappa = result.cohensKappa,
                scoreDelta = result.scoreDelta,
                matchedEpochCount = result.matchedEpochCount,
                createdAtMillis = System.currentTimeMillis(),
            )
        )
    }
}
