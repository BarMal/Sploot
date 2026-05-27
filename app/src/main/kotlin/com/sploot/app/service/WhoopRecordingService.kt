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
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.sploot.app.MainActivity
import com.sploot.app.R
import com.sploot.app.worker.SleepProcessingWorker
import com.sploot.data.repository.RecordingRepository
import com.sploot.whoopble.gatt.ConnectionState
import com.sploot.whoopble.gatt.WhoopGattManager
import com.sploot.whoopble.model.WhoopRecord
import com.sploot.whoopble.protocol.WhoopConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentSessionId = -1L

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP  -> stopRecording()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        gattManager.disconnect()
        super.onDestroy()
    }

    // ── Recording control ─────────────────────────────────────────────────────

    private fun startRecording() {
        startForeground(NOTIF_ID, buildNotification("Searching for WHOOP…"))
        serviceScope.launch {
            currentSessionId = recordingRepo.startSession()
            Timber.i("Recording session started: $currentSessionId")
            try {
                connectWithRetry()
                collectRecords()
            } catch (e: Exception) {
                Timber.e(e, "Recording session failed")
                stopSelf()
            }
        }
    }

    private fun stopRecording() {
        serviceScope.launch {
            gattManager.disconnect()
            if (currentSessionId >= 0) {
                recordingRepo.endSession(currentSessionId)
                Timber.i("Recording session ended: $currentSessionId")
                // Kick off sleep-stage processing in the background
                WorkManager.getInstance(applicationContext)
                    .enqueue(SleepProcessingWorker.buildRequest(currentSessionId))
                Timber.i("SleepProcessingWorker enqueued for session $currentSessionId")
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── BLE connection with exponential backoff ───────────────────────────────

    private suspend fun connectWithRetry() {
        var backoffMs = 2_000L

        repeat(6) { attempt ->
            try {
                val device = scanForWhoop() ?: throw IOException("No WHOOP device found")
                updateNotification("Connecting to ${device.name}…")
                gattManager.connect(device)
                updateNotification("WHOOP connected — recording")
                return
            } catch (e: Exception) {
                Timber.w(e, "Connect attempt $attempt failed, retry in ${backoffMs}ms")
                if (attempt < 5) {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                }
            }
        }
        throw IOException("Failed to connect to WHOOP after 6 attempts")
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
            when (record) {
                is WhoopRecord.Imu ->
                    recordingRepo.insertImu(
                        sessionId = currentSessionId,
                        tsSeconds = record.timestamp.epochSecond,
                        hrBpm     = record.hrBpm,
                        accelX    = record.accelX.toLeBytes(),
                        accelY    = record.accelY.toLeBytes(),
                        accelZ    = record.accelZ.toLeBytes(),
                        gyroX     = record.gyroX.toLeBytes(),
                        gyroY     = record.gyroY.toLeBytes(),
                        gyroZ     = record.gyroZ.toLeBytes(),
                    )

                is WhoopRecord.Ppg ->
                    recordingRepo.insertPpg(
                        sessionId = currentSessionId,
                        tsSeconds = record.timestamp.epochSecond,
                        ledDrive  = record.ledDrive,
                        channelA  = record.channelA.toUInt16Bytes(),
                        channelB  = record.channelB.toUInt16Bytes(),
                        channelC  = record.channelC.toUInt16Bytes(),
                        channelD  = record.channelD.toUInt16Bytes(),
                        channelE  = record.channelE.toUInt16Bytes(),
                        channelF  = record.channelF.toUInt16Bytes(),
                    )

                is WhoopRecord.Battery ->
                    recordingRepo.insertEvent(currentSessionId, record.timestamp.epochSecond, "BATTERY", record.percent)

                is WhoopRecord.Temperature ->
                    recordingRepo.insertEvent(currentSessionId, record.timestamp.epochSecond, "TEMP", record.celsius)

                is WhoopRecord.WristOn ->
                    recordingRepo.insertEvent(currentSessionId, record.timestamp.epochSecond, "WRIST_ON", null)

                is WhoopRecord.WristOff ->
                    recordingRepo.insertEvent(currentSessionId, record.timestamp.epochSecond, "WRIST_OFF", null)
            }
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
        const val ACTION_STOP  = "com.sploot.app.STOP_RECORDING"
        private const val CHANNEL_ID = "sploot_recording"
        private const val NOTIF_ID   = 1001

        fun startIntent(context: Context) =
            Intent(context, WhoopRecordingService::class.java).also { it.action = ACTION_START }

        fun stopIntent(context: Context) =
            Intent(context, WhoopRecordingService::class.java).also { it.action = ACTION_STOP }
    }
}
