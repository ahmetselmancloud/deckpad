package com.dokunmatikekosistem.app.data.hid

import com.dokunmatikekosistem.app.domain.HidKeyChord
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HidKeyboardReportTest {

    @Test
    fun `build with no modifier produces modifier byte 0`() {
        val report = HidKeyboardReport.build(HidKeyChord(modifierBits = 0, usageCode = 0x04))
        assertArrayEquals(byteArrayOf(0, 0, 0x04, 0, 0, 0, 0, 0), report)
    }

    @Test
    fun `build with shift modifier sets modifier byte`() {
        val report = HidKeyboardReport.build(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x04))
        assertArrayEquals(byteArrayOf(0x02, 0, 0x04, 0, 0, 0, 0, 0), report)
    }

    @Test
    fun `release produces all-zero 8-byte report`() {
        val report = HidKeyboardReport.release()
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0), report)
    }

    @Test
    fun `build with combined ctrl and alt modifiers sets both bits`() {
        val report = HidKeyboardReport.build(
            HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_ALT, usageCode = 0x4C)
        )
        assertArrayEquals(byteArrayOf(0x05, 0, 0x4C, 0, 0, 0, 0, 0), report)
    }

    @Test
    fun `win modifier constant is bit 3`() {
        assertEquals(0x08, HidKeyboardReport.MODIFIER_WIN)
    }
}
