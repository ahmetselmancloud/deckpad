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

private class FakeHidManager : HidManager {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val reportsSent = MutableStateFlow(0)
    var registerCalled = false
    val allReports = mutableListOf<Triple<Int, Int, Boolean>>()
    val allKeyPresses = mutableListOf<Pair<Int, Int>>()
    var releaseKeyboardCalled = false

    override fun register() {
        registerCalled = true
    }

    override fun sendMouseReport(dx: Int, dy: Int, wheelDelta: Int, panDelta: Int, leftButtonPressed: Boolean, rightButtonPressed: Boolean) {
        allReports.add(Triple(dx, dy, leftButtonPressed))
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

        assertEquals(listOf(Triple(5, -3, false)), fake.allReports)
    }

    @Test
    fun `onGesture LeftClick sends press then release and triggers haptic click`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.onGesture(RecognizedGesture.LeftClick)

        assertEquals(listOf(Triple(0, 0, true), Triple(0, 0, false)), fake.allReports)
        assertEquals(1, haptics.clickCount)
    }

    @Test
    fun `onGesture RightClick triggers haptic click`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.onGesture(RecognizedGesture.RightClick)

        assertEquals(1, haptics.clickCount)
    }

    @Test
    fun `onGesture DragLockEngaged triggers haptic dragLockEngaged`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.onGesture(RecognizedGesture.DragLockEngaged)

        assertEquals(1, haptics.dragLockEngagedCount)
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
