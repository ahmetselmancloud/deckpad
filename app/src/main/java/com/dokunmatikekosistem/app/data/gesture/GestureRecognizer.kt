package com.dokunmatikekosistem.app.data.gesture

import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAP_MAX_MOVEMENT_PX = 10f
private const val TAP_MAX_DURATION_MS = 200L
private const val TWO_FINGER_DOWN_WINDOW_MS = 150L
private const val DRAG_LOCK_TAP_GAP_MS = 300L
private const val DRAG_LOCK_HOLD_MS = 150L

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
}

private class ActivePointer(var lastX: Float, var lastY: Float, val downX: Float, val downY: Float, val downTimeMs: Long) {
    var totalMovement = 0f
}

class GestureRecognizer {

    private val active = mutableMapOf<Int, ActivePointer>()
    private var lastTapUpTimeMs: Long? = null
    private var dragLockPointerId: Int? = null
    private var dragLockEngagedSent = false

    // Tracks the current multi-touch "session": from the first PointerDown
    // after all pointers were previously up, until all pointers are up again.
    private var sessionMaxPointers = 0
    private var sessionAllTapsSoFar = true

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

        // Drag-lock candidate: a second down shortly after the previous tap's up,
        // and this is the only pointer down right now (not part of a 2-finger session).
        val gap = lastTapUpTimeMs?.let { event.timeMs - it }
        if (active.size == 1 && gap != null && gap in 0..DRAG_LOCK_TAP_GAP_MS) {
            dragLockPointerId = event.id
        }
        return null
    }

    private fun onMove(event: RawTouchEvent.PointerMove): RecognizedGesture? {
        val pointer = active[event.id] ?: return null
        val dx = (event.x - pointer.lastX)
        val dy = (event.y - pointer.lastY)
        pointer.totalMovement += kotlin.math.hypot(dx, dy)
        pointer.lastX = event.x
        pointer.lastY = event.y

        if (pointer.totalMovement > TAP_MAX_MOVEMENT_PX) {
            sessionAllTapsSoFar = false
        }

        if (event.id == dragLockPointerId) {
            val heldMs = event.timeMs - pointer.downTimeMs
            if (!dragLockEngagedSent) {
                if (heldMs >= DRAG_LOCK_HOLD_MS) {
                    dragLockEngagedSent = true
                    return RecognizedGesture.DragLockEngaged
                }
                return null
            }
            return RecognizedGesture.DragMove(dx.roundToInt(), dy.roundToInt())
        }

        if (active.size == 2) {
            val other = active.entries.first { it.key != event.id }.value
            if (other.totalMovement > TAP_MAX_MOVEMENT_PX && pointer.totalMovement > TAP_MAX_MOVEMENT_PX) {
                return RecognizedGesture.Scroll(vDelta = dy.roundToInt(), hDelta = dx.roundToInt())
            }
            return null
        }

        if (active.size == 1) {
            return RecognizedGesture.CursorMove(dx.roundToInt(), dy.roundToInt())
        }
        return null
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
            if (active.isEmpty()) { sessionMaxPointers = 0; sessionAllTapsSoFar = true }
            return if (wasEngaged) RecognizedGesture.DragLockReleased else null
        }

        if (active.isNotEmpty()) return null

        // All pointers are now up: decide what this session was.
        val result = when {
            sessionMaxPointers >= 2 && sessionAllTapsSoFar -> RecognizedGesture.RightClick
            sessionMaxPointers == 1 && isTap -> {
                lastTapUpTimeMs = event.timeMs
                RecognizedGesture.LeftClick
            }
            else -> null
        }
        sessionMaxPointers = 0
        sessionAllTapsSoFar = true
        return result
    }
}
