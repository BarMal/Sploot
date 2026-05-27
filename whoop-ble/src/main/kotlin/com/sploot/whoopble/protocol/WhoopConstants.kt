package com.sploot.whoopble.protocol

/**
 * WHOOP 4.0 BLE protocol constants.
 *
 * Source: https://github.com/project-whoopsie/whoopsie-protocol
 *
 * UUID note: The Python reference implementation uses CoreBluetooth with short
 * UUID strings ("61080001" etc.).  On Android we discover services at runtime
 * and match by the first 8 characters of the full 128-bit UUID string, which
 * is device-model-agnostic w.r.t. the UUID base suffix.  Verify the actual
 * 128-bit UUIDs for your device with nRF Connect if needed.
 */
object WhoopConstants {

    // ── GATT UUID prefixes ───────────────────────────────────────────────────

    /** BLE service that hosts all Whoop characteristics. */
    const val SERVICE_UUID_PREFIX       = "61080001"

    /** Write characteristic — commands sent to the strap. */
    const val CMD_TO_STRAP_PREFIX       = "61080002"

    /** Read/Notify characteristic — command responses from the strap. */
    const val CMD_FROM_STRAP_PREFIX     = "61080003"

    /** Notify characteristic — device events (battery, temp, wrist). */
    const val EVENTS_PREFIX             = "61080004"

    /** Notify characteristic — sensor data records (R10 IMU + R21 PPG). */
    const val DATA_PREFIX               = "61080005"

    /** Standard Client Characteristic Configuration Descriptor UUID. */
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    /** BLE advertisement name prefix used for scan filtering. */
    const val DEVICE_NAME_PREFIX = "WHOOP"

    // ── Frame constants ──────────────────────────────────────────────────────

    /** Start-of-frame marker. */
    const val SOF: Byte = 0xAA.toByte()

    /** Requested BLE MTU (strap supports up to 512). */
    const val MTU = 512

    // ── Record type bytes (byte 1 of inner content in DATA frames) ───────────

    const val RECORD_TYPE_IMU: Int = 0x10  // R10
    const val RECORD_TYPE_PPG: Int = 0x21  // R21

    // ── Event type values (uint16 at inner content offset 2 in EVENT frames) ─

    const val EVENT_BATTERY:   Int = 3
    const val EVENT_WRIST_ON:  Int = 9
    const val EVENT_WRIST_OFF: Int = 10
    const val EVENT_TEMP:      Int = 17

    // ── Sequence number ──────────────────────────────────────────────────────

    /**
     * Starting sequence number for host-originated commands.
     * The strap uses ~0x05 for ACKs during historical sync, so start
     * host commands at 0xA0 to avoid collisions.
     */
    const val INITIAL_SEQ: Int = 0xA0

    // ── Command bytes (used with buildCommandPacket) ─────────────────────────

    const val CMD_TYPE_COMMAND: Int = 0x23

    /** Enables R10 (HR + IMU) streaming.  Payload: [0x01]. */
    const val CMD_ENABLE_IMU: Int = 0x3F

    /** Enables R21 (PPG) streaming — send both commands.  Payload: [0x01]. */
    const val CMD_ENABLE_PPG_1: Int = 0x9A
    const val CMD_ENABLE_PPG_2: Int = 0x6C

    // ── Five hardcoded initialisation packets ────────────────────────────────
    //
    // Sent in order 0..4 immediately after subscribing to notifications.
    // These are raw frames (header + inner content + CRC32) ready to write
    // to CMD_TO_STRAP.
    //
    // Verified via whoopsie-protocol WHOOP_BLE_PROTOCOL.md.

    val INIT_PACKETS: List<ByteArray> = listOf(
        // GET_HELLO_HARVARD
        hexToBytes("aa0800a823002300ada86a2d"),
        // GET_ADVERTISING_NAME
        hexToBytes("aa0800a823014c00f2b5cdce"),
        // GET_DATA_RANGE
        hexToBytes("aa0800a823022200824df537"),
        // GET_ALARM_TIME
        hexToBytes("aa0800a823034301c54dd63d"),
        // SEND_HISTORICAL_DATA
        hexToBytes("aa0800a823041600c7c25288"),
    )

    // ── R10 inner-content field offsets ──────────────────────────────────────
    //
    // Offsets are relative to inner_content (= frame[4]).
    // Total inner content size: 1928 bytes.

    object R10 {
        const val OFFSET_HR      = 17   // uint8
        const val OFFSET_ACCEL_X = 85   // 100 × int16 LE
        const val OFFSET_ACCEL_Y = 285
        const val OFFSET_ACCEL_Z = 485
        const val OFFSET_GYRO_X  = 688
        const val OFFSET_GYRO_Y  = 888
        const val OFFSET_GYRO_Z  = 1088
        const val SAMPLES        = 100
        const val INNER_SIZE     = 1928
    }

    // ── R21 inner-content field offsets ──────────────────────────────────────
    //
    // Total inner content size: 1244 bytes.
    // Note: 12-byte gap between Channel C end (620) and Channel D start (632).

    object R21 {
        const val OFFSET_LED_DRIVE = 14   // uint16 LE
        const val OFFSET_CHANNEL_A = 20   // 100 × uint16 LE (Green 1 — HR/HRV)
        const val OFFSET_CHANNEL_B = 220  // Green 2
        const val OFFSET_CHANNEL_C = 420  // Infrared
        const val OFFSET_CHANNEL_D = 632  // (12-byte gap skipped)
        const val OFFSET_CHANNEL_E = 832
        const val OFFSET_CHANNEL_F = 1032 // Red / SpO₂
        const val SAMPLES          = 100
        const val INNER_SIZE       = 1244
    }

    // ── DATA frame inner-content header (common to R10 + R21) ───────────────

    object DataHeader {
        // inner[0] = frame type (DATA = 0x28 / 0x2F / 0x2B — varies)
        // inner[1] = record_type (0x10 or 0x21)
        // inner[2] = sequence number
        // inner[3..6] = j_field (4 bytes, opaque)
        const val OFFSET_TS_SECONDS    = 7   // uint32 LE  (Unix epoch seconds)
        const val OFFSET_TS_SUBSECONDS = 11  // uint16 LE  (fractional seconds)
        const val RECORD_START         = 13  // record bytes start here
    }

    // ── EVENT frame inner-content layout ────────────────────────────────────
    //
    // inner[0]    = 0x30 (event marker)
    // inner[1]    = sequence number
    // inner[2..3] = event_type  uint16 LE
    // inner[4..7] = ts_seconds  uint32 LE
    // inner[8..9] = ts_subseconds uint16 LE
    // inner[10..11] = padding
    // inner[12..] = payload (event-type specific)

    object EventHeader {
        const val OFFSET_TYPE          = 2
        const val OFFSET_TS_SECONDS    = 4
        const val OFFSET_TS_SUBSECONDS = 8
        const val PAYLOAD_START        = 12
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
}
