package com.sploot.whoopble

import com.sploot.whoopble.protocol.CrcValidator
import com.sploot.whoopble.protocol.WhoopConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies CRC8 and CRC32 against the 5 known init packets from whoopsie-protocol.
 *
 * Each init packet structure:
 *   [0]     0xAA
 *   [1..2]  frame_length (uint16 LE)
 *   [3]     CRC8([1..2])
 *   [4..7]  inner_content (4 bytes for the 8-byte frame_length init packets)
 *   [8..11] CRC32(inner_content)
 */
class CrcValidatorTest {

    @Test
    fun `init packet 0 header CRC8 is valid`() {
        val packet = WhoopConstants.INIT_PACKETS[0]
        assertTrue("Header CRC8 invalid", CrcValidator.validateHeader(packet))
    }

    @Test
    fun `all init packets header CRC8 are valid`() {
        WhoopConstants.INIT_PACKETS.forEachIndexed { i, packet ->
            assertTrue("Init packet $i header CRC8 invalid", CrcValidator.validateHeader(packet))
        }
    }

    @Test
    fun `all init packets content CRC32 are valid`() {
        WhoopConstants.INIT_PACKETS.forEachIndexed { i, packet ->
            assertTrue("Init packet $i content CRC32 invalid", CrcValidator.validateContent(packet))
        }
    }

    @Test
    fun `CRC8 of 0x08 0x00 equals 0xA8`() {
        // First init packet: bytes [1..2] = 0x08, 0x00; byte [3] = 0xA8
        val expected = 0xA8.toByte()
        assertEquals(expected, CrcValidator.crc8(byteArrayOf(0x08, 0x00)))
    }

    @Test
    fun `CRC8 empty input returns 0`() {
        assertEquals(0.toByte(), CrcValidator.crc8(ByteArray(0)))
    }

    @Test
    fun `CRC32 of first init packet inner content matches stored value`() {
        // First init packet: aa 08 00 a8 | 23 00 23 00 | ad a8 6a 2d
        // inner_content = [0x23, 0x00, 0x23, 0x00]
        // stored CRC32 = 0x2d6aa8ad (little-endian: ad a8 6a 2d)
        val inner    = byteArrayOf(0x23, 0x00, 0x23, 0x00)
        val expected = 0x2d6aa8ad.toInt()
        assertEquals(expected, CrcValidator.crc32(inner))
    }
}
