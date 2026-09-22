package com.dokunmatikekosistem.app.data.keyboard

import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import com.dokunmatikekosistem.app.domain.HidKeyChord
import com.dokunmatikekosistem.app.domain.KeyboardLayout

/**
 * Turkish Q layout. Regular a-z letters, digits, space/enter/backspace share the
 * same physical-key usage codes as English US (Turkish Q is QWERTY-based). The
 * dotless 'ı'/'I' share the physical position of the English "I" key (usage
 * 0x0C). Seven Turkish letters replace punctuation-key positions on the
 * physical Turkish Q keyboard:
 *   ğ/Ğ -> usage 0x2F (physical '[' key)
 *   ü/Ü -> usage 0x30 (physical ']' key)
 *   ş/Ş -> usage 0x33 (physical ';' key)
 *   i/İ -> usage 0x34 (physical ''' key) -- dotted i, distinct from dotless ı/I
 *   ö/Ö -> usage 0x36 (physical ',' key)
 *   ç/Ç -> usage 0x37 (physical '.' key)
 */
class TurkishQLayout : KeyboardLayout {

    private val base = EnglishUsLayout()

    private val turkishUsage: Map<Char, Int> = mapOf(
        'ğ' to 0x2F, 'Ğ' to 0x2F,
        'ü' to 0x30, 'Ü' to 0x30,
        'ş' to 0x33, 'Ş' to 0x33,
        'i' to 0x34, 'İ' to 0x34,
        'ö' to 0x36, 'Ö' to 0x36,
        'ç' to 0x37, 'Ç' to 0x37,
        'ı' to 0x0C, 'I' to 0x0C
    )

    private val turkishUppercase = setOf('Ğ', 'Ü', 'Ş', 'İ', 'Ö', 'Ç', 'I')

    override fun mapChar(char: Char): HidKeyChord? {
        // 'i'/'I' are looked up here too (dotted/dotless split), which shadows
        // EnglishUsLayout's plain a-z mapping for those two characters specifically.
        turkishUsage[char]?.let { usage ->
            val shift = if (char in turkishUppercase) HidKeyboardReport.MODIFIER_SHIFT else 0
            return HidKeyChord(modifierBits = shift, usageCode = usage)
        }
        // Everything else (a-z except i/I, digits, space/enter/backspace) is
        // physically unchanged from English US.
        return base.mapChar(char)
    }
}
