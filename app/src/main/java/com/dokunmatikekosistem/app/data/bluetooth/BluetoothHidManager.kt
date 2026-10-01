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

import com.dokunmatikekosistem.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private const val TAG = "BluetoothHidManager"

/** Pure guard: register() may only start a new registration from these states. */
internal fun shouldAttemptRegister(currentState: ConnectionState): Boolean =
    currentState == ConnectionState.DISCONNECTED || currentState == ConnectionState.ERROR

@Singleton
class BluetoothHidManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository? = null
) : HidManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _reportsSent = MutableStateFlow(0)
    override val reportsSent: StateFlow<Int> = _reportsSent

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "TouchpadEkosistemV3",
        "Dokunmatik Ekosistem V3",
        "DokunmatikEkosistem",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        com.dokunmatikekosistem.app.data.hid.HidDescriptor.DESCRIPTOR
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered pluggedDevice=$pluggedDevice")
            _connectionState.value = if (registered) ConnectionState.REGISTERED else ConnectionState.ERROR
            if (registered) {
                tryConnectBondedHost(pluggedDevice)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: device=$device state=$state")
            connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            _connectionState.value = when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    device?.address?.let { addr ->
                        scope.launch { settingsRepository?.setLastDeviceAddress(addr) }
                    }
                    ConnectionState.CONNECTED
                }
                BluetoothProfile.STATE_CONNECTING -> ConnectionState.REGISTERING
                else -> {
                    scheduleAutoReconnect()
                    ConnectionState.DISCONNECTED
                }
            }
        }
    }

    private fun tryConnectBondedHost(pluggedDevice: BluetoothDevice?) {
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
            val adapter = manager.adapter ?: return
            if (!adapter.isEnabled) return

            val bondedDevices = adapter.bondedDevices ?: emptySet()
            if (bondedDevices.isEmpty()) return

            scope.launch {
                val settings = settingsRepository?.userSettingsFlow?.firstOrNull()
                if (settings != null && !settings.autoReconnect) return@launch

                val target = pluggedDevice
                    ?: bondedDevices.firstOrNull { it.address == settings?.lastDeviceAddress }
                    ?: bondedDevices.firstOrNull { dev ->
                        dev.bluetoothClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.COMPUTER
                    }
                    ?: bondedDevices.firstOrNull()

                if (target != null && connectedDevice == null && _connectionState.value != ConnectionState.CONNECTED) {
                    Log.d(TAG, "Auto-connecting to bonded host: ${target.name} (${target.address})")
                    hidDevice?.connect(target)
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException during auto-connect: ${e.message}")
        }
    }

    private fun scheduleAutoReconnect() {
        scope.launch {
            delay(3000L)
            if (connectedDevice == null && _connectionState.value == ConnectionState.DISCONNECTED && hidDevice != null) {
                val settings = settingsRepository?.userSettingsFlow?.firstOrNull()
                if (settings?.autoReconnect != false) {
                    tryConnectBondedHost(null)
                }
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

    override fun sendMouseReport(
        dx: Int,
        dy: Int,
        wheelDelta: Int,
        panDelta: Int,
        leftButtonPressed: Boolean,
        rightButtonPressed: Boolean,
        middleButtonPressed: Boolean
    ) {
        val device = connectedDevice ?: return
        val report = com.dokunmatikekosistem.app.data.hid.HidMouseReport.build(
            dx, dy, wheelDelta, panDelta, leftButtonPressed, rightButtonPressed, middleButtonPressed
        )
        val sent = hidDevice?.sendReport(device, com.dokunmatikekosistem.app.data.hid.HidDescriptor.MOUSE_REPORT_ID.toInt(), report)
        if (sent == true && (leftButtonPressed || rightButtonPressed || middleButtonPressed)) {
            _reportsSent.value = _reportsSent.value + 1
        }
    }

    override fun sendKeyboardReport(modifierBits: Int, usageCode: Int) {
        val device = connectedDevice ?: return
        val report = com.dokunmatikekosistem.app.data.hid.HidKeyboardReport.build(
            com.dokunmatikekosistem.app.domain.HidKeyChord(modifierBits, usageCode)
        )
        val sent = hidDevice?.sendReport(device, com.dokunmatikekosistem.app.data.hid.HidDescriptor.KEYBOARD_REPORT_ID.toInt(), report)
        if (sent == true) {
            _reportsSent.value = _reportsSent.value + 1
        }
    }

    override fun releaseKeyboardReport() {
        val device = connectedDevice ?: return
        val report = com.dokunmatikekosistem.app.data.hid.HidKeyboardReport.release()
        val sent = hidDevice?.sendReport(device, com.dokunmatikekosistem.app.data.hid.HidDescriptor.KEYBOARD_REPORT_ID.toInt(), report)
        if (sent == true) {
            _reportsSent.value = _reportsSent.value + 1
        } else {
            Log.w(TAG, "releaseKeyboardReport() sendReport failed or hidDevice is null")
        }
    }

    override fun sendConsumerReport(usageCode: Int) {
        val device = connectedDevice ?: run {
            Log.w(TAG, "sendConsumerReport: connectedDevice is null, report dropped")
            return
        }
        val pressReport = com.dokunmatikekosistem.app.data.hid.HidConsumerReport.build(usageCode)
        val releaseReport = com.dokunmatikekosistem.app.data.hid.HidConsumerReport.release()
        val reportId = com.dokunmatikekosistem.app.data.hid.HidDescriptor.CONSUMER_REPORT_ID.toInt()
        val sent = hidDevice?.sendReport(device, reportId, pressReport)
        Log.d(TAG, "sendConsumerReport: press sent=$sent reportId=$reportId usage=0x${usageCode.toString(16)}")
        scope.launch {
            delay(70L)
            val relSent = hidDevice?.sendReport(device, reportId, releaseReport)
            Log.d(TAG, "sendConsumerReport: release sent=$relSent")
        }
        if (sent == true) {
            _reportsSent.value = _reportsSent.value + 1
        }
    }
}
