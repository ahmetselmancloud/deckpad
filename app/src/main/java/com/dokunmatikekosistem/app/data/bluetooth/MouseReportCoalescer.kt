package com.dokunmatikekosistem.app.data.bluetooth

/**
 * Single coalesced HID mouse report data container.
 */
data class CoalescedMouseReport(
    val dx: Int,
    val dy: Int,
    val wheel: Int,
    val pan: Int,
    val leftButton: Boolean,
    val rightButton: Boolean,
    val middleButton: Boolean
)

/**
 * Coalescing decision returned by [MouseReportCoalescer.onEvent].
 * @property reportsToSend Immediate reports that must be dispatched to the host.
 * @property scheduleDelayMs Delay in milliseconds to schedule a future flush, or null if not needed.
 * @property cancelPendingFlush True if any previously scheduled flush should be canceled.
 */
data class CoalesceDecision(
    val reportsToSend: List<CoalescedMouseReport>,
    val scheduleDelayMs: Long? = null,
    val cancelPendingFlush: Boolean = false
)

/**
 * Pure Kotlin mouse report coalescer with zero Android framework dependencies.
 *
 * Responsibilities:
 * 1. Immediate Dispatch on Button Transitions: Any transition of left, right, or
 *    middle mouse buttons (down OR up) immediately produces a report with all pending deltas,
 *    eliminating button-release drops and lag.
 * 2. Delta Preservation: Any pending movement deltas are merged into button transition reports
 *    so the cursor is precisely at the expected position when clicking or releasing.
 * 3. 100Hz Throttling / Coalescing: Pure relative motion and scroll events are throttled to
 *    [minReportIntervalMs] (~100Hz) to prevent Bluetooth buffer bloat.
 * 4. Delta Splitting (>127 Limit): Deltas exceeding HID 8-bit limits (-127..127) are split into
 *    multiple sequential reports rather than being clipped, preventing lost cursor distance on fast flicks.
 */
