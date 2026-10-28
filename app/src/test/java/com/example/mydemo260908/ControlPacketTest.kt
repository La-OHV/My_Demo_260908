package com.example.mydemo260908

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlPacketTest {
    @Test
    fun enabledFrameEncodesSignedControlsAndCrc() {
        val frame = ControlPacket.create(
            sequence = 0x27,
            enabled = true,
            leftX = -45,
            leftY = 80,
            rightX = 0,
            rightY = -100,
            leftGear = 2,
            rightGear = 1,
        )

        assertArrayEquals(
            byteArrayOf(
                0xAA.toByte(), 0x55, 0x01, 0x27, 0x01,
                0xD3.toByte(), 0x50, 0x00, 0x9C.toByte(), 0x02, 0x01, 0x4A,
            ),
            frame,
        )
    }

    @Test
    fun disabledFrameForcesEveryControlToZero() {
        val frame = ControlPacket.create(
            sequence = 3,
            enabled = false,
            leftX = 100,
            leftY = -100,
            rightX = 40,
            rightY = -30,
            leftGear = 2,
            rightGear = 2,
        )

        assertEquals(0, frame[4].toInt())
        for (index in 5..10) assertEquals(0, frame[index].toInt())
        assertEquals(
            ControlPacket.crc8(frame, offset = 2, length = 9),
            frame[11],
        )
    }
}
