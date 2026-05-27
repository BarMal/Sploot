package com.sploot.whoopble.gatt

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.sploot.whoopble.model.WhoopRecord
import com.sploot.whoopble.protocol.CrcValidator
import com.sploot.whoopble.protocol.EventDecoder
import com.sploot.whoopble.protocol.FrameAssembler
import com.sploot.whoopble.protocol.R10Decoder
import com.sploot.whoopble.protocol.R21Decoder
import com.sploot.whoopble.protocol.WhoopConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Observable connection state. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, READY }

/**
 * Manages the GATT connection to a WHOOP 4.0 device.
 *
 * Usage:
 * 1. Scan with [BluetoothLeScanner] filtering on device name "WHOOP" (see companion)
 * 2. Call [connect] with the [BluetoothDevice] — suspends until streaming starts
 * 3. Collect [records] to receive parsed [WhoopRecord] events
 * 4. Call [disconnect] on cleanup
 *
 * One-shot connection protocol (per whoopsie-protocol spec):
 *   connect → request MTU 512 → discover services → enable 3 notify characteristics
 *   → send 5 init packets → send 3 realtime-enable commands → data flows
 */
@Singleton
class WhoopGattManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ── Public observables ────────────────────────────────────────────────────

    private val _records = MutableSharedFlow<WhoopRecord>(extraBufferCapacity = 2048)
    val records: SharedFlow<WhoopRecord> = _records.asSharedFlow()

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // ── Internal state ────────────────────────────────────────────────────────

    private var gatt: BluetoothGatt? = null

    // One assembler per incoming characteristic (DATA / EVENTS / CMD_FROM_STRAP)
    private val dataAssembler  = FrameAssembler()
    private val eventAssembler = FrameAssembler()
    private val cmdAssembler   = FrameAssembler()

    // Serialize all GATT write operations (Android BLE allows only one outstanding write)
    private val writeMutex = Mutex()

    // One-shot deferreds reset before each operation
    @Volatile private var connectionDeferred = CompletableDeferred<Unit>()
    @Volatile private var mtuDeferred        = CompletableDeferred<Int>()
    @Volatile private var servicesDeferred   = CompletableDeferred<Unit>()
    @Volatile private var writeDeferred      = CompletableDeferred<Unit>()

    // ── GATT callback ─────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Timber.d("onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = ConnectionState.CONNECTED
                    connectionDeferred.complete(Unit)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = ConnectionState.DISCONNECTED
                    dataAssembler.reset()
                    eventAssembler.reset()
                    cmdAssembler.reset()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Timber.d("onMtuChanged mtu=$mtu status=$status")
            mtuDeferred.complete(mtu)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Timber.d("onServicesDiscovered status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS)
                servicesDeferred.complete(Unit)
            else
                servicesDeferred.completeExceptionally(IOException("discoverServices failed: status=$status"))
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS)
                writeDeferred.complete(Unit)
            else
                writeDeferred.completeExceptionally(IOException("write failed: status=$status char=${characteristic.uuid}"))
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS)
                writeDeferred.complete(Unit)
            else
                writeDeferred.completeExceptionally(IOException("descriptor write failed: status=$status"))
        }

        // API 33+ preferred callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = handleNotification(characteristic.uuid, value)

        // API < 33 fallback
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            handleNotification(characteristic.uuid, characteristic.value ?: return)
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Connect to [device] and start realtime data streaming.
     * Suspends until streaming is active (state = READY) or throws on error.
     */
    suspend fun connect(device: BluetoothDevice) {
        check(_state.value == ConnectionState.DISCONNECTED) { "Already connected" }
        _state.value = ConnectionState.CONNECTING

        connectionDeferred = CompletableDeferred()
        mtuDeferred        = CompletableDeferred()
        servicesDeferred   = CompletableDeferred()

        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        withTimeout(12_000L) { connectionDeferred.await() }
        Timber.i("GATT connected")

        gatt!!.requestMtu(WhoopConstants.MTU)
        withTimeout(5_000L) { mtuDeferred.await() }
        Timber.i("MTU negotiated")

        gatt!!.discoverServices()
        withTimeout(12_000L) { servicesDeferred.await() }
        Timber.i("Services discovered: ${gatt!!.services.map { it.uuid }}")

        // Enable notifications on the three incoming characteristics in order
        for (prefix in listOf(
            WhoopConstants.CMD_FROM_STRAP_PREFIX,
            WhoopConstants.EVENTS_PREFIX,
            WhoopConstants.DATA_PREFIX,
        )) {
            val char = gatt!!.findCharacteristic(prefix)
                ?: throw IOException("Characteristic $prefix not found")
            enableNotify(char)
        }

        // Write 5 hardcoded init packets
        val cmdChar = gatt!!.findCharacteristic(WhoopConstants.CMD_TO_STRAP_PREFIX)
            ?: throw IOException("CMD_TO_STRAP characteristic not found")

        for (packet in WhoopConstants.INIT_PACKETS) {
            writeGatt(cmdChar, packet)
            delay(50L)
        }

        // Enable realtime IMU + PPG streaming
        var seq = WhoopConstants.INITIAL_SEQ
        writeGatt(cmdChar, buildCommandPacket(seq++, WhoopConstants.CMD_ENABLE_IMU,   byteArrayOf(0x01)))
        writeGatt(cmdChar, buildCommandPacket(seq++, WhoopConstants.CMD_ENABLE_PPG_1, byteArrayOf(0x01)))
        writeGatt(cmdChar, buildCommandPacket(seq,   WhoopConstants.CMD_ENABLE_PPG_2, byteArrayOf(0x01)))

        _state.value = ConnectionState.READY
        Timber.i("Whoop streaming READY")
    }

    /** Gracefully disconnect and release GATT resources. */
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = ConnectionState.DISCONNECTED
        dataAssembler.reset()
        eventAssembler.reset()
        cmdAssembler.reset()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        val uuidStr = uuid.toString().lowercase()

        val assembler = when {
            uuidStr.startsWith(WhoopConstants.DATA_PREFIX.lowercase())           -> dataAssembler
            uuidStr.startsWith(WhoopConstants.EVENTS_PREFIX.lowercase())         -> eventAssembler
            uuidStr.startsWith(WhoopConstants.CMD_FROM_STRAP_PREFIX.lowercase()) -> cmdAssembler
            else -> return
        }

        for (frame in assembler.feed(value)) {
            val record: WhoopRecord? = when {
                uuidStr.startsWith(WhoopConstants.EVENTS_PREFIX.lowercase()) ->
                    EventDecoder.decode(frame)

                uuidStr.startsWith(WhoopConstants.DATA_PREFIX.lowercase()) -> {
                    // inner_content[1] = record_type  (frame[4+1] = frame[5])
                    if (frame.size < 6) null
                    else when (frame[5].toInt() and 0xFF) {
                        WhoopConstants.RECORD_TYPE_IMU -> R10Decoder.decode(frame)
                        WhoopConstants.RECORD_TYPE_PPG -> R21Decoder.decode(frame)
                        else -> null
                    }
                }

                else -> null  // CMD_FROM_STRAP responses — not needed for data collection
            }

            record?.let { _records.tryEmit(it) }
        }
    }

    private suspend fun enableNotify(characteristic: BluetoothGattCharacteristic) {
        writeMutex.withLock {
            writeDeferred = CompletableDeferred()

            gatt!!.setCharacteristicNotification(characteristic, true)

            val cccd = characteristic.getDescriptor(UUID.fromString(WhoopConstants.CCCD_UUID))
                ?: throw IOException("CCCD not found on ${characteristic.uuid}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt!!.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt!!.writeDescriptor(cccd)
            }

            withTimeout(5_000L) { writeDeferred.await() }
        }
    }

    private suspend fun writeGatt(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        writeMutex.withLock {
            writeDeferred = CompletableDeferred()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt!!.writeCharacteristic(
                    characteristic,
                    value,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                )
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = value
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt!!.writeCharacteristic(characteristic)
            }

            withTimeout(5_000L) { writeDeferred.await() }
        }
    }

    /**
     * Build a command frame ready to write to CMD_TO_STRAP.
     *
     * Inner content: [CMD_TYPE=0x23, seq, cmd, payload..., pad to 4-byte boundary]
     */
    private fun buildCommandPacket(seq: Int, cmd: Int, payload: ByteArray): ByteArray {
        val minInner  = 3 + payload.size
        val innerSize = ((minInner + 3) / 4) * 4   // round up to 4-byte alignment
        val inner = ByteArray(innerSize).also { buf ->
            buf[0] = WhoopConstants.CMD_TYPE_COMMAND.toByte()
            buf[1] = seq.toByte()
            buf[2] = cmd.toByte()
            payload.copyInto(buf, destinationOffset = 3)
        }

        val frameLength = innerSize + 4
        val lenLo  = (frameLength and 0xFF).toByte()
        val lenHi  = ((frameLength shr 8) and 0xFF).toByte()
        val hdrCrc = CrcValidator.crc8(byteArrayOf(lenLo, lenHi))
        val crc32  = CrcValidator.crc32(inner)

        return byteArrayOf(WhoopConstants.SOF, lenLo, lenHi, hdrCrc) +
                inner +
                byteArrayOf(
                    (crc32         and 0xFF).toByte(),
                    ((crc32 shr  8) and 0xFF).toByte(),
                    ((crc32 shr 16) and 0xFF).toByte(),
                    ((crc32 ushr 24) and 0xFF).toByte(),
                )
    }

    /** Finds a characteristic by matching the first 8 characters of its UUID. */
    private fun BluetoothGatt.findCharacteristic(uuidPrefix: String): BluetoothGattCharacteristic? =
        services.flatMap { it.characteristics }.find { char ->
            char.uuid.toString().lowercase().startsWith(uuidPrefix.lowercase())
        }
}
