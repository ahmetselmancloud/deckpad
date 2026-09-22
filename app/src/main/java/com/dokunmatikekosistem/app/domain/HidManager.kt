package com.dokunmatikekosistem.app.domain

import kotlinx.coroutines.flow.StateFlow

interface HidManager {
    val connectionState: StateFlow<ConnectionState>
    val reportsSent: StateFlow<Int>
    fun register()
    fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean)
}
