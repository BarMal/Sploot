package com.sploot.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sploot.data.dao.ActivityEvaluationDao
import com.sploot.data.dao.ActivityLapDao
import com.sploot.data.dao.ActivitySessionDao
import com.sploot.data.dao.ActivityTrackPointDao
import com.sploot.data.dao.AlgorithmEvaluationDao
import com.sploot.data.dao.AlgorithmRevisionDao
import com.sploot.data.dao.DailyMetricSummaryDao
import com.sploot.data.dao.ExternalHeartRateSampleDao
import com.sploot.data.dao.GarminGroundTruthDao
import com.sploot.data.dao.HrSampleDao
import com.sploot.data.dao.HrvWindowDao
import com.sploot.data.dao.ImportedArtifactDao
import com.sploot.data.dao.RawImuDao
import com.sploot.data.dao.RawPpgDao
import com.sploot.data.dao.RecordingSessionDao
import com.sploot.data.dao.SleepEpochDao
import com.sploot.data.dao.SleepSessionDao
import com.sploot.data.dao.TrainingExampleDao
import com.sploot.data.dao.WhoopEventDao
import com.sploot.data.dao.WhoopUnknownObservationDao
import com.sploot.data.entity.ActivityEvaluationEntity
import com.sploot.data.entity.ActivitySessionEntity
import com.sploot.data.entity.ActivityLapEntity
import com.sploot.data.entity.ActivityTrackPointEntity
import com.sploot.data.entity.AlgorithmEvaluationEntity
import com.sploot.data.entity.AlgorithmRevisionEntity
import com.sploot.data.entity.DailyMetricSummaryEntity
import com.sploot.data.entity.ExternalHeartRateSampleEntity
import com.sploot.data.entity.GarminGroundTruthEntity
import com.sploot.data.entity.HrSampleEntity
import com.sploot.data.entity.HrvWindowEntity
import com.sploot.data.entity.ImportedArtifactEntity
import com.sploot.data.entity.RawImuEntity
import com.sploot.data.entity.RawPpgEntity
import com.sploot.data.entity.RecordingSessionEntity
import com.sploot.data.entity.SleepEpochEntity
import com.sploot.data.entity.SleepSessionEntity
import com.sploot.data.entity.TrainingExampleEntity
import com.sploot.data.entity.WhoopEventEntity
import com.sploot.data.entity.WhoopUnknownObservationEntity

@Database(
    entities = [
        RecordingSessionEntity::class,
        RawImuEntity::class,
        RawPpgEntity::class,
        HrSampleEntity::class,
        HrvWindowEntity::class,
        SleepSessionEntity::class,
        SleepEpochEntity::class,
        GarminGroundTruthEntity::class,
        WhoopEventEntity::class,
        AlgorithmRevisionEntity::class,
        AlgorithmEvaluationEntity::class,
        ImportedArtifactEntity::class,
        ActivityEvaluationEntity::class,
        ActivitySessionEntity::class,
        ActivityLapEntity::class,
        ActivityTrackPointEntity::class,
        ExternalHeartRateSampleEntity::class,
        DailyMetricSummaryEntity::class,
        TrainingExampleEntity::class,
        WhoopUnknownObservationEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class SplootDatabase : RoomDatabase() {
    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun rawImuDao(): RawImuDao
    abstract fun rawPpgDao(): RawPpgDao
    abstract fun hrSampleDao(): HrSampleDao
    abstract fun hrvWindowDao(): HrvWindowDao
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun sleepEpochDao(): SleepEpochDao
    abstract fun garminGroundTruthDao(): GarminGroundTruthDao
    abstract fun whoopEventDao(): WhoopEventDao
    abstract fun algorithmRevisionDao(): AlgorithmRevisionDao
    abstract fun algorithmEvaluationDao(): AlgorithmEvaluationDao
    abstract fun importedArtifactDao(): ImportedArtifactDao
    abstract fun activityEvaluationDao(): ActivityEvaluationDao
    abstract fun activitySessionDao(): ActivitySessionDao
    abstract fun activityLapDao(): ActivityLapDao
    abstract fun activityTrackPointDao(): ActivityTrackPointDao
    abstract fun externalHeartRateSampleDao(): ExternalHeartRateSampleDao
    abstract fun dailyMetricSummaryDao(): DailyMetricSummaryDao
    abstract fun trainingExampleDao(): TrainingExampleDao
    abstract fun whoopUnknownObservationDao(): WhoopUnknownObservationDao
}
