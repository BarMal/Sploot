package com.sploot.whoopble

import com.sploot.whoopble.protocol.CrcValidator
import com.sploot.whoopble.protocol.WhoopHistoricalProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhoopHistoricalProtocolTest {

    @Test
    fun `batch ack uses all eight batch id bytes`() {
        val batchId = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)

        val packet = WhoopHistoricalProtocol.buildBatchAckPacket(counter = 5, batchNumber = batchId)

        assertEquals("aa100057230517010102030405060708", packet.copyOf(16).toHex())
        assertTrue("Header CRC8 invalid", CrcValidator.validateHeader(packet))
        assertTrue("Content CRC32 invalid", CrcValidator.validateContent(packet))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
