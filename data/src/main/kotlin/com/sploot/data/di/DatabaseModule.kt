package com.sploot.data.di

import android.content.Context
import androidx.room.Room
import com.sploot.data.SplootDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSplootDatabase(@ApplicationContext context: Context): SplootDatabase =
        Room.databaseBuilder(context, SplootDatabase::class.java, "sploot.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideRecordingSessionDao(db: SplootDatabase) = db.recordingSessionDao()
    @Provides fun provideRawImuDao(db: SplootDatabase)           = db.rawImuDao()
    @Provides fun provideRawPpgDao(db: SplootDatabase)           = db.rawPpgDao()
    @Provides fun provideHrSampleDao(db: SplootDatabase)         = db.hrSampleDao()
    @Provides fun provideHrvWindowDao(db: SplootDatabase)        = db.hrvWindowDao()
    @Provides fun provideSleepSessionDao(db: SplootDatabase)     = db.sleepSessionDao()
    @Provides fun provideSleepEpochDao(db: SplootDatabase)       = db.sleepEpochDao()
    @Provides fun provideGarminGroundTruthDao(db: SplootDatabase) = db.garminGroundTruthDao()
    @Provides fun provideWhoopEventDao(db: SplootDatabase)        = db.whoopEventDao()
    @Provides fun provideAlgorithmRevisionDao(db: SplootDatabase) = db.algorithmRevisionDao()
    @Provides fun provideAlgorithmEvaluationDao(db: SplootDatabase) = db.algorithmEvaluationDao()
    @Provides fun provideImportedArtifactDao(db: SplootDatabase) = db.importedArtifactDao()
    @Provides fun provideActivityEvaluationDao(db: SplootDatabase) = db.activityEvaluationDao()
    @Provides fun provideActivitySessionDao(db: SplootDatabase) = db.activitySessionDao()
    @Provides fun provideActivityLapDao(db: SplootDatabase) = db.activityLapDao()
    @Provides fun provideActivityTrackPointDao(db: SplootDatabase) = db.activityTrackPointDao()
    @Provides fun provideExternalHeartRateSampleDao(db: SplootDatabase) = db.externalHeartRateSampleDao()
    @Provides fun provideDailyMetricSummaryDao(db: SplootDatabase) = db.dailyMetricSummaryDao()
    @Provides fun provideTrainingExampleDao(db: SplootDatabase) = db.trainingExampleDao()
    @Provides fun provideWhoopUnknownObservationDao(db: SplootDatabase) = db.whoopUnknownObservationDao()
}
