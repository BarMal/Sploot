package com.sploot.whoopble.protocol

/**
 * Reassembles BLE notification fragments into complete Whoop frames.
 *
 * BLE notifications arrive in MTU-sized chunks (up to 512 bytes each).
 * R10 frames are ~1940 bytes; R21 frames are ~1256 bytes — both arrive
 * fragmented across multiple consecutive notifications on the same characteristic.
 *
 * Frame layout (for reference):
 *   [0]     0xAA  start-of-frame
 *   [1..2]  uint16 LE  frame_length (= inner_content_size + 4)
 *   [3]     CRC8 of bytes [1..2]
 *   [4..]   inner_content  (frame_length – 4 bytes)
 *   [-4:]   CRC32 LE of inner_content
 *
 * Total bytes per frame = 4 (header) + frame_length
 *
 * One FrameAssembler instance should be maintained per BLE characteristic,
 * since notifications from different characteristics interleave independently.
 */
class FrameAssembler {

    private var buf = ByteArray(0)

    /**
     * Feed raw notification bytes.  Returns all complete frames that can be
     * extracted from the accumulated buffer.  May return more than one frame
     * if small event frames trail a data frame in the same notification burst.
     */
    fun feed(bytes: ByteArray): List<ByteArray> {
        buf += bytes
        val frames = mutableListOf<ByteArray>()
        var pos = 0

        while (pos < buf.size) {
            // ── 1. Sync to SOF ────────────────────────────────────────────────
            while (pos < buf.size && buf[pos] != WhoopConstants.SOF) pos++
            if (pos >= buf.size) break

            // ── 2. Need at least 4 bytes to read the header ───────────────────
            if (buf.size - pos < 4) break

            // ── 3. Validate header CRC8 ───────────────────────────────────────
            val expectedHeaderCrc = CrcValidator.crc8(byteArrayOf(buf[pos + 1], buf[pos + 2]))
            if (buf[pos + 3] != expectedHeaderCrc) {
                // Not a valid frame start; advance past this byte and try again
                pos++
                continue
            }

            // ── 4. Compute total frame size ───────────────────────────────────
            val frameLength = buf.getUInt16LE(pos + 1)
            val totalSize   = 4 + frameLength

            if (buf.size - pos < totalSize) break  // Fragment pending — wait

            // ── 5. Extract and optionally verify content CRC32 ───────────────
            val frame = buf.copyOfRange(pos, pos + totalSize)
            frames += frame
            pos += totalSize
        }

        buf = if (pos >= buf.size) ByteArray(0) else buf.copyOfRange(pos, buf.size)
        return frames
    }

    /** Discard all buffered bytes (e.g. on reconnect). */
    fun reset() {
        buf = ByteArray(0)
    }

    // ── ByteArray helper ─────────────────────────────────────────────────────

    private fun ByteArray.getUInt16LE(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}
