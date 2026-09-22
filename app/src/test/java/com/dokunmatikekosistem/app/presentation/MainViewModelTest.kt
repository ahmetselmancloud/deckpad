package com.dokunmatikekosistem.app.presentation

import com.dokunmatikekosistem.app.data.gesture.RecognizedGesture
import com.dokunmatikekosistem.app.data.keyboard.EnglishUsLayout
import com.dokunmatikekosistem.app.data.keyboard.TurkishQLayout
import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.Haptics
import com.dokunmatikekosistem.app.domain.HidManager
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
}
