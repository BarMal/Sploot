package com.sploot.app.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhoopHistoricalSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule(intervalHours: Int = DAILY_SYNC_INTERVAL_HOURS) {
        val request = PeriodicWorkRequestBuilder<WhoopHistoricalSyncWorker>(
            DAILY_SYNC_INTERVAL_HOURS.toLong(),
            TimeUnit.HOURS,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "whoop_historical_sync"
        private const val DAILY_SYNC_INTERVAL_HOURS = 24
    }
}
