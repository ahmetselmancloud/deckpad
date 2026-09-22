package com.dokunmatikekosistem.app.data.bluetooth

import com.dokunmatikekosistem.app.domain.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothHidManagerTest {

    @Test
    fun `register is allowed from DISCONNECTED`() {
        assertEquals(true, shouldAttemptRegister(ConnectionState.DISCONNECTED))
    }

    @Test
    fun `register is allowed from ERROR`() {
        assertEquals(true, shouldAttemptRegister(ConnectionState.ERROR))
    }

    @Test
    fun `register is blocked while REGISTERING`() {
        assertEquals(false, shouldAttemptRegister(ConnectionState.REGISTERING))
    }

    @Test
    fun `register is blocked while REGISTERED`() {
        assertEquals(false, shouldAttemptRegister(ConnectionState.REGISTERED))
    }

    @Test
    fun `register is blocked while CONNECTED`() {
        assertEquals(false, shouldAttemptRegister(ConnectionState.CONNECTED))
    }
}
