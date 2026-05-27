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
}
