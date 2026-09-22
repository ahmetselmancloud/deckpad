package com.dokunmatikekosistem.app.data.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HidMouseReportTest {

    @Test
    fun `all zero produces zeroed 5-byte report`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, wheelDelta = 0, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0), report)
    }

    @Test
    fun `left button sets bit 0`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, wheelDelta = 0, panDelta = 0, leftButtonPressed = true, rightButtonPressed = false)
        assertArrayEquals(byteArrayOf(1, 0, 0, 0, 0), report)
    }

    @Test
    fun `right button sets bit 1`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, wheelDelta = 0, panDelta = 0, leftButtonPressed = false, rightButtonPressed = true)
        assertArrayEquals(byteArrayOf(2, 0, 0, 0, 0), report)
    }

    @Test
    fun `both buttons set bits 0 and 1`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, wheelDelta = 0, panDelta = 0, leftButtonPressed = true, rightButtonPressed = true)
        assertArrayEquals(byteArrayOf(3, 0, 0, 0, 0), report)
    }

    @Test
    fun `positive dx dy wheel pan pass through unchanged`() {
        val report = HidMouseReport.build(dx = 10, dy = 20, wheelDelta = 3, panDelta = 4, leftButtonPressed = false, rightButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 10, 20, 3, 4), report)
    }

    @Test
    fun `negative values encode as signed bytes`() {
        val report = HidMouseReport.build(dx = -10, dy = -20, wheelDelta = -3, panDelta = -4, leftButtonPressed = false, rightButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, (-10).toByte(), (-20).toByte(), (-3).toByte(), (-4).toByte()), report)
    }

    @Test
    fun `all four movement fields clamp to -127 and 127 independently`() {
        val report = HidMouseReport.build(dx = 500, dy = -500, wheelDelta = 500, panDelta = -500, leftButtonPressed = false, rightButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 127, (-127).toByte(), 127, (-127).toByte()), report)
    }
}
