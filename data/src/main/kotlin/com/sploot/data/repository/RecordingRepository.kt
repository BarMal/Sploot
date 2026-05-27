package com.sploot.data.repository

import com.sploot.data.dao.HrSampleDao
import com.sploot.data.dao.RawImuDao
import com.sploot.data.dao.RawPpgDao
import com.sploot.data.dao.RecordingSessionDao
import com.sploot.data.dao.WhoopEventDao
import com.sploot.data.entity.HrSampleEntity
import com.sploot.data.entity.RawImuEntity
import com.sploot.data.entity.RawPpgEntity
import com.sploot.data.entity.RecordingSessionEntity
import com.sploot.data.entity.WhoopEventEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists raw Whoop sensor data during a recording session.
 *
 * This repository takes primitive/ByteArray parameters deliberately —
 * it does NOT import WhoopRecord to avoid creating a :data→:whoop-ble
 * module dependency.  Serialisation happens in the calling layer
 * (WhoopRecordingService in :app).
 */
@Singleton
class RecordingRepository @Inject constructor(
    private val sessionDao: RecordingSessionDao,
    private val imuDao:     RawImuDao,
    private val ppgDao:     RawPpgDao,
    private val hrDao:      HrSampleDao,
    private val eventDao:   WhoopEventDao,
) {

    // ── Session lifecycle ─────────────────────────────────────────────────────

    suspend fun startSession(): Long = sessionDao.insert(
        RecordingSessionEntity(
            startTimestampSeconds = Instant.now().epochSecond,
            endTimestampSeconds   = null,
        )
    )

    suspend fun endSession(sessionId: Long) =
        sessionDao.close(sessionId, Instant.now().epochSecond)

    fun sessionsFlow(): Flow<List<RecordingSessionEntity>> = sessionDao.getAllFlow()

    // ── Raw IMU ───────────────────────────────────────────────────────────────

    /**
     * @param accelX/Y/Z  100 × int16 LE = 200 bytes per channel
     * @param gyroX/Y/Z   100 × int16 LE = 200 bytes per channel
     */
    suspend fun insertImu(
        sessionId: Long, tsSeconds: Long, hrBpm: Int,
        accelX: ByteArray, accelY: ByteArray, accelZ: ByteArray,
        gyroX:  ByteArray, gyroY:  ByteArray, gyroZ:  ByteArray,
    ) {
        imuDao.insert(
            RawImuEntity(sessionId, tsSeconds, hrBpm, accelX, accelY, accelZ, gyroX, gyroY, gyroZ)
        )
        hrDao.insert(HrSampleEntity(sessionId, tsSeconds, hrBpm))
    }

    // ── Raw PPG ───────────────────────────────────────────────────────────────

    /** @param channelA..F  100 × uint16 LE = 200 bytes per channel */
    suspend fun insertPpg(
        sessionId: Long, tsSeconds: Long, ledDrive: Int,
        channelA: ByteArray, channelB: ByteArray, channelC: ByteArray,
        channelD: ByteArray, channelE: ByteArray, channelF: ByteArray,
    ) = ppgDao.insert(
        RawPpgEntity(sessionId, tsSeconds, ledDrive, channelA, channelB, channelC, channelD, channelE, channelF)
    )

    // ── Events ────────────────────────────────────────────────────────────────

    /** @param eventType  "BATTERY" | "TEMP" | "WRIST_ON" | "WRIST_OFF" */
    suspend fun insertEvent(sessionId: Long, tsSeconds: Long, eventType: String, value: Float?) =
        eventDao.insert(WhoopEventEntity(sessionId = sessionId, tsSeconds = tsSeconds, eventType = eventType, valueFloat = value))

    // ── Maintenance ───────────────────────────────────────────────────────────

    suspend fun purgeOldRawData(retentionDays: Int = 7): Pair<Int, Int> {
        val cutoff = Instant.now().epochSecond - retentionDays * 86_400L
        return imuDao.deleteOlderThan(cutoff) to ppgDao.deleteOlderThan(cutoff)
    }
}
