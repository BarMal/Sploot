package com.sploot.app.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sploot.whoopble.protocol.WhoopConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WhoopDeviceInfo(
    val address: String,
    val name: String,
    val isBonded: Boolean,
    val isNearby: Boolean,
)

@Singleton
class WhoopDeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val scanner get() = bluetoothManager.adapter?.bluetoothLeScanner

    private val _devices = MutableStateFlow<List<WhoopDeviceInfo>>(emptyList())
    val devices: StateFlow<List<WhoopDeviceInfo>> = _devices.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.safeName() ?: return
            if (!name.startsWith(WhoopConstants.DEVICE_NAME_PREFIX)) return
            upsertDevice(device, name, isNearby = true)
        }
    }

    fun refreshBondedDevices() {
        if (!hasConnectPermission()) return
        val bonded = bluetoothManager.adapter?.bondedDevices.orEmpty()
            .filter { (it.safeName() ?: "").startsWith(WhoopConstants.DEVICE_NAME_PREFIX) }
            .map { device ->
                WhoopDeviceInfo(
                    address = device.address,
                    name = device.safeName() ?: "WHOOP",
                    isBonded = true,
                    isNearby = false,
                )
            }
        _devices.value = mergeDevices(_devices.value, bonded)
    }

    fun startScan() {
        refreshBondedDevices()
        if (!hasScanPermission()) return
        scanner?.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback,
        )
    }

    fun stopScan() {
        if (!hasScanPermission()) return
        scanner?.stopScan(scanCallback)
    }

    fun createBond(address: String): Boolean {
        if (!hasConnectPermission()) return false
        val device = bluetoothManager.adapter?.getRemoteDevice(address) ?: return false
        return device.createBond()
    }

    fun getRemoteDevice(address: String): BluetoothDevice? =
        bluetoothManager.adapter?.getRemoteDevice(address)

    private fun upsertDevice(device: BluetoothDevice, name: String, isNearby: Boolean) {
        val entry = WhoopDeviceInfo(
            address = device.address,
            name = name,
            isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
            isNearby = isNearby,
        )
        _devices.value = mergeDevices(_devices.value, listOf(entry))
    }

    private fun mergeDevices(
        existing: List<WhoopDeviceInfo>,
        incoming: List<WhoopDeviceInfo>,
    ): List<WhoopDeviceInfo> {
        val merged = linkedMapOf<String, WhoopDeviceInfo>()
        (existing + incoming).forEach { info ->
            val prior = merged[info.address]
            merged[info.address] = if (prior == null) {
                info
            } else {
                prior.copy(
                    name = if (info.name.isNotBlank()) info.name else prior.name,
                    isBonded = prior.isBonded || info.isBonded,
                    isNearby = prior.isNearby || info.isNearby,
                )
            }
        }
        return merged.values.sortedWith(
            compareByDescending<WhoopDeviceInfo> { it.isBonded }
                .thenByDescending { it.isNearby }
                .thenBy { it.name }
        )
    }

    private fun BluetoothDevice.safeName(): String? {
        if (!hasConnectPermission()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) name else @Suppress("DEPRECATION") name
    }

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}