class MouseReportCoalescer(
    val minReportIntervalMs: Long = 10L
) {
    private var lastLeft = false
    private var lastRight = false
    private var lastMiddle = false

    private var pendingDx = 0
    private var pendingDy = 0
    private var pendingWheel = 0
    private var pendingPan = 0

    private var lastSendUptimeMs = 0L
    private var isFlushScheduled = false

    val hasPendingData: Boolean
        get() = pendingDx != 0 || pendingDy != 0 || pendingWheel != 0 || pendingPan != 0

    val currentButtonsState: Triple<Boolean, Boolean, Boolean>
        get() = Triple(lastLeft, lastRight, lastMiddle)

    /**
     * Process an incoming mouse report request.
     * @param nowMs Monotonic clock timestamp (e.g. SystemClock.uptimeMillis()).
     */
    fun onEvent(
        dx: Int,
        dy: Int,
        wheel: Int = 0,
        pan: Int = 0,
        leftButton: Boolean = false,
        rightButton: Boolean = false,
        middleButton: Boolean = false,
        nowMs: Long
    ): CoalesceDecision {
        val buttonChanged = (leftButton != lastLeft || rightButton != lastRight || middleButton != lastMiddle)

        if (buttonChanged) {
            val reports = mutableListOf<CoalescedMouseReport>()

            // 1. If there were pending movements prior to this button change, dispatch them
            // with the OLD button state so the cursor arrives at the target coordinate before
            // the click/release is registered.
            if (hasPendingData) {
                reports.addAll(splitIntoReports(pendingDx, pendingDy, pendingWheel, pendingPan, lastLeft, lastRight, lastMiddle))
                pendingDx = 0
                pendingDy = 0
                pendingWheel = 0
                pendingPan = 0
            }

            // 2. Dispatch the button transition with the NEW event's deltas and NEW button state
            reports.addAll(splitIntoReports(dx, dy, wheel, pan, leftButton, rightButton, middleButton))

            lastLeft = leftButton
            lastRight = rightButton
            lastMiddle = middleButton
            lastSendUptimeMs = nowMs

            val cancelFlush = isFlushScheduled
            isFlushScheduled = false

            return CoalesceDecision(
                reportsToSend = reports,
                scheduleDelayMs = null,
                cancelPendingFlush = cancelFlush
            )
        }

        // No button state change: accumulate deltas
        pendingDx += dx
        pendingDy += dy
        pendingWheel += wheel
        pendingPan += pan

        val elapsed = nowMs - lastSendUptimeMs
        if (elapsed >= minReportIntervalMs && !isFlushScheduled) {
            if (hasPendingData) {
                val reports = splitIntoReports(pendingDx, pendingDy, pendingWheel, pendingPan, lastLeft, lastRight, lastMiddle)
                pendingDx = 0
                pendingDy = 0
                pendingWheel = 0
                pendingPan = 0
                lastSendUptimeMs = nowMs
                return CoalesceDecision(reportsToSend = reports, scheduleDelayMs = null)
            }
            return CoalesceDecision(reportsToSend = emptyList())
        }

        if (!isFlushScheduled && hasPendingData) {
            isFlushScheduled = true
            val delayMs = (minReportIntervalMs - elapsed).coerceAtLeast(1L)
            return CoalesceDecision(reportsToSend = emptyList(), scheduleDelayMs = delayMs)
        }

        return CoalesceDecision(reportsToSend = emptyList())
    }

    /**
     * Called when a scheduled flush timer fires.
     * @param nowMs Monotonic clock timestamp.
     */
    fun onFlush(nowMs: Long): List<CoalescedMouseReport> {
        isFlushScheduled = false
        if (!hasPendingData) {
            return emptyList()
        }
        val reports = splitIntoReports(pendingDx, pendingDy, pendingWheel, pendingPan, lastLeft, lastRight, lastMiddle)
        pendingDx = 0
        pendingDy = 0
        pendingWheel = 0
        pendingPan = 0
        lastSendUptimeMs = nowMs
        return reports
    }

    /**
     * Emergency / safety reset: forces all mouse buttons to released and flushes any pending deltas.
     */
    fun forceReleaseAll(nowMs: Long): List<CoalescedMouseReport> {
        val anyButtonWasDown = lastLeft || lastRight || lastMiddle
        isFlushScheduled = false
        val totalDx = pendingDx
        val totalDy = pendingDy
        val totalWheel = pendingWheel
        val totalPan = pendingPan

        pendingDx = 0
        pendingDy = 0
        pendingWheel = 0
        pendingPan = 0
        lastLeft = false
        lastRight = false
        lastMiddle = false
        lastSendUptimeMs = nowMs

        return if (anyButtonWasDown || totalDx != 0 || totalDy != 0 || totalWheel != 0 || totalPan != 0) {
            splitIntoReports(totalDx, totalDy, totalWheel, totalPan, false, false, false)
        } else {
            emptyList()
        }
    }

    private fun splitIntoReports(
        dx: Int,
        dy: Int,
        wheel: Int,
        pan: Int,
        left: Boolean,
        right: Boolean,
        middle: Boolean
    ): List<CoalescedMouseReport> {
        if (dx == 0 && dy == 0 && wheel == 0 && pan == 0) {
            return listOf(CoalescedMouseReport(0, 0, 0, 0, left, right, middle))
        }

        val reports = mutableListOf<CoalescedMouseReport>()
        var remDx = dx
        var remDy = dy
        var remWheel = wheel
        var remPan = pan

        while (remDx != 0 || remDy != 0 || remWheel != 0 || remPan != 0) {
            val stepDx = remDx.coerceIn(-127, 127)
            val stepDy = remDy.coerceIn(-127, 127)
            val stepWheel = remWheel.coerceIn(-127, 127)
            val stepPan = remPan.coerceIn(-127, 127)

            remDx -= stepDx
            remDy -= stepDy
            remWheel -= stepWheel
            remPan -= stepPan

            reports.add(CoalescedMouseReport(stepDx, stepDy, stepWheel, stepPan, left, right, middle))
        }
        return reports
    }
}
