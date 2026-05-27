package com.sploot.whoopble.protocol

import com.sploot.whoopble.model.WhoopRecord
import java.time.Instant

/**
 * Decodes R10 IMU frames (HR + Accelerometer + Gyroscope).
 *
 * R10 inner-content layout (offsets from frame[4]):
 *   offset  0: frame sub-type (1 byte)
 *   offset  1: record type = 0x10 (1 byte)
 *   offset  2: sequence number (1 byte)
 *   offset  3..6: j_field (4 bytes, opaque)
 *   offset  7..10: ts_seconds  uint32 LE
 *   offset 11..12: ts_subseconds uint16 LE
 *   offset 13..16: record header (4 bytes)
 *   offset 17: heart rate  uint8 bpm
 *   offset 85..284:   accel X  100 × int16 LE
 *   offset 285..484:  accel Y
 *   offset 485..684:  accel Z
 *   offset 688..887:  gyro X   100 × int16 LE  (4-byte gap before gyro block)
 *   offset 888..1087: gyro Y
 *   offset 1088..1287: gyro Z
 */
object R10Decoder {

    /** Minimum frame size to contain all R10 fields. */
    private const val MIN_FRAME_SIZE =
        4 + WhoopConstants.R10.INNER_SIZE + 4  // header + inner + CRC32

    /**
     * Decode a complete R10 frame.
     * @param frame Full raw frame bytes (header + inner_content + CRC32).
     * @return [WhoopRecord.Imu] or null if the frame is malformed / too short.
     */
    fun decode(frame: ByteArray): WhoopRecord.Imu? {
        if (frame.size < MIN_FRAME_SIZE) return null

        // inner_content starts at frame[4]
        val inner = frame
        val base  = 4  // inner_content base offset within frame[]

        val tsSeconds = inner.getUInt32LE(base + WhoopConstants.DataHeader.OFFSET_TS_SECONDS)
        val timestamp = Instant.ofEpochSecond(tsSeconds)

        val hrBpm  = inner[base + WhoopConstants.R10.OFFSET_HR].toInt() and 0xFF

        val accelX = inner.readInt16Array(base + WhoopConstants.R10.OFFSET_ACCEL_X, WhoopConstants.R10.SAMPLES)
        val accelY = inner.readInt16Array(base + WhoopConstants.R10.OFFSET_ACCEL_Y, WhoopConstants.R10.SAMPLES)
        val accelZ = inner.readInt16Array(base + WhoopConstants.R10.OFFSET_ACCEL_Z, WhoopConstants.R10.SAMPLES)
        val gyroX  = inner.readInt16Array(base + WhoopConstants.R10.OFFSET_GYRO_X,  WhoopConstants.R10.SAMPLES)
        val gyroY  = inner.readInt16Array(base + WhoopConstants.R10.OFFSET_GYRO_Y,  WhoopConstants.R10.SAMPLES)
        val gyroZ  = inner.readInt16Array(base + WhoopConstants.R10.OFFSET_GYRO_Z,  WhoopConstants.R10.SAMPLES)

        return WhoopRecord.Imu(
            timestamp = timestamp,
            hrBpm     = hrBpm,
            accelX    = accelX,
            accelY    = accelY,
            accelZ    = accelZ,
            gyroX     = gyroX,
            gyroY     = gyroY,
            gyroZ     = gyroZ,
        )
    }

    // ── Read helpers (operate on the full frame array with absolute offsets) ──

    private fun ByteArray.getUInt32LE(offset: Int): Long =
        (this[offset].toLong()     and 0xFF)         or
        ((this[offset + 1].toLong() and 0xFF) shl 8)  or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)

    private fun ByteArray.getInt16LE(offset: Int): Short =
        (((this[offset + 1].toInt() and 0xFF) shl 8) or (this[offset].toInt() and 0xFF)).toShort()

    private fun ByteArray.readInt16Array(offset: Int, count: Int): ShortArray =
        ShortArray(count) { i -> getInt16LE(offset + i * 2) }
}
