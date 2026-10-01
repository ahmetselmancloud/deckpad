package com.dokunmatikekosistem.app.data.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HidConsumerReportTest {

    @Test
    fun `build with play pause produces bit 0`() {
        val report = HidConsumerReport.build(HidConsumerReport.PLAY_PAUSE)
        assertArrayEquals(byteArrayOf(0x01), report)
    }

    @Test
    fun `build with next track produces bit 1`() {
        val report = HidConsumerReport.build(HidConsumerReport.SCAN_NEXT_TRACK)
        assertArrayEquals(byteArrayOf(0x02), report)
    }

    @Test
    fun `build with prev track produces bit 2`() {
        val report = HidConsumerReport.build(HidConsumerReport.SCAN_PREV_TRACK)
        assertArrayEquals(byteArrayOf(0x04), report)
    }

    @Test
    fun `build with stop produces bit 3`() {
        val report = HidConsumerReport.build(HidConsumerReport.STOP)
        assertArrayEquals(byteArrayOf(0x08), report)
    }

    @Test
    fun `build with volume increment produces bit 4`() {
        val report = HidConsumerReport.build(HidConsumerReport.VOLUME_INCREMENT)
        assertArrayEquals(byteArrayOf(0x10), report)
    }

    @Test
    fun `build with volume decrement produces bit 5`() {
        val report = HidConsumerReport.build(HidConsumerReport.VOLUME_DECREMENT)
        assertArrayEquals(byteArrayOf(0x20), report)
    }

    @Test
    fun `build with mute produces bit 6`() {
        val report = HidConsumerReport.build(HidConsumerReport.MUTE)
        assertArrayEquals(byteArrayOf(0x40), report)
    }

    @Test
    fun `release produces 1-byte 0`() {
        val report = HidConsumerReport.release()
        assertArrayEquals(byteArrayOf(0x00), report)
    }
}
