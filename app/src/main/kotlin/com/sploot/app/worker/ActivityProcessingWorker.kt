package com.sploot.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sploot.data.entity.ActivitySessionEntity
import com.sploot.data.entity.HrSampleEntity
import com.sploot.data.entity.RawImuEntity
import com.sploot.data.repository.ActivityReviewRepository
import com.sploot.data.repository.AlgorithmRepository
import com.sploot.data.repository.CanonicalImportRepository
import com.sploot.data.repository.RecordingRepository
import com.sploot.domain.model.MetricFamily
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.math.abs
import kotlin.math.sqrt
import timber.log.Timber

@HiltWorker
class ActivityProcessingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val algorithmRepository: AlgorithmRepository,
    private val canonicalImportRepository: CanonicalImportRepository,
    private val recordingRepository: RecordingRepository,
    private val reviewRepository: ActivityReviewRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId < 0L) return Result.failure()

        return try {
            processSession(sessionId)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "ActivityProcessingWorker failed for session $sessionId")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun processSession(sessionId: Long) {
        val revision = algorithmRepository.getOrCreateActiveRevision(MetricFamily.ACTIVITY)
        val session = recordingRepository.getSessionById(sessionId) ?: return
        val endTimestampSeconds = session.endTimestampSeconds ?: return
        val hrSamples = recordingRepository.getHrSamplesForSession(sessionId)
        val imuRows = recordingRepository.getRawImuForSession(sessionId)

        val durationSeconds = (endTimestampSeconds - session.startTimestampSeconds).coerceAtLeast(0L)
        if (durationSeconds < MIN_DURATION_SECONDS || hrSamples.isEmpty()) {
            Timber.i("Activity session $sessionId skipped: duration=$durationSeconds, hrSamples=${hrSamples.size}")
            return
        }

        val seconds = buildActivitySeconds(hrSamples, imuRows)
        val segments = detectSegments(seconds)
        if (segments.isEmpty()) {
            recordingRepository.markSessionProcessed(sessionId)
            Timber.i("Activity session $sessionId produced no candidate activity segments")
            return
        }

        val derivedActivities = segments.mapIndexed { index, segment ->
            buildDerivedActivity(
                sessionId = sessionId,
                revisionId = revision.id,
                segmentIndex = index,
                seconds = segment,
            )
        }

        canonicalImportRepository.upsertActivitySessions(derivedActivities)
        derivedActivities.forEach { derivedActivity ->
            reviewRepository.evaluateAgainstGarmin(derivedActivity.naturalKey, revision.id)
        }
        recordingRepository.markSessionProcessed(sessionId)

        Timber.i(
            "Activity session $sessionId processed with revision ${revision.version} -> ${derivedActivities.size} derived activities"
        )
    }

    private fun buildDerivedActivity(
        sessionId: Long,
        revisionId: Long,
        segmentIndex: Int,
        seconds: List<ActivitySecond>,
    ): ActivitySessionEntity {
        val startEpochSeconds = seconds.first().tsSeconds
        val endEpochSeconds = seconds.last().tsSeconds + 1L
        val durationSeconds = (endEpochSeconds - startEpochSeconds).coerceAtLeast(1L)
        val avgHr = seconds.map { it.hrBpm }.average().toFloat()
        val maxHr = seconds.maxOf { it.hrBpm }.toFloat()
        val movementScore = seconds.map { it.movementScore }.average().toFloat()

        return ActivitySessionEntity(
            naturalKey = "WHOOP-ALGO:$sessionId:$revisionId:$segmentIndex",
            source = WHOOP_ALGO_SOURCE,
            externalId = "$sessionId:$segmentIndex",
            activityType = inferActivityType(avgHr, movementScore),
            title = "WHOOP Derived Activity ${segmentIndex + 1}",
            startEpochSeconds = startEpochSeconds,
            endEpochSeconds = endEpochSeconds,
            avgHrBpm = avgHr,
            maxHrBpm = maxHr,
            caloriesKcal = estimateCaloriesKcal(durationSeconds, avgHr, movementScore),
            distanceMeters = null,
            sourceFileFingerprint = "whoop-session-$sessionId",
        )
    }

    private fun inferActivityType(avgHr: Float, movementScore: Float): String =
        when {
            avgHr >= 135f -> "running"
            movementScore >= 0.65f -> "training"
            else -> "generic"
        }

    private fun estimateCaloriesKcal(durationSeconds: Long, avgHr: Float, movementScore: Float): Float {
        val durationMinutes = durationSeconds / 60f
        val hrComponent = (avgHr - 60f).coerceAtLeast(0f) * 0.11f
        val movementComponent = movementScore * 4.5f
        return (durationMinutes * (1.6f + hrComponent + movementComponent)).coerceAtLeast(0f)
    }

    private fun estimateMovementScore(imuRows: List<RawImuEntity>): Float {
        if (imuRows.isEmpty()) return 0f
        val meanMagnitude = imuRows.map { row ->
            row.meanAccelerationMagnitude()
        }.average()
        return (meanMagnitude / MOVEMENT_NORMALIZATION).toFloat().coerceIn(0f, 1f)
    }

    private fun buildActivitySeconds(
        hrSamples: List<HrSampleEntity>,
        imuRows: List<RawImuEntity>,
    ): List<ActivitySecond> {
        val imuBySecond = imuRows.associateBy { it.tsSeconds }
        return hrSamples.mapNotNull { sample ->
            val imuRow = imuBySecond[sample.tsSeconds] ?: return@mapNotNull null
            ActivitySecond(
                tsSeconds = sample.tsSeconds,
                hrBpm = sample.hrBpm,
                movementScore = (imuRow.meanAccelerationMagnitude() / MOVEMENT_NORMALIZATION).toFloat().coerceIn(0f, 1f),
            )
        }.sortedBy { it.tsSeconds }
    }

    private fun detectSegments(seconds: List<ActivitySecond>): List<List<ActivitySecond>> {
        if (seconds.isEmpty()) return emptyList()

        val rawSegments = mutableListOf<MutableList<ActivitySecond>>()
        var current = mutableListOf<ActivitySecond>()
        var idleGapSeconds = 0

        seconds.forEachIndexed { index, second ->
            val active = isActiveSecond(second)
            if (current.isEmpty()) {
                if (active) current += second
                return@forEachIndexed
            }

            val previousSecond = current.last().tsSeconds
            val tsGap = (second.tsSeconds - previousSecond).coerceAtLeast(1L)
            if (tsGap > MAX_SEGMENT_CLOCK_GAP_SECONDS) {
                rawSegments += current
                current = mutableListOf()
                idleGapSeconds = 0
                if (active) current += second
                return@forEachIndexed
            }

            if (active) {
                current += second
                idleGapSeconds = 0
            } else {
                idleGapSeconds += tsGap.toInt()
                if (idleGapSeconds <= MAX_IDLE_GAP_SECONDS) {
                    current += second
                } else {
                    rawSegments += current.dropLast(idleGapSeconds).toMutableList()
                    current = mutableListOf()
                    idleGapSeconds = 0
                }
            }

            if (index == seconds.lastIndex && current.isNotEmpty()) {
                rawSegments += current
            }
        }

        if (current.isNotEmpty() && rawSegments.lastOrNull() !== current) {
            rawSegments += current
        }

        return rawSegments
            .mapNotNull { segment ->
                val trimmed = trimInactiveEdges(segment)
                trimmed.takeIf { it.isNotEmpty() && segmentDurationSeconds(it) >= MIN_SEGMENT_DURATION_SECONDS }
            }
    }

    private fun trimInactiveEdges(segment: List<ActivitySecond>): List<ActivitySecond> {
        var startIndex = 0
        var endIndex = segment.lastIndex
        while (startIndex <= endIndex && !isActiveSecond(segment[startIndex])) startIndex++
        while (endIndex >= startIndex && !isActiveSecond(segment[endIndex])) endIndex--
        return if (startIndex > endIndex) emptyList() else segment.subList(startIndex, endIndex + 1)
    }

    private fun segmentDurationSeconds(segment: List<ActivitySecond>): Long =
        (segment.last().tsSeconds - segment.first().tsSeconds + 1L).coerceAtLeast(0L)

    private fun isActiveSecond(second: ActivitySecond): Boolean =
        second.hrBpm >= ACTIVE_HR_BPM ||
            second.movementScore >= ACTIVE_MOVEMENT_SCORE ||
            (second.hrBpm >= ELEVATED_HR_BPM && second.movementScore >= ELEVATED_MOVEMENT_SCORE)

    private fun RawImuEntity.meanAccelerationMagnitude(): Double {
        val x = accelX.decodeInt16Le()
        val y = accelY.decodeInt16Le()
        val z = accelZ.decodeInt16Le()
        var total = 0.0
        for (index in x.indices) {
            total += sqrt(
                x[index].toDouble() * x[index] +
                    y[index].toDouble() * y[index] +
                    z[index].toDouble() * z[index]
            )
        }
        return total / x.size.coerceAtLeast(1)
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

    companion object {
        const val KEY_SESSION_ID = "session_id"

        private const val WHOOP_ALGO_SOURCE = "WHOOP_ALGO"
        private const val MIN_DURATION_SECONDS = 300L
        private const val MIN_ACTIVITY_AVG_HR = 90f
        private const val MIN_ACTIVITY_MOVEMENT_SCORE = 0.18f
        private const val MOVEMENT_NORMALIZATION = 4000.0
        private const val ACTIVE_HR_BPM = 105
        private const val ELEVATED_HR_BPM = 95
        private const val ACTIVE_MOVEMENT_SCORE = 0.24f
        private const val ELEVATED_MOVEMENT_SCORE = 0.14f
        private const val MIN_SEGMENT_DURATION_SECONDS = 300L
        private const val MAX_IDLE_GAP_SECONDS = 90
        private const val MAX_SEGMENT_CLOCK_GAP_SECONDS = 20L

        fun buildRequest(sessionId: Long) =
            OneTimeWorkRequestBuilder<ActivityProcessingWorker>()
                .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
                .build()
    }

    private data class ActivitySecond(
        val tsSeconds: Long,
        val hrBpm: Int,
        val movementScore: Float,
    )
}
