package com.dokunmatikekosistem.app.data.gesture

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val TAP_MAX_MOVEMENT_PX = 10f
private const val TAP_MAX_DURATION_MS = 200L
private const val TWO_FINGER_DOWN_WINDOW_MS = 150L
private const val DRAG_LOCK_TAP_GAP_MS = 300L
private const val DRAG_LOCK_HOLD_MS = 150L
private const val SCROLL_PX_PER_UNIT = 24f
private const val SWIPE_MIN_DISTANCE_PX = 60f

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

sealed interface RawTouchEvent {
    val id: Int
    val x: Float
    val y: Float
    val timeMs: Long

    data class PointerDown(override val id: Int, override val x: Float, override val y: Float, override val timeMs: Long) : RawTouchEvent
    data class PointerMove(override val id: Int, override val x: Float, override val y: Float, override val timeMs: Long) : RawTouchEvent
    data class PointerUp(override val id: Int, override val x: Float, override val y: Float, override val timeMs: Long) : RawTouchEvent
}

sealed interface RecognizedGesture {
    data class CursorMove(val dx: Int, val dy: Int) : RecognizedGesture
    object LeftClick : RecognizedGesture
    object RightClick : RecognizedGesture
    data class Scroll(val vDelta: Int, val hDelta: Int) : RecognizedGesture
    object DragLockEngaged : RecognizedGesture
    data class DragMove(val dx: Int, val dy: Int) : RecognizedGesture
    object DragLockReleased : RecognizedGesture
    data class ThreeFingerSwipe(val direction: SwipeDirection) : RecognizedGesture
    object ThreeFingerTap : RecognizedGesture
    data class FourFingerSwipe(val direction: SwipeDirection) : RecognizedGesture
    object FourFingerTap : RecognizedGesture
}

private class ActivePointer(var lastX: Float, var lastY: Float, val downX: Float, val downY: Float, val downTimeMs: Long) {
    var totalMovement = 0f
    var residualX = 0f
    var residualY = 0f
}

/**
 * Classifies raw multi-touch pointer events into recognized gestures.
 *
 * Call [reset] if the caller detects that touch input was interrupted
 * without a matching PointerUp for every currently-down pointer (e.g. a
 * Compose gesture cancellation) — otherwise a "stuck" pointer entry would
 * corrupt classification for the rest of this instance's lifetime.
 */
class GestureRecognizer {

    private val active = mutableMapOf<Int, ActivePointer>()
    private var lastTapUpTimeMs: Long? = null
    private var dragLockPointerId: Int? = null
    private var dragLockEngagedSent = false

    // Tracks the current multi-touch "session": from the first PointerDown
    // after all pointers were previously up, until all pointers are up again.
    private var sessionMaxPointers = 0
    private var sessionAllTapsSoFar = true
    private var firstDownTimeMsInSession: Long? = null

    // Net displacement of the very first finger down this session, used to
    // classify 3/4-finger swipe direction once the session ends.
    private var sessionFirstPointerId: Int? = null
    private var sessionFirstNetDx = 0f
    private var sessionFirstNetDy = 0f

    // Pixel-to-detent accumulation for two-finger scroll, shared across
    // whichever pointer is currently the canonical (lowest-id) reporter.
    private var scrollResidualV = 0f
    private var scrollResidualH = 0f

    /** Clears all tracked pointer/session/drag-lock state. Safe to call at any time. */
    fun reset() {
        active.clear()
        lastTapUpTimeMs = null
        dragLockPointerId = null
        dragLockEngagedSent = false
        resetSession()
    }

    fun onEvent(event: RawTouchEvent): RecognizedGesture? {
        return when (event) {
            is RawTouchEvent.PointerDown -> onDown(event)
            is RawTouchEvent.PointerMove -> onMove(event)
            is RawTouchEvent.PointerUp -> onUp(event)
        }
    }

    private fun onDown(event: RawTouchEvent.PointerDown): RecognizedGesture? {
        active[event.id] = ActivePointer(event.x, event.y, event.x, event.y, event.timeMs)
        sessionMaxPointers = maxOf(sessionMaxPointers, active.size)

        if (active.size == 1) {
            firstDownTimeMsInSession = event.timeMs
            sessionFirstPointerId = event.id
            // Drag-lock candidate: a second down shortly after the previous tap's up.
            val gap = lastTapUpTimeMs?.let { event.timeMs - it }
            if (gap != null && gap in 0..DRAG_LOCK_TAP_GAP_MS) {
                dragLockPointerId = event.id
            }
        } else if (active.size == 2) {
            val gap = event.timeMs - (firstDownTimeMsInSession ?: event.timeMs)
            if (gap > TWO_FINGER_DOWN_WINDOW_MS) {
                sessionAllTapsSoFar = false
            }
            // A second finger arriving turns this into a two-finger gesture
            // (scroll or tap) rather than a drag — but never interrupt a
            // drag-lock that has ALREADY engaged (a stray extra contact
            // shouldn't cancel an in-progress drag).
            if (!dragLockEngagedSent) {
                dragLockPointerId = null
            }
        }
        return null
    }

