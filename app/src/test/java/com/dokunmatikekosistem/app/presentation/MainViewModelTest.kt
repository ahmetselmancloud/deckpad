package com.dokunmatikekosistem.app.presentation

import com.dokunmatikekosistem.app.data.gesture.RecognizedGesture
import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import com.dokunmatikekosistem.app.data.keyboard.EnglishUsLayout
import com.dokunmatikekosistem.app.data.keyboard.TurkishQLayout
import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.Haptics
import com.dokunmatikekosistem.app.domain.HidManager
import com.dokunmatikekosistem.app.domain.KeyboardModifierState
import com.dokunmatikekosistem.app.domain.ShiftState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private data class FakeMouseReport(
    val dx: Int,
    val dy: Int,
    val wheelDelta: Int,
    val panDelta: Int,
    val leftButtonPressed: Boolean,
    val rightButtonPressed: Boolean
)

private class FakeHidManager : HidManager {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val reportsSent = MutableStateFlow(0)
    var registerCalled = false
    val allReports = mutableListOf<FakeMouseReport>()
    val allKeyPresses = mutableListOf<Pair<Int, Int>>()
    var releaseKeyboardCalled = false

    override fun register() {
        registerCalled = true
    }

    override fun sendMouseReport(dx: Int, dy: Int, wheelDelta: Int, panDelta: Int, leftButtonPressed: Boolean, rightButtonPressed: Boolean) {
        allReports.add(FakeMouseReport(dx, dy, wheelDelta, panDelta, leftButtonPressed, rightButtonPressed))
        reportsSent.value = reportsSent.value + 1
    }

    override fun sendKeyboardReport(modifierBits: Int, usageCode: Int) {
        allKeyPresses.add(modifierBits to usageCode)
        reportsSent.value = reportsSent.value + 1
    }

    override fun releaseKeyboardReport() {
        releaseKeyboardCalled = true
    }
}

private class FakeHaptics : Haptics {
    var clickCount = 0
    var dragLockEngagedCount = 0
    override fun click() { clickCount++ }
    override fun dragLockEngaged() { dragLockEngagedCount++ }
}

class MainViewModelTest {

    @Test
    fun `onConnectClicked delegates to hid manager register`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onConnectClicked()

