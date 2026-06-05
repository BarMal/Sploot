package com.sploot.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sploot.data.entity.RawImuEntity
import com.sploot.data.entity.RawPpgEntity
import com.sploot.data.entity.HrSampleEntity
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
        val rawPpgRows = recordingRepo.getRawPpgForSession(sessionId)
        val rawImuRows = recordingRepo.getRawImuForSession(sessionId)
        val rawHrRows = recordingRepo.getHrSamplesForSession(sessionId)

        val sleepWindow = selectContiguousSleepWindow(
            timestamps = rawImuRows.map { it.tsSeconds },
        )
        if (sleepWindow == null) {
            Timber.i("Session $sessionId skipped: no plausible contiguous IMU-backed sleep window")
            return
        }

        val ppgRows = rawPpgRows.filter { it.tsSeconds in sleepWindow.startSeconds until sleepWindow.endSeconds }
        val imuRows = rawImuRows.filter { it.tsSeconds in sleepWindow.startSeconds until sleepWindow.endSeconds }
        val hrRows = rawHrRows.filter { it.tsSeconds in sleepWindow.startSeconds until sleepWindow.endSeconds }

        if (imuRows.size < MIN_IMU_ROWS || hrRows.size < MIN_HR_ROWS) {
            Timber.i(
                "Session $sessionId too sparse to process after IMU window filtering " +
                    "(${ppgRows.size} PPG rows, ${imuRows.size} IMU rows, ${hrRows.size} HR rows)"
            )
            return
        }

        val sessionStartSec = sleepWindow.startSeconds
        val sessionEndSec = sleepWindow.endSeconds
        val windowSeconds = (sessionEndSec - sessionStartSec).coerceAtLeast(1L)
        val imuCoverage = imuRows.map { it.tsSeconds }.distinct().size.toFloat() / windowSeconds
        val hrCoverage = hrRows.map { it.tsSeconds }.distinct().size.toFloat() / windowSeconds

        if (imuCoverage < MIN_IMU_COVERAGE_RATIO || hrCoverage < MIN_HR_COVERAGE_RATIO) {
            Timber.i(
                "Session $sessionId skipped: insufficient signal coverage in sleep window " +
                    "(imu=${"%.2f".format(imuCoverage)}, hr=${"%.2f".format(hrCoverage)}, " +
                    "windowSeconds=$windowSeconds)"
            )
            return
        }

        val hrvWindows = if (ppgRows.size >= MIN_PPG_ROWS) {
            val ppgSamples = ppgRows.decodePpgChannelA()
            val peakIndices = PpgPeakDetector.detect(ppgSamples, SAMPLE_RATE_HZ)
            val ibiMs = IbiExtractor.extract(peakIndices, SAMPLE_RATE_HZ)
            buildHrvWindows(sessionId, sessionStartSec, peakIndices, ibiMs)
        } else {
            Timber.i(
                "Session $sessionId has ${ppgRows.size} PPG rows; using HR/IMU-only sleep fallback"
            )
            emptyList()
        }

        val numEpochs = ((sessionEndSec - sessionStartSec) / EPOCH_SEC).toInt()
        if (numEpochs < MIN_SLEEP_EPOCHS) {
            Timber.i("Session $sessionId too short to process as sleep (${numEpochs * EPOCH_SEC / 60} min)")
            return
        }
        val (accelX, accelY, accelZ) = imuRows.decodeAccel()
        val activityCounts = computeActivityCounts(
            accelX = accelX,
            accelY = accelY,
            accelZ = accelZ,
            numEpochs = minOf(numEpochs, accelX.size / (SAMPLE_RATE_HZ * EPOCH_SEC)),
        )
        val epochFeatures = buildEpochFeatures(
            sessionStartSec = sessionStartSec,
            numEpochs = numEpochs,
            hrvWindows = hrvWindows,
            imuRows = imuRows,
            hrRows = hrRows,
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
        hrRows: List<HrSampleEntity>,
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
            val epochHrRows = hrRows.filter { it.tsSeconds in epochStart until epochEnd }
            val meanHr = when {
                epochImuRows.isNotEmpty() -> epochImuRows.map { it.hrBpm }.average().toFloat()
                epochHrRows.isNotEmpty() -> epochHrRows.map { it.hrBpm }.average().toFloat()
                else -> matchedWindow?.let { if (it.meanRrMs > 0f) 60000f / it.meanRrMs else null } ?: 60f
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

    private fun selectContiguousSleepWindow(timestamps: List<Long>): SleepCandidateWindow? {
        val sorted = timestamps.distinct().sorted()
        if (sorted.isEmpty()) return null

        val windows = mutableListOf<SleepCandidateWindow>()
        var start = sorted.first()
        var previous = start
        var count = 1

        for (timestamp in sorted.drop(1)) {
            if (timestamp - previous > MAX_SAMPLE_GAP_SECONDS) {
                windows += SleepCandidateWindow(start, previous + 1L, count)
                start = timestamp
                count = 1
            } else {
                count += 1
            }
            previous = timestamp
        }
        windows += SleepCandidateWindow(start, previous + 1L, count)

        return windows
            .filter { it.durationSeconds in MIN_SLEEP_DURATION_SECONDS..MAX_SLEEP_DURATION_SECONDS }
            .maxWithOrNull(compareBy<SleepCandidateWindow> { it.durationSeconds }.thenBy { it.sampleCount })
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
        private const val MIN_HR_ROWS = 60
        private const val MIN_SLEEP_EPOCHS = 40
        private const val MAX_SAMPLE_GAP_SECONDS = 2 * 60L
        private const val MIN_IMU_COVERAGE_RATIO = 0.70f
        private const val MIN_HR_COVERAGE_RATIO = 0.50f
        private const val MIN_SLEEP_DURATION_SECONDS = 2 * 3600L
        private const val MAX_SLEEP_DURATION_SECONDS = 14 * 3600L

        fun buildRequest(sessionId: Long) =
            OneTimeWorkRequestBuilder<SleepProcessingWorker>()
                .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
                .build()
    }

    private data class SleepCandidateWindow(
        val startSeconds: Long,
        val endSeconds: Long,
        val sampleCount: Int,
    ) {
        val durationSeconds: Long = endSeconds - startSeconds
    }
}
