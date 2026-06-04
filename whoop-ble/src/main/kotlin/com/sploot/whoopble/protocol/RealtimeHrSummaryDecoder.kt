package com.sploot.whoopble.protocol

import com.sploot.whoopble.model.WhoopRecord
import java.time.Instant

/**
 * Decodes compact 0x28 realtime-data recordType=2 frames observed on-device.
 *
 * This layout is not described in the WHOOPSIE Gen4 docs, but the live traces
 * are consistent with:
 *   inner[0]    = 0x28 packet type
 *   inner[1]    = 0x02 compact realtime summary type
 *   inner[2..5] = uint32 LE timestamp_seconds
 *   inner[6..7] = uint16 LE timestamp_subseconds
 *   inner[8]    = uint8 heart rate bpm
 *
 * Remaining bytes are currently treated as unknown status/flags.
 */
object RealtimeHrSummaryDecoder {

    private const val MIN_FRAME_SIZE = 4 + 9
    private const val MIN_PLAUSIBLE_HR_BPM = 30
    private const val MAX_PLAUSIBLE_HR_BPM = 240

    fun decode(frame: ByteArray): WhoopRecord.HeartRate? {
        if (frame.size < MIN_FRAME_SIZE) return null

        val base = 4
        val tsSeconds = frame.getUInt32LE(base + 2)
        val timestamp = Instant.ofEpochSecond(tsSeconds)
        val hrBpm = frame[base + 8].toInt() and 0xFF
        if (hrBpm !in MIN_PLAUSIBLE_HR_BPM..MAX_PLAUSIBLE_HR_BPM) return null

        return WhoopRecord.HeartRate(
            timestamp = timestamp,
            hrBpm = hrBpm,
            source = "RT2",
        )
    }

    private fun ByteArray.getUInt32LE(offset: Int): Long =
        (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)
}
