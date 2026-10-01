package com.dokunmatikekosistem.app.data.hid

/**
 * Builds 2-byte consumer control HID reports (Volume, Play/Pause, Next/Prev, etc.)
 */
object HidConsumerReport {
    const val VOLUME_INCREMENT = 0x00E9
    const val VOLUME_DECREMENT = 0x00EA
    const val MUTE = 0x00E2
    const val PLAY_PAUSE = 0x00CD
    const val SCAN_NEXT_TRACK = 0x00B5
    const val SCAN_PREV_TRACK = 0x00B6
    const val STOP = 0x00B7

    fun build(usageCode: Int): ByteArray {
        return byteArrayOf(
            (usageCode and 0xFF).toByte(),
            ((usageCode shr 8) and 0xFF).toByte()
        )
    }

    fun release(): ByteArray = byteArrayOf(0, 0)
}
