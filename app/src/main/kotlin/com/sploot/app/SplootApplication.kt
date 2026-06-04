package com.sploot.app

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.sploot.app.settings.AppSettingsRepository
import com.sploot.app.worker.WhoopHistoricalSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Custom Application class.
 *
 * Implements [Configuration.Provider] so WorkManager uses [HiltWorkerFactory]
 * and @HiltWorker-annotated workers receive injected dependencies.
 * The default WorkManager initializer is disabled in AndroidManifest.xml.
 */
@HiltAndroidApp
class SplootApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: AppSettingsRepository

    @Inject
    lateinit var historicalSyncScheduler: WhoopHistoricalSyncScheduler

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        val settings = settingsRepository.current()
        if (settings.enablePeriodicHistoricalSync) {
            historicalSyncScheduler.schedule(settings.periodicHistoricalSyncHours)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