        assertEquals(true, fake.registerCalled)
    }

    @Test
    fun `connectionState exposes the hid manager state`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        fake.connectionState.value = ConnectionState.CONNECTED

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState.value)
    }

    @Test
    fun `onGesture CursorMove sends a mouse report with no buttons`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onGesture(RecognizedGesture.CursorMove(dx = 5, dy = -3))

        assertEquals(listOf(FakeMouseReport(5, -3, 0, 0, leftButtonPressed = false, rightButtonPressed = false)), fake.allReports)
    }

    @Test
    fun `onGesture LeftClick sends press then release on the left button and triggers haptic click`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.onGesture(RecognizedGesture.LeftClick)

        assertEquals(
            listOf(
                FakeMouseReport(0, 0, 0, 0, leftButtonPressed = true, rightButtonPressed = false),
                FakeMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false)
            ),
            fake.allReports
        )
        assertEquals(1, haptics.clickCount)
    }

    @Test
    fun `onGesture RightClick sends press then release on the right button and triggers haptic click`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.onGesture(RecognizedGesture.RightClick)

        assertEquals(
            listOf(
                FakeMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = true),
                FakeMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false)
            ),
            fake.allReports
        )
        assertEquals(1, haptics.clickCount)
    }

    @Test
    fun `onGesture Scroll sends vDelta as wheel and hDelta as pan with no buttons`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onGesture(RecognizedGesture.Scroll(vDelta = 2, hDelta = -1))

        assertEquals(
            listOf(FakeMouseReport(0, 0, wheelDelta = 2, panDelta = -1, leftButtonPressed = false, rightButtonPressed = false)),
            fake.allReports
        )
    }

    @Test
    fun `onGesture DragLockEngaged sends button-down and triggers haptic dragLockEngaged`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.onGesture(RecognizedGesture.DragLockEngaged)

        assertEquals(listOf(FakeMouseReport(0, 0, 0, 0, leftButtonPressed = true, rightButtonPressed = false)), fake.allReports)
        assertEquals(1, haptics.dragLockEngagedCount)
    }

    @Test
    fun `onGesture DragMove sends movement with the left button still held`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onGesture(RecognizedGesture.DragMove(dx = 7, dy = 4))

        assertEquals(listOf(FakeMouseReport(7, 4, 0, 0, leftButtonPressed = true, rightButtonPressed = false)), fake.allReports)
    }

    @Test
    fun `onGesture DragLockReleased sends a button-up report`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onGesture(RecognizedGesture.DragLockReleased)

        assertEquals(listOf(FakeMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false)), fake.allReports)
    }

    @Test
    fun `activeLayout starts as Turkish Q`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        assertTrue(viewModel.activeLayout.value is TurkishQLayout)
    }

    @Test
    fun `onLayoutToggleClicked switches from Turkish Q to English US and back`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onLayoutToggleClicked()
        assertTrue(viewModel.activeLayout.value is EnglishUsLayout)

        viewModel.onLayoutToggleClicked()
        assertTrue(viewModel.activeLayout.value is TurkishQLayout)
    }

    @Test
    fun `onKeyTyped sends the mapped chord as key-down then key-up`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onKeyTyped('a')

        assertEquals(listOf(0 to 0x04), fake.allKeyPresses)
        assertEquals(true, fake.releaseKeyboardCalled)
    }

    @Test
    fun `onKeyTyped with unmapped character is a no-op`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onKeyTyped('#')

        assertEquals(emptyList<Pair<Int, Int>>(), fake.allKeyPresses)
        assertEquals(false, fake.releaseKeyboardCalled)
    }

    @Test
    fun `modifierState starts as default (all off)`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        assertEquals(KeyboardModifierState(), viewModel.modifierState.value)
    }

    @Test
    fun `onShiftClicked toggles Off to OneShot`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)

        assertEquals(ShiftState.OneShot, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onShiftClicked toggles OneShot back to Off when taps are far apart`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)
        viewModel.onShiftClicked(atMillis = 1000L)

        assertEquals(ShiftState.Off, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onShiftClicked twice within 300ms locks shift`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)
        viewModel.onShiftClicked(atMillis = 200L)

        assertEquals(ShiftState.Locked, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onShiftClicked while locked unlocks to Off`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)
        viewModel.onShiftClicked(atMillis = 200L)
        viewModel.onShiftClicked(atMillis = 400L)

        assertEquals(ShiftState.Off, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onCapsLockClicked toggles independently of shift`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onCapsLockClicked()

        assertEquals(true, viewModel.modifierState.value.capsLockActive)
        assertEquals(ShiftState.Off, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onCtrlClicked onAltClicked onWinClicked toggle sticky state`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onCtrlClicked()
        viewModel.onAltClicked()
        viewModel.onWinClicked()

        assertEquals(true, viewModel.modifierState.value.ctrlActive)
        assertEquals(true, viewModel.modifierState.value.altActive)
        assertEquals(true, viewModel.modifierState.value.winActive)
    }

    @Test
    fun `onKeyTyped combines sticky ctrl and alt bits with the chord's own modifier bits`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onCtrlClicked()
        viewModel.onAltClicked()
        viewModel.onKeyTyped('a')

        assertEquals(
            listOf((HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_ALT) to 0x04),
            fake.allKeyPresses
        )
    }

    @Test
    fun `onKeyTyped clears one-shot shift and sticky ctrl alt win but keeps caps lock`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)
        viewModel.onCapsLockClicked()
        viewModel.onCtrlClicked()
        viewModel.onKeyTyped('a')

        val state = viewModel.modifierState.value
        assertEquals(ShiftState.Off, state.shiftState)
        assertEquals(true, state.capsLockActive)
        assertEquals(false, state.ctrlActive)
    }

    @Test
    fun `onKeyTyped keeps locked shift after typing`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)
        viewModel.onShiftClicked(atMillis = 200L)
        viewModel.onKeyTyped('a')

        assertEquals(ShiftState.Locked, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onKeyTyped with unmapped character does not touch modifier state`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onCtrlClicked()
        viewModel.onKeyTyped('#')

        assertEquals(true, viewModel.modifierState.value.ctrlActive)
    }

    @Test
    fun `onCtrlAltDelClicked sends ctrl+alt+delete and does not touch sticky state`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onCtrlAltDelClicked()

        assertEquals(
            listOf((HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_ALT) to 0x4C),
            fake.allKeyPresses
        )
        assertEquals(true, fake.releaseKeyboardCalled)
        assertEquals(false, viewModel.modifierState.value.ctrlActive)
        assertEquals(false, viewModel.modifierState.value.altActive)
    }
}
