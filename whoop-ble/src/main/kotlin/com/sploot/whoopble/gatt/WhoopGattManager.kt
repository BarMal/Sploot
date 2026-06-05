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
import com.sploot.whoopble.model.TraceDirection
import com.sploot.whoopble.model.WhoopBleTraceEvent
import com.sploot.whoopble.model.WhoopUnknownObservation
import com.sploot.whoopble.model.UnknownObservationCategory
import com.sploot.whoopble.protocol.CrcValidator
import com.sploot.whoopble.protocol.EventDecoder
import com.sploot.whoopble.protocol.FrameAssembler
import com.sploot.whoopble.protocol.R10Decoder
import com.sploot.whoopble.protocol.R11Decoder
import com.sploot.whoopble.protocol.R12Decoder
import com.sploot.whoopble.protocol.R21Decoder
import com.sploot.whoopble.protocol.RealtimeHrSummaryDecoder
import com.sploot.whoopble.protocol.WhoopConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Observable connection state. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, READY }

data class WhoopStreamConfig(
    val enableHr: Boolean = true,
    val enableImu: Boolean = true,
    val enablePpg: Boolean = true,
)

enum class WhoopSessionMode { LIVE_RECORDING, HISTORICAL_SYNC }

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
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Public observables ────────────────────────────────────────────────────

    private val _records = MutableSharedFlow<WhoopRecord>(extraBufferCapacity = 2048)
    val records: SharedFlow<WhoopRecord> = _records.asSharedFlow()

    private val _traceEvents = MutableSharedFlow<WhoopBleTraceEvent>(extraBufferCapacity = 512)
    val traceEvents: SharedFlow<WhoopBleTraceEvent> = _traceEvents.asSharedFlow()

    private val _unknownObservations = MutableSharedFlow<WhoopUnknownObservation>(extraBufferCapacity = 256)
    val unknownObservations: SharedFlow<WhoopUnknownObservation> = _unknownObservations.asSharedFlow()

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
    @Volatile private var activeStreamConfig = WhoopStreamConfig()
    @Volatile private var nextCommandSeq     = WhoopConstants.INITIAL_SEQ
    @Volatile private var historicalSyncDeferred = CompletableDeferred<Unit>()
    @Volatile private var liveReadyDeferred = CompletableDeferred<Unit>()
    @Volatile private var sessionMode = WhoopSessionMode.LIVE_RECORDING
    @Volatile private var batchCounter = 5
    @Volatile private var pendingLiveStreamConfig = WhoopStreamConfig()
    @Volatile private var realtimeEnableIssued = false
    private val observedSecondaryRecordTypes = mutableSetOf<Int>()
    private var loggedR11FallbackIgnore = false
    private var loggedObservedR11Companion = false
    private var loggedObservedFirmwareLogs = false
    private val recentProtocolContext = ArrayDeque<String>()

    // ── GATT callback ─────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Timber.d("onConnectionStateChange status=$status newState=$newState")
            traceInternal(
                channel = "gatt",
                summary = "Connection state -> status=$status newState=$newState",
            )
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
            traceInternal("gatt", "MTU changed -> $mtu (status=$status)")
            mtuDeferred.complete(mtu)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Timber.d("onServicesDiscovered status=$status")
            traceInternal("gatt", "Services discovered (status=$status)")
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
            traceInternal(
                channel = "write",
                summary = "Characteristic write complete status=$status uuid=${characteristic.uuid}",
            )
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
            traceInternal(
                channel = "descriptor",
                summary = "Descriptor write complete status=$status uuid=${descriptor.uuid}",
            )
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
    suspend fun connect(
        device: BluetoothDevice,
        streamConfig: WhoopStreamConfig = WhoopStreamConfig(),
        sessionMode: WhoopSessionMode = WhoopSessionMode.LIVE_RECORDING,
    ) {
        check(_state.value == ConnectionState.DISCONNECTED) { "Already connected" }
        _state.value = ConnectionState.CONNECTING
        activeStreamConfig = WhoopStreamConfig(enableHr = false, enableImu = false, enablePpg = false)
        nextCommandSeq = WhoopConstants.INITIAL_SEQ
        historicalSyncDeferred = CompletableDeferred()
        liveReadyDeferred = CompletableDeferred()
        batchCounter = 5
        this.sessionMode = sessionMode
        pendingLiveStreamConfig = streamConfig
        realtimeEnableIssued = false
        recentProtocolContext.clear()

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

        val initPackets = WhoopConstants.INIT_PACKETS
        val historyRequestPacket = initPackets.last()

        // Keep SET_CLOCK out of the realtime-enable sequence. The strap may emit
        // the live sync boundary immediately after SEND_HISTORICAL_DATA, which can
        // otherwise race and interleave SET_CLOCK between R10/R11 and R21 enables.
        for (packet in initPackets.dropLast(1)) {
            writeGatt(cmdChar, packet)
            delay(50L)
        }

        syncClock(cmdChar)

        writeGatt(cmdChar, historyRequestPacket)

        if (sessionMode == WhoopSessionMode.LIVE_RECORDING) {
            _state.value = ConnectionState.CONNECTED
            withTimeout(180_000L) { liveReadyDeferred.await() }
            Timber.i("Whoop live streaming READY")
        } else {
            _state.value = ConnectionState.CONNECTED
            Timber.i("Whoop historical sync started")
        }
    }

    /** Gracefully disconnect and release GATT resources. */
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        activeStreamConfig = WhoopStreamConfig()
        nextCommandSeq = WhoopConstants.INITIAL_SEQ
        historicalSyncDeferred = CompletableDeferred()
        liveReadyDeferred = CompletableDeferred()
        batchCounter = 5
        sessionMode = WhoopSessionMode.LIVE_RECORDING
        pendingLiveStreamConfig = WhoopStreamConfig()
        realtimeEnableIssued = false
        observedSecondaryRecordTypes.clear()
        loggedR11FallbackIgnore = false
        loggedObservedR11Companion = false
        loggedObservedFirmwareLogs = false
        recentProtocolContext.clear()
        _state.value = ConnectionState.DISCONNECTED
        dataAssembler.reset()
        eventAssembler.reset()
        cmdAssembler.reset()
    }

    suspend fun disableActiveStreams() {
        val localGatt = gatt ?: return
        val cmdChar = localGatt.findCharacteristic(WhoopConstants.CMD_TO_STRAP_PREFIX)
            ?: return

        disableConfiguredStreams(cmdChar, activeStreamConfig)
    }

    suspend fun applyStreamConfig(streamConfig: WhoopStreamConfig) {
        val localGatt = gatt ?: return
        val cmdChar = localGatt.findCharacteristic(WhoopConstants.CMD_TO_STRAP_PREFIX)
            ?: return

        val current = activeStreamConfig
        if (current == streamConfig) return

        if (current.enableHr && !streamConfig.enableHr) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_HR, false)
        }
        if (current.enableImu && !streamConfig.enableImu) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_IMU, false)
        }
        if (current.enablePpg && !streamConfig.enablePpg) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_PPG_1, false)
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_PPG_2, false)
        }

        if (!current.enableHr && streamConfig.enableHr) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_HR, true)
        }
        if (!current.enableImu && streamConfig.enableImu) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_IMU, true)
        }
        if (!current.enablePpg && streamConfig.enablePpg) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_PPG_1, true)
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_PPG_2, true)
        }

        activeStreamConfig = streamConfig
    }

    suspend fun runHapticsPattern(
        patternId: Int = DEFAULT_HARVARD_HAPTIC_PATTERN_ID,
        loops: Int = 0,
    ) {
        val localGatt = gatt ?: return
        val cmdChar = localGatt.findCharacteristic(WhoopConstants.CMD_TO_STRAP_PREFIX)
            ?: return
        val payload = byteArrayOf(
            (patternId and 0xFF).toByte(),
            (loops and 0xFF).toByte(),
            ((loops shr 8) and 0xFF).toByte(),
            ((loops shr 16) and 0xFF).toByte(),
            ((loops shr 24) and 0xFF).toByte(),
        )
        writeGatt(cmdChar, buildCommandPacket(nextSeq(), WhoopConstants.CMD_RUN_HAPTICS_PATTERN, payload))
    }

    suspend fun stopHaptics() {
        val localGatt = gatt ?: return
        val cmdChar = localGatt.findCharacteristic(WhoopConstants.CMD_TO_STRAP_PREFIX)
            ?: return
        writeGatt(cmdChar, buildCommandPacket(nextSeq(), WhoopConstants.CMD_STOP_HAPTICS, byteArrayOf()))
    }

    suspend fun awaitHistoricalSyncCompletion(timeoutMs: Long = 180_000L) {
        withTimeout(timeoutMs) {
            historicalSyncDeferred.await()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        val uuidStr = uuid.toString().lowercase()
        traceIncoming(
            channel = uuidStr.take(8),
            summary = "Notification fragment",
            bytes = value,
        )

        val assembler = when {
            uuidStr.startsWith(WhoopConstants.DATA_PREFIX.lowercase())           -> dataAssembler
            uuidStr.startsWith(WhoopConstants.EVENTS_PREFIX.lowercase())         -> eventAssembler
            uuidStr.startsWith(WhoopConstants.CMD_FROM_STRAP_PREFIX.lowercase()) -> cmdAssembler
            else -> return
        }

        for (frame in assembler.feed(value)) {
            val packetType = frame.getOrNull(4)?.toInt()?.and(0xFF)
            traceIncoming(
                channel = uuidStr.take(8),
                summary = "Frame assembled type=${packetTypeName(frame)}",
                bytes = frame,
            )
            if (handleMetadataFrame(frame)) {
                continue
            }

            if (packetType == 0x24) {
                traceCommandResponse(frame)
            }
            if (packetType == 0x30) {
                traceEvent(frame)
            }

            val record: WhoopRecord? = when {
                packetType == 0x24 ->
                    decodeCommandResponseRecord(frame)

                packetType == 0x30 ->
                    EventDecoder.decode(frame)

                packetType == 0x28 || packetType == 0x2B || packetType == 0x2F -> {
                    // inner_content[1] = record_type  (frame[4+1] = frame[5])
                    if (frame.size < 6) null
                    else when (frame[5].toInt() and 0xFF) {
                        WhoopConstants.RECORD_TYPE_REALTIME_HR_SUMMARY -> RealtimeHrSummaryDecoder.decode(frame)
                        WhoopConstants.RECORD_TYPE_IMU -> R10Decoder.decode(frame)
                        WhoopConstants.RECORD_TYPE_COMPANION_IMU -> {
                            val decoded = R11Decoder.decode(frame)
                            if (decoded == null && !loggedR11FallbackIgnore) {
                                loggedR11FallbackIgnore = true
                                traceInternal(
                                    channel = "decode",
                                    summary = "Ignored R11 companion HR payload because it was implausible or incomplete",
                                )
                            }
                            decoded
                        }
                        WhoopConstants.RECORD_TYPE_R12 -> R12Decoder.decode(frame)
                        WhoopConstants.RECORD_TYPE_PPG -> R21Decoder.decode(frame)
                        else -> null
                    }
                }

                else -> null
            }

            record?.let { _records.tryEmit(it) }
            if (
                sessionMode == WhoopSessionMode.HISTORICAL_SYNC &&
                !historicalSyncDeferred.isCompleted &&
                (packetType == 0x28 || packetType == 0x2B)
            ) {
                traceInternal("metadata", "Historical sync implicitly complete after realtime frame")
                historicalSyncDeferred.complete(Unit)
                _state.value = ConnectionState.READY
            }
            if (packetType == 0x30) {
                if (record == null) {
                    val eventType = eventType(frame)
                    if (observedUndecodedEventTypeName(eventType) != null) {
                        auditObservedUndecodedEvent(frame)
                    } else if (decodedEventTypeName(eventType) == null) {
                        auditUnknownEvent(frame)
                    }
                }
                appendProtocolContext(eventContextSummary(frame))
            } else if (record == null && (packetType == 0x28 || packetType == 0x2B || packetType == 0x2F)) {
                val recordType = frame.getOrNull(5)?.toInt()?.and(0xFF)
                if (recordType == WhoopConstants.RECORD_TYPE_COMPANION_IMU) {
                    if (!loggedObservedR11Companion) {
                        loggedObservedR11Companion = true
                        traceInternal(
                            channel = "decode",
                            summary = "Observed R11 companion frames; currently treated as auxiliary and ignored when they do not contain plausible HR",
                        )
                    }
                } else if (recordType != null && isKnownSecondaryRecordType(recordType)) {
                    if (observedSecondaryRecordTypes.add(recordType)) {
                        traceInternal(
                            channel = "decode",
                            summary = "Observed secondary WHOOP data frame ${recordTypeLabel(frame)} size=${frame.size}",
                        )
                    }
                } else {
                    traceInternal(
                        channel = "decode",
                        summary = "Unhandled data frame ${recordTypeLabel(frame)} size=${frame.size} packetType=${packetTypeName(frame)}",
                    )
                    auditUnknownDataFrame(frame)
                }
            } else if (record == null && packetType == 0x32 && !loggedObservedFirmwareLogs) {
                loggedObservedFirmwareLogs = true
                traceInternal(
                    channel = "decode",
                    summary = "Observed WHOOP firmware log frames on BLE; ignoring them in normal app flow",
                )
            }
        }
    }

    private fun handleMetadataFrame(frame: ByteArray): Boolean {
        if (frame.size < 5) return false

        if (
            frame.size >= 25 &&
            frame[0] == WhoopConstants.SOF &&
            frame[1] == 0x1C.toByte() &&
            frame[2] == 0x00.toByte() &&
            frame[3] == 0xAB.toByte() &&
            frame[4] == 0x31.toByte()
        ) {
            val batchNumber = frame.copyOfRange(17, 21)
            traceInternal(
                "metadata",
                "Historical batch marker received batch=${frame.getUInt32LE(17)}",
            )
            appendProtocolContext("metadata:historical-batch batch=${frame.getUInt32LE(17)}")
            serviceScopeLaunchBatchAck(batchNumber)
            return true
        }

        if (frame[4] == 0x31.toByte()) {
            if (sessionMode == WhoopSessionMode.HISTORICAL_SYNC && !historicalSyncDeferred.isCompleted) {
                traceInternal("metadata", "Historical sync complete marker received")
                appendProtocolContext("metadata:historical-complete size=${frame.size}")
                historicalSyncDeferred.complete(Unit)
                _state.value = ConnectionState.READY
                Timber.i("Whoop historical sync complete")
            } else if (sessionMode == WhoopSessionMode.LIVE_RECORDING && !realtimeEnableIssued) {
                traceInternal("metadata", "Live end-of-sync marker received, enabling realtime streams")
                appendProtocolContext("metadata:live-sync-boundary size=${frame.size}")
                realtimeEnableIssued = true
                managerScope.launch {
                    runCatching {
                        val localGatt = gatt ?: error("No GATT available for realtime enable")
                        val cmdChar = localGatt.findCharacteristic(WhoopConstants.CMD_TO_STRAP_PREFIX)
                            ?: error("CMD_TO_STRAP characteristic not found for realtime enable")
                        enableConfiguredStreams(cmdChar, pendingLiveStreamConfig)
                        _state.value = ConnectionState.READY
                        if (!liveReadyDeferred.isCompleted) {
                            liveReadyDeferred.complete(Unit)
                        }
                    }.onFailure {
                        Timber.e(it, "Failed to enable WHOOP realtime streams after sync metadata")
                        if (!liveReadyDeferred.isCompleted) {
                            liveReadyDeferred.completeExceptionally(it)
                        }
                    }
                }
            }
            return true
        }

        return false
    }

    private fun serviceScopeLaunchBatchAck(batchNumber: ByteArray) {
        managerScope.launch {
            runCatching {
                val localGatt = gatt ?: return@launch
                val cmdChar = localGatt.findCharacteristic(WhoopConstants.CMD_TO_STRAP_PREFIX)
                    ?: return@launch
                writeGatt(cmdChar, buildBatchAckPacket(batchCounter++, batchNumber))
            }.onFailure {
                Timber.w(it, "Failed to ACK historical batch marker")
            }
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
            traceOutgoing(
                channel = characteristic.uuid.toString().take(8),
                summary = "Write ${commandSummary(value)}",
                bytes = value,
            )
            appendProtocolContext("out:${commandSummary(value)}")

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

    private suspend fun enableConfiguredStreams(
        cmdChar: BluetoothGattCharacteristic,
        streamConfig: WhoopStreamConfig,
    ) {
        if (streamConfig.enableHr) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_HR, true)
        }
        if (streamConfig.enableImu) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_IMU, true)
        }
        if (streamConfig.enablePpg) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_PPG_1, true)
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_PPG_2, true)
        }
        activeStreamConfig = streamConfig
    }

    private suspend fun disableConfiguredStreams(
        cmdChar: BluetoothGattCharacteristic,
        streamConfig: WhoopStreamConfig,
    ) {
        if (streamConfig.enableHr) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_HR, false)
        }
        if (streamConfig.enableImu) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_IMU, false)
        }
        if (streamConfig.enablePpg) {
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_PPG_1, false)
            writeCommand(cmdChar, WhoopConstants.CMD_ENABLE_PPG_2, false)
        }
        activeStreamConfig = WhoopStreamConfig(enableHr = false, enableImu = false, enablePpg = false)
    }

    private suspend fun writeCommand(
        cmdChar: BluetoothGattCharacteristic,
        cmd: Int,
        enabled: Boolean,
    ) {
        val payload = if (enabled) byteArrayOf(0x01) else byteArrayOf(0x00)
        writeGatt(cmdChar, buildCommandPacket(nextSeq(), cmd, payload))
    }

    private suspend fun syncClock(cmdChar: BluetoothGattCharacteristic) {
        val epochSeconds = Instant.now().epochSecond
        val payload = byteArrayOf(
            (epochSeconds and 0xFF).toByte(),
            ((epochSeconds shr 8) and 0xFF).toByte(),
            ((epochSeconds shr 16) and 0xFF).toByte(),
            ((epochSeconds shr 24) and 0xFF).toByte(),
        )
        writeGatt(cmdChar, buildCommandPacket(nextSeq(), WhoopConstants.CMD_SET_CLOCK, payload))
    }

    private fun nextSeq(): Int {
        val seq = nextCommandSeq
        nextCommandSeq = (nextCommandSeq + 1) and 0xFF
        return seq
    }

    private fun traceIncoming(channel: String, summary: String, bytes: ByteArray) {
        emitTrace(
            WhoopBleTraceEvent(
                direction = TraceDirection.INCOMING,
                channel = channel,
                summary = summary,
                sizeBytes = bytes.size,
                hexPreview = bytes.hexPreview(),
            )
        )
    }

    private fun traceOutgoing(channel: String, summary: String, bytes: ByteArray) {
        emitTrace(
            WhoopBleTraceEvent(
                direction = TraceDirection.OUTGOING,
                channel = channel,
                summary = summary,
                sizeBytes = bytes.size,
                hexPreview = bytes.hexPreview(),
            )
        )
    }

    private fun traceInternal(channel: String, summary: String) {
        emitTrace(
            WhoopBleTraceEvent(
                direction = TraceDirection.INTERNAL,
                channel = channel,
                summary = summary,
                sizeBytes = 0,
                hexPreview = "",
            )
        )
    }

    private fun appendProtocolContext(summary: String) {
        while (recentProtocolContext.size >= RECENT_PROTOCOL_CONTEXT_LIMIT) {
            recentProtocolContext.removeFirst()
        }
        recentProtocolContext.addLast(summary.take(MAX_PROTOCOL_CONTEXT_ENTRY_CHARS))
    }

    private fun precedingProtocolContextNote(): String =
        if (recentProtocolContext.isEmpty()) {
            "Preceding context: none"
        } else {
            "Preceding context: ${recentProtocolContext.joinToString(separator = " | ")}"
        }

    private fun emitTrace(event: WhoopBleTraceEvent) {
        _traceEvents.tryEmit(event)
        Timber.tag("WhoopTrace").d(
            "%s | %s | %s | %dB | %s",
            event.direction.name,
            event.channel,
            event.summary,
            event.sizeBytes,
            event.hexPreview,
        )
    }

    private fun traceCommandResponse(frame: ByteArray) {
        if (frame.size < 7 || frame.getOrNull(4)?.toInt()?.and(0xFF) != 0x24) return
        val cmd = frame[6].toInt() and 0xFF
        val payload = if (frame.size > 7) frame.copyOfRange(7, frame.size) else byteArrayOf()
        traceInternal(
            channel = "cmd",
            summary = "CMD_RESPONSE ${commandName(cmd)} payload=${payload.hexPreview(12)}",
        )
        appendProtocolContext("cmd-response:${commandName(cmd)} payload=${payload.hexPreview(12)}")
    }

    private fun decodeCommandResponseRecord(frame: ByteArray): WhoopRecord? {
        if (frame.size < 8 || frame.getOrNull(4)?.toInt()?.and(0xFF) != 0x24) return null
        val cmd = frame[6].toInt() and 0xFF
        val payload = frame.copyOfRange(7, frame.size)
        return when (cmd) {
            WhoopConstants.CMD_GET_HELLO_HARVARD -> decodeHelloHarvardBattery(payload)
            WhoopConstants.CMD_GET_BATTERY_LEVEL -> decodeBatteryLevel(payload)
            else -> null
        }
    }

    private fun decodeHelloHarvardBattery(payload: ByteArray): WhoopRecord.Battery? {
        val variants = listOf(
            Triple(1, 6, "documented"),
            Triple(3, 8, "observed"),
        )

        for ((batteryOffset, rtcOffset, source) in variants) {
            if (payload.size < batteryOffset + 4) continue

            val percent = (payload.getUInt32LE(batteryOffset).toFloat() / 10f)
            if (percent !in 0f..100f) continue

            val timestamp = payload.getPlausibleInstantOrNow(rtcOffset)
            traceInternal(
                channel = "cmd",
                summary = "Parsed battery ${"%.1f".format(percent)}% from GET_HELLO_HARVARD ($source layout)",
            )
            return WhoopRecord.Battery(timestamp, percent, "cmd:get_hello_harvard")
        }

        return null
    }

    private fun decodeBatteryLevel(payload: ByteArray): WhoopRecord.Battery? {
        val candidateOffsets = listOf(0, 2)
        for (offset in candidateOffsets) {
            if (payload.size < offset + 4) continue
            val percent = (payload.getUInt32LE(offset).toFloat() / 10f)
            if (percent in 0f..100f) {
                traceInternal(
                    channel = "cmd",
                    summary = "Parsed battery ${"%.1f".format(percent)}% from GET_BATTERY_LEVEL",
                )
                return WhoopRecord.Battery(Instant.now(), percent, "cmd:get_battery_level")
            }
        }
        return null
    }

    private fun traceEvent(frame: ByteArray) {
        if (frame.size < 8 || frame.getOrNull(4)?.toInt()?.and(0xFF) != 0x30) return
        val eventType = eventType(frame) ?: return
        val summary = decodedEventTypeName(eventType)?.let { "EVENT $it" }
            ?: observedUndecodedEventTypeName(eventType)?.let { "EVENT $it (observed undecoded)" }
            ?: run {
                traceInternal(channel = "event", summary = "Unknown WHOOP event type=$eventType size=${frame.size}")
                return
            }
        traceInternal(channel = "event", summary = summary)
    }

    private fun auditUnknownEvent(frame: ByteArray) {
        val eventType = eventType(frame)
        val observation = WhoopUnknownObservation(
            category = UnknownObservationCategory.EVENT,
            packetType = frame.getOrNull(4)?.toInt()?.and(0xFF),
            packetTypeName = packetTypeName(frame),
            identifier = eventType,
            identifierLabel = eventType?.let { "eventType=$it" } ?: "eventType=unknown",
            frameSizeBytes = frame.size,
            hexPreview = frame.hexPreview(40),
            note = "Unknown WHOOP event emitted by strap. ${precedingProtocolContextNote()}",
        )
        _unknownObservations.tryEmit(observation)
    }

    private fun auditObservedUndecodedEvent(frame: ByteArray) {
        val eventType = eventType(frame)
        val observation = WhoopUnknownObservation(
            category = UnknownObservationCategory.OBSERVED_UNDECODED_EVENT,
            packetType = frame.getOrNull(4)?.toInt()?.and(0xFF),
            packetTypeName = packetTypeName(frame),
            identifier = eventType,
            identifierLabel = eventType?.let {
                "eventType=$it (${observedUndecodedEventTypeName(it)})"
            } ?: "eventType=unknown",
            frameSizeBytes = frame.size,
            hexPreview = frame.hexPreview(40),
            note = "Observed recurring WHOOP event with undecoded semantics. ${precedingProtocolContextNote()}",
        )
        _unknownObservations.tryEmit(observation)
    }

    private fun auditUnknownDataFrame(frame: ByteArray) {
        val recordType = frame.getOrNull(5)?.toInt()?.and(0xFF)
        val observation = WhoopUnknownObservation(
            category = UnknownObservationCategory.DATA_FRAME,
            packetType = frame.getOrNull(4)?.toInt()?.and(0xFF),
            packetTypeName = packetTypeName(frame),
            identifier = recordType,
            identifierLabel = recordTypeLabel(frame),
            frameSizeBytes = frame.size,
            hexPreview = frame.hexPreview(40),
            note = "Undecoded WHOOP data frame. ${precedingProtocolContextNote()}",
        )
        _unknownObservations.tryEmit(observation)
    }

    private fun packetTypeName(frame: ByteArray): String =
        when (frame.getOrNull(4)?.toInt()?.and(0xFF)) {
            0x24 -> "cmd-response"
            0x28 -> "realtime-data"
            0x2B -> "raw-realtime"
            0x2F -> "historical-data"
            0x30 -> "event"
            0x31 -> "metadata"
            0x32 -> "fw-log"
            else -> "unknown"
        }

    private fun commandSummary(bytes: ByteArray): String {
        if (bytes.size < 7) return "short packet"
        val cmd = bytes[6].toInt() and 0xFF
        val toggleState = commandToggleState(cmd, bytes)
        return when (cmd) {
            WhoopConstants.CMD_ENABLE_HR -> "TOGGLE_REALTIME_HR$toggleState"
            WhoopConstants.CMD_SET_CLOCK -> "SET_CLOCK"
            WhoopConstants.CMD_ENABLE_IMU -> "SEND_R10_R11_REALTIME$toggleState"
            WhoopConstants.CMD_ENABLE_PPG_1 -> "TOGGLE_PERSISTENT_R21$toggleState"
            WhoopConstants.CMD_ENABLE_PPG_2 -> "TOGGLE_OPTICAL_MODE$toggleState"
            WhoopConstants.CMD_RUN_HAPTICS_PATTERN -> "RUN_HAPTICS_PATTERN"
            WhoopConstants.CMD_STOP_HAPTICS -> "STOP_HAPTICS"
            0x16 -> "SEND_HISTORICAL_DATA"
            0x17 -> "HISTORICAL_DATA_RESULT_ACK"
            0x22 -> "GET_DATA_RANGE"
            0x23 -> "GET_HELLO_HARVARD"
            0x43 -> "GET_ALARM_TIME"
            0x4C -> "GET_ADVERTISING_NAME"
            else -> "cmd=0x${cmd.toHexByte()}"
        }
    }

    private fun commandToggleState(cmd: Int, bytes: ByteArray): String {
        if (cmd != WhoopConstants.CMD_ENABLE_HR &&
            cmd != WhoopConstants.CMD_ENABLE_IMU &&
            cmd != WhoopConstants.CMD_ENABLE_PPG_1 &&
            cmd != WhoopConstants.CMD_ENABLE_PPG_2
        ) {
            return ""
        }
        return when (bytes.getOrNull(7)?.toInt()?.and(0xFF)) {
            0x00 -> " OFF"
            0x01 -> " ON"
            else -> ""
        }
    }

    private fun commandName(cmd: Int): String =
        when (cmd) {
            WhoopConstants.CMD_ENABLE_HR -> "TOGGLE_REALTIME_HR"
            WhoopConstants.CMD_SET_CLOCK -> "SET_CLOCK"
            WhoopConstants.CMD_ENABLE_IMU -> "SEND_R10_R11_REALTIME"
            WhoopConstants.CMD_ENABLE_PPG_1 -> "TOGGLE_PERSISTENT_R21"
            WhoopConstants.CMD_ENABLE_PPG_2 -> "TOGGLE_OPTICAL_MODE"
            WhoopConstants.CMD_RUN_HAPTICS_PATTERN -> "RUN_HAPTICS_PATTERN"
            WhoopConstants.CMD_STOP_HAPTICS -> "STOP_HAPTICS"
            0x16 -> "SEND_HISTORICAL_DATA"
            0x17 -> "HISTORICAL_DATA_RESULT_ACK"
            0x22 -> "GET_DATA_RANGE"
            0x23 -> "GET_HELLO_HARVARD"
            0x43 -> "GET_ALARM_TIME"
            0x4C -> "GET_ADVERTISING_NAME"
            else -> "cmd=0x${cmd.toHexByte()}"
        }

    private fun recordTypeLabel(frame: ByteArray): String {
        val recordType = frame.getOrNull(5)?.toInt()?.and(0xFF) ?: return "recordType=unknown"
        val family = when (recordType) {
            WhoopConstants.RECORD_TYPE_REALTIME_HR_SUMMARY -> "RT2"
            WhoopConstants.RECORD_TYPE_R7 -> "R7"
            WhoopConstants.RECORD_TYPE_R9 -> "R9"
            WhoopConstants.RECORD_TYPE_IMU -> "R10"
            WhoopConstants.RECORD_TYPE_COMPANION_IMU -> "R11"
            WhoopConstants.RECORD_TYPE_R12 -> "R12"
            WhoopConstants.RECORD_TYPE_R18 -> "R18"
            WhoopConstants.RECORD_TYPE_R20 -> "R20"
            WhoopConstants.RECORD_TYPE_PPG -> "R21"
            WhoopConstants.RECORD_TYPE_R24 -> "R24"
            else -> "unknown"
        }
        return "recordType=$recordType ($family / 0x${recordType.toHexByte()})"
    }

    private fun isKnownSecondaryRecordType(recordType: Int): Boolean =
        recordType == WhoopConstants.RECORD_TYPE_R7 ||
            recordType == WhoopConstants.RECORD_TYPE_R9 ||
            recordType == WhoopConstants.RECORD_TYPE_R12 ||
            recordType == WhoopConstants.RECORD_TYPE_R18 ||
            recordType == WhoopConstants.RECORD_TYPE_R20 ||
            recordType == WhoopConstants.RECORD_TYPE_R24

    private fun decodedEventTypeName(eventType: Int?): String? =
        when (eventType) {
            WhoopConstants.EVENT_BATTERY -> "BATTERY"
            WhoopConstants.EVENT_WRIST_ON -> "WRIST_ON"
            WhoopConstants.EVENT_WRIST_OFF -> "WRIST_OFF"
            WhoopConstants.EVENT_DOUBLE_TAP -> "DOUBLE_TAP"
            WhoopConstants.EVENT_SET_RTC -> "SET_RTC"
            WhoopConstants.EVENT_TEMP -> "TEMP"
            WhoopConstants.EVENT_CAPTOUCH_AUTOTHRESHOLD_ACTION -> "CAPTOUCH_AUTOTHRESHOLD_ACTION"
            WhoopConstants.EVENT_BLE_REALTIME_HR_ON -> "BLE_REALTIME_HR_ON"
            WhoopConstants.EVENT_BLE_REALTIME_HR_OFF -> "BLE_REALTIME_HR_OFF"
            WhoopConstants.EVENT_BLE_SYSTEM_INITIALIZED -> "BLE_SYSTEM_INITIALIZED"
            WhoopConstants.EVENT_HAPTICS_FIRED -> "HAPTICS_FIRED"
            WhoopConstants.EVENT_EXTENDED_BATTERY_INFORMATION -> "EXTENDED_BATTERY_INFORMATION"
            WhoopConstants.EVENT_HAPTICS_TERMINATED -> "HAPTICS_TERMINATED"
            else -> null
        }

    private fun observedUndecodedEventTypeName(eventType: Int?): String? =
        when (eventType) {
            WhoopConstants.EVENT_UNDECODED_1 -> "UNDECODED_1"
            WhoopConstants.EVENT_UNDECODED_7 -> "UNDECODED_7"
            WhoopConstants.EVENT_UNDECODED_11 -> "UNDECODED_11"
            WhoopConstants.EVENT_UNDECODED_12 -> "UNDECODED_12"
            WhoopConstants.EVENT_UNDECODED_21 -> "UNDECODED_21"
            WhoopConstants.EVENT_UNDECODED_24 -> "UNDECODED_24"
            WhoopConstants.EVENT_UNDECODED_29 -> "UNDECODED_29"
            WhoopConstants.EVENT_UNDECODED_36 -> "UNDECODED_36"
            WhoopConstants.EVENT_UNDECODED_44 -> "UNDECODED_44"
            else -> null
        }

    private fun eventType(frame: ByteArray): Int? =
        if (frame.size >= 8 && frame.getOrNull(4)?.toInt()?.and(0xFF) == 0x30) {
            ((frame[7].toInt() and 0xFF) shl 8) or (frame[6].toInt() and 0xFF)
        } else {
            null
        }

    private fun eventContextSummary(frame: ByteArray): String {
        val type = eventType(frame)
        val name = decodedEventTypeName(type) ?: observedUndecodedEventTypeName(type) ?: "UNKNOWN"
        val tsSeconds = if (frame.size >= 12) frame.getUInt32LE(8) else null
        val subsecond = if (frame.size >= 14) {
            (frame[12].toInt() and 0xFF) or ((frame[13].toInt() and 0xFF) shl 8)
        } else {
            null
        }
        val payloadOffset = 4 + WhoopConstants.EventHeader.PAYLOAD_START
        val payloadEnd = frame.size - 4
        val payload = if (payloadEnd > payloadOffset) frame.copyOfRange(payloadOffset, payloadEnd) else byteArrayOf()
        return "event:type=$type/$name ts=$tsSeconds sub=$subsecond size=${frame.size} payload=${payload.hexPreview(12)}"
    }

    private fun ByteArray.hexPreview(maxBytes: Int = 24): String =
        take(maxBytes).joinToString(separator = " ") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private fun ByteArray.getUInt32LE(offset: Int): Long =
        (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)

    private fun ByteArray.getPlausibleInstantOrNow(offset: Int): Instant {
        if (size < offset + 4) return Instant.now()
        val seconds = getUInt32LE(offset)
        return if (seconds in 1_577_836_800L..4_102_444_800L) {
            Instant.ofEpochSecond(seconds)
        } else {
            Instant.now()
        }
    }

    private fun Int.toHexByte(): String = "%02x".format(this and 0xFF)

    private fun buildBatchAckPacket(counter: Int, batchNumber: ByteArray): ByteArray {
        val inner = byteArrayOf(
            WhoopConstants.CMD_TYPE_COMMAND.toByte(),
            counter.toByte(),
            0x17,
            0x01,
        ) + batchNumber.copyOf(4) + byteArrayOf(0x00, 0x00, 0x00, 0x00)

        val frameLength = inner.size + 4
        val lenLo  = (frameLength and 0xFF).toByte()
        val lenHi  = ((frameLength shr 8) and 0xFF).toByte()
        val hdrCrc = CrcValidator.crc8(byteArrayOf(lenLo, lenHi))
        val crc32  = CrcValidator.crc32(inner)

        return byteArrayOf(WhoopConstants.SOF, lenLo, lenHi, hdrCrc) +
            inner +
            byteArrayOf(
                (crc32 and 0xFF).toByte(),
                ((crc32 shr 8) and 0xFF).toByte(),
                ((crc32 shr 16) and 0xFF).toByte(),
                ((crc32 ushr 24) and 0xFF).toByte(),
            )
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

    companion object {
        private const val DEFAULT_HARVARD_HAPTIC_PATTERN_ID = 2
        private const val RECENT_PROTOCOL_CONTEXT_LIMIT = 8
        private const val MAX_PROTOCOL_CONTEXT_ENTRY_CHARS = 180
    }
}
