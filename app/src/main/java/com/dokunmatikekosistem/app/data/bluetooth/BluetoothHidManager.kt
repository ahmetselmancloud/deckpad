package com.dokunmatikekosistem.app.data.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.HidManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BluetoothHidManager"

/** Pure guard: register() may only start a new registration from these states. */
internal fun shouldAttemptRegister(currentState: ConnectionState): Boolean =
    currentState == ConnectionState.DISCONNECTED || currentState == ConnectionState.ERROR

@Singleton
class BluetoothHidManager @Inject constructor(
    @ApplicationContext private val context: Context
) : HidManager {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _reportsSent = MutableStateFlow(0)
    override val reportsSent: StateFlow<Int> = _reportsSent

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "TouchpadEkosistem",
        "Dokunmatik Ekosistem",
        "DokunmatikEkosistem",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        com.dokunmatikekosistem.app.data.hid.HidDescriptor.DESCRIPTOR
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered pluggedDevice=$pluggedDevice")
            _connectionState.value = if (registered) ConnectionState.REGISTERED else ConnectionState.ERROR
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: device=$device state=$state")
            connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            _connectionState.value = when (state) {
                BluetoothProfile.STATE_CONNECTED -> ConnectionState.CONNECTED
                BluetoothProfile.STATE_CONNECTING -> ConnectionState.REGISTERING
                else -> ConnectionState.DISCONNECTED
            }
        }
    }

    override fun register() {
        if (!shouldAttemptRegister(_connectionState.value)) {
            Log.d(TAG, "register() ignored, already ${_connectionState.value}")
            return
        }
        Log.d(TAG, "register() called")
        _connectionState.value = ConnectionState.REGISTERING
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val executor = Executor { command -> command.run() }
        try {
            val proxyRequested = manager.adapter.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        Log.d(TAG, "onServiceConnected: profile=$profile proxy=$proxy")
                        hidDevice = proxy as BluetoothHidDevice
                        try {
                            val registerRequested = hidDevice?.registerApp(sdpSettings, null, null, executor, callback)
                            Log.d(TAG, "registerApp() called, requested=$registerRequested")
                            if (registerRequested != true) {
                                _connectionState.value = ConnectionState.ERROR
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "registerApp() threw SecurityException", e)
                            _connectionState.value = ConnectionState.ERROR
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        Log.d(TAG, "onServiceDisconnected: profile=$profile")
                        hidDevice = null
                        connectedDevice = null
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                },
                BluetoothProfile.HID_DEVICE
            )
            Log.d(TAG, "getProfileProxy() called, requested=$proxyRequested")
            if (!proxyRequested) {
                _connectionState.value = ConnectionState.ERROR
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "getProfileProxy() threw SecurityException", e)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    override fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean) {
        val device = connectedDevice ?: return
        val report = com.dokunmatikekosistem.app.data.hid.HidMouseReport.build(
            dx = dx,
            dy = dy,
            wheelDelta = 0,
            panDelta = 0,
            leftButtonPressed = leftButtonPressed,
            rightButtonPressed = false
        )
        val sent = hidDevice?.sendReport(device, com.dokunmatikekosistem.app.data.hid.HidDescriptor.MOUSE_REPORT_ID.toInt(), report)
        if (sent == true) {
            _reportsSent.value = _reportsSent.value + 1
        }
    }
}
