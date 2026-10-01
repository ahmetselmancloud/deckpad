package com.dokunmatikekosistem.app.data.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRecognizerTest {

    @Test
    fun `single tap under threshold produces LeftClick on up`() {
        val recognizer = GestureRecognizer()
        assertNull(recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0)))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 102f, y = 101f, timeMs = 100))
        assertEquals(RecognizedGesture.LeftClick, result)
    }

    @Test
    fun `single pointer move produces CursorMove with delta since last event`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 115f, y = 108f, timeMs = 16))
        assertEquals(RecognizedGesture.CursorMove(dx = 15, dy = 8), result)
    }

    @Test
    fun `two finger simultaneous tap produces RightClick`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 30))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 101f, y = 100f, timeMs = 120))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 201f, y = 100f, timeMs = 130))
        assertEquals(RecognizedGesture.RightClick, result)
    }

    @Test
    fun `two fingers moving together produce Scroll from the canonical pointer`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 130f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 130f, timeMs = 60))
        // Both pointers have now crossed the tap-movement threshold; a further
        // move of the canonical (lowest-id) pointer emits Scroll.
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 160f, timeMs = 70))
        assertTrue(result is RecognizedGesture.Scroll)
    }

    @Test
    fun `scroll accumulates pixels and emits whole units with residual carried`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 130f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 130f, timeMs = 60))
        val underThreshold = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 148f, timeMs = 70)) // 18px < 24px/unit
        assertNull(underThreshold)
        val overThreshold = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 160f, timeMs = 80)) // +12px = 30px total -> 1 unit, 6px carried
        assertEquals(RecognizedGesture.Scroll(vDelta = 1, hDelta = 0), overThreshold)
    }

    @Test
    fun `only the canonical pointer emits Scroll, not both fingers`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 130f, timeMs = 50))
        // id 1 is the non-canonical (higher-id) pointer; even though both are
        // now past the movement threshold, id 1's own move must not emit.
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 130f, timeMs = 60))
        assertNull(result)
    }

    @Test
    fun `three finger tap produces ThreeFingerTap`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 100f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 100f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 100f, timeMs = 120))
        assertEquals(RecognizedGesture.ThreeFingerTap, result)
    }

    @Test
    fun `single remaining finger after two-finger scroll does not produce CursorMove`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 130f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 130f, timeMs = 60))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 130f, timeMs = 70))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 160f, timeMs = 80))
        assertNull(result)
    }

    @Test
    fun `tap then hold-and-drag engages drag lock only once held long enough and moved enough, then moves then releases`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 80))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 100f, y = 100f, timeMs = 200))
        val notYetEngaged = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 100f, y = 100f, timeMs = 300))
        assertNull(notYetEngaged)
        val engaged = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 120f, y = 100f, timeMs = 360))
        assertEquals(RecognizedGesture.DragLockEngaged, engaged)
        val moved = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 140f, y = 100f, timeMs = 400))
        assertEquals(RecognizedGesture.DragMove(dx = 20, dy = 0), moved)
        val released = recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 140f, y = 100f, timeMs = 500))
        assertEquals(RecognizedGesture.DragLockReleased, released)
    }

    @Test
    fun `holding still past the hold-time threshold never engages drag lock without movement`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 80))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 100f, y = 100f, timeMs = 200))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 100f, y = 100f, timeMs = 400))
        assertNull(result)
    }

    @Test
    fun `moving past the movement threshold before the hold-time elapses does not engage drag lock early`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 80))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 100f, y = 100f, timeMs = 200))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 130f, y = 100f, timeMs = 210))
        assertNull(result)
    }

    @Test
    fun `two finger tap outside the down-window is not RightClick`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 190))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 195))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 100f, timeMs = 350))
        assertNull(result)
    }

    @Test
    fun `a third finger moving during an active drag lock does not produce Scroll`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 80))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 100f, y = 100f, timeMs = 200))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 120f, y = 100f, timeMs = 360)) // engages drag lock
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 100f, timeMs = 400))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 2, x = 300f, y = 140f, timeMs = 420))
        assertTrue(result !is RecognizedGesture.Scroll)
    }

    @Test
    fun `a quick second tap that never engages drag lock produces LeftClick`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 80))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 100f, y = 100f, timeMs = 200))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 101f, y = 100f, timeMs = 260))
        assertEquals(RecognizedGesture.LeftClick, result)
    }

    @Test
    fun `a tap followed by a second finger joining does not engage drag lock (becomes two-finger gesture instead)`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 80))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 100f, y = 100f, timeMs = 200))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 200f, y = 100f, timeMs = 210))
        val moveResult = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 100f, y = 130f, timeMs = 260))
        assertTrue(moveResult !is RecognizedGesture.DragLockEngaged)
    }

    @Test
    fun `three finger swipe up produces ThreeFingerSwipe UP`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 300f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 300f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 300f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 200f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 300f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 300f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 200f, timeMs = 120))
        assertEquals(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.UP), result)
    }

    @Test
    fun `three finger swipe down produces ThreeFingerSwipe DOWN`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 100f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 200f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 100f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 100f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 200f, timeMs = 120))
        assertEquals(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.DOWN), result)
    }

    @Test
    fun `three finger swipe left produces ThreeFingerSwipe LEFT`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 300f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 300f, y = 200f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 300f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 200f, y = 100f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 300f, y = 200f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 300f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 200f, y = 100f, timeMs = 120))
        assertEquals(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.LEFT), result)
    }

    @Test
    fun `three finger swipe right produces ThreeFingerSwipe RIGHT`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 100f, y = 200f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 100f, y = 300f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 200f, y = 100f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 100f, y = 200f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 100f, y = 300f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 200f, y = 100f, timeMs = 120))
        assertEquals(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.RIGHT), result)
    }

    @Test
    fun `four finger swipe up produces FourFingerSwipe UP`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 300f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 300f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 300f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 3, x = 400f, y = 300f, timeMs = 30))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 200f, timeMs = 60))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 300f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 300f, timeMs = 110))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 3, x = 400f, y = 300f, timeMs = 120))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 200f, timeMs = 130))
        assertEquals(RecognizedGesture.FourFingerSwipe(SwipeDirection.UP), result)
    }

    @Test
    fun `four finger swipe left produces FourFingerSwipe LEFT`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 400f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 400f, y = 200f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 400f, y = 300f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 3, x = 400f, y = 400f, timeMs = 30))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 300f, y = 100f, timeMs = 60))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 400f, y = 200f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 400f, y = 300f, timeMs = 110))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 3, x = 400f, y = 400f, timeMs = 120))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 300f, y = 100f, timeMs = 130))
        assertEquals(RecognizedGesture.FourFingerSwipe(SwipeDirection.LEFT), result)
    }

    @Test
    fun `four finger tap produces FourFingerTap`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 100f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 3, x = 400f, y = 100f, timeMs = 30))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 100f, timeMs = 110))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 100f, timeMs = 120))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 3, x = 400f, y = 100f, timeMs = 130))
        assertEquals(RecognizedGesture.FourFingerTap, result)
    }

    @Test
    fun `three finger session with movement below the swipe threshold and above the tap threshold produces nothing`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 100f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 130f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 100f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 100f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 130f, timeMs = 120))
        assertNull(result)
    }

    @Test
    fun `pinch apart engages zoom, emits deltas, and ends when a finger lifts`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 150f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 250f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 280f, y = 100f, timeMs = 30))
        val seeded = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 120f, y = 100f, timeMs = 40))
        assertNull(seeded)
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 330f, y = 100f, timeMs = 50))
        val started = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 90f, y = 100f, timeMs = 60))
        assertEquals(RecognizedGesture.PinchZoomStarted, started)
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 380f, y = 100f, timeMs = 70))
        val delta = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 40f, y = 100f, timeMs = 80))
        assertEquals(RecognizedGesture.PinchZoomDelta(units = 2), delta)
        val ended = recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 380f, y = 100f, timeMs = 90))
        assertEquals(RecognizedGesture.PinchZoomEnded, ended)
    }

    @Test
    fun `two fingers moving together in parallel do not trigger pinch zoom`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 130f, timeMs = 30))
        val seeded = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 130f, timeMs = 40))
        assertNull(seeded)
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 160f, timeMs = 50))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 160f, timeMs = 60))
        assertTrue(result is RecognizedGesture.Scroll)
    }

    @Test
    fun `pinch still engages when the canonical pointer seeds and reports again before its partner`() {
        // Reversed order from the other pinch test: here the LOWEST-id (canonical)
        // pointer is the one whose move first makes both fingers qualify, and it
        // then reports a SECOND move with no event from its partner in between —
        // exactly the ordering that can happen on real hardware. The canonical
        // pointer's second move must not be misread as Scroll just because its
        // own single-frame movement alone can't prove a pinch.
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 150f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 250f, y = 100f, timeMs = 10))
        // Pointer 1 crosses its own movement threshold first, but pointer 0 hasn't
        // moved yet, so this alone doesn't qualify as a two-finger frame.
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 270f, y = 100f, timeMs = 20))
        // Pointer 0 (canonical) now crosses too: the session's first qualifying
        // frame, seeded by the canonical pointer itself.
        val seeded = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 120f, y = 100f, timeMs = 40))
        assertNull(seeded)
        // Pointer 0 (canonical) reports again immediately, with no pointer-1 event
        // in between. Its own single-frame movement (-30px) can never exceed
        // hypot(rawDx, rawDy) of that same movement, so this must defer rather
        // than fall through to Scroll.
        val deferred = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 90f, y = 100f, timeMs = 60))
        assertNull(deferred)
        // Pointer 1 finally reports, unblocking classification.
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 300f, y = 100f, timeMs = 70))
        // Pointer 0 (canonical) decides: the full separation change since the
        // original seed (150px, spanning all the deferred movement) comfortably
        // exceeds this frame's own single-pointer movement (50px), so this must
        // be recognized as a pinch, not swallowed as a spurious Scroll.
        val started = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 40f, y = 100f, timeMs = 80))
        assertEquals(RecognizedGesture.PinchZoomStarted, started)
    }

    @Test
    fun `a finger swapped mid-scroll for a new one does not misclassify the continued scroll as pinch`() {
        // Reproduces a real everyday gesture: two-finger scroll, lift one
        // finger, put a (new-id) finger back down elsewhere, keep scrolling.
        // Before the fix, the stale pinch-distance baseline from the OLD pair
        // would get compared against the NEW pair's very different actual
        // separation on the next qualifying frame, and the resulting huge
        // "distance change" would spuriously exceed the pinch-engage
        // threshold even though both fingers are moving in parallel.
        val recognizer = GestureRecognizer()

        // Establish an ordinary two-finger scroll, exactly like the canonical
        // "two fingers moving together produce Scroll" case.
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 130f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 130f, timeMs = 60))
        val firstScroll = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 160f, timeMs = 70))
        assertTrue(firstScroll is RecognizedGesture.Scroll)

        // Pinch never engaged above, so this is a plain lift, not the
        // wasPinching release path.
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 160f, timeMs = 80))

        // A brand-new finger (new, higher pointer id) touches down far from
        // where the lifted finger was — this is what makes the OLD baseline
        // distance (~104px, between the original pair) wildly different from
        // the NEW pair's actual separation (~300px) once both are compared.
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 400f, y = 160f, timeMs = 90))

        // Neither finger has re-qualified (moved past the tap threshold) yet,
        // so these two moves are each swallowed without emitting anything.
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 180f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 2, x = 400f, y = 180f, timeMs = 110))

        // Both fingers now qualify and are moving in parallel (same
        // direction, ~constant ~300px apart). Without the fix, the canonical
        // pointer's move here compares the new pair's real distance (~300.7px)
        // against the stale old-pair baseline (~104.4px), a change of ~196px
        // that dwarfs this frame's own movement (20px) and spuriously engages
        // pinch. With the fix, the baseline was reset on the finger swap, this
        // frame instead seeds fresh at the earlier move, and this is
        // recognized as the continued Scroll it actually is.
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 200f, timeMs = 120))
        assertTrue(result is RecognizedGesture.Scroll)
    }

    @Test
    fun `a slow jittery two-finger scroll with asymmetric event delivery is not misread as a pinch`() {
        // Reproduces what real touch hardware does that clean synthetic
        // coordinates hadn't: the two fingers are never reported in lockstep.
        // Here the non-canonical finger reports three small moves in a row
        // (each drifting 1px inward from ordinary hand imprecision) before the
        // canonical finger reports its own (tiny, slow-scroll) next move.
        // Before the fix, only that single tiny canonical delta (2px) was
        // compared against the resulting distance change (~2.5px) and would
        // have wrongly engaged pinch. The fix compares against BOTH fingers'
        // accumulated movement since the last decision (~14.4px), which the
        // small distance change falls well short of. This frame's own scroll
        // delta (2px) is below the whole-unit emission threshold, so the
        // correct result here is null (not yet a full Scroll unit) — the
        // regression this guards against is specifically that it must NOT be
        // PinchZoomStarted.
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 112f, timeMs = 20))
        val seeded = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 112f, timeMs = 30))
        assertNull(seeded)
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 199f, y = 116f, timeMs = 40))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 198f, y = 120f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 197f, y = 124f, timeMs = 60))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 114f, timeMs = 70))
        assertNull(result)

        // Confirm the gesture is genuinely continuing as a scroll (not just
        // silently stuck): one more ordinary canonical move should now cross
        // the whole-unit threshold and emit a real Scroll, never a Pinch event.
        val continued = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 140f, timeMs = 80))
        assertEquals(RecognizedGesture.Scroll(vDelta = 1, hDelta = 0), continued)
    }

    @Test
    fun `reversing scroll direction without lifting fingers does not lock into pinch and immediately scrolls in new direction`() {
        val recognizer = GestureRecognizer()
        // 1. Touch down both fingers
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 300f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 300f, timeMs = 10))

        // 2. Scroll UP (negative Y movement)
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 270f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 270f, timeMs = 30))
        val scrollUp = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 240f, timeMs = 40))
        assertTrue(scrollUp is RecognizedGesture.Scroll)
        assertEquals(-1, (scrollUp as RecognizedGesture.Scroll).vDelta)

        // 3. Reverse direction to DOWN without lifting fingers
        // Even if finger 1 reverses slightly earlier or drifts, vector analysis prevents false pinch
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 270f, timeMs = 50))
        val scrollDown = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 270f, timeMs = 60))
        assertTrue(scrollDown is RecognizedGesture.Scroll)
        assertEquals(1, (scrollDown as RecognizedGesture.Scroll).vDelta)
    }

    @Test
    fun `when zoomEnabled is false, two finger movement never triggers PinchZoomStarted`() {
        val recognizer = GestureRecognizer(zoomEnabled = false)
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 150f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 250f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 350f, y = 100f, timeMs = 20))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 50f, y = 100f, timeMs = 30))
        // Should NOT be PinchZoomStarted
        assertTrue(result !is RecognizedGesture.PinchZoomStarted)
    }

    @Test
    fun `scroll where midpoint travels significantly never triggers pinch despite minor finger distance wobble`() {
        val recognizer = GestureRecognizer(zoomEnabled = true)
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))

        // Finger 0 moves down 40px, Finger 1 moves down 35px (5px wobble)
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 140f, timeMs = 30))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 205f, y = 135f, timeMs = 40))
        // Baseline seeded or scroll emitted, never pinch
        assertTrue(result !is RecognizedGesture.PinchZoomStarted)
    }
}


