package com.dokunmatikekosistem.app.domain

import kotlinx.coroutines.flow.StateFlow

interface HidManager {
    val connectionState: StateFlow<ConnectionState>
    val reportsSent: StateFlow<Int>
    fun register()
    fun sendMouseReport(dx: Int, dy: Int, wheelDelta: Int, panDelta: Int, leftButtonPressed: Boolean, rightButtonPressed: Boolean)
    fun sendKeyboardReport(modifierBits: Int, usageCode: Int)
    fun releaseKeyboardReport()
}
