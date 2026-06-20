package com.sploot.whoopble.protocol

object WhoopHistoricalProtocol {
    private const val BATCH_ID_BYTES = 8

    fun buildBatchAckPacket(counter: Int, batchNumber: ByteArray): ByteArray {
        require(batchNumber.size >= BATCH_ID_BYTES) {
            "Historical batch ACK requires an 8-byte batch number"
        }

        val inner = byteArrayOf(
            WhoopConstants.CMD_TYPE_COMMAND.toByte(),
            counter.toByte(),
            WhoopConstants.CMD_HISTORICAL_DATA_RESULT.toByte(),
            0x01,
        ) + batchNumber.copyOf(BATCH_ID_BYTES)

        val frameLength = inner.size + 4
        val lenLo = (frameLength and 0xFF).toByte()
        val lenHi = ((frameLength shr 8) and 0xFF).toByte()
        val hdrCrc = CrcValidator.crc8(byteArrayOf(lenLo, lenHi))
        val crc32 = CrcValidator.crc32(inner)

        return byteArrayOf(WhoopConstants.SOF, lenLo, lenHi, hdrCrc) +
            inner +
            byteArrayOf(
                (crc32 and 0xFF).toByte(),
                ((crc32 shr 8) and 0xFF).toByte(),
                ((crc32 shr 16) and 0xFF).toByte(),
                ((crc32 ushr 24) and 0xFF).toByte(),
            )
    }
}
