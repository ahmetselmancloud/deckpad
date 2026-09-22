package com.dokunmatikekosistem.app.presentation

import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.HidManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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

class MainViewModelTest {

    @Test
    fun `onConnectClicked delegates to hid manager register`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        viewModel.onConnectClicked()

        assertEquals(true, fake.registerCalled)
    }

    @Test
    fun `onDrag sends a mouse report with no button pressed`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        viewModel.onDrag(dx = 5, dy = -3)

        assertEquals(listOf(Triple(5, -3, false)), fake.allReports)
    }

    @Test
    fun `onTap sends a press followed by a release`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        viewModel.onTap()

        assertEquals(
            listOf(Triple(0, 0, true), Triple(0, 0, false)),
            fake.allReports
        )
    }

    @Test
    fun `connectionState exposes the hid manager state`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        fake.connectionState.value = ConnectionState.CONNECTED

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState.value)
    }
}
