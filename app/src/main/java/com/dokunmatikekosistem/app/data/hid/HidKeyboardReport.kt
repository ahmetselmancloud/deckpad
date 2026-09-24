package com.dokunmatikekosistem.app.data.hid

import com.dokunmatikekosistem.app.domain.HidKeyChord

/** Builds the 8-byte boot-keyboard HID report: [modifier, reserved, key1..key6]. */
object HidKeyboardReport {
    const val MODIFIER_CTRL = 0x01
    const val MODIFIER_SHIFT = 0x02
    const val MODIFIER_ALT = 0x04
    const val MODIFIER_WIN = 0x08

    fun build(chord: HidKeyChord): ByteArray = byteArrayOf(
        chord.modifierBits.toByte(),
        0,
        chord.usageCode.toByte(),
        0, 0, 0, 0, 0
    )

    fun release(): ByteArray = ByteArray(8)
}
