package com.sploot.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sploot.app.service.WhoopRecordingService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class WhoopHistoricalSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        applicationContext.startForegroundService(
            WhoopRecordingService.startHistoricalSyncIntent(applicationContext)
        )
        return Result.success()
    }
}
