package com.dokunmatikekosistem.app.data.hid

import com.dokunmatikekosistem.app.domain.HidKeyChord
import org.junit.Assert.assertArrayEquals
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
}
