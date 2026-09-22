package com.dokunmatikekosistem.app.presentation

import androidx.lifecycle.ViewModel
import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.HidManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val hidManager: HidManager
) : ViewModel() {

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
