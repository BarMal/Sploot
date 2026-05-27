package com.sploot.whoopble

import com.sploot.whoopble.protocol.FrameAssembler
import com.sploot.whoopble.protocol.WhoopConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FrameAssemblerTest {

    private lateinit var assembler: FrameAssembler

    // First init packet: aa 08 00 a8 23 00 23 00 ad a8 6a 2d (12 bytes)
    private val INIT_PACKET_0 = WhoopConstants.INIT_PACKETS[0]

    @Before
    fun setUp() {
        assembler = FrameAssembler()
    }

    @Test
    fun `complete frame in one feed returns one frame`() {
        val frames = assembler.feed(INIT_PACKET_0)
        assertEquals(1, frames.size)
        assertTrue(frames[0].contentEquals(INIT_PACKET_0))
    }

    @Test
    fun `frame split across two feeds is reassembled`() {
        val half1 = INIT_PACKET_0.copyOfRange(0, 6)
        val half2 = INIT_PACKET_0.copyOfRange(6, INIT_PACKET_0.size)

        val frames1 = assembler.feed(half1)
        assertEquals(0, frames1.size)

        val frames2 = assembler.feed(half2)
        assertEquals(1, frames2.size)
        assertTrue(frames2[0].contentEquals(INIT_PACKET_0))
    }

    @Test
    fun `frame split byte-by-byte is reassembled`() {
        for (b in INIT_PACKET_0.dropLast(1)) {
            assertEquals(0, assembler.feed(byteArrayOf(b)).size)
        }
        val frames = assembler.feed(byteArrayOf(INIT_PACKET_0.last()))
        assertEquals(1, frames.size)
    }

    @Test
    fun `two complete frames in one feed returns two frames`() {
        val doubled = INIT_PACKET_0 + INIT_PACKET_0
        val frames  = assembler.feed(doubled)
        assertEquals(2, frames.size)
    }

    @Test
    fun `garbage bytes before SOF are skipped`() {
        val garbage = byteArrayOf(0x00, 0x11, 0x22, 0x33)
        val frames  = assembler.feed(garbage + INIT_PACKET_0)
        assertEquals(1, frames.size)
        assertTrue(frames[0].contentEquals(INIT_PACKET_0))
    }

    @Test
    fun `reset clears buffer`() {
        assembler.feed(INIT_PACKET_0.copyOfRange(0, 4))
        assembler.reset()
        val frames = assembler.feed(INIT_PACKET_0)
        assertEquals(1, frames.size)
    }

    @Test
    fun `invalid header CRC causes byte-by-byte scan to next valid SOF`() {
        // Build a fake frame with a bad CRC8 in byte [3]
        val bad = INIT_PACKET_0.copyOf()
        bad[3] = (bad[3].toInt() xor 0xFF).toByte()  // flip all bits → invalid CRC8

        // Feed bad frame followed by a real frame
        val frames = assembler.feed(bad + INIT_PACKET_0)

        // Only the real frame should be returned
        assertEquals(1, frames.size)
        assertTrue(frames[0].contentEquals(INIT_PACKET_0))
    }
}
