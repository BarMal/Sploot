package com.sploot.whoopble.protocol

import com.sploot.whoopble.model.WhoopRecord
import java.time.Instant

/**
 * Decodes compact historical R12 frames observed during SEND_HISTORICAL_DATA.
 *
 * These 96-byte historical-data frames carry the common data timestamp header
 * and a plausible HR byte at the same relative offset used by R10.
 */
object R12Decoder {

    private const val MIN_FRAME_SIZE = 4 + WhoopConstants.R10.OFFSET_HR + 1
    private const val MIN_PLAUSIBLE_HR_BPM = 30
    private const val MAX_PLAUSIBLE_HR_BPM = 240

    fun decode(frame: ByteArray): WhoopRecord.HeartRate? {
        if (frame.size < MIN_FRAME_SIZE) return null

        val base = 4
        val tsSeconds = frame.getUInt32LE(base + WhoopConstants.DataHeader.OFFSET_TS_SECONDS)
        val hrBpm = frame[base + WhoopConstants.R10.OFFSET_HR].toInt() and 0xFF
        if (hrBpm !in MIN_PLAUSIBLE_HR_BPM..MAX_PLAUSIBLE_HR_BPM) return null

        return WhoopRecord.HeartRate(
            timestamp = Instant.ofEpochSecond(tsSeconds),
            hrBpm = hrBpm,
            source = "R12",
        )
    }

    private fun ByteArray.getUInt32LE(offset: Int): Long =
        (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)
}
