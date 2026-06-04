package com.sploot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.sploot.app.service.WhoopRecordingService
import com.sploot.app.settings.AppSettingsRepository
import com.sploot.app.ui.SplootNavHost
import com.sploot.app.ui.theme.SplootTheme
import com.sploot.data.repository.RecordingRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: AppSettingsRepository
    @Inject lateinit var recordingRepository: RecordingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        triggerWhoopFlowOnOpen()
        setContent {
            SplootTheme {
                SplootNavHost()
            }
        }
    }

    private fun triggerWhoopFlowOnOpen() {
        val settings = settingsRepository.current()
        if (settings.preferredWhoopDeviceAddress == null) return
        lifecycleScope.launch {
            val latestStoredTimestamp = recordingRepository.getLatestStoredTimestamp()
            if (latestStoredTimestamp == null) {
                Timber.i("App-open WHOOP flow: no local history found, requesting full strap backfill")
                startForegroundService(
                    WhoopRecordingService.startHistoricalSyncIntent(
                        context = this@MainActivity,
                        autoStartLiveAfter = false,
                    )
                )
            } else {
                Timber.i("App-open WHOOP flow: local history exists, requesting incremental sync only")
                startForegroundService(
                    WhoopRecordingService.startHistoricalSyncIntent(
                        context = this@MainActivity,
                        autoStartLiveAfter = false,
                    )
                )
            }
        }
    }
}
