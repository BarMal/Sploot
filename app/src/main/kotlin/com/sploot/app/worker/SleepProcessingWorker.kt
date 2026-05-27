package com.sploot.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sploot.data.entity.RawImuEntity
import com.sploot.data.entity.RawPpgEntity
import com.sploot.data.repository.AlgorithmRepository
import com.sploot.data.repository.AlgorithmReviewRepository
import com.sploot.data.repository.RecordingRepository
import com.sploot.data.repository.SleepRepository
import com.sploot.domain.model.HrvWindow
import com.sploot.domain.model.MetricFamily
import com.sploot.domain.model.SleepEpoch
import com.sploot.domain.model.SleepSession
import com.sploot.domain.model.SleepSource
import com.sploot.domain.model.SleepStage
import com.sploot.domain.model.SleepStageThresholds
import com.sploot.domain.scorer.SleepScorer
import com.sploot.signalproc.actigraphy.Actigraphy
import com.sploot.signalproc.hrv.HrvCalculator
import com.sploot.signalproc.ppg.IbiExtractor
import com.sploot.signalproc.ppg.PpgPeakDetector
import com.sploot.signalproc.sleep.SleepStageClassifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.math.sqrt
import timber.log.Timber

@HiltWorker
class SleepProcessingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val algorithmRepository: AlgorithmRepository,
    private val reviewRepository: AlgorithmReviewRepository,
    private val recordingRepo: RecordingRepository,
    private val sleepRepo: SleepRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId < 0) {
            Timber.w("SleepProcessingWorker: no valid session ID")
            return Result.failure()
        }

        return try {
            processSession(sessionId)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SleepProcessingWorker: processing failed for session $sessionId")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun processSession(sessionId: Long) {
        val activeRevision = algorithmRepository.getOrCreateActiveRevision(MetricFamily.SLEEP)
        val ppgRows = recordingRepo.getRawPpgForSession(sessionId)
        val imuRows = recordingRepo.getRawImuForSession(sessionId)

        if (ppgRows.size < MIN_PPG_ROWS || imuRows.size < MIN_IMU_ROWS) {
            Timber.i("Session $sessionId too short to process (${ppgRows.size} PPG rows, ${imuRows.size} IMU rows)")
            return
        }

        val sessionStartSec = ppgRows.first().tsSeconds - 1L
        val sessionEndSec = ppgRows.last().tsSeconds

        val ppgSamples = ppgRows.decodePpgChannelA()
        val peakIndices = PpgPeakDetector.detect(ppgSamples, SAMPLE_RATE_HZ)
        val ibiMs = IbiExtractor.extract(peakIndices, SAMPLE_RATE_HZ)

        val hrvWindows = buildHrvWindows(sessionId, sessionStartSec, peakIndices, ibiMs)

        val (accelX, accelY, accelZ) = imuRows.decodeAccel()
        val numEpochs = minOf(
            accelX.size / (SAMPLE_RATE_HZ * EPOCH_SEC),
            ((sessionEndSec - sessionStartSec) / EPOCH_SEC).toInt(),
        )
        val activityCounts = computeActivityCounts(accelX, accelY, accelZ, numEpochs)
        val epochFeatures = buildEpochFeatures(
            sessionStartSec = sessionStartSec,
            numEpochs = numEpochs,
            hrvWindows = hrvWindows,
            imuRows = imuRows,
            activityCounts = activityCounts,
        )

        val stages = SleepStageClassifier.classifyTuned(
            epochFeatures,
            requireNotNull(activeRevision.sleepConfig).thresholds.toSignalProcThresholds(),
        )

        val sleepEpochs = stages.mapIndexed { index, classifiedStage ->
            SleepEpoch(
                epochStartSeconds = sessionStartSec + index.toLong() * EPOCH_SEC,
                sessionId = sessionId,
                stage = classifiedStage.toDomainStage(),
                source = SleepSource.ALGO,
                rmssd = epochFeatures[index].rmssd,
                meanHr = epochFeatures[index].meanHr,
                movementIntensity = epochFeatures[index].movementIntensity,
                respRate = epochFeatures[index].respRate,
            )
        }

        val stageCounts = stages.groupingBy { it }.eachCount()
        val deepMin = (stageCounts[SleepStageClassifier.Stage.DEEP] ?: 0) * EPOCH_SEC / 60
        val lightMin = (stageCounts[SleepStageClassifier.Stage.LIGHT] ?: 0) * EPOCH_SEC / 60
        val remMin = (stageCounts[SleepStageClassifier.Stage.REM] ?: 0) * EPOCH_SEC / 60
        val awakeMin = (stageCounts[SleepStageClassifier.Stage.AWAKE] ?: 0) * EPOCH_SEC / 60
        val totalSleepMin = deepMin + lightMin + remMin
        val timeInBedMin = ((sessionEndSec - sessionStartSec) / 60).toInt().coerceAtLeast(1)
        val efficiencyPct = totalSleepMin.toFloat() / timeInBedMin * 100f
        val firstSleepEpoch = stages.indexOfFirst { it != SleepStageClassifier.Stage.AWAKE }
        val latencyMin = if (firstSleepEpoch >= 0) firstSleepEpoch * EPOCH_SEC / 60 else 0

        val draftSession = SleepSession(
            id = 0L,
            startEpochSeconds = sessionStartSec,
            endEpochSeconds = sessionEndSec,
            source = SleepSource.ALGO,
            algorithmRevisionId = activeRevision.id,
            totalScore = null,
            deepMinutes = deepMin,
            lightMinutes = lightMin,
            remMinutes = remMin,
            awakeMinutes = awakeMin,
            latencyMinutes = latencyMin,
            efficiencyPercent = efficiencyPct,
        )
        val score = SleepScorer(requireNotNull(activeRevision.sleepConfig).scoreParameters).score(draftSession)

        val sleepSessionId = sleepRepo.saveSession(draftSession.copy(totalScore = score))
        sleepRepo.saveHrvWindows(hrvWindows.map { it.copy(sessionId = sleepSessionId) })
        sleepRepo.saveEpochs(sleepEpochs.map { it.copy(sessionId = sleepSessionId) })
        reviewRepository.evaluateAgainstGarmin(sleepSessionId)

        Timber.i(
            "Session $sessionId processed with revision ${activeRevision.version} -> " +
                "sleep session #$sleepSessionId, score=$score"
        )
    }

    private fun List<RawPpgEntity>.decodePpgChannelA(): FloatArray {
        val result = FloatArray(size * 100)
        forEachIndexed { rowIdx, row ->
            for (i in 0 until 100) {
                val lo = row.channelA[i * 2].toInt() and 0xFF
                val hi = row.channelA[i * 2 + 1].toInt() and 0xFF
                result[rowIdx * 100 + i] = ((hi shl 8) or lo).toFloat()
            }
        }
        return result
    }

    private fun ByteArray.decodeInt16Le(numSamples: Int = 100): FloatArray {
        val result = FloatArray(numSamples)
        for (i in 0 until numSamples.coerceAtMost(size / 2)) {
            val lo = this[i * 2].toInt() and 0xFF
            val hi = this[i * 2 + 1].toInt()
            result[i] = ((hi shl 8) or lo).toShort().toFloat()
        }
        return result
    }

    private fun List<RawImuEntity>.decodeAccel(): Triple<FloatArray, FloatArray, FloatArray> {
        val totalSamples = size * 100
        val x = FloatArray(totalSamples)
        val y = FloatArray(totalSamples)
        val z = FloatArray(totalSamples)
        forEachIndexed { rowIdx, row ->
            row.accelX.decodeInt16Le().copyInto(x, rowIdx * 100)
            row.accelY.decodeInt16Le().copyInto(y, rowIdx * 100)
            row.accelZ.decodeInt16Le().copyInto(z, rowIdx * 100)
        }
        return Triple(x, y, z)
    }

    private fun buildHrvWindows(
        sessionId: Long,
        sessionStart: Long,
        peakIndices: List<Int>,
        ibiMs: FloatArray,
    ): List<HrvWindow> {
        if (ibiMs.size < MIN_IBI_PER_WINDOW) return emptyList()

        data class TimedIbi(val tsSec: Long, val ms: Float)

        val timedIbis = ibiMs.indices.map { index ->
            val endPeakSample = peakIndices.getOrElse(index + 1) { peakIndices.last() }
            val tsSec = sessionStart + endPeakSample.toLong() / SAMPLE_RATE_HZ
            TimedIbi(tsSec, ibiMs[index])
        }

        val firstTs = timedIbis.first().tsSec
        val lastTs = timedIbis.last().tsSec
        val numWindows = ((lastTs - firstTs) / HRV_WINDOW_SEC).toInt() + 1

        return (0 until numWindows).mapNotNull { windowIndex ->
            val windowStart = firstTs + windowIndex.toLong() * HRV_WINDOW_SEC
            val windowEnd = windowStart + HRV_WINDOW_SEC
            val windowIbis = timedIbis
                .filter { it.tsSec in windowStart until windowEnd }
                .map { it.ms }
                .toFloatArray()

            if (windowIbis.size < MIN_IBI_PER_WINDOW) {
                null
            } else {
                HrvWindow(
                    windowStartSeconds = windowStart,
                    windowEndSeconds = windowEnd,
                    sessionId = sessionId,
                    rmssd = HrvCalculator.rmssd(windowIbis),
                    sdnn = HrvCalculator.sdnn(windowIbis),
                    pnn50 = HrvCalculator.pnn50(windowIbis),
                    lfPower = null,
                    hfPower = null,
                    lfHfRatio = null,
                    meanRrMs = HrvCalculator.meanRr(windowIbis),
                )
            }
        }
    }

    private fun buildEpochFeatures(
        sessionStartSec: Long,
        numEpochs: Int,
        hrvWindows: List<HrvWindow>,
        imuRows: List<RawImuEntity>,
        activityCounts: FloatArray,
    ): List<SleepStageClassifier.EpochFeatures> {
        return List(numEpochs) { epochIndex ->
            val epochStart = sessionStartSec + epochIndex.toLong() * EPOCH_SEC
            val epochEnd = epochStart + EPOCH_SEC

            val matchedWindow = hrvWindows.minByOrNull { window ->
                val overlapStart = maxOf(window.windowStartSeconds, epochStart)
                val overlapEnd = minOf(window.windowEndSeconds, epochEnd)
                if (overlapEnd > overlapStart) {
                    0L
                } else {
                    minOf(
                        kotlin.math.abs(window.windowEndSeconds - epochStart),
                        kotlin.math.abs(window.windowStartSeconds - epochEnd),
                    )
                }
            }

            val epochImuRows = imuRows.filter { it.tsSeconds in epochStart until epochEnd }
            val meanHr = if (epochImuRows.isNotEmpty()) {
                epochImuRows.map { it.hrBpm }.average().toFloat()
            } else {
                matchedWindow?.let { if (it.meanRrMs > 0f) 60000f / it.meanRrMs else null } ?: 60f
            }

            SleepStageClassifier.EpochFeatures(
                rmssd = matchedWindow?.rmssd ?: 0f,
                meanHr = meanHr,
                movementIntensity = activityCounts.getOrElse(epochIndex) { 0f },
                respRate = 0f,
            )
        }
    }

    private fun computeActivityCounts(
        accelX: FloatArray,
        accelY: FloatArray,
        accelZ: FloatArray,
        numEpochs: Int,
    ): FloatArray {
        val samplesPerEpoch = SAMPLE_RATE_HZ * EPOCH_SEC
        val rawCounts = DoubleArray(numEpochs) { epochIndex ->
            val start = epochIndex * samplesPerEpoch
            val end = minOf(start + samplesPerEpoch, accelX.size)
            (start until end).sumOf { sampleIndex ->
                sqrt(
                    accelX[sampleIndex].toDouble() * accelX[sampleIndex] +
                        accelY[sampleIndex].toDouble() * accelY[sampleIndex] +
                        accelZ[sampleIndex].toDouble() * accelZ[sampleIndex]
                )
            }
        }
        val maxCount = rawCounts.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        return FloatArray(numEpochs) { epochIndex -> (rawCounts[epochIndex] / maxCount).toFloat() }
    }

    private fun SleepStageClassifier.Stage.toDomainStage(): SleepStage = when (this) {
        SleepStageClassifier.Stage.DEEP -> SleepStage.DEEP
        SleepStageClassifier.Stage.LIGHT -> SleepStage.LIGHT
        SleepStageClassifier.Stage.REM -> SleepStage.REM
        SleepStageClassifier.Stage.AWAKE -> SleepStage.AWAKE
    }

    private fun SleepStageThresholds.toSignalProcThresholds() =
        SleepStageClassifier.Thresholds(
            awakeMovement = awakeMovement,
            awakeHrHigh = awakeHrHigh,
            awakeRmssdHigh = awakeRmssdHigh,
            deepRmssd = deepRmssd,
            deepHr = deepHr,
            deepResp = deepResp,
            remMinRmssd = remMinRmssd,
            remMaxMovement = remMaxMovement,
        )

    companion object {
        const val KEY_SESSION_ID = "session_id"

        private const val SAMPLE_RATE_HZ = 100
        private const val EPOCH_SEC = 30
        private const val HRV_WINDOW_SEC = 300L
        private const val MIN_IBI_PER_WINDOW = 5
        private const val MIN_PPG_ROWS = 60
        private const val MIN_IMU_ROWS = 60

        fun buildRequest(sessionId: Long) =
            OneTimeWorkRequestBuilder<SleepProcessingWorker>()
                .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
                .build()
    }
}
