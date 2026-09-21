package com.dokunmatikekosistem.app

import androidx.lifecycle.ViewModel
import com.dokunmatikekosistem.app.bluetooth.ConnectionState
import kotlinx.coroutines.flow.StateFlow

interface HidManager {
    val connectionState: StateFlow<ConnectionState>
    val reportsSent: StateFlow<Int>
    fun register()
    fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean)
}

class MainViewModel(private val hidManager: HidManager) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = hidManager.connectionState
    val reportsSent: StateFlow<Int> = hidManager.reportsSent

    fun onConnectClicked() {
        hidManager.register()
    }

    fun onDrag(dx: Int, dy: Int) {
        hidManager.sendMouseReport(dx, dy, leftButtonPressed = false)
    }

    fun onTap() {
        hidManager.sendMouseReport(dx = 0, dy = 0, leftButtonPressed = true)
        hidManager.sendMouseReport(dx = 0, dy = 0, leftButtonPressed = false)
    }
}
