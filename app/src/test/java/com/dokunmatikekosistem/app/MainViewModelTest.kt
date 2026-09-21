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
    val allReports = mutableListOf<Triple<Int, Int, Boolean>>()

    override fun register() {
        registerCalled = true
    }

    override fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean) {
        lastDrag = dx to dy
        lastButton = leftButtonPressed
        allReports.add(Triple(dx, dy, leftButtonPressed))
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
    fun `onTap sends a press followed by a release`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        viewModel.onTap()

        assertEquals(
            listOf(
                Triple(0, 0, true),
                Triple(0, 0, false)
            ),
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