    private fun onMove(event: RawTouchEvent.PointerMove): RecognizedGesture? {
        val pointer = active[event.id] ?: return null
        val rawDx = event.x - pointer.lastX
        val rawDy = event.y - pointer.lastY
        pointer.totalMovement += hypot(rawDx, rawDy)
        pointer.lastX = event.x
        pointer.lastY = event.y

        if (event.id == sessionFirstPointerId) {
            sessionFirstNetDx += rawDx
            sessionFirstNetDy += rawDy
        }

        if (pointer.totalMovement > TAP_MAX_MOVEMENT_PX) {
            sessionAllTapsSoFar = false
        }

        if (event.id == dragLockPointerId) {
            val heldMs = event.timeMs - pointer.downTimeMs
            if (!dragLockEngagedSent) {
                // Require real movement, not just elapsed time, before sending the mouse
                // button-down to the host: engaging on a near-stationary hold lets Windows'
                // own double-click detection mistake a fast, tiny drag for a second click
                // (e.g. un-maximizing a snapped window instead of dragging it).
                if (heldMs >= DRAG_LOCK_HOLD_MS && pointer.totalMovement > TAP_MAX_MOVEMENT_PX) {
                    dragLockEngagedSent = true
                    return RecognizedGesture.DragLockEngaged
                }
                return null
            }
            return emitResidualMove(pointer, rawDx, rawDy) { dx, dy -> RecognizedGesture.DragMove(dx, dy) }
        }

        if (active.size == 2 && dragLockPointerId == null) {
            val other = active.entries.first { it.key != event.id }.value
            if (other.totalMovement > TAP_MAX_MOVEMENT_PX && pointer.totalMovement > TAP_MAX_MOVEMENT_PX) {
                // Only the canonical (lowest-id) pointer emits Scroll, so a
                // two-finger move doesn't fire twice per frame.
                val canonicalId = active.keys.min()
                if (event.id != canonicalId) return null
                scrollResidualV += rawDy
                scrollResidualH += rawDx
                val vUnits = (scrollResidualV / SCROLL_PX_PER_UNIT).toInt()
                val hUnits = (scrollResidualH / SCROLL_PX_PER_UNIT).toInt()
                if (vUnits == 0 && hUnits == 0) return null
                scrollResidualV -= vUnits * SCROLL_PX_PER_UNIT
                scrollResidualH -= hUnits * SCROLL_PX_PER_UNIT
                return RecognizedGesture.Scroll(vDelta = vUnits, hDelta = hUnits)
            }
            return null
        }

        if (active.size == 1 && sessionMaxPointers <= 1) {
            return emitResidualMove(pointer, rawDx, rawDy) { dx, dy -> RecognizedGesture.CursorMove(dx, dy) }
        }
        // A lone remaining pointer from what WAS a multi-touch session (e.g.
        // the tail end of a two-finger scroll after one finger lifted) must
        // not suddenly start moving the cursor.
        return null
    }

    private inline fun emitResidualMove(pointer: ActivePointer, rawDx: Float, rawDy: Float, build: (Int, Int) -> RecognizedGesture): RecognizedGesture? {
        pointer.residualX += rawDx
        pointer.residualY += rawDy
        val dx = pointer.residualX.roundToInt()
        val dy = pointer.residualY.roundToInt()
        pointer.residualX -= dx
        pointer.residualY -= dy
        return if (dx != 0 || dy != 0) build(dx, dy) else null
    }

    private fun onUp(event: RawTouchEvent.PointerUp): RecognizedGesture? {
        val pointer = active.remove(event.id) ?: return null
        val durationMs = event.timeMs - pointer.downTimeMs
        val isTap = pointer.totalMovement <= TAP_MAX_MOVEMENT_PX && durationMs <= TAP_MAX_DURATION_MS
        if (!isTap) sessionAllTapsSoFar = false

        if (event.id == dragLockPointerId) {
            val wasEngaged = dragLockEngagedSent
            dragLockPointerId = null
            dragLockEngagedSent = false
            if (active.isEmpty()) resetSession()
            return when {
                wasEngaged -> RecognizedGesture.DragLockReleased
                isTap -> {
                    lastTapUpTimeMs = event.timeMs
                    RecognizedGesture.LeftClick
                }
                else -> null
            }
        }

        if (active.isNotEmpty()) return null

        // All pointers are now up: decide what this session was.
        val direction = classifySwipeDirection()
        val result = when {
            sessionMaxPointers == 4 && sessionAllTapsSoFar -> RecognizedGesture.FourFingerTap
            sessionMaxPointers == 4 && direction != null -> RecognizedGesture.FourFingerSwipe(direction)
            sessionMaxPointers == 3 && sessionAllTapsSoFar -> RecognizedGesture.ThreeFingerTap
            sessionMaxPointers == 3 && direction != null -> RecognizedGesture.ThreeFingerSwipe(direction)
            sessionMaxPointers == 2 && sessionAllTapsSoFar -> RecognizedGesture.RightClick
            sessionMaxPointers == 1 && isTap -> {
                lastTapUpTimeMs = event.timeMs
                RecognizedGesture.LeftClick
            }
            else -> null
        }
        resetSession()
        return result
    }

    private fun classifySwipeDirection(): SwipeDirection? {
        val absDx = abs(sessionFirstNetDx)
        val absDy = abs(sessionFirstNetDy)
        if (maxOf(absDx, absDy) < SWIPE_MIN_DISTANCE_PX) return null
        return if (absDx >= absDy) {
            if (sessionFirstNetDx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
        } else {
            if (sessionFirstNetDy > 0) SwipeDirection.DOWN else SwipeDirection.UP
        }
    }

    private fun resetSession() {
        sessionMaxPointers = 0
        sessionAllTapsSoFar = true
        firstDownTimeMsInSession = null
        scrollResidualV = 0f
        scrollResidualH = 0f
        sessionFirstPointerId = null
        sessionFirstNetDx = 0f
        sessionFirstNetDy = 0f
    }
}
