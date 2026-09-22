package com.dokunmatikekosistem.app.data.hid

/** Builds the 5-byte relative-mouse HID report: [buttons, dx, dy, wheel, pan]. */
object HidMouseReport {
    fun build(
        dx: Int,
        dy: Int,
        wheelDelta: Int,
        panDelta: Int,
        leftButtonPressed: Boolean,
        rightButtonPressed: Boolean
    ): ByteArray {
        var buttons = 0
        if (leftButtonPressed) buttons = buttons or 0x01
        if (rightButtonPressed) buttons = buttons or 0x02
        return byteArrayOf(
            buttons.toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheelDelta.coerceIn(-127, 127).toByte(),
            panDelta.coerceIn(-127, 127).toByte()
        )
    }
}
