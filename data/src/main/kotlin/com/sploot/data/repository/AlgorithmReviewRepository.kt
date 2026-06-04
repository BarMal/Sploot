package com.sploot.data.repository

import com.sploot.domain.model.AlgorithmEvaluation
import com.sploot.domain.model.MetricFamily
import com.sploot.domain.model.SleepSession
import com.sploot.domain.model.SleepSource
import com.sploot.domain.model.TrainingExample
import com.sploot.domain.scorer.AlgorithmComparator
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class AlgorithmReviewRepository @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val algorithmRepository: AlgorithmRepository,
    private val trainingExampleRepository: TrainingExampleRepository,
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
        val garminDate = Instant.ofEpochSecond(garminSession.startEpochSeconds)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .toString()
        val evaluation = AlgorithmEvaluation(
            id = 0L,
            family = MetricFamily.SLEEP,
            algorithmRevisionId = revisionId,
            algorithmSessionId = algorithmSession.id,
            garminSessionId = garminSession.id,
            garminDate = garminDate,
            epochAccuracy = result.epochAccuracy,
            cohensKappa = result.cohensKappa,
            scoreDelta = result.scoreDelta,
            matchedEpochCount = result.matchedEpochCount,
            createdAtMillis = System.currentTimeMillis(),
        )
        algorithmRepository.saveEvaluation(evaluation)

        val hrvWindows = sleepRepository.getHrvWindows(algorithmSession.id)
        val now = System.currentTimeMillis()
        trainingExampleRepository.save(
            TrainingExample(
                id = 0L,
                exampleKey = "SLEEP:${revisionId}:${algorithmSession.id}:${garminSession.id}",
                family = MetricFamily.SLEEP,
                algorithmRevisionId = revisionId,
                algorithmReference = algorithmSession.id.toString(),
                garminReference = garminSession.id.toString(),
                exampleDate = garminDate,
                featureJson = buildSleepFeatureJson(algorithmSession, algoEpochs, hrvWindows),
                labelJson = buildSleepLabelJson(garminSession, garminEpochs),
                evaluationJson = buildSleepEvaluationJson(evaluation),
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        )
    }

    private fun buildSleepFeatureJson(
        algorithmSession: SleepSession,
        algoEpochs: List<com.sploot.domain.model.SleepEpoch>,
        hrvWindows: List<com.sploot.domain.model.HrvWindow>,
    ): String {
        val stageCounts = algoEpochs.groupingBy { it.stage.name }.eachCount()
        val epochArray = JSONArray().apply {
            algoEpochs.forEach { epoch ->
                put(
                    JSONObject()
                        .put("startEpochSeconds", epoch.epochStartSeconds)
                        .put("stage", epoch.stage.name)
                        .put("rmssd", epoch.rmssd)
                        .put("meanHr", epoch.meanHr)
                        .put("movementIntensity", epoch.movementIntensity)
                        .put("respRate", epoch.respRate)
                )
            }
        }
        val hrvArray = JSONArray().apply {
            hrvWindows.forEach { window ->
                put(
                    JSONObject()
                        .put("windowStartSeconds", window.windowStartSeconds)
                        .put("windowEndSeconds", window.windowEndSeconds)
                        .put("rmssd", window.rmssd)
                        .put("sdnn", window.sdnn)
                        .put("pnn50", window.pnn50)
                        .put("meanRrMs", window.meanRrMs)
                )
            }
        }
        return JSONObject()
            .put("algorithmSession", JSONObject()
                .put("startEpochSeconds", algorithmSession.startEpochSeconds)
                .put("endEpochSeconds", algorithmSession.endEpochSeconds)
                .put("totalScore", algorithmSession.totalScore)
                .put("deepMinutes", algorithmSession.deepMinutes)
                .put("lightMinutes", algorithmSession.lightMinutes)
                .put("remMinutes", algorithmSession.remMinutes)
                .put("awakeMinutes", algorithmSession.awakeMinutes)
                .put("latencyMinutes", algorithmSession.latencyMinutes)
                .put("efficiencyPercent", algorithmSession.efficiencyPercent)
            )
            .put("epochCount", algoEpochs.size)
            .put("stageCounts", JSONObject(stageCounts))
            .put("epochs", epochArray)
            .put("hrvWindows", hrvArray)
            .toString()
    }

    private fun buildSleepLabelJson(
        garminSession: SleepSession,
        garminEpochs: List<com.sploot.domain.model.SleepEpoch>,
    ): String {
        val stageCounts = garminEpochs.groupingBy { it.stage.name }.eachCount()
        val epochArray = JSONArray().apply {
            garminEpochs.forEach { epoch ->
                put(
                    JSONObject()
                        .put("startEpochSeconds", epoch.epochStartSeconds)
                        .put("stage", epoch.stage.name)
                        .put("rmssd", epoch.rmssd)
                        .put("meanHr", epoch.meanHr)
                        .put("movementIntensity", epoch.movementIntensity)
                        .put("respRate", epoch.respRate)
                )
            }
        }
        return JSONObject()
            .put("garminSession", JSONObject()
                .put("startEpochSeconds", garminSession.startEpochSeconds)
                .put("endEpochSeconds", garminSession.endEpochSeconds)
                .put("totalScore", garminSession.totalScore)
                .put("deepMinutes", garminSession.deepMinutes)
                .put("lightMinutes", garminSession.lightMinutes)
                .put("remMinutes", garminSession.remMinutes)
                .put("awakeMinutes", garminSession.awakeMinutes)
                .put("latencyMinutes", garminSession.latencyMinutes)
                .put("efficiencyPercent", garminSession.efficiencyPercent)
            )
            .put("epochCount", garminEpochs.size)
            .put("stageCounts", JSONObject(stageCounts))
            .put("epochs", epochArray)
            .toString()
    }

    private fun buildSleepEvaluationJson(evaluation: AlgorithmEvaluation): String =
        JSONObject()
            .put("epochAccuracy", evaluation.epochAccuracy)
            .put("cohensKappa", evaluation.cohensKappa)
            .put("scoreDelta", evaluation.scoreDelta)
            .put("matchedEpochCount", evaluation.matchedEpochCount)
            .toString()
}
