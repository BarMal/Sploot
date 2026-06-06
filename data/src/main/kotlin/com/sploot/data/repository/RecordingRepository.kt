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
 * This repository keeps the API primitive so the data module does not depend
 * directly on the BLE decoding module.
 */
@Singleton
class RecordingRepository @Inject constructor(
    private val sessionDao: RecordingSessionDao,
    private val imuDao: RawImuDao,
    private val ppgDao: RawPpgDao,
    private val hrDao: HrSampleDao,
    private val eventDao: WhoopEventDao,
) {

    // Session lifecycle

    suspend fun startSession(): Long =
        sessionDao.insert(
            RecordingSessionEntity(
                startTimestampSeconds = Instant.now().epochSecond,
                endTimestampSeconds = null,
            )
        )

    suspend fun endSession(sessionId: Long) {
        sessionDao.close(sessionId, Instant.now().epochSecond)
    }

    suspend fun markSessionProcessed(sessionId: Long) {
        sessionDao.markProcessed(sessionId)
    }

    suspend fun getSessionById(sessionId: Long): RecordingSessionEntity? =
        sessionDao.getById(sessionId)

    fun sessionsFlow(): Flow<List<RecordingSessionEntity>> = sessionDao.getAllFlow()

    // Raw IMU

    /**
     * Each accelerometer and gyroscope channel contains 100 int16 LE samples.
     */
    suspend fun insertImu(
        sessionId: Long,
        tsSeconds: Long,
        hrBpm: Int,
        accelX: ByteArray,
        accelY: ByteArray,
        accelZ: ByteArray,
        gyroX: ByteArray,
        gyroY: ByteArray,
        gyroZ: ByteArray,
    ) {
        imuDao.insert(
            RawImuEntity(
                sessionId,
                tsSeconds,
                hrBpm,
                accelX,
                accelY,
                accelZ,
                gyroX,
                gyroY,
                gyroZ,
            )
        )
        hrDao.insert(HrSampleEntity(sessionId, tsSeconds, hrBpm))
    }

    // Raw PPG

    /**
     * Each optical channel contains 100 uint16 LE samples.
     */
    suspend fun insertPpg(
        sessionId: Long,
        tsSeconds: Long,
        ledDrive: Int,
        channelA: ByteArray,
        channelB: ByteArray,
        channelC: ByteArray,
        channelD: ByteArray,
        channelE: ByteArray,
        channelF: ByteArray,
    ) {
        ppgDao.insert(
            RawPpgEntity(
                sessionId,
                tsSeconds,
                ledDrive,
                channelA,
                channelB,
                channelC,
                channelD,
                channelE,
                channelF,
            )
        )
    }

    // Events

    /** Event names include battery/temp/wrist plus touch and haptics event families. */
    suspend fun insertEvent(
        sessionId: Long,
        tsSeconds: Long,
        eventType: String,
        value: Float?,
    ) {
        eventDao.insert(
            WhoopEventEntity(
                sessionId = sessionId,
                tsSeconds = tsSeconds,
                eventType = eventType,
                valueFloat = value,
            )
        )
    }

    // Raw reads for post-session processing

    suspend fun getRawPpgForSession(sessionId: Long): List<RawPpgEntity> =
        ppgDao.getBySession(sessionId)

    suspend fun getRawImuForSession(sessionId: Long): List<RawImuEntity> =
        imuDao.getBySession(sessionId)

    suspend fun getHrSamplesForSession(sessionId: Long): List<HrSampleEntity> =
        hrDao.getBySession(sessionId)

    suspend fun getHrSamplesSince(fromSeconds: Long): List<HrSampleEntity> =
        hrDao.getSince(fromSeconds)

    suspend fun insertHrSample(
        sessionId: Long,
        tsSeconds: Long,
        hrBpm: Int,
    ) {
        hrDao.insert(HrSampleEntity(sessionId, tsSeconds, hrBpm))
    }

    suspend fun hasImuAtTimestamp(tsSeconds: Long): Boolean =
        imuDao.countAtTimestamp(tsSeconds) > 0

    suspend fun hasPpgAtTimestamp(tsSeconds: Long): Boolean =
        ppgDao.countAtTimestamp(tsSeconds) > 0

    suspend fun hasHrAtTimestamp(tsSeconds: Long): Boolean =
        hrDao.countAtTimestamp(tsSeconds) > 0

    suspend fun hasEventAtTimestamp(
        tsSeconds: Long,
        eventType: String,
    ): Boolean =
        eventDao.countAtTimestamp(tsSeconds, eventType) > 0

    suspend fun getEventsByTypeSince(
        fromSeconds: Long,
        eventType: String,
    ): List<WhoopEventEntity> =
        eventDao.getByTypeSince(fromSeconds, eventType)

    suspend fun getLatestStoredTimestamp(): Long? =
        listOfNotNull(
            imuDao.getLatestTimestamp(),
            ppgDao.getLatestTimestamp(),
            hrDao.getLatestTimestamp(),
            eventDao.getLatestTimestamp(),
        ).maxOrNull()

    suspend fun sessionHasAnyData(sessionId: Long): Boolean =
        imuDao.countForSession(sessionId) > 0 ||
            ppgDao.countForSession(sessionId) > 0 ||
            hrDao.countForSession(sessionId) > 0 ||
            eventDao.countForSession(sessionId) > 0

    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteById(sessionId)
    }

    // Maintenance

    suspend fun purgeOldRawData(retentionDays: Int = 7): Pair<Int, Int> {
        val cutoff = Instant.now().epochSecond - retentionDays * 86_400L
        return imuDao.deleteOlderThan(cutoff) to ppgDao.deleteOlderThan(cutoff)
    }
}
