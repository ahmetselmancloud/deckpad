package com.dokunmatikekosistem.app.data.keyboard

import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import com.dokunmatikekosistem.app.domain.HidKeyChord
import com.dokunmatikekosistem.app.domain.KeyboardLayout

/** Standard US QWERTY: HID usage codes match physical key labels directly. */
class EnglishUsLayout : KeyboardLayout {

    private val letterUsage: Map<Char, Int> = ('a'..'z').mapIndexed { index, char -> char to (0x04 + index) }.toMap()
    private val digitUsage: Map<Char, Int> = mapOf(
        '1' to 0x1E, '2' to 0x1F, '3' to 0x20, '4' to 0x21, '5' to 0x22,
        '6' to 0x23, '7' to 0x24, '8' to 0x25, '9' to 0x26, '0' to 0x27
    )

    override fun mapChar(char: Char): HidKeyChord? {
        letterUsage[char.lowercaseChar()]?.let { usage ->
            val shift = if (char.isUpperCase()) HidKeyboardReport.MODIFIER_SHIFT else 0
            return HidKeyChord(modifierBits = shift, usageCode = usage)
        }
        digitUsage[char]?.let { usage -> return HidKeyChord(modifierBits = 0, usageCode = usage) }
        return when (char) {
            ' ' -> HidKeyChord(modifierBits = 0, usageCode = 0x2C)
            '\n' -> HidKeyChord(modifierBits = 0, usageCode = 0x28)
            '\b' -> HidKeyChord(modifierBits = 0, usageCode = 0x2A)
            else -> null
        }
    }

    override fun displayRows(): List<List<Char>> = listOf(
        "1234567890".toList(),
        "qwertyuiop".toList(),
        "asdfghjkl".toList(),
        "zxcvbnm".toList()
    )

    override fun shiftedChar(baseChar: Char): Char = baseChar.uppercaseChar()
}
