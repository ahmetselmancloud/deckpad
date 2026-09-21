package com.dokunmatikekosistem.app.hid

/** Builds the 3-byte relative-mouse HID report: [buttons, dx, dy]. */
object HidMouseReport {
    fun build(dx: Int, dy: Int, leftButtonPressed: Boolean): ByteArray {
        val clampedDx = dx.coerceIn(-127, 127).toByte()
        val clampedDy = dy.coerceIn(-127, 127).toByte()
        val buttons: Byte = if (leftButtonPressed) 1 else 0
        return byteArrayOf(buttons, clampedDx, clampedDy)
    }
}
