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
    fun `two fingers moving together produce Scroll`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 130f, timeMs = 50))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 130f, timeMs = 60))
        assertTrue(result is RecognizedGesture.Scroll)
    }

    @Test
    fun `tap then hold-and-drag engages drag lock then moves then releases`() {
        val recognizer = GestureRecognizer()
        // First tap
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 80))
        // Second touch within 300ms, held down
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
}
