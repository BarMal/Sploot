package com.sploot.data.entity

import androidx.room.Entity

/**
 * Raw PPG frame as received from the Whoop R21 record (100 Hz, 6 channels).
 *
 * Channels stored as ByteArrays (uint16 LE, 100 samples = 200 bytes each).
 * Deleted after 7 days once the session has been processed.
 */
@Entity(tableName = "raw_ppg", primaryKeys = ["sessionId", "tsSeconds"])
data class RawPpgEntity(
    val sessionId: Long,
    val tsSeconds: Long,
    val ledDrive: Int,
    /** Channels A–F: 100 × uint16 LE = 200 bytes each. */
    val channelA: ByteArray,
    val channelB: ByteArray,
    val channelC: ByteArray,
    val channelD: ByteArray,
    val channelE: ByteArray,
    val channelF: ByteArray,
)
