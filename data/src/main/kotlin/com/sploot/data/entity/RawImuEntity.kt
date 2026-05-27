package com.sploot.data.entity

import androidx.room.Entity

/**
 * Raw IMU frame as received from the Whoop R10 record (100 Hz).
 *
 * Arrays stored as ByteArrays (int16 LE, 100 samples = 200 bytes each).
 * Deleted after 7 days once the session has been processed.
 */
@Entity(tableName = "raw_imu", primaryKeys = ["sessionId", "tsSeconds"])
data class RawImuEntity(
    val sessionId: Long,
    /** Unix epoch seconds from Whoop clock. */
    val tsSeconds: Long,
    val hrBpm: Int,
    /** 100 × int16 LE = 200 bytes per channel. */
    val accelX: ByteArray,
    val accelY: ByteArray,
    val accelZ: ByteArray,
    val gyroX:  ByteArray,
    val gyroY:  ByteArray,
    val gyroZ:  ByteArray,
)
