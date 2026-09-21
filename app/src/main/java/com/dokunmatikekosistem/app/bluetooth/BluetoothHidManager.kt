package com.dokunmatikekosistem.app.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.dokunmatikekosistem.app.hid.HidDescriptor
import com.dokunmatikekosistem.app.hid.HidMouseReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor

enum class ConnectionState { DISCONNECTED, REGISTERING, CONNECTED, ERROR }

class BluetoothHidManager(private val context: Context) : com.dokunmatikekosistem.app.HidManager {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _reportsSent = MutableStateFlow(0)
    override val reportsSent: StateFlow<Int> = _reportsSent

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "TouchpadEkosistem",
        "Dokunmatik Ekosistem Faz 0 Prototip",
        "DokunmatikEkosistem",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        HidDescriptor.DESCRIPTOR
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (!registered) {
                _connectionState.value = ConnectionState.ERROR
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            _connectionState.value = when (state) {
                BluetoothProfile.STATE_CONNECTED -> ConnectionState.CONNECTED
                BluetoothProfile.STATE_CONNECTING -> ConnectionState.REGISTERING
                else -> ConnectionState.DISCONNECTED
            }
        }
    }

    override fun register() {
        _connectionState.value = ConnectionState.REGISTERING
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val executor = Executor { command -> command.run() }
        manager.adapter.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    hidDevice = proxy as BluetoothHidDevice
                    hidDevice?.registerApp(sdpSettings, null, null, executor, callback)
                }

                override fun onServiceDisconnected(profile: Int) {
                    hidDevice = null
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            },
            BluetoothProfile.HID_DEVICE
        )
    }

    override fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean) {
        val device = connectedDevice ?: return
        val report = HidMouseReport.build(dx, dy, leftButtonPressed)
        val sent = hidDevice?.sendReport(device, HidDescriptor.MOUSE_REPORT_ID.toInt(), report)
        if (sent == true) {
            _reportsSent.value = _reportsSent.value + 1
        }
    }
}
