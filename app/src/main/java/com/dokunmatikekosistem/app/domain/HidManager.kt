package com.dokunmatikekosistem.app.domain

import kotlinx.coroutines.flow.StateFlow

interface HidManager {
    val connectionState: StateFlow<ConnectionState>
    val reportsSent: StateFlow<Int>
    fun register()
    fun sendMouseReport(
        dx: Int,
        dy: Int,
        wheelDelta: Int = 0,
        panDelta: Int = 0,
        leftButtonPressed: Boolean = false,
        rightButtonPressed: Boolean = false,
        middleButtonPressed: Boolean = false
    )
    fun sendKeyboardReport(modifierBits: Int, usageCode: Int)
    fun releaseKeyboardReport()
    fun sendConsumerReport(usageCode: Int)
    fun releaseAll()
}
