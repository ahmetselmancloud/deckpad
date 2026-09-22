package com.dokunmatikekosistem.app.domain

/** A physical HID key press: modifier bits (see HidKeyboardReport.MODIFIER_*) + USB HID usage code. */
data class HidKeyChord(val modifierBits: Int, val usageCode: Int)

/** Maps a displayed character to the physical key chord that produces it under this layout. */
interface KeyboardLayout {
    fun mapChar(char: Char): HidKeyChord?
}
