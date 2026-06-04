package com.sploot.app.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sploot.app.ble.WhoopDeviceInfo
import com.sploot.app.ble.WhoopDeviceScanner
import com.sploot.app.settings.AppSettingsRepository
import com.sploot.app.worker.WhoopHistoricalSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WhoopDevicesUiState(
    val devices: List<WhoopDeviceInfo> = emptyList(),
    val preferredAddress: String? = null,
    val preferredName: String? = null,
)

@HiltViewModel
class WhoopDevicesViewModel @Inject constructor(
    private val scanner: WhoopDeviceScanner,
    private val settingsRepository: AppSettingsRepository,
    private val scheduler: WhoopHistoricalSyncScheduler,
) : ViewModel() {
    val uiState: StateFlow<WhoopDevicesUiState> = combine(
        scanner.devices,
        settingsRepository.settings,
    ) { devices, settings ->
        WhoopDevicesUiState(
            devices = devices,
            preferredAddress = settings.preferredWhoopDeviceAddress,
            preferredName = settings.preferredWhoopDeviceName,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WhoopDevicesUiState())

    fun startScan() = scanner.startScan()

    fun stopScan() = scanner.stopScan()

    fun refreshBondedDevices() = scanner.refreshBondedDevices()

    fun pair(address: String) {
        scanner.createBond(address)
    }

    fun selectPreferred(device: WhoopDeviceInfo) {
        settingsRepository.update {
            it.copy(
                preferredWhoopDeviceAddress = device.address,
                preferredWhoopDeviceName = device.name,
            )
        }
        val settings = settingsRepository.current()
        if (settings.enablePeriodicHistoricalSync) {
            scheduler.schedule(settings.periodicHistoricalSyncHours)
        }
    }
}
