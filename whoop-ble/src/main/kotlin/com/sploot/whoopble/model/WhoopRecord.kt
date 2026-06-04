package com.sploot.whoopble.model

import java.time.Instant

/**
 * Typed records emitted by the Whoop BLE protocol layer.
 *
 * Note: Imu and Ppg records contain primitive arrays.  Kotlin data-class
 * `equals` / `hashCode` use reference equality for arrays — this is
 * intentional; records are flow-through values, not compared by value.
 */
sealed class WhoopRecord {

    /**
     * R10 record — arrives once per second.
     *
     * @param timestamp   Unix epoch from Whoop device clock
     * @param hrBpm       Heart rate in beats-per-minute (0–255)
     * @param accelX/Y/Z  100 samples at 100 Hz, raw int16 (scale: /100 → g)
     * @param gyroX/Y/Z   100 samples at 100 Hz, raw int16 (scale: /100 → °/s)
     */
    data class Imu(
        val timestamp: Instant,
        val hrBpm:  Int,
        val accelX: ShortArray,
        val accelY: ShortArray,
        val accelZ: ShortArray,
        val gyroX:  ShortArray,
        val gyroY:  ShortArray,
        val gyroZ:  ShortArray,
    ) : WhoopRecord()

    /**
     * R21 record — arrives once per second.
     *
     * @param timestamp  Unix epoch from Whoop device clock
     * @param ledDrive   LED drive level (raw uint16)
     * @param channelA   Green 1 — 100 samples uint16 (primary HR/HRV channel)
     * @param channelB   Green 2 — 100 samples uint16
     * @param channelC   Infrared — 100 samples uint16 (motion rejection + SpO₂ denominator)
     * @param channelD   Unknown — 100 samples uint16
     * @param channelE   Unknown — 100 samples uint16
     * @param channelF   Red/SpO₂ — 100 samples uint16 (SpO₂ numerator)
     *
     * Stored as IntArray to avoid sign-extension issues with uint16 values.
     */
    data class Ppg(
        val timestamp: Instant,
        val ledDrive:  Int,
        val channelA:  IntArray,
        val channelB:  IntArray,
        val channelC:  IntArray,
        val channelD:  IntArray,
        val channelE:  IntArray,
        val channelF:  IntArray,
    ) : WhoopRecord()

    /** Companion/live HR sample when WHOOP does not provide a full IMU payload. */
    data class HeartRate(
        val timestamp: Instant,
        val hrBpm: Int,
        val source: String,
    ) : WhoopRecord()

    /** EVENT 17 — skin temperature, reported on-change. */
    data class Temperature(val timestamp: Instant, val celsius: Float) : WhoopRecord()

    /** EVENT 3 — battery level, reported on-change. */
    data class Battery(
        val timestamp: Instant,
        val percent: Float,
        val source: String,
    ) : WhoopRecord()

    /** EVENT 9 — wrist detected. */
    data class DoubleTap(val timestamp: Instant) : WhoopRecord()

    data class CapTouchAutoThreshold(
        val timestamp: Instant,
        val payloadHex: String,
    ) : WhoopRecord()

    data class HapticsFired(
        val timestamp: Instant,
        val patternId: Int?,
    ) : WhoopRecord()

    data class HapticsTerminated(
        val timestamp: Instant,
        val reasonCode: Int?,
    ) : WhoopRecord()

    data class WristOn(val timestamp: Instant) : WhoopRecord()

    /** EVENT 10 — wrist removed. */
    data class WristOff(val timestamp: Instant) : WhoopRecord()
}
