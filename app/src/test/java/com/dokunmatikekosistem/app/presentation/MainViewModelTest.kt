package com.dokunmatikekosistem.app.presentation

import com.dokunmatikekosistem.app.data.gesture.RecognizedGesture
import com.dokunmatikekosistem.app.data.gesture.SwipeDirection
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
    val rightButtonPressed: Boolean,
    val middleButtonPressed: Boolean = false
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

    override fun sendMouseReport(
        dx: Int,
        dy: Int,
        wheelDelta: Int,
        panDelta: Int,
        leftButtonPressed: Boolean,
        rightButtonPressed: Boolean,
        middleButtonPressed: Boolean
    ) {
        allReports.add(FakeMouseReport(dx, dy, wheelDelta, panDelta, leftButtonPressed, rightButtonPressed, middleButtonPressed))
        reportsSent.value = reportsSent.value + 1
    }

    val allConsumerPresses = mutableListOf<Int>()

    override fun sendKeyboardReport(modifierBits: Int, usageCode: Int) {
        allKeyPresses.add(modifierBits to usageCode)
        reportsSent.value = reportsSent.value + 1
    }

    override fun releaseKeyboardReport() {
        releaseKeyboardCalled = true
    }

    override fun sendConsumerReport(usageCode: Int) {
        allConsumerPresses.add(usageCode)
        reportsSent.value = reportsSent.value + 1
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
    fun `onCtrlClicked onAltClicked toggle sticky state and onWinClicked sends win key`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onCtrlClicked()
        viewModel.onAltClicked()
        viewModel.onWinClicked()

        assertEquals(true, viewModel.modifierState.value.ctrlActive)
        assertEquals(true, viewModel.modifierState.value.altActive)
        assertEquals(
            listOf(HidKeyboardReport.MODIFIER_WIN to 0),
            fake.allKeyPresses
        )
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
    fun `onKeyTyped strips CapsLock-derived shift bit when a sticky modifier is active`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        // CapsLock on (no real Shift) makes the caller pass the uppercase char, same as
        // VirtualKeyboard does via isUpperCaseEffective() -> layout.shiftedChar().
        viewModel.onCapsLockClicked()
        viewModel.onCtrlClicked()
        viewModel.onKeyTyped('C')

        assertEquals(
            listOf(HidKeyboardReport.MODIFIER_CTRL to 0x06),
            fake.allKeyPresses
        )
    }

    @Test
    fun `onKeyTyped keeps CapsLock-derived shift bit when no sticky modifier is active`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onCapsLockClicked()
        viewModel.onKeyTyped('C')

        assertEquals(
            listOf(HidKeyboardReport.MODIFIER_SHIFT to 0x06),
            fake.allKeyPresses
        )
    }

    @Test
    fun `onKeyTyped keeps shift bit alongside sticky ctrl when real shift is active`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)
        viewModel.onCtrlClicked()
        viewModel.onKeyTyped('C')

        assertEquals(
            listOf((HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_SHIFT) to 0x06),
            fake.allKeyPresses
        )
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

    @Test
    fun `ThreeFingerSwipe UP sends Win+Tab`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.UP))
        assertEquals(listOf(HidKeyboardReport.MODIFIER_WIN to 0x2B), fake.allKeyPresses)
        assertEquals(true, fake.releaseKeyboardCalled)
    }

    @Test
    fun `ThreeFingerSwipe DOWN sends Win+D`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.DOWN))
        assertEquals(listOf(HidKeyboardReport.MODIFIER_WIN to 0x07), fake.allKeyPresses)
    }

    @Test
    fun `ThreeFingerSwipe LEFT sends Alt+Tab`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.LEFT))
        assertEquals(listOf(HidKeyboardReport.MODIFIER_ALT to 0x2B), fake.allKeyPresses)
    }

    @Test
    fun `ThreeFingerSwipe RIGHT sends Alt+Shift+Tab`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.RIGHT))
        assertEquals(listOf((HidKeyboardReport.MODIFIER_ALT or HidKeyboardReport.MODIFIER_SHIFT) to 0x2B), fake.allKeyPresses)
    }

    @Test
    fun `ThreeFingerTap sends middle click by default and clicks haptics`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)
        viewModel.onGesture(RecognizedGesture.ThreeFingerTap)
        assertEquals(
            listOf(
                FakeMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false, middleButtonPressed = true),
                FakeMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false, middleButtonPressed = false)
            ),
            fake.allReports
        )
        assertEquals(1, haptics.clickCount)
    }

    @Test
    fun `ThreeFingerTap with WINDOWS_SEARCH action sends Win+S`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.setThreeFingerTapAction(com.dokunmatikekosistem.app.data.settings.TapAction.WINDOWS_SEARCH)
        viewModel.onGesture(RecognizedGesture.ThreeFingerTap)
        assertEquals(listOf(HidKeyboardReport.MODIFIER_WIN to 0x16), fake.allKeyPresses)
    }

    @Test
    fun `ThreeFingerTap with SHOW_DESKTOP action sends Win+D`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.setThreeFingerTapAction(com.dokunmatikekosistem.app.data.settings.TapAction.SHOW_DESKTOP)
        viewModel.onGesture(RecognizedGesture.ThreeFingerTap)
        assertEquals(listOf(HidKeyboardReport.MODIFIER_WIN to 0x07), fake.allKeyPresses)
    }

    @Test
    fun `FourFingerSwipe LEFT sends Ctrl+Win+Right for next virtual desktop`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.FourFingerSwipe(SwipeDirection.LEFT))
        assertEquals(listOf((HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_WIN) to 0x4F), fake.allKeyPresses)
    }

    @Test
    fun `FourFingerSwipe RIGHT sends Ctrl+Win+Left for previous virtual desktop`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.FourFingerSwipe(SwipeDirection.RIGHT))
        assertEquals(listOf((HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_WIN) to 0x50), fake.allKeyPresses)
    }

    @Test
    fun `FourFingerTap sends Win+N`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.FourFingerTap)
        assertEquals(listOf(HidKeyboardReport.MODIFIER_WIN to 0x11), fake.allKeyPresses)
    }

    @Test
    fun `PinchZoomStarted holds Ctrl with no key`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.PinchZoomStarted)
        assertEquals(listOf(HidKeyboardReport.MODIFIER_CTRL to 0), fake.allKeyPresses)
        assertEquals(false, fake.releaseKeyboardCalled)
    }

    @Test
    fun `PinchZoomDelta sends a mouse wheel report with the given units`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.PinchZoomDelta(units = 3))
        assertEquals(listOf(FakeMouseReport(0, 0, wheelDelta = 3, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)), fake.allReports)
    }

    @Test
    fun `PinchZoomEnded releases the keyboard report`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.PinchZoomEnded)
        assertEquals(true, fake.releaseKeyboardCalled)
        assertEquals(emptyList<Pair<Int, Int>>(), fake.allKeyPresses)
    }

    @Test
    fun `onZoomToggleClicked flips zoomEnabled state`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        assertEquals(true, viewModel.zoomEnabled.value)
        viewModel.onZoomToggleClicked()
        assertEquals(false, viewModel.zoomEnabled.value)
        viewModel.onZoomToggleClicked()
        assertEquals(true, viewModel.zoomEnabled.value)
    }

    @Test
    fun `CursorMove scales with cursorSpeed`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.setCursorSpeed(2.0f)
        viewModel.onGesture(RecognizedGesture.CursorMove(dx = 10, dy = -5))
        assertEquals(
            listOf(FakeMouseReport(20, -10, wheelDelta = 0, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)),
            fake.allReports
        )
    }

    @Test
    fun `Scroll scales with scrollSpeed`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.setScrollSpeed(2.0f)
        viewModel.onGesture(RecognizedGesture.Scroll(vDelta = 3, hDelta = -2))
        assertEquals(
            listOf(FakeMouseReport(0, 0, wheelDelta = 6, panDelta = -4, leftButtonPressed = false, rightButtonPressed = false)),
            fake.allReports
        )
    }

    @Test
    fun `setAutoReconnect updates userSettings`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        assertEquals(true, viewModel.userSettings.value.autoReconnect)
        viewModel.setAutoReconnect(false)
        assertEquals(false, viewModel.userSettings.value.autoReconnect)
    }

    @Test
    fun `selectTab updates currentTab state`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        assertEquals(AppTab.TOUCHPAD, viewModel.currentTab.value)

        viewModel.selectTab(AppTab.NUMPAD)
        assertEquals(AppTab.NUMPAD, viewModel.currentTab.value)

        viewModel.selectTab(AppTab.MEDIA)
        assertEquals(AppTab.MEDIA, viewModel.currentTab.value)

        viewModel.selectTab(AppTab.KEYBOARD)
        assertEquals(AppTab.KEYBOARD, viewModel.currentTab.value)
    }

    @Test
    fun `fullscreen toggling works as expected`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        assertEquals(false, viewModel.isFullscreen.value)

        viewModel.setFullscreen(true)
        assertEquals(true, viewModel.isFullscreen.value)

        viewModel.toggleFullscreen()
        assertEquals(false, viewModel.isFullscreen.value)
    }

    @Test
    fun `sendConsumer delegates to hidManager and triggers haptic`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.sendConsumer(com.dokunmatikekosistem.app.data.hid.HidConsumerReport.VOLUME_INCREMENT)
        assertEquals(listOf(com.dokunmatikekosistem.app.data.hid.HidConsumerReport.VOLUME_INCREMENT), fake.allConsumerPresses)
        assertEquals(1, haptics.clickCount)
    }

    @Test
    fun `macroCopy sends Ctrl+C and triggers haptic`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.macroCopy()
        assertEquals(listOf(com.dokunmatikekosistem.app.data.hid.HidKeyboardReport.MODIFIER_CTRL to com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_C), fake.allKeyPresses)
        assertEquals(true, fake.releaseKeyboardCalled)
        assertEquals(1, haptics.clickCount)
    }

    @Test
    fun `macroPaste sends Ctrl+V and triggers haptic`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.macroPaste()
        assertEquals(listOf(com.dokunmatikekosistem.app.data.hid.HidKeyboardReport.MODIFIER_CTRL to com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_V), fake.allKeyPresses)
        assertEquals(1, haptics.clickCount)
    }
}

