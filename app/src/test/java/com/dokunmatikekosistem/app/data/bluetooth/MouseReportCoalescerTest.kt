package com.dokunmatikekosistem.app.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseReportCoalescerTest {

    private val coalescer = MouseReportCoalescer(minReportIntervalMs = 10L)

    @Test
    fun `button press is dispatched immediately`() {
        val decision = coalescer.onEvent(0, 0, leftButton = true, nowMs = 1000L)
        assertEquals(1, decision.reportsToSend.size)
        val report = decision.reportsToSend.first()
        assertTrue(report.leftButton)
        assertEquals(0, report.dx)
        assertEquals(0, report.dy)
        assertNull(decision.scheduleDelayMs)
    }

    @Test
    fun `button release is dispatched immediately even when deltas are zero (fixes dropped release)`() {
        // Step 1: Press
        coalescer.onEvent(0, 0, leftButton = true, nowMs = 1000L)

        // Step 2: Release 1ms later with zero deltas
        val decision = coalescer.onEvent(0, 0, leftButton = false, nowMs = 1001L)

        // Must NOT be deferred or dropped: immediate report required!
        assertEquals(1, decision.reportsToSend.size)
        val report = decision.reportsToSend.first()
        assertFalse(report.leftButton)
        assertEquals(0, report.dx)
        assertEquals(0, report.dy)
        assertNull(decision.scheduleDelayMs)
    }

    @Test
    fun `button press cancels pending flush and dispatches pending deltas with old button state first`() {
        // Baseline event at t=1000 (sends immediately, sets lastSendUptimeMs=1000)
        coalescer.onEvent(1, 1, leftButton = false, nowMs = 1000L)

        // Move with no button at t=1002 (<10ms, schedules flush)
        val moveDecision = coalescer.onEvent(5, 5, leftButton = false, nowMs = 1002L)
        assertEquals(0, moveDecision.reportsToSend.size)
        assertNotNull(moveDecision.scheduleDelayMs)

        // Click down arrives before flush timer
        val clickDecision = coalescer.onEvent(2, 3, leftButton = true, nowMs = 1004L)
        assertTrue(clickDecision.cancelPendingFlush)
        // Must emit 2 reports: 1st moves cursor to target position with button UP, 2nd presses button DOWN!
        assertEquals(2, clickDecision.reportsToSend.size)

        val moveReport = clickDecision.reportsToSend[0]
        assertFalse(moveReport.leftButton)
        assertEquals(5, moveReport.dx)
        assertEquals(5, moveReport.dy)

        val pressReport = clickDecision.reportsToSend[1]
        assertTrue(pressReport.leftButton)
        assertEquals(2, pressReport.dx)
        assertEquals(3, pressReport.dy)
    }

    @Test
    fun `relative movement is throttled when interval less than 10ms`() {
        // First event at t=1000 with interval met sends immediately
        val first = coalescer.onEvent(2, 2, nowMs = 1000L)
        assertEquals(1, first.reportsToSend.size)

        // Second event at t=1003 (<10ms) must be coalesced and scheduled
        val second = coalescer.onEvent(3, 4, nowMs = 1003L)
        assertEquals(0, second.reportsToSend.size)
        assertEquals(7L, second.scheduleDelayMs) // 10 - (1003 - 1000) = 7ms

        // Third event at t=1005 while flush already scheduled doesn't schedule again
        val third = coalescer.onEvent(1, 1, nowMs = 1005L)
        assertEquals(0, third.reportsToSend.size)
        assertNull(third.scheduleDelayMs)

        // Flush at t=1010 dispatches coalesced deltas (3+1=4, 4+1=5)
        val flushed = coalescer.onFlush(nowMs = 1010L)
        assertEquals(1, flushed.size)
        assertEquals(4, flushed.first().dx)
        assertEquals(5, flushed.first().dy)
    }

    @Test
    fun `large delta exceeding 127 is split into multiple reports without loss`() {
        // Move with 250 px delta
        val decision = coalescer.onEvent(250, -200, nowMs = 1000L)
        assertEquals(2, decision.reportsToSend.size)

        // Chunk 1: 127, -127
        assertEquals(127, decision.reportsToSend[0].dx)
        assertEquals(-127, decision.reportsToSend[0].dy)

        // Chunk 2: 250-127=123, -200 - (-127) = -73
        assertEquals(123, decision.reportsToSend[1].dx)
        assertEquals(-73, decision.reportsToSend[1].dy)

        // Total received by host equals exactly 250 and -200
        val sumDx = decision.reportsToSend.sumOf { it.dx }
        val sumDy = decision.reportsToSend.sumOf { it.dy }
        assertEquals(250, sumDx)
        assertEquals(-200, sumDy)
    }

    @Test
    fun `drag session keeps button state across movement and split reports`() {
        // Drag lock engaged
        val down = coalescer.onEvent(0, 0, leftButton = true, nowMs = 1000L)
        assertTrue(down.reportsToSend.first().leftButton)

        // Drag move > 127 with left button held
        val dragMove = coalescer.onEvent(150, 0, leftButton = true, nowMs = 1020L)
        assertEquals(2, dragMove.reportsToSend.size)
        assertTrue(dragMove.reportsToSend[0].leftButton)
        assertTrue(dragMove.reportsToSend[1].leftButton)
        assertEquals(127, dragMove.reportsToSend[0].dx)
        assertEquals(23, dragMove.reportsToSend[1].dx)

        // Drag lock released
        val up = coalescer.onEvent(0, 0, leftButton = false, nowMs = 1050L)
        assertEquals(1, up.reportsToSend.size)
        assertFalse(up.reportsToSend.first().leftButton)
    }

    @Test
    fun `drag release with pending movement finishes drag before releasing button`() {
        // Drag lock engaged at t=1000
        coalescer.onEvent(0, 0, leftButton = true, nowMs = 1000L)

        // Drag move at t=1002 (<10ms, gets buffered in pending)
        val move = coalescer.onEvent(15, 25, leftButton = true, nowMs = 1002L)
        assertEquals(0, move.reportsToSend.size)

        // Release at t=1004
        val release = coalescer.onEvent(0, 0, leftButton = false, nowMs = 1004L)
        assertEquals(2, release.reportsToSend.size)

        // Report 1 finishes drag with leftButton = true
        val dragFinish = release.reportsToSend[0]
        assertTrue(dragFinish.leftButton)
        assertEquals(15, dragFinish.dx)
        assertEquals(25, dragFinish.dy)

        // Report 2 releases left button at final spot
        val dropReport = release.reportsToSend[1]
        assertFalse(dropReport.leftButton)
        assertEquals(0, dropReport.dx)
        assertEquals(0, dropReport.dy)
    }

    @Test
    fun `forceReleaseAll resets buttons and flushes pending data`() {
        // Engage right click + pending delta
        coalescer.onEvent(10, 20, rightButton = true, nowMs = 1000L)

        // Add movement while right click held
        coalescer.onEvent(5, 5, rightButton = true, nowMs = 1002L)

        // Safety reset
        val resetReports = coalescer.forceReleaseAll(nowMs = 1005L)
        assertEquals(1, resetReports.size)
        val report = resetReports.first()
        assertFalse(report.rightButton)
        assertEquals(5, report.dx)
        assertEquals(5, report.dy)

        // Next event starts fresh with no pending data
        assertFalse(coalescer.hasPendingData)
        assertEquals(Triple(false, false, false), coalescer.currentButtonsState)
    }
}
