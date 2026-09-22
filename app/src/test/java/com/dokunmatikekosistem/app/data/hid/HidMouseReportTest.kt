package com.dokunmatikekosistem.app.data.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HidMouseReportTest {

    @Test
    fun `no movement no button produces zeroed report`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, leftButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 0, 0), report)
    }

    @Test
    fun `left button pressed sets bit 0 of buttons byte`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, leftButtonPressed = true)
        assertArrayEquals(byteArrayOf(1, 0, 0), report)
    }

    @Test
    fun `positive dx dy pass through unchanged`() {
        val report = HidMouseReport.build(dx = 10, dy = 20, leftButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 10, 20), report)
    }

    @Test
    fun `negative dx dy encode as signed bytes`() {
        val report = HidMouseReport.build(dx = -10, dy = -20, leftButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, (-10).toByte(), (-20).toByte()), report)
    }

    @Test
    fun `dx dy beyond range clamp to -127 and 127`() {
        val report = HidMouseReport.build(dx = 500, dy = -500, leftButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 127, (-127).toByte()), report)
    }
}
