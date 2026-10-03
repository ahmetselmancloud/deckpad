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

    private val symbolUsage: Map<Char, HidKeyChord> = mapOf(
        '.' to HidKeyChord(0, 0x37),
        ',' to HidKeyChord(0, 0x36),
        '/' to HidKeyChord(0, 0x38),
        '?' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x38),
        ';' to HidKeyChord(0, 0x33),
        ':' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x33),
        '\'' to HidKeyChord(0, 0x34),
        '"' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x34),
        '[' to HidKeyChord(0, 0x2F),
        '{' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x2F),
        ']' to HidKeyChord(0, 0x30),
        '}' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x30),
        '\\' to HidKeyChord(0, 0x31),
        '|' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x31),
        '-' to HidKeyChord(0, 0x2D),
        '_' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x2D),
        '=' to HidKeyChord(0, 0x2E),
        '+' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x2E),
        '!' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x1E),
        '@' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x1F),
        '#' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x20),
        '$' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x21),
        '%' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x22),
        '^' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x23),
        '&' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x24),
        '*' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x25),
        '(' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x26),
        ')' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x27),
        '`' to HidKeyChord(0, 0x35),
        '~' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x35),
        '<' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x36),
        '>' to HidKeyChord(HidKeyboardReport.MODIFIER_SHIFT, 0x37)
    )

    override fun mapChar(char: Char): HidKeyChord? {
        letterUsage[char.lowercaseChar()]?.let { usage ->
            val shift = if (char.isUpperCase()) HidKeyboardReport.MODIFIER_SHIFT else 0
            return HidKeyChord(modifierBits = shift, usageCode = usage)
        }
        digitUsage[char]?.let { usage -> return HidKeyChord(modifierBits = 0, usageCode = usage) }
        symbolUsage[char]?.let { return it }
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
