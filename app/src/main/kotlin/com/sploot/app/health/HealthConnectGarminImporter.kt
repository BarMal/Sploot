package com.sploot.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.sploot.app.settings.AppSettingsRepository
import com.sploot.data.entity.ActivityLapEntity
import com.sploot.data.entity.ActivitySessionEntity
import com.sploot.data.entity.ExternalHeartRateSampleEntity
import com.sploot.data.repository.AlgorithmReviewRepository
import com.sploot.data.repository.CanonicalImportRepository
import com.sploot.data.repository.SleepRepository
import com.sploot.domain.model.SleepEpoch
import com.sploot.domain.model.SleepSession
import com.sploot.domain.model.SleepSource
import com.sploot.domain.model.SleepStage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class HealthConnectGarminImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: AppSettingsRepository,
    private val canonicalImportRepository: CanonicalImportRepository,
    private val sleepRepository: SleepRepository,
    private val algorithmReviewRepository: AlgorithmReviewRepository,
) {

    data class SyncResult(
        val importedSleepSessions: Int,
        val importedActivities: Int,
        val importedHeartRateSamples: Int,
        val importedLaps: Int,
    )

    val requiredPermissions: Set<String> = setOf(
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(HeartRateRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun availabilityStatus(): Int =
        HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PROVIDER_PACKAGE)

    suspend fun hasAllPermissions(): Boolean =
        availabilityStatus() == HealthConnectClient.SDK_AVAILABLE &&
            client().permissionController.getGrantedPermissions().containsAll(requiredPermissions)

    suspend fun syncLast30Days(): SyncResult {
        val settings = settingsRepository.current()
        val end = Instant.now()
        val start = end.minusSeconds(settings.healthConnectLookbackDays.toLong() * 86_400L)
        val healthConnectClient = client()

        val exerciseRecords = if (settings.importHealthConnectActivities) {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            ).records.filterGarmin()
        } else {
            emptyList()
        }

        val heartRateRecords = if (settings.importHealthConnectHeartRate) {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            ).records.filterGarmin()
        } else {
            emptyList()
        }

        val sleepRecords = if (settings.importHealthConnectSleep) {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            ).records.filterGarmin()
        } else {
            emptyList()
        }

        val activitySessions = exerciseRecords.map { it.toActivitySessionEntity() }
        val activityLaps = exerciseRecords.flatMap { it.toActivityLaps() }
        val heartRateSamples = heartRateRecords.flatMap { it.toHeartRateSamples() }

        val activityResult = canonicalImportRepository.upsertActivitySessions(activitySessions)
        val lapResult = canonicalImportRepository.upsertActivityLaps(activityLaps)
        val heartRateResult = canonicalImportRepository.upsertExternalHeartRateSamples(heartRateSamples)

        var importedSleepSessions = 0
        sleepRecords.forEach { record ->
            val session = record.toSleepSession()
            val epochs = record.toSleepEpochs()
            val sessionId = sleepRepository.replaceImportedGarminSession(session, epochs)
            algorithmReviewRepository.evaluateAgainstGarmin(sessionId)
            importedSleepSessions += 1
        }

        Timber.i(
            "Health Connect Garmin sync imported ${sleepRecords.size} sleep sessions, " +
                "${activityResult.inserted + activityResult.updated} activity sessions, " +
                "${lapResult.inserted + lapResult.updated} laps, " +
                "${heartRateResult.inserted + heartRateResult.updated} HR samples"
        )

        return SyncResult(
            importedSleepSessions = importedSleepSessions,
            importedActivities = activityResult.inserted + activityResult.updated,
            importedHeartRateSamples = heartRateResult.inserted + heartRateResult.updated,
            importedLaps = lapResult.inserted + lapResult.updated,
        )
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    private fun ExerciseSessionRecord.toActivitySessionEntity(): ActivitySessionEntity {
        val startEpochSeconds = startTime.epochSecond
        val endEpochSeconds = endTime.epochSecond
        return ActivitySessionEntity(
            naturalKey = "GARMIN-HC-EXERCISE:${metadata.id ?: "$startEpochSeconds:$endEpochSeconds:$exerciseType"}",
            source = GARMIN_SOURCE,
            externalId = metadata.id,
            activityType = mapExerciseType(exerciseType),
            title = title,
            startEpochSeconds = startEpochSeconds,
            endEpochSeconds = endEpochSeconds,
            avgHrBpm = null,
            maxHrBpm = null,
            caloriesKcal = null,
            distanceMeters = null,
            sourceFileFingerprint = HEALTH_CONNECT_SOURCE_FILE,
        )
    }

    private fun ExerciseSessionRecord.toActivityLaps(): List<ActivityLapEntity> =
        laps.mapIndexed { index, lap ->
            ActivityLapEntity(
                naturalKey = "GARMIN-HC-LAP:${metadata.id ?: startTime.epochSecond}:$index:${lap.startTime.epochSecond}:${lap.endTime.epochSecond}",
                source = GARMIN_SOURCE,
                activityNaturalKey = "GARMIN-HC-EXERCISE:${metadata.id ?: "${startTime.epochSecond}:${endTime.epochSecond}:$exerciseType"}",
                lapIndex = index,
                activityType = mapExerciseType(exerciseType),
                startEpochSeconds = lap.startTime.epochSecond,
                endEpochSeconds = lap.endTime.epochSecond,
                distanceMeters = lap.length?.inMeters?.toFloat(),
                caloriesKcal = null,
                avgHrBpm = null,
                maxHrBpm = null,
                avgSpeedMetersPerSecond = null,
                maxSpeedMetersPerSecond = null,
                sourceFileFingerprint = HEALTH_CONNECT_SOURCE_FILE,
            )
        }

    private fun HeartRateRecord.toHeartRateSamples(): List<ExternalHeartRateSampleEntity> =
        samples.map { sample ->
            ExternalHeartRateSampleEntity(
                naturalKey = "GARMIN-HC-HR:${sample.time.toEpochMilli()}",
                source = GARMIN_SOURCE,
                tsSeconds = sample.time.epochSecond,
                hrBpm = sample.beatsPerMinute.toInt(),
                sourceFileFingerprint = HEALTH_CONNECT_SOURCE_FILE,
            )
        }

    private fun SleepSessionRecord.toSleepSession(): SleepSession {
        val stageMinutes = stages.groupBy { it.toDomainStage() }
            .mapValues { (_, entries) ->
                entries.sumOf { stage -> (stage.endTime.epochSecond - stage.startTime.epochSecond).coerceAtLeast(0L) }.toInt() / 60
            }
        val totalSleepMinutes = (stageMinutes[SleepStage.DEEP] ?: 0) + (stageMinutes[SleepStage.LIGHT] ?: 0) + (stageMinutes[SleepStage.REM] ?: 0)
        val timeInBedMinutes = ((endTime.epochSecond - startTime.epochSecond).coerceAtLeast(60L) / 60f)
        return SleepSession(
            id = 0L,
            startEpochSeconds = startTime.epochSecond,
            endEpochSeconds = endTime.epochSecond,
            source = SleepSource.GARMIN,
            algorithmRevisionId = null,
            totalScore = null,
            deepMinutes = stageMinutes[SleepStage.DEEP] ?: 0,
            lightMinutes = stageMinutes[SleepStage.LIGHT] ?: 0,
            remMinutes = stageMinutes[SleepStage.REM] ?: 0,
            awakeMinutes = stageMinutes[SleepStage.AWAKE] ?: 0,
            latencyMinutes = 0,
            efficiencyPercent = if (timeInBedMinutes > 0f) totalSleepMinutes / timeInBedMinutes * 100f else null,
        )
    }

    private fun SleepSessionRecord.toSleepEpochs(): List<SleepEpoch> =
        stages.map { stage ->
            SleepEpoch(
                epochStartSeconds = stage.startTime.epochSecond,
                sessionId = 0L,
                stage = stage.toDomainStage(),
                source = SleepSource.GARMIN,
                rmssd = null,
                meanHr = null,
                movementIntensity = null,
                respRate = null,
            )
        }

    private fun SleepSessionRecord.Stage.toDomainStage(): SleepStage = when (stage) {
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
        SleepSessionRecord.STAGE_TYPE_LIGHT,
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStage.LIGHT
        else -> SleepStage.AWAKE
    }

    private fun mapExerciseType(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "walking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "training"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "swimming"
        else -> "generic"
    }

    private fun <T : androidx.health.connect.client.records.Record> List<T>.filterGarmin(): List<T> =
        filter { record -> record.metadata.dataOrigin.packageName == GARMIN_CONNECT_PACKAGE }

    companion object {
        const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        private const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"
        private const val GARMIN_SOURCE = "GARMIN"
        private const val HEALTH_CONNECT_SOURCE_FILE = "health-connect:garmin"
    }
}
