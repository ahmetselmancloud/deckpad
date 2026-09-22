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
    fun `three finger tap does not produce RightClick`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 100f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 100f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 100f, timeMs = 120))
        assertNull(result)
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
    fun `tap then hold-and-drag engages drag lock then moves then releases`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 80))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 100f, y = 100f, timeMs = 200))
        val engaged = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 100f, y = 100f, timeMs = 360))
        assertEquals(RecognizedGesture.DragLockEngaged, engaged)
        val moved = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 120f, y = 100f, timeMs = 400))
        assertEquals(RecognizedGesture.DragMove(dx = 20, dy = 0), moved)
        val released = recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 120f, y = 100f, timeMs = 500))
        assertEquals(RecognizedGesture.DragLockReleased, released)
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
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 100f, y = 100f, timeMs = 360)) // engages drag lock
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
}
