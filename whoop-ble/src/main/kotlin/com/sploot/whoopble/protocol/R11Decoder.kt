package com.sploot.whoopble.protocol

import com.sploot.whoopble.model.WhoopRecord
import java.time.Instant

/**
 * Decodes R11 companion IMU frames.
 *
 * The whoopsie protocol notes that R11 often mirrors R10, but in practice we
 * also want to tolerate slimmer companion frames and at least surface live HR.
 */
object R11Decoder {

    private const val HR_ONLY_MIN_SIZE = 4 + WhoopConstants.R10.OFFSET_HR + 1
    private const val MIN_PLAUSIBLE_HR_BPM = 30
    private const val MAX_PLAUSIBLE_HR_BPM = 240

    fun decode(frame: ByteArray): WhoopRecord? {
        if (frame.size < HR_ONLY_MIN_SIZE) return null

        val base = 4
        val tsSeconds = frame.getUInt32LE(base + WhoopConstants.DataHeader.OFFSET_TS_SECONDS)
        val timestamp = Instant.ofEpochSecond(tsSeconds)
        val hrBpm = frame[base + WhoopConstants.R10.OFFSET_HR].toInt() and 0xFF
        if (hrBpm !in MIN_PLAUSIBLE_HR_BPM..MAX_PLAUSIBLE_HR_BPM) {
            return null
        }

        return WhoopRecord.HeartRate(
            timestamp = timestamp,
            hrBpm = hrBpm,
            source = "R11",
        )
    }

    private fun ByteArray.getUInt32LE(offset: Int): Long =
        (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)
}
