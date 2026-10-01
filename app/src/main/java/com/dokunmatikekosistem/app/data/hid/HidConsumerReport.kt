package com.dokunmatikekosistem.app.data.hid

/**
 * Builds 1-byte variable consumer control HID reports for Report ID 3.
 * Matches the universal Windows & Android bitmask:
 * Bit 0 (0x01): Play/Pause
 * Bit 1 (0x02): Scan Next Track
 * Bit 2 (0x04): Scan Prev Track
 * Bit 3 (0x08): Stop
 * Bit 4 (0x10): Volume Increment
 * Bit 5 (0x20): Volume Decrement
 * Bit 6 (0x40): Mute
 */
object HidConsumerReport {
    const val PLAY_PAUSE = 0x01
    const val SCAN_NEXT_TRACK = 0x02
    const val SCAN_PREV_TRACK = 0x04
    const val STOP = 0x08
    const val VOLUME_INCREMENT = 0x10
    const val VOLUME_DECREMENT = 0x20
    const val MUTE = 0x40

    fun build(mask: Int): ByteArray {
        return byteArrayOf(mask.toByte())
    }

    fun release(): ByteArray = byteArrayOf(0)
}
