package com.sploot.whoopble.protocol

import com.sploot.whoopble.model.WhoopRecord
import java.time.Instant

/**
 * Decodes EVENT frames from the EVENTS characteristic (0x61080004).
 *
 * EVENT inner-content layout (offsets from frame[4]):
 *   offset 0:    0x30  (event frame marker)
 *   offset 1:    sequence number (uint8)
 *   offset 2..3: event_type (uint16 LE)
 *   offset 4..7: ts_seconds (uint32 LE)
 *   offset 8..9: ts_subseconds (uint16 LE)
 *   offset 10..11: padding (2 bytes)
 *   offset 12..: payload (event-type specific)
 *
 * Supported events:
 *   TYPE  3  — Battery: payload uint32 LE / 10 → percent
 *   TYPE  9  — Wrist on  (no payload)
 *   TYPE 10  — Wrist off (no payload)
 *   TYPE 17  — Temperature: payload int16 LE / 10 → °C
 */
object EventDecoder {

    /** Minimum inner content to safely read the event type + timestamp. */
    private const val MIN_INNER_SIZE = WhoopConstants.EventHeader.PAYLOAD_START

    /**
     * Decode a complete event frame.
     * @return [WhoopRecord] subtype, or null if unrecognised or malformed.
     */
    fun decode(frame: ByteArray): WhoopRecord? {
        // Need header (4) + at least MIN_INNER_SIZE bytes of inner content
        if (frame.size < 4 + MIN_INNER_SIZE) return null

        val base = 4  // inner_content base

        val eventType  = frame.getUInt16LE(base + WhoopConstants.EventHeader.OFFSET_TYPE)
        val tsSeconds  = frame.getUInt32LE(base + WhoopConstants.EventHeader.OFFSET_TS_SECONDS)
        val timestamp  = Instant.ofEpochSecond(tsSeconds)

        return when (eventType) {

            WhoopConstants.EVENT_WRIST_ON -> WhoopRecord.WristOn(timestamp)

            WhoopConstants.EVENT_WRIST_OFF -> WhoopRecord.WristOff(timestamp)

            WhoopConstants.EVENT_DOUBLE_TAP -> WhoopRecord.DoubleTap(timestamp)

            WhoopConstants.EVENT_BATTERY -> {
                // Payload: uint32 LE / 10 → percent
                val payloadOffset = base + WhoopConstants.EventHeader.PAYLOAD_START
                if (frame.size < payloadOffset + 4) return null
                val raw     = frame.getUInt32LE(payloadOffset)
                val percent = raw.toFloat() / 10f
                if (percent !in 0f..100f) return null
                WhoopRecord.Battery(timestamp, percent, "event:battery")
            }

            WhoopConstants.EVENT_EXTENDED_BATTERY_INFORMATION -> {
                val payloadOffset = base + WhoopConstants.EventHeader.PAYLOAD_START
                if (frame.size < payloadOffset + 4) return null
                val raw     = frame.getUInt32LE(payloadOffset)
                val percent = raw.toFloat() / 10f
                if (percent !in 0f..100f) return null
                WhoopRecord.Battery(timestamp, percent, "event:extended_battery_information")
            }

            WhoopConstants.EVENT_CAPTOUCH_AUTOTHRESHOLD_ACTION -> {
                val payloadOffset = base + WhoopConstants.EventHeader.PAYLOAD_START
                val payload = if (frame.size > payloadOffset) frame.copyOfRange(payloadOffset, frame.size) else byteArrayOf()
                WhoopRecord.CapTouchAutoThreshold(timestamp, payload.toHexString())
            }

            WhoopConstants.EVENT_HAPTICS_FIRED -> {
                val payloadOffset = base + WhoopConstants.EventHeader.PAYLOAD_START
                val patternId = if (frame.size >= payloadOffset + 4) frame.getUInt32LE(payloadOffset).toInt() else null
                WhoopRecord.HapticsFired(timestamp, patternId)
            }

            WhoopConstants.EVENT_HAPTICS_TERMINATED -> {
                val payloadOffset = base + WhoopConstants.EventHeader.PAYLOAD_START
                val reasonCode = frame.getOrNull(payloadOffset)?.toInt()?.and(0xFF)
                WhoopRecord.HapticsTerminated(timestamp, reasonCode)
            }

            WhoopConstants.EVENT_TEMP -> {
                // Payload: int16 LE / 10 → °C
                val payloadOffset = base + WhoopConstants.EventHeader.PAYLOAD_START
                if (frame.size < payloadOffset + 2) return null
                val raw     = frame.getInt16LE(payloadOffset)
                val celsius = raw.toFloat() / 10f
                WhoopRecord.Temperature(timestamp, celsius)
            }

            else -> null  // Unknown event type — silently ignored
        }
    }

    // ── Read helpers ──────────────────────────────────────────────────────────

    private fun ByteArray.getUInt16LE(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.getUInt32LE(offset: Int): Long =
        (this[offset].toLong()     and 0xFF)         or
        ((this[offset + 1].toLong() and 0xFF) shl 8)  or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)

    private fun ByteArray.getInt16LE(offset: Int): Short =
        (((this[offset + 1].toInt() and 0xFF) shl 8) or (this[offset].toInt() and 0xFF)).toShort()

    private fun ByteArray.toHexString(): String =
        joinToString(separator = " ") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
