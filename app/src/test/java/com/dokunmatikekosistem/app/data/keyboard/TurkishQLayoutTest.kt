package com.dokunmatikekosistem.app.data.keyboard

import com.dokunmatikekosistem.app.domain.HidKeyChord
import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import org.junit.Assert.assertEquals
import org.junit.Test

class TurkishQLayoutTest {
    private val layout = TurkishQLayout()

    @Test
    fun `lowercase a maps the same as english (unchanged physical position)`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x04), layout.mapChar('a'))
    }

    @Test
    fun `g breve lowercase maps to usage 0x2F with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x2F), layout.mapChar('ğ'))
    }

    @Test
    fun `G breve uppercase maps to usage 0x2F with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x2F), layout.mapChar('Ğ'))
    }

    @Test
    fun `u umlaut lowercase maps to usage 0x30 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x30), layout.mapChar('ü'))
    }

    @Test
    fun `U umlaut uppercase maps to usage 0x30 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x30), layout.mapChar('Ü'))
    }

    @Test
    fun `s cedilla lowercase maps to usage 0x33 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x33), layout.mapChar('ş'))
    }

    @Test
    fun `S cedilla uppercase maps to usage 0x33 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x33), layout.mapChar('Ş'))
    }

    @Test
    fun `dotted lowercase i maps to usage 0x34 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x34), layout.mapChar('i'))
    }

    @Test
    fun `dotted uppercase I maps to usage 0x34 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x34), layout.mapChar('İ'))
    }

    @Test
    fun `o umlaut lowercase maps to usage 0x36 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x36), layout.mapChar('ö'))
    }

    @Test
    fun `O umlaut uppercase maps to usage 0x36 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x36), layout.mapChar('Ö'))
    }

    @Test
    fun `c cedilla lowercase maps to usage 0x37 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x37), layout.mapChar('ç'))
    }

    @Test
    fun `C cedilla uppercase maps to usage 0x37 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x37), layout.mapChar('Ç'))
    }

    @Test
    fun `dotless lowercase i maps to usage 0x0C with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x0C), layout.mapChar('ı'))
    }

    @Test
    fun `dotless uppercase I maps to usage 0x0C with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x0C), layout.mapChar('I'))
    }

    @Test
    fun `display rows show turkish letters in their physical positions`() {
        assertEquals(
            listOf(
                "1234567890".toList(),
                "qwertyuıopğü".toList(),
                "asdfghjklşi".toList(),
                "zxcvbnmöç".toList()
            ),
            layout.displayRows()
        )
    }

    @Test
    fun `shiftedChar maps dotless i to dotless I`() {
        assertEquals('I', layout.shiftedChar('ı'))
    }

    @Test
    fun `shiftedChar maps dotted i to dotted I`() {
        assertEquals('İ', layout.shiftedChar('i'))
    }

    @Test
    fun `shiftedChar maps g breve to uppercase g breve`() {
        assertEquals('Ğ', layout.shiftedChar('ğ'))
    }

    @Test
    fun `shiftedChar falls back to english uppercasing for regular letters`() {
        assertEquals('Q', layout.shiftedChar('q'))
    }

    @Test
    fun `punctuation symbols map to standard turkish Q physical keys`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x38), layout.mapChar('.'))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x38), layout.mapChar(':'))
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x31), layout.mapChar(','))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x31), layout.mapChar(';'))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x2D), layout.mapChar('?'))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x1E), layout.mapChar('!'))
    }

    @Test
    fun `altgr symbols map to correct usage codes and modifier`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_ALT_GR, usageCode = 0x14), layout.mapChar('@'))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_ALT_GR, usageCode = 0x20), layout.mapChar('#'))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_ALT_GR, usageCode = 0x21), layout.mapChar('$'))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_ALT_GR, usageCode = 0x24), layout.mapChar('{'))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_ALT_GR, usageCode = 0x25), layout.mapChar('['))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_ALT_GR, usageCode = 0x26), layout.mapChar(']'))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_ALT_GR, usageCode = 0x27), layout.mapChar('}'))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_ALT_GR, usageCode = 0x2D), layout.mapChar('\\'))
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_ALT_GR, usageCode = 0x2E), layout.mapChar('|'))
    }
}
