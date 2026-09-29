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
        assertEquals(RecognizedGesture.PinchZoomDelta(units = 8), delta)
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
}
