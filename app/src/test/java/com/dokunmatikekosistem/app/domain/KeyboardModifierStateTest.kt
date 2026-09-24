package com.dokunmatikekosistem.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardModifierStateTest {

    @Test
    fun `default state is not uppercase`() {
        assertEquals(false, KeyboardModifierState().isUpperCaseEffective())
    }

    @Test
    fun `shift one-shot makes it uppercase`() {
        val state = KeyboardModifierState(shiftState = ShiftState.OneShot)
        assertEquals(true, state.isUpperCaseEffective())
    }

    @Test
    fun `shift locked makes it uppercase`() {
        val state = KeyboardModifierState(shiftState = ShiftState.Locked)
        assertEquals(true, state.isUpperCaseEffective())
    }

    @Test
    fun `caps lock alone makes it uppercase`() {
        val state = KeyboardModifierState(capsLockActive = true)
        assertEquals(true, state.isUpperCaseEffective())
    }

    @Test
    fun `shift and caps lock together cancel out to lowercase`() {
        val state = KeyboardModifierState(shiftState = ShiftState.OneShot, capsLockActive = true)
        assertEquals(false, state.isUpperCaseEffective())
    }
}
