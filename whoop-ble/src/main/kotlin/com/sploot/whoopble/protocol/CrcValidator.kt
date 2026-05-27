package com.sploot.whoopble.protocol

import java.util.zip.CRC32

/**
 * CRC validation for Whoop BLE frames.
 *
 * Frame header CRC:
 *   CRC8 applied to the 2 length bytes [1..2] using the custom 256-byte
 *   lookup table from whoopsie-protocol.
 *
 * Frame content CRC:
 *   Standard zlib CRC32 (Java's java.util.zip.CRC32, polynomial 0xEDB88320)
 *   applied to inner_content only (excludes the 4-byte frame header and the
 *   4-byte CRC32 footer).
 */
object CrcValidator {

    // Full 256-entry lookup table from whoopsie-protocol WHOOP_BLE_PROTOCOL.md
    private val CRC8_TABLE: ByteArray = intArrayOf(
          0,   7,  14,   9,  28,  27,  18,  21,  56,  63,  54,  49,  36,  35,  42,  45,
        112, 119, 126, 121, 108, 107,  98, 101,  72,  79,  70,  65,  84,  83,  90,  93,
        224, 231, 238, 233, 252, 251, 242, 245, 216, 223, 214, 209, 196, 195, 202, 205,
        144, 151, 158, 153, 140, 139, 130, 133, 168, 175, 166, 161, 180, 179, 186, 189,
        199, 192, 201, 206, 219, 220, 213, 210, 255, 248, 241, 246, 227, 228, 237, 234,
        183, 176, 185, 190, 171, 172, 165, 162, 143, 136, 129, 134, 147, 148, 157, 154,
         39,  32,  41,  46,  59,  60,  53,  50,  31,  24,  17,  22,   3,   4,  13,  10,
         87,  80,  89,  94,  75,  76,  69,  66, 111, 104,  97, 102, 115, 116, 125, 122,
        137, 142, 135, 128, 149, 146, 155, 156, 177, 182, 191, 184, 173, 170, 163, 164,
        249, 254, 247, 240, 229, 226, 235, 236, 193, 198, 207, 200, 221, 218, 211, 212,
        105, 110, 103,  96, 117, 114, 123, 124,  81,  86,  95,  88,  77,  74,  67,  68,
         25,  30,  23,  16,   5,   2,  11,  12,  33,  38,  47,  40,  61,  58,  51,  52,
         78,  73,  64,  71,  82,  85,  92,  91, 118, 113, 120, 127, 106, 109, 100,  99,
         62,  57,  48,  55,  34,  37,  44,  43,   6,   1,   8,  15,  26,  29,  20,  19,
        174, 169, 160, 167, 178, 181, 188, 187, 150, 145, 152, 159, 138, 141, 132, 131,
        222, 217, 208, 215, 194, 197, 204, 203, 230, 225, 232, 239, 250, 253, 244, 243,
    ).let { ints -> ByteArray(ints.size) { ints[it].toByte() } }

    /**
     * Compute CRC8 over [data].
     * Used to validate frame[3] against frame[1..2] (length bytes).
     */
    fun crc8(data: ByteArray): Byte {
        var crc = 0
        for (b in data) {
            crc = CRC8_TABLE[(crc xor (b.toInt() and 0xFF)) and 0xFF].toInt() and 0xFF
        }
        return crc.toByte()
    }

    /**
     * Compute zlib CRC32 over [data].
     * Used to validate the last 4 bytes of a frame against inner_content.
     * Returns the value as a signed Int (matches Java's CRC32.value cast).
     */
    fun crc32(data: ByteArray): Int {
        val crc = CRC32()
        crc.update(data)
        return (crc.value and 0xFFFFFFFFL).toInt()
    }

    /**
     * Validate the header CRC of a complete frame.
     * @param frame Full raw frame ByteArray (must be ≥ 4 bytes).
     */
    fun validateHeader(frame: ByteArray): Boolean {
        if (frame.size < 4) return false
        if (frame[0] != WhoopConstants.SOF) return false
        val expected = crc8(byteArrayOf(frame[1], frame[2]))
        return frame[3] == expected
    }

    /**
     * Validate the content CRC32 of a complete frame.
     * @param frame Full raw frame ByteArray (must be ≥ 8 bytes, i.e. header + min inner + CRC32).
     */
    fun validateContent(frame: ByteArray): Boolean {
        if (frame.size < 8) return false
        val frameLength = frame.getUInt16LE(1)
        val innerSize   = frameLength - 4
        if (frame.size < 4 + innerSize + 4) return false

        val inner    = frame.copyOfRange(4, 4 + innerSize)
        val expected = crc32(inner)

        val storedOffset = 4 + innerSize
        val stored = frame.getInt32LE(storedOffset)
        return stored == expected
    }

    // ── ByteArray read helpers ────────────────────────────────────────────────

    private fun ByteArray.getUInt16LE(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.getInt32LE(offset: Int): Int =
        (this[offset].toInt()     and 0xFF)        or
        ((this[offset + 1].toInt() and 0xFF) shl 8)  or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)
}
