package com.sploot.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.work.WorkManager
import com.sploot.app.MainActivity
import com.sploot.app.R
import com.sploot.app.settings.AppSettingsRepository
import com.sploot.app.sync.WhoopSyncCoordinator
import com.sploot.app.worker.ActivityProcessingWorker
import com.sploot.app.worker.SleepProcessingWorker
import com.sploot.data.repository.RecordingRepository
import com.sploot.data.repository.UnknownObservationRecordResult
import com.sploot.data.repository.WhoopUnknownObservationRepository
import com.sploot.whoopble.gatt.ConnectionState
import com.sploot.whoopble.gatt.WhoopSessionMode
import com.sploot.whoopble.gatt.WhoopStreamConfig
import com.sploot.whoopble.gatt.WhoopGattManager
import com.sploot.whoopble.model.UnknownObservationCategory
import com.sploot.whoopble.model.WhoopUnknownObservation
import com.sploot.whoopble.model.WhoopRecord
import com.sploot.whoopble.protocol.WhoopConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Foreground service that:
 *   1. Scans for a WHOOP device
 *   2. Connects via [WhoopGattManager]
 *   3. Streams [WhoopRecord] events to [RecordingRepository] with exponential backoff reconnect
 *
 * Lifecycle: START_STICKY so Android restarts it if killed mid-recording.
 */
@AndroidEntryPoint
class WhoopRecordingService : Service() {

