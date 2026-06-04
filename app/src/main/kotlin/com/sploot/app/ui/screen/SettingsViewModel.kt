package com.sploot.app.ui.screen

import androidx.lifecycle.ViewModel
import com.sploot.app.settings.AppSettings
import com.sploot.app.settings.AppSettingsRepository
import com.sploot.app.settings.WhoopDoubleTapAction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val historicalSyncScheduler: com.sploot.app.worker.WhoopHistoricalSyncScheduler,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun setFollowSystemBatterySaver(enabled: Boolean) =
        settingsRepository.update { it.copy(followSystemBatterySaver = enabled) }

    fun setBatterySaverDisableWhoopHrStream(enabled: Boolean) =
        settingsRepository.update { it.copy(batterySaverDisableWhoopHrStream = enabled) }

    fun setBatterySaverDisableWhoopImuStream(enabled: Boolean) =
        settingsRepository.update { it.copy(batterySaverDisableWhoopImuStream = enabled) }

    fun setBatterySaverDisableWhoopPpgStream(enabled: Boolean) =
        settingsRepository.update { it.copy(batterySaverDisableWhoopPpgStream = enabled) }

    fun setBatterySaverForceGlobalWhoopInterval(enabled: Boolean) =
        settingsRepository.update { it.copy(batterySaverForceGlobalWhoopInterval = enabled) }

    fun setBatterySaverGlobalWhoopInterval(seconds: Int) =
        settingsRepository.update { it.copy(batterySaverGlobalWhoopIntervalSeconds = seconds.coerceIn(1, 30)) }

    fun setEnableWhoopHrStream(enabled: Boolean) =
        settingsRepository.update { it.copy(enableWhoopHrStream = enabled) }

    fun setEnableWhoopImuStream(enabled: Boolean) =
        settingsRepository.update { it.copy(enableWhoopImuStream = enabled) }

    fun setEnableWhoopPpgStream(enabled: Boolean) =
        settingsRepository.update { it.copy(enableWhoopPpgStream = enabled) }

    fun setGlobalWhoopCaptureEnabled(enabled: Boolean) =
        settingsRepository.update { it.copy(globalWhoopCaptureIntervalEnabled = enabled) }

    fun setGlobalWhoopCaptureInterval(seconds: Int) =
        settingsRepository.update { it.copy(globalWhoopCaptureIntervalSeconds = seconds.coerceIn(1, 30)) }

    fun setImuInterval(seconds: Int) =
        settingsRepository.update { it.copy(imuCaptureIntervalSeconds = seconds.coerceIn(1, 30)) }

    fun setPpgInterval(seconds: Int) =
        settingsRepository.update { it.copy(ppgCaptureIntervalSeconds = seconds.coerceIn(1, 30)) }

    fun setHrInterval(seconds: Int) =
        settingsRepository.update { it.copy(hrCaptureIntervalSeconds = seconds.coerceIn(1, 30)) }

    fun setCaptureWhoopEvents(enabled: Boolean) =
        settingsRepository.update { it.copy(captureWhoopEvents = enabled) }

    fun setWhoopDoubleTapAction(action: WhoopDoubleTapAction) =
        settingsRepository.update { it.copy(whoopDoubleTapAction = action) }

    fun setEnableWhoopUnknownTagPrompts(enabled: Boolean) =
        settingsRepository.update { it.copy(enableWhoopUnknownTagPrompts = enabled) }

    fun setRunSleepProcessing(enabled: Boolean) =
        settingsRepository.update { it.copy(runSleepProcessing = enabled) }

    fun setRunActivityProcessing(enabled: Boolean) =
        settingsRepository.update { it.copy(runActivityProcessing = enabled) }

    fun setImportHealthConnectSleep(enabled: Boolean) =
        settingsRepository.update { it.copy(importHealthConnectSleep = enabled) }

    fun setImportHealthConnectActivities(enabled: Boolean) =
        settingsRepository.update { it.copy(importHealthConnectActivities = enabled) }

    fun setImportHealthConnectHeartRate(enabled: Boolean) =
        settingsRepository.update { it.copy(importHealthConnectHeartRate = enabled) }

    fun setHealthConnectLookbackDays(days: Int) =
        settingsRepository.update { it.copy(healthConnectLookbackDays = days.coerceIn(1, 30)) }

    fun setPreferredWhoopDevice(address: String?, name: String?) =
        settingsRepository.update {
            it.copy(
                preferredWhoopDeviceAddress = address,
                preferredWhoopDeviceName = name,
            )
        }

    fun setEnablePeriodicHistoricalSync(enabled: Boolean) {
        settingsRepository.update { it.copy(enablePeriodicHistoricalSync = enabled) }
        updateHistoricalSyncSchedule()
    }

    fun setPeriodicHistoricalSyncHours(hours: Int) {
        settingsRepository.update { it.copy(periodicHistoricalSyncHours = hours.coerceIn(1, 24)) }
        updateHistoricalSyncSchedule()
    }

    private fun updateHistoricalSyncSchedule() {
        val settings = settingsRepository.current()
        if (settings.enablePeriodicHistoricalSync) {
            historicalSyncScheduler.schedule(settings.periodicHistoricalSyncHours)
        } else {
            historicalSyncScheduler.cancel()
        }
    }
}
