package com.sploot.whoopble.protocol

import com.sploot.whoopble.model.WhoopRecord
import java.time.Instant

/**
 * Decodes R21 optical/PPG frames (6-channel photoplethysmography).
 *
 * R21 inner-content layout (offsets from frame[4]):
 *   offset  0: frame sub-type (1 byte)
 *   offset  1: record type = 0x21 (1 byte)
 *   offset  2: sequence number (1 byte)
 *   offset  3..6: j_field (4 bytes, opaque)
 *   offset  7..10: ts_seconds  uint32 LE
 *   offset 11..12: ts_subseconds uint16 LE
 *   offset 13: record header start
 *   offset 14..15: LED drive level  uint16 LE
 *   offset 16: sample count  uint8
 *   offset 20..219:   channel A (Green 1)  100 × uint16 LE
 *   offset 220..419:  channel B (Green 2)
 *   offset 420..619:  channel C (Infrared)
 *   — 12-byte gap (offsets 620..631 are undefined/skipped) —
 *   offset 632..831:  channel D
 *   offset 832..1031: channel E
 *   offset 1032..1231: channel F (Red / SpO₂)
 *
 * Channel mapping for downstream algorithms:
 *   HR / HRV:  channel A (Green 1)
 *   SpO₂:      channel F (numerator) + channel C (denominator) via Beer-Lambert
 */
object R21Decoder {

    private const val MIN_FRAME_SIZE =
        4 + WhoopConstants.R21.INNER_SIZE + 4

    /**
     * Decode a complete R21 frame.
     * @return [WhoopRecord.Ppg] or null if malformed.
     */
    fun decode(frame: ByteArray): WhoopRecord.Ppg? {
        if (frame.size < MIN_FRAME_SIZE) return null

        val base = 4  // inner_content base offset within frame[]

        val tsSeconds = frame.getUInt32LE(base + WhoopConstants.DataHeader.OFFSET_TS_SECONDS)
        val timestamp = Instant.ofEpochSecond(tsSeconds)

        val ledDrive = frame.getUInt16LE(base + WhoopConstants.R21.OFFSET_LED_DRIVE)

        val channelA = frame.readUInt16Array(base + WhoopConstants.R21.OFFSET_CHANNEL_A, WhoopConstants.R21.SAMPLES)
        val channelB = frame.readUInt16Array(base + WhoopConstants.R21.OFFSET_CHANNEL_B, WhoopConstants.R21.SAMPLES)
        val channelC = frame.readUInt16Array(base + WhoopConstants.R21.OFFSET_CHANNEL_C, WhoopConstants.R21.SAMPLES)
        val channelD = frame.readUInt16Array(base + WhoopConstants.R21.OFFSET_CHANNEL_D, WhoopConstants.R21.SAMPLES)
        val channelE = frame.readUInt16Array(base + WhoopConstants.R21.OFFSET_CHANNEL_E, WhoopConstants.R21.SAMPLES)
        val channelF = frame.readUInt16Array(base + WhoopConstants.R21.OFFSET_CHANNEL_F, WhoopConstants.R21.SAMPLES)

        return WhoopRecord.Ppg(
            timestamp = timestamp,
            ledDrive  = ledDrive,
            channelA  = channelA,
            channelB  = channelB,
            channelC  = channelC,
            channelD  = channelD,
            channelE  = channelE,
            channelF  = channelF,
        )
    }

    // ── Read helpers ──────────────────────────────────────────────────────────

    private fun ByteArray.getUInt32LE(offset: Int): Long =
        (this[offset].toLong()     and 0xFF)         or
        ((this[offset + 1].toLong() and 0xFF) shl 8)  or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)

    private fun ByteArray.getUInt16LE(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.readUInt16Array(offset: Int, count: Int): IntArray =
        IntArray(count) { i -> getUInt16LE(offset + i * 2) }
}
