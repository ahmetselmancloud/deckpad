package com.dokunmatikekosistem.app.data.hid

/** Builds the 5-byte relative-mouse HID report: [buttons, dx, dy, wheel, pan]. */
object HidMouseReport {
    fun build(
        dx: Int,
        dy: Int,
        wheelDelta: Int,
        panDelta: Int,
        leftButtonPressed: Boolean,
        rightButtonPressed: Boolean,
        middleButtonPressed: Boolean = false
    ): ByteArray {
        val target = ByteArray(5)
        buildInto(target, dx, dy, wheelDelta, panDelta, leftButtonPressed, rightButtonPressed, middleButtonPressed)
        return target
    }

    fun buildInto(
        target: ByteArray,
        dx: Int,
        dy: Int,
        wheelDelta: Int,
        panDelta: Int,
        leftButtonPressed: Boolean,
        rightButtonPressed: Boolean,
        middleButtonPressed: Boolean = false
    ) {
        var buttons = 0
        if (leftButtonPressed) buttons = buttons or 0x01
        if (rightButtonPressed) buttons = buttons or 0x02
        if (middleButtonPressed) buttons = buttons or 0x04
        target[0] = buttons.toByte()
        target[1] = dx.coerceIn(-127, 127).toByte()
        target[2] = dy.coerceIn(-127, 127).toByte()
        target[3] = wheelDelta.coerceIn(-127, 127).toByte()
        target[4] = panDelta.coerceIn(-127, 127).toByte()
    }
}
