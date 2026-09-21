package com.dokunmatikekosistem.app

import com.dokunmatikekosistem.app.bluetooth.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeHidManager : HidManager {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val reportsSent = MutableStateFlow(0)
    var registerCalled = false
    var lastDrag: Pair<Int, Int>? = null
    var lastButton: Boolean? = null

    override fun register() {
        registerCalled = true
    }

    override fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean) {
        lastDrag = dx to dy
        lastButton = leftButtonPressed
        reportsSent.value = reportsSent.value + 1
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

        assertEquals(5 to -3, fake.lastDrag)
        assertEquals(false, fake.lastButton)
    }

    @Test
    fun `onTap sends a zero-delta report with left button pressed`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        viewModel.onTap()

        assertEquals(0 to 0, fake.lastDrag)
        assertEquals(true, fake.lastButton)
    }

    @Test
    fun `connectionState exposes the hid manager state`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        fake.connectionState.value = ConnectionState.CONNECTED

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState.value)
    }
}
