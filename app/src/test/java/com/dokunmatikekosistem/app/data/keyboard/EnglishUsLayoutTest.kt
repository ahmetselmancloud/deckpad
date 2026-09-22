package com.dokunmatikekosistem.app.data.keyboard

import com.dokunmatikekosistem.app.domain.HidKeyChord
import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishUsLayoutTest {
    private val layout = EnglishUsLayout()

    @Test
    fun `lowercase a maps to usage 0x04 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x04), layout.mapChar('a'))
    }

    @Test
    fun `uppercase A maps to usage 0x04 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x04), layout.mapChar('A'))
    }

    @Test
    fun `digit 1 maps to usage 0x1E with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x1E), layout.mapChar('1'))
    }

    @Test
    fun `space maps to usage 0x2C`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x2C), layout.mapChar(' '))
    }

    @Test
    fun `turkish character returns null on english layout`() {
        assertEquals(null, layout.mapChar('ğ'))
    }
}
