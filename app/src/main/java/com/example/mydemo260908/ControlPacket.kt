package com.example.mydemo260908

object ControlPacket {
    const val LENGTH = 12

    fun create(
        sequence: Int,
        enabled: Boolean,
        leftX: Int,
        leftY: Int,
        rightX: Int,
        rightY: Int,
        leftGear: Int,
        rightGear: Int,
    ): ByteArray {
        val frame = ByteArray(LENGTH)
        frame[0] = 0xAA.toByte()
        frame[1] = 0x55
        frame[2] = 0x01
        frame[3] = sequence.toByte()
        frame[4] = if (enabled) 1 else 0
        frame[5] = if (enabled) leftX.coerceIn(-100, 100).toByte() else 0
        frame[6] = if (enabled) leftY.coerceIn(-100, 100).toByte() else 0
        frame[7] = if (enabled) rightX.coerceIn(-100, 100).toByte() else 0
        frame[8] = if (enabled) rightY.coerceIn(-100, 100).toByte() else 0
        frame[9] = if (enabled) leftGear.coerceIn(1, 2).toByte() else 0
        frame[10] = if (enabled) rightGear.coerceIn(1, 2).toByte() else 0
        frame[11] = crc8(frame, offset = 2, length = 9)
        return frame
    }

    // CRC-8: polynomial 0x07, initial value 0x00, no reflection, no final XOR.
    fun crc8(data: ByteArray, offset: Int, length: Int): Byte {
        var crc = 0
        for (index in offset until offset + length) {
            crc = crc xor (data[index].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x80 != 0) {
                    (crc shl 1 xor 0x07) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc.toByte()
    }

    fun toHex(frame: ByteArray): String = frame.joinToString(" ") {
        "%02X".format(it.toInt() and 0xFF)
    }
}