    @Inject lateinit var gattManager: WhoopGattManager
    @Inject lateinit var recordingRepo: RecordingRepository
    @Inject lateinit var settingsRepository: AppSettingsRepository
    @Inject lateinit var unknownObservationRepository: WhoopUnknownObservationRepository
    @Inject lateinit var whoopSyncCoordinator: WhoopSyncCoordinator
    @Inject lateinit var whoopRuntimeCoordinator: WhoopRuntimeCoordinator

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentSessionId = -1L
    private var recordCollectorJob: Job? = null
    private var lastPersistedImuSecond: Long? = null
    private var lastPersistedPpgSecond: Long? = null
    private var lastPersistedHrSecond: Long? = null
    private var batterySaverActive = false
    private var historicalImuPersisted = 0
    private var historicalPpgPersisted = 0
    private var historicalHrPersisted = 0
    private var historicalEventPersisted = 0
    private var historicalSkippedByCutoff = 0
    private var currentMode = WhoopSessionMode.LIVE_RECORDING
    private var startupMode: WhoopSessionMode? = null
    private var syncCutoffSeconds: Long? = null
    private var pendingHistoricalSyncAfterStop = false
    private var pendingLiveRestartAfterStop = false
    private var unknownCollectorJob: Job? = null
    private var lastUnknownPromptAtMs = 0L

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) return

            val wasActive = batterySaverActive
            batterySaverActive = isSystemBatterySaverOn()
            if (wasActive == batterySaverActive) return

            serviceScope.launch {
                applyBatterySaverProfileIfRecording()
            }
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createUnknownPromptChannel()
        batterySaverActive = isSystemBatterySaverOn()
        registerPowerSaveReceiver()
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStartRecording()
            ACTION_SYNC_HISTORY -> handleStartHistoricalSync(
                autoStartLiveAfter = intent.getBooleanExtra(EXTRA_AUTO_START_LIVE_AFTER, false)
            )
            ACTION_STOP  -> {
                pendingHistoricalSyncAfterStop = false
                pendingLiveRestartAfterStop = false
                stopRecording()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        closeCurrentSessionIfNeeded(reason = "service destroyed")
        serviceScope.cancel()
        unregisterReceiver(powerSaveReceiver)
        gattManager.disconnect()
        whoopSyncCoordinator.markSyncFinished()
        whoopRuntimeCoordinator.setState(WhoopRuntimeState.IDLE)
        super.onDestroy()
    }

    // ── Recording control ─────────────────────────────────────────────────────

    private fun handleStartRecording() {
        if (startupMode == WhoopSessionMode.LIVE_RECORDING) return
        if (currentSessionId >= 0L) {
            if (currentMode == WhoopSessionMode.LIVE_RECORDING) return
            pendingHistoricalSyncAfterStop = false
            pendingLiveRestartAfterStop = true
            whoopRuntimeCoordinator.setState(WhoopRuntimeState.SWITCHING_TO_LIVE)
            stopRecording()
            return
        }

        pendingHistoricalSyncAfterStop = false
        pendingLiveRestartAfterStop = false
        startRecording()
    }

    private fun handleStartHistoricalSync(autoStartLiveAfter: Boolean) {
        if (startupMode == WhoopSessionMode.HISTORICAL_SYNC) return
        whoopSyncCoordinator.markSyncStarted()
        val shouldResumeLiveAfterSync = autoStartLiveAfter
        if (currentSessionId >= 0L) {
            if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) return
            pendingHistoricalSyncAfterStop = true
            pendingLiveRestartAfterStop = shouldResumeLiveAfterSync
            whoopRuntimeCoordinator.setState(WhoopRuntimeState.SWITCHING_TO_HISTORY)
            stopRecording()
            return
        }

        pendingHistoricalSyncAfterStop = false
        startHistoricalSync(autoStartLiveAfter = shouldResumeLiveAfterSync)
    }

    private fun startRecording() {
        currentMode = WhoopSessionMode.LIVE_RECORDING
        startupMode = WhoopSessionMode.LIVE_RECORDING
        syncCutoffSeconds = null
        whoopRuntimeCoordinator.setState(WhoopRuntimeState.STARTING_LIVE)
        startForeground(NOTIF_ID, buildNotification("Searching for WHOOP…"))
        serviceScope.launch {
            currentSessionId = recordingRepo.startSession()
            startupMode = null
            lastPersistedImuSecond = null
            lastPersistedPpgSecond = null
            lastPersistedHrSecond = null
            Timber.i("Recording session started: $currentSessionId")
            try {
                recordCollectorJob?.cancel()
                recordCollectorJob = launch { collectRecords() }
                unknownCollectorJob?.cancel()
                unknownCollectorJob = launch { collectUnknownObservations() }
                connectWithRetry(currentMode)
                whoopRuntimeCoordinator.setState(WhoopRuntimeState.LIVE)
            } catch (e: Exception) {
                startupMode = null
                Timber.e(e, "Recording session failed")
                cleanupFailedSession(reason = "recording failed", wasHistoricalSync = false)
                whoopRuntimeCoordinator.setState(WhoopRuntimeState.ERROR)
                stopSelf()
            }
        }
    }

    private fun startHistoricalSync(autoStartLiveAfter: Boolean = false) {
        currentMode = WhoopSessionMode.HISTORICAL_SYNC
        startupMode = WhoopSessionMode.HISTORICAL_SYNC
        syncCutoffSeconds = null
        resetHistoricalCounters()
        pendingLiveRestartAfterStop = autoStartLiveAfter
        whoopRuntimeCoordinator.setState(WhoopRuntimeState.STARTING_HISTORY)
        startForeground(NOTIF_ID, buildNotification("Syncing WHOOP history…"))
        serviceScope.launch {
            syncCutoffSeconds = recordingRepo.getLatestStoredTimestamp()
            updateNotification(
                if (syncCutoffSeconds == null) {
                    "No WHOOP history stored yet - pulling full strap history"
                } else {
                    "Syncing WHOOP history since $syncCutoffSeconds"
                }
            )
            currentSessionId = recordingRepo.startSession()
            startupMode = null
            lastPersistedImuSecond = null
            lastPersistedPpgSecond = null
            lastPersistedHrSecond = null
            try {
                recordCollectorJob?.cancel()
                recordCollectorJob = launch { collectRecords() }
                unknownCollectorJob?.cancel()
                unknownCollectorJob = launch { collectUnknownObservations() }
                connectWithRetry(currentMode) {
                    whoopRuntimeCoordinator.setState(WhoopRuntimeState.HISTORY)
                    gattManager.awaitHistoricalSyncCompletion()
                }
                val repairedHrSamples = recordingRepo.repairMissingHrSamplesFromRawImu()
                if (repairedHrSamples > 0) {
                    Timber.i("Repaired $repairedHrSamples missing WHOOP HR samples from raw IMU")
                }
                delay(1_000L)
                stopRecording()
            } catch (e: Exception) {
                startupMode = null
                if (hasHistoricalProgress()) {
                    Timber.w(e, "Historical sync ended after receiving data; treating as partial success")
                    updateNotification("WHOOP history received - finishing sync")
                    cleanupFailedSession(reason = "historical sync ended after receiving data", wasHistoricalSync = true)
                    whoopRuntimeCoordinator.setState(WhoopRuntimeState.IDLE)
                } else {
                    Timber.e(e, "Historical sync failed")
                    cleanupFailedSession(reason = "historical sync failed", wasHistoricalSync = true)
                    whoopRuntimeCoordinator.setState(WhoopRuntimeState.ERROR)
                }
                stopSelf()
            }
        }
    }

    private fun stopRecording() {
        serviceScope.launch {
            val restartHistorical = pendingHistoricalSyncAfterStop
            val restartLive = pendingLiveRestartAfterStop
            pendingHistoricalSyncAfterStop = false
            pendingLiveRestartAfterStop = false
            startupMode = null
            val wasHistoricalSync = currentMode == WhoopSessionMode.HISTORICAL_SYNC
            runCatching { gattManager.disableActiveStreams() }
                .onFailure { Timber.w(it, "Failed to disable WHOOP streams before disconnect") }
            gattManager.disconnect()
            // Give the collectors a moment to drain any records already buffered
            // from the GATT callback before we cancel them, so the last batch of
            // historical/live data isn't dropped on the floor at shutdown.
            delay(500L)
            recordCollectorJob?.cancelAndJoin()
            recordCollectorJob = null
            unknownCollectorJob?.cancelAndJoin()
            unknownCollectorJob = null
            if (currentSessionId >= 0) {
                val sessionId = currentSessionId
                recordingRepo.endSession(sessionId)
                val hasAnyData = recordingRepo.sessionHasAnyData(sessionId)
                if (!hasAnyData) {
                    recordingRepo.deleteSession(sessionId)
                    Timber.i("Dropped empty WHOOP session: $sessionId")
                } else {
                    Timber.i("Recording session ended: $sessionId")
                    enqueueProcessingWorkers(sessionId)
                }
            }
            if (wasHistoricalSync) {
                logHistoricalSummary(sessionId = currentSessionId)
                whoopSyncCoordinator.markSyncFinished()
            }
            currentSessionId = -1L
            syncCutoffSeconds = null
            when {
                restartHistorical -> {
                    whoopRuntimeCoordinator.setState(WhoopRuntimeState.SWITCHING_TO_HISTORY)
                    startHistoricalSync(autoStartLiveAfter = restartLive)
                }
                restartLive -> {
                    whoopRuntimeCoordinator.setState(WhoopRuntimeState.SWITCHING_TO_LIVE)
                    startRecording()
                }
                else -> {
                    whoopRuntimeCoordinator.setState(WhoopRuntimeState.STOPPING)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    whoopRuntimeCoordinator.setState(WhoopRuntimeState.IDLE)
                    stopSelf()
                }
            }
        }
    }

    // ── BLE connection with exponential backoff ───────────────────────────────

    private suspend fun cleanupFailedSession(
        reason: String,
        wasHistoricalSync: Boolean,
    ) {
        pendingHistoricalSyncAfterStop = false
        pendingLiveRestartAfterStop = false
        startupMode = null
        recordCollectorJob?.cancel()
        recordCollectorJob = null
        unknownCollectorJob?.cancel()
        unknownCollectorJob = null
        runCatching { gattManager.disableActiveStreams() }
            .onFailure { Timber.w(it, "Failed to disable WHOOP streams after $reason") }
        gattManager.disconnect()

        val sessionId = currentSessionId
        if (sessionId >= 0L) {
            recordingRepo.endSession(sessionId)
            val hasAnyData = recordingRepo.sessionHasAnyData(sessionId)
            if (!hasAnyData) {
                recordingRepo.deleteSession(sessionId)
                Timber.i("Dropped empty WHOOP session $sessionId after $reason")
            } else {
                Timber.i("Closed WHOOP session $sessionId after $reason")
                enqueueProcessingWorkers(sessionId)
            }
            if (wasHistoricalSync) {
                logHistoricalSummary(sessionId)
            }
        }

        currentSessionId = -1L
        syncCutoffSeconds = null
        if (wasHistoricalSync) {
            whoopSyncCoordinator.markSyncFinished()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private suspend fun connectWithRetry(
        mode: WhoopSessionMode,
        afterConnected: suspend () -> Unit = {},
    ) {
        var backoffMs = 2_000L

        repeat(6) { attempt ->
            try {
                val device = resolveWhoopDevice() ?: throw IOException("No WHOOP device found")
                updateNotification("Connecting to ${device.name}…")
                val settings = effectiveSettings()
                gattManager.connect(
                    device = device,
                    streamConfig = buildStreamConfig(settings),
                    sessionMode = mode,
                )
                updateNotification(
                    if (mode == WhoopSessionMode.HISTORICAL_SYNC) {
                        "WHOOP connected — syncing history"
                    } else {
                        "WHOOP connected — recording"
                    }
                )
                afterConnected()
                return
            } catch (e: Exception) {
                Timber.w(e, "Connect attempt $attempt failed, retry in ${backoffMs}ms")
                gattManager.disconnect()
                if (attempt < 5) {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                }
            }
        }
        throw IOException("Failed to connect to WHOOP after 6 attempts")
    }

    private suspend fun resolveWhoopDevice() =
        preferredWhoopDevice() ?: scanForWhoop()

    private fun preferredWhoopDevice() =
        settingsRepository.current().preferredWhoopDeviceAddress?.let { address ->
            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            runCatching { btManager.adapter.getRemoteDevice(address) }.getOrNull()
        }

    private suspend fun scanForWhoop() = withTimeoutOrNull(20_000L) {
        suspendCancellableCoroutine { cont ->
            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val scanner   = btManager.adapter.bluetoothLeScanner

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val hasPermission = checkPermission(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Process.myPid(), Process.myUid()
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasPermission) return
                    @Suppress("MissingPermission")
                    val name = result.device.name ?: return

                    if (name.startsWith(WhoopConstants.DEVICE_NAME_PREFIX)) {
                        scanner.stopScan(this)
                        if (cont.isActive) cont.resume(result.device)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    if (cont.isActive) cont.cancel(IOException("BLE scan failed: $errorCode"))
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            @Suppress("MissingPermission")
            scanner.startScan(null, settings, callback)
            cont.invokeOnCancellation { @Suppress("MissingPermission") scanner.stopScan(callback) }
        }
    }

    // ── Record collection & persistence ──────────────────────────────────────

    private suspend fun collectRecords() {
        gattManager.records.collect { record ->
            val settings = effectiveSettings()
            when (record) {
                is WhoopRecord.Imu -> {
                    if (!settings.enableWhoopImuStream) return@collect
                    val tsSeconds = record.timestamp.epochSecond
                    if (shouldSkipExistingHistoricalImu(tsSeconds)) return@collect
                    if (shouldPersist(tsSeconds, lastPersistedImuSecond, settings.effectiveImuIntervalSeconds())) {
                        recordingRepo.insertImu(
                            sessionId = currentSessionId,
                            tsSeconds = tsSeconds,
                            hrBpm     = record.hrBpm,
                            accelX    = record.accelX.toLeBytes(),
                            accelY    = record.accelY.toLeBytes(),
                            accelZ    = record.accelZ.toLeBytes(),
                            gyroX     = record.gyroX.toLeBytes(),
                            gyroY     = record.gyroY.toLeBytes(),
                            gyroZ     = record.gyroZ.toLeBytes(),
                        )
                        lastPersistedImuSecond = tsSeconds
                        lastPersistedHrSecond = tsSeconds
                        if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) {
                            historicalImuPersisted++
                            historicalHrPersisted++
                        }
                    } else if (
                        settings.enableWhoopHrStream &&
                        shouldPersist(tsSeconds, lastPersistedHrSecond, settings.effectiveHrIntervalSeconds())
                    ) {
                        recordingRepo.insertHrSample(
                            sessionId = currentSessionId,
                            tsSeconds = tsSeconds,
                            hrBpm = record.hrBpm,
                        )
                        lastPersistedHrSecond = tsSeconds
                        if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) {
                            historicalHrPersisted++
                        }
                    }
                }

                is WhoopRecord.Ppg -> {
                    if (!settings.enableWhoopPpgStream) return@collect
                    val tsSeconds = record.timestamp.epochSecond
                    if (shouldSkipExistingHistoricalPpg(tsSeconds)) return@collect
                    if (shouldPersist(tsSeconds, lastPersistedPpgSecond, settings.effectivePpgIntervalSeconds())) {
                        recordingRepo.insertPpg(
                            sessionId = currentSessionId,
                            tsSeconds = tsSeconds,
                            ledDrive  = record.ledDrive,
                            channelA  = record.channelA.toUInt16Bytes(),
                            channelB  = record.channelB.toUInt16Bytes(),
                            channelC  = record.channelC.toUInt16Bytes(),
                            channelD  = record.channelD.toUInt16Bytes(),
                            channelE  = record.channelE.toUInt16Bytes(),
                            channelF  = record.channelF.toUInt16Bytes(),
                        )
                        lastPersistedPpgSecond = tsSeconds
                        if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) {
                            historicalPpgPersisted++
                        }
                    }
                }

                is WhoopRecord.HeartRate -> {
                    if (!settings.enableWhoopHrStream) return@collect
                    val tsSeconds = record.timestamp.epochSecond
                    if (shouldSkipExistingHistoricalHr(tsSeconds)) return@collect
                    if (shouldPersist(tsSeconds, lastPersistedHrSecond, settings.effectiveHrIntervalSeconds())) {
                        recordingRepo.insertHrSample(
                            sessionId = currentSessionId,
                            tsSeconds = tsSeconds,
                            hrBpm = record.hrBpm,
                        )
                        lastPersistedHrSecond = tsSeconds
                        if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) {
                            historicalHrPersisted++
                        }
                    }
                }

                is WhoopRecord.Battery ->
                    if (settings.captureWhoopEvents) {
                        val tsSeconds = record.timestamp.epochSecond
                        if (!shouldSkipExistingHistoricalEvent(tsSeconds, "BATTERY")) {
                            recordingRepo.insertEvent(currentSessionId, tsSeconds, "BATTERY", record.percent)
                            if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) historicalEventPersisted++
                        }
                    }

                is WhoopRecord.Temperature ->
                    if (settings.captureWhoopEvents) {
                        val tsSeconds = record.timestamp.epochSecond
                        if (!shouldSkipExistingHistoricalEvent(tsSeconds, "TEMP")) {
                            recordingRepo.insertEvent(currentSessionId, tsSeconds, "TEMP", record.celsius)
                            if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) historicalEventPersisted++
                        }
                    }

                is WhoopRecord.WristOn ->
                    if (settings.captureWhoopEvents) {
                        val tsSeconds = record.timestamp.epochSecond
                        if (!shouldSkipExistingHistoricalEvent(tsSeconds, "WRIST_ON")) {
                            recordingRepo.insertEvent(currentSessionId, tsSeconds, "WRIST_ON", null)
                            if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) historicalEventPersisted++
                        }
                    }

                is WhoopRecord.WristOff ->
                    if (settings.captureWhoopEvents) {
                        val tsSeconds = record.timestamp.epochSecond
                        if (!shouldSkipExistingHistoricalEvent(tsSeconds, "WRIST_OFF")) {
                            recordingRepo.insertEvent(currentSessionId, tsSeconds, "WRIST_OFF", null)
                            if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) historicalEventPersisted++
                        }
                    }

                is WhoopRecord.DoubleTap ->
                    if (settings.captureWhoopEvents) {
                        val tsSeconds = record.timestamp.epochSecond
                        if (!shouldSkipExistingHistoricalEvent(tsSeconds, "DOUBLE_TAP")) {
                            recordingRepo.insertEvent(currentSessionId, tsSeconds, "DOUBLE_TAP", null)
                            if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) historicalEventPersisted++
                        }
                    }

                is WhoopRecord.CapTouchAutoThreshold ->
                    if (settings.captureWhoopEvents) {
                        val tsSeconds = record.timestamp.epochSecond
                        if (!shouldSkipExistingHistoricalEvent(tsSeconds, "CAPTOUCH_AUTOTHRESHOLD_ACTION")) {
                            recordingRepo.insertEvent(
                                currentSessionId,
                                tsSeconds,
                                "CAPTOUCH_AUTOTHRESHOLD_ACTION",
                                null,
                            )
                            if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) historicalEventPersisted++
                        }
                    }

                is WhoopRecord.HapticsFired ->
                    if (settings.captureWhoopEvents) {
                        val tsSeconds = record.timestamp.epochSecond
                        if (!shouldSkipExistingHistoricalEvent(tsSeconds, "HAPTICS_FIRED")) {
                            recordingRepo.insertEvent(
                                currentSessionId,
                                tsSeconds,
                                "HAPTICS_FIRED",
                                record.patternId?.toFloat(),
                            )
                            if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) historicalEventPersisted++
                        }
                    }

                is WhoopRecord.HapticsTerminated ->
                    if (settings.captureWhoopEvents) {
                        val tsSeconds = record.timestamp.epochSecond
                        if (!shouldSkipExistingHistoricalEvent(tsSeconds, "HAPTICS_TERMINATED")) {
                            recordingRepo.insertEvent(
                                currentSessionId,
                                tsSeconds,
                                "HAPTICS_TERMINATED",
                                record.reasonCode?.toFloat(),
                            )
                            if (currentMode == WhoopSessionMode.HISTORICAL_SYNC) historicalEventPersisted++
                        }
                    }
            }
        }
    }

    private suspend fun collectUnknownObservations() {
        gattManager.unknownObservations.collect { observation ->
            val result = unknownObservationRepository.recordObservation(
                sessionId = currentSessionId.takeIf { it >= 0 },
                category = observation.category.name,
                packetType = observation.packetType,
                packetTypeName = observation.packetTypeName,
                identifier = observation.identifier,
                identifierLabel = observation.identifierLabel,
                frameSizeBytes = observation.frameSizeBytes,
                hexPreview = observation.hexPreview,
                note = observation.note,
                observedAtSeconds = observation.timestamp.epochSecond,
            )
            maybePromptForUnknownObservation(result, observation)
        }
    }

    private fun maybePromptForUnknownObservation(
        result: UnknownObservationRecordResult,
        observation: WhoopUnknownObservation,
    ) {
        val settings = settingsRepository.current()
        if (!settings.enableWhoopUnknownTagPrompts) return
        if (observation.category == UnknownObservationCategory.OBSERVED_UNDECODED_EVENT) return
        if (!result.isNewSignature) return
        if (result.entity.userAnnotation != null) return
        if (!canPostUnknownTagPrompt()) return

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastUnknownPromptAtMs < UNKNOWN_PROMPT_COOLDOWN_MS) return
        lastUnknownPromptAtMs = nowMs

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val replyInput = RemoteInput.Builder(WhoopUnknownAnnotationReceiver.KEY_TEXT_REPLY)
            .setLabel("What were you doing?")
            .build()
        val replyIntent = Intent(this, WhoopUnknownAnnotationReceiver::class.java).also {
            it.action = WhoopUnknownAnnotationReceiver.ACTION_ANNOTATE_UNKNOWN_WHOOP
            it.putExtra(WhoopUnknownAnnotationReceiver.EXTRA_OBSERVATION_ID, result.entity.id)
            it.putExtra(
                WhoopUnknownAnnotationReceiver.EXTRA_NOTIFICATION_ID,
                WhoopUnknownAnnotationReceiver.UNKNOWN_PROMPT_NOTIFICATION_ID,
            )
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            result.entity.id.toInt(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit,
            "Tag",
            replyPendingIntent,
        )
            .addRemoteInput(replyInput)
            .setAllowGeneratedReplies(false)
            .build()

        val notification = NotificationCompat.Builder(this, UNKNOWN_PROMPT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Unknown WHOOP packet")
            .setContentText(observation.identifierLabel)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${observation.packetTypeName} ${observation.identifierLabel}, ${observation.frameSizeBytes}B\n${observation.hexPreview}"
                )
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    result.entity.id.toInt(),
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .addAction(replyAction)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        notificationManager.notify(
            WhoopUnknownAnnotationReceiver.UNKNOWN_PROMPT_NOTIFICATION_ID,
            notification,
        )
    }

    private fun enqueueProcessingWorkers(sessionId: Long) {
        val settings = effectiveSettings()
        val workManager = WorkManager.getInstance(applicationContext)
        when {
            settings.runSleepProcessing && settings.runActivityProcessing -> {
                workManager.beginWith(SleepProcessingWorker.buildRequest(sessionId))
                    .then(ActivityProcessingWorker.buildRequest(sessionId))
                    .enqueue()
            }
            settings.runSleepProcessing -> {
                workManager.enqueue(SleepProcessingWorker.buildRequest(sessionId))
            }
            settings.runActivityProcessing -> {
                workManager.enqueue(ActivityProcessingWorker.buildRequest(sessionId))
            }
            else -> {
                Timber.i("Processing disabled in settings for session $sessionId")
            }
        }
        Timber.i("Processing configuration applied for session $sessionId")
    }

    private fun shouldPersist(
        tsSeconds: Long,
        lastPersistedSecond: Long?,
        intervalSeconds: Int,
    ): Boolean =
        currentMode == WhoopSessionMode.HISTORICAL_SYNC ||
            lastPersistedSecond == null ||
            (tsSeconds - lastPersistedSecond) >= intervalSeconds

    private suspend fun shouldSkipExistingHistoricalImu(tsSeconds: Long): Boolean =
        currentMode == WhoopSessionMode.HISTORICAL_SYNC &&
            recordingRepo.hasImuAtTimestamp(tsSeconds).also { exists ->
                if (exists) historicalSkippedByCutoff++
            }

    private suspend fun shouldSkipExistingHistoricalPpg(tsSeconds: Long): Boolean =
        currentMode == WhoopSessionMode.HISTORICAL_SYNC &&
            recordingRepo.hasPpgAtTimestamp(tsSeconds).also { exists ->
                if (exists) historicalSkippedByCutoff++
            }

    private suspend fun shouldSkipExistingHistoricalHr(tsSeconds: Long): Boolean =
        currentMode == WhoopSessionMode.HISTORICAL_SYNC &&
            recordingRepo.hasHrAtTimestamp(tsSeconds).also { exists ->
                if (exists) historicalSkippedByCutoff++
            }

    private suspend fun shouldSkipExistingHistoricalEvent(tsSeconds: Long, eventType: String): Boolean =
        currentMode == WhoopSessionMode.HISTORICAL_SYNC &&
            recordingRepo.hasEventAtTimestamp(tsSeconds, eventType).also { exists ->
                if (exists) historicalSkippedByCutoff++
            }

    private fun resetHistoricalCounters() {
        historicalImuPersisted = 0
        historicalPpgPersisted = 0
        historicalHrPersisted = 0
        historicalEventPersisted = 0
        historicalSkippedByCutoff = 0
    }

    private fun hasHistoricalProgress(): Boolean =
        historicalImuPersisted > 0 ||
            historicalPpgPersisted > 0 ||
            historicalHrPersisted > 0 ||
            historicalEventPersisted > 0 ||
            historicalSkippedByCutoff > 0

    private fun logHistoricalSummary(sessionId: Long) {
        Timber.i(
            "WHOOP historical sync summary session=$sessionId cutoff=$syncCutoffSeconds " +
                "imu=$historicalImuPersisted ppg=$historicalPpgPersisted " +
                "hr=$historicalHrPersisted events=$historicalEventPersisted " +
                "skippedExisting=$historicalSkippedByCutoff"
        )
    }

    private fun closeCurrentSessionIfNeeded(reason: String) {
        val sessionId = currentSessionId
        if (sessionId < 0L) return
        runBlocking {
            recordCollectorJob?.cancelAndJoin()
            unknownCollectorJob?.cancelAndJoin()
            runCatching { gattManager.disableActiveStreams() }
                .onFailure { Timber.w(it, "Failed to disable WHOOP streams during $reason") }
            recordingRepo.endSession(sessionId)
            val hasAnyData = recordingRepo.sessionHasAnyData(sessionId)
            if (!hasAnyData) {
                recordingRepo.deleteSession(sessionId)
                Timber.i("Dropped empty WHOOP session $sessionId during $reason")
            } else {
                Timber.i("Closed WHOOP session $sessionId during $reason")
                enqueueProcessingWorkers(sessionId)
            }
        }
        currentSessionId = -1L
        syncCutoffSeconds = null
    }

    private suspend fun applyBatterySaverProfileIfRecording() {
        if (currentSessionId < 0L || gattManager.state.value == ConnectionState.DISCONNECTED) return

        runCatching {
            gattManager.applyStreamConfig(buildStreamConfig(effectiveSettings()))
        }.onFailure {
            Timber.w(it, "Failed to apply Battery Saver WHOOP stream profile")
        }
    }

    private fun effectiveSettings(): com.sploot.app.settings.AppSettings =
        applyBatterySaverOverrides(settingsRepository.current(), batterySaverActive)

    private fun applyBatterySaverOverrides(
        base: com.sploot.app.settings.AppSettings,
        isBatterySaverActive: Boolean,
    ): com.sploot.app.settings.AppSettings {
        if (!isBatterySaverActive || !base.followSystemBatterySaver) return base

        var updated = base.copy(
            enableWhoopHrStream = base.enableWhoopHrStream && !base.batterySaverDisableWhoopHrStream,
            enableWhoopImuStream = base.enableWhoopImuStream && !base.batterySaverDisableWhoopImuStream,
            enableWhoopPpgStream = base.enableWhoopPpgStream && !base.batterySaverDisableWhoopPpgStream,
        )

        if (base.batterySaverForceGlobalWhoopInterval) {
            updated = updated.copy(
                globalWhoopCaptureIntervalEnabled = true,
                globalWhoopCaptureIntervalSeconds = base.batterySaverGlobalWhoopIntervalSeconds,
            )
        }

        return updated
    }

    private fun buildStreamConfig(settings: com.sploot.app.settings.AppSettings) = WhoopStreamConfig(
        enableHr = settings.enableWhoopHrStream,
        enableImu = settings.enableWhoopImuStream,
        enablePpg = settings.enableWhoopPpgStream,
    )

    private fun isSystemBatterySaverOn(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode

    private fun canPostUnknownTagPrompt(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val interruptionFilter = runCatching { notificationManager.currentInterruptionFilter }
            .getOrElse {
                Timber.w(it, "Unable to read Android interruption filter; suppressing unknown packet prompt")
                return false
            }
        return interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
            interruptionFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun registerPowerSaveReceiver() {
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerSaveReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(powerSaveReceiver, filter)
        }
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_recording),
            NotificationManager.IMPORTANCE_LOW,
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun createUnknownPromptChannel() {
        val channel = NotificationChannel(
            UNKNOWN_PROMPT_CHANNEL_ID,
            "WHOOP packet tags",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        channel.description = "Optional prompts for annotating unknown WHOOP packet signatures."
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String = getString(R.string.notif_recording_text)) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_recording_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
    }

    // ── Serialisation helpers ─────────────────────────────────────────────────

    private fun ShortArray.toLeBytes(): ByteArray {
        val buf = ByteArray(size * 2)
        forEachIndexed { i, v ->
            buf[i * 2]     = (v.toInt() and 0xFF).toByte()
            buf[i * 2 + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
        }
        return buf
    }

    private fun IntArray.toUInt16Bytes(): ByteArray {
        val buf = ByteArray(size * 2)
        forEachIndexed { i, v ->
            buf[i * 2]     = (v and 0xFF).toByte()
            buf[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return buf
    }

    // ── Intent factory ────────────────────────────────────────────────────────

    companion object {
        const val ACTION_START = "com.sploot.app.START_RECORDING"
        const val ACTION_SYNC_HISTORY = "com.sploot.app.SYNC_HISTORY"
        const val ACTION_STOP  = "com.sploot.app.STOP_RECORDING"
        private const val EXTRA_AUTO_START_LIVE_AFTER = "extra_auto_start_live_after"
        private const val CHANNEL_ID = "sploot_recording"
        private const val UNKNOWN_PROMPT_CHANNEL_ID = "sploot_whoop_packet_tags"
        private const val NOTIF_ID   = 1001
        private const val UNKNOWN_PROMPT_COOLDOWN_MS = 5 * 60 * 1000L

        fun startIntent(context: Context) =
            Intent(context, WhoopRecordingService::class.java).also { it.action = ACTION_START }

        fun stopIntent(context: Context) =
            Intent(context, WhoopRecordingService::class.java).also { it.action = ACTION_STOP }

        fun startHistoricalSyncIntent(
            context: Context,
            autoStartLiveAfter: Boolean = false,
        ) =
            Intent(context, WhoopRecordingService::class.java).also {
                it.action = ACTION_SYNC_HISTORY
                it.putExtra(EXTRA_AUTO_START_LIVE_AFTER, autoStartLiveAfter)
            }
    }
}
