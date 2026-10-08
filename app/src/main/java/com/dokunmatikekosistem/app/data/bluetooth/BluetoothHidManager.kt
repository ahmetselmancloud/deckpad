package com.dokunmatikekosistem.app.data.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
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

    private val hidThread = HandlerThread("HidSenderThread", Process.THREAD_PRIORITY_MORE_FAVORABLE).apply { start() }
    private val hidHandler = Handler(hidThread.looper)

    private val coalescer = MouseReportCoalescer(minReportIntervalMs = 10L)

    private val flushRunnable = Runnable {
        val device = connectedDevice ?: return@Runnable
        val now = SystemClock.uptimeMillis()
        val reports = coalescer.onFlush(now)
        for (report in reports) {
            dispatchMouseReport(device, report)
        }
    }

    private fun dispatchMouseReport(device: BluetoothDevice, report: CoalescedMouseReport) {
        val rawReport = ByteArray(5)
        com.dokunmatikekosistem.app.data.hid.HidMouseReport.buildInto(
            rawReport,
            report.dx,
            report.dy,
            report.wheel,
            report.pan,
            report.leftButton,
            report.rightButton,
            report.middleButton
        )
        val sent = hidDevice?.sendReport(
            device,
            com.dokunmatikekosistem.app.data.hid.HidDescriptor.MOUSE_REPORT_ID.toInt(),
            rawReport
        )
        if (sent == true) {
            _reportsSent.value = _reportsSent.value + 1
        }
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _reportsSent = MutableStateFlow(0)
    override val reportsSent: StateFlow<Int> = _reportsSent

    private var hidDevice: BluetoothHidDevice? = null
    @Volatile
    private var connectedDevice: BluetoothDevice? = null

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "DeckPad",
        "DeckPad PC Remote",
        "DeckPad",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        com.dokunmatikekosistem.app.data.hid.HidDescriptor.DESCRIPTOR
    )

    @Volatile
    private var isAppRegistered = false

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered pluggedDevice=$pluggedDevice")
            isAppRegistered = registered
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
                    releaseAll()
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
                    ?: bondedDevices.firstOrNull { it.address.equals("6C:2F:80:26:B5:E6", ignoreCase = true) || it.name?.contains("MSI", ignoreCase = true) == true }
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
        if (isAppRegistered && hidDevice != null) {
            Log.d(TAG, "register() called but app is already registered with HID proxy. Triggering host connect...")
            tryConnectBondedHost(null)
            return
        }
        if (!shouldAttemptRegister(_connectionState.value)) {
            Log.d(TAG, "register() ignored, already ${_connectionState.value}")
            return
        }
        Log.d(TAG, "register() called")
        _connectionState.value = ConnectionState.REGISTERING
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val executor = Executor { command -> command.run() }

        val currentProxy = hidDevice
        if (currentProxy != null) {
            try {
                try {
                    currentProxy.unregisterApp()
                } catch (e: Exception) {
                    Log.w(TAG, "unregisterApp prior to re-register failed", e)
                }
                val registerRequested = currentProxy.registerApp(sdpSettings, null, null, executor, callback)
                Log.d(TAG, "registerApp() on existing proxy called, requested=$registerRequested")
                if (registerRequested != true) {
                    _connectionState.value = ConnectionState.ERROR
                }
                return
            } catch (e: SecurityException) {
                Log.e(TAG, "registerApp() threw SecurityException", e)
                _connectionState.value = ConnectionState.ERROR
                return
            }
        }

        try {
            val proxyRequested = manager.adapter.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        Log.d(TAG, "onServiceConnected: profile=$profile proxy=$proxy")
                        hidDevice = proxy as BluetoothHidDevice
                        try {
                            var registerRequested = hidDevice?.registerApp(sdpSettings, null, null, executor, callback)
                            Log.d(TAG, "registerApp() called, requested=$registerRequested")
                            if (registerRequested != true) {
                                Log.w(TAG, "registerApp() returned false, attempting unregister and retry...")
                                try {
                                    hidDevice?.unregisterApp()
                                    registerRequested = hidDevice?.registerApp(sdpSettings, null, null, executor, callback)
                                    Log.d(TAG, "registerApp() retry result=$registerRequested")
                                } catch (e: Exception) {
                                    Log.w(TAG, "registerApp() retry threw exception", e)
                                }
                            }
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
                        releaseAll()
                        isAppRegistered = false
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
        val now = SystemClock.uptimeMillis()
        hidHandler.post {
            val device = connectedDevice ?: return@post
            val decision = coalescer.onEvent(
                dx = dx,
                dy = dy,
                wheel = wheelDelta,
                pan = panDelta,
                leftButton = leftButtonPressed,
                rightButton = rightButtonPressed,
                middleButton = middleButtonPressed,
                nowMs = now
            )

            if (decision.cancelPendingFlush) {
                hidHandler.removeCallbacks(flushRunnable)
            }
            decision.scheduleDelayMs?.let { delayMs ->
                hidHandler.postDelayed(flushRunnable, delayMs)
            }

            for (report in decision.reportsToSend) {
                dispatchMouseReport(device, report)
            }
        }
    }

    override fun sendKeyboardReport(modifierBits: Int, usageCode: Int) {
        val device = connectedDevice ?: return
        val report = com.dokunmatikekosistem.app.data.hid.HidKeyboardReport.build(
            com.dokunmatikekosistem.app.domain.HidKeyChord(modifierBits, usageCode)
        )
        hidHandler.post {
            val sent = hidDevice?.sendReport(device, com.dokunmatikekosistem.app.data.hid.HidDescriptor.KEYBOARD_REPORT_ID.toInt(), report)
            if (sent == true) {
                _reportsSent.value = _reportsSent.value + 1
            }
        }
    }

    override fun releaseKeyboardReport() {
        val device = connectedDevice ?: return
        val report = com.dokunmatikekosistem.app.data.hid.HidKeyboardReport.release()
        hidHandler.post {
            val sent = hidDevice?.sendReport(device, com.dokunmatikekosistem.app.data.hid.HidDescriptor.KEYBOARD_REPORT_ID.toInt(), report)
            if (sent == true) {
                _reportsSent.value = _reportsSent.value + 1
            } else {
                Log.w(TAG, "releaseKeyboardReport() sendReport failed or hidDevice is null")
            }
        }
    }

    override fun releaseAll() {
        val targetDevice = connectedDevice
        hidHandler.post {
            hidHandler.removeCallbacks(flushRunnable)
            val now = SystemClock.uptimeMillis()
            val reports = coalescer.forceReleaseAll(now)
            val device = targetDevice ?: connectedDevice
            if (device != null) {
                for (report in reports) {
                    dispatchMouseReport(device, report)
                }
                val kbdRelease = com.dokunmatikekosistem.app.data.hid.HidKeyboardReport.release()
                hidDevice?.sendReport(
                    device,
                    com.dokunmatikekosistem.app.data.hid.HidDescriptor.KEYBOARD_REPORT_ID.toInt(),
                    kbdRelease
                )
            }
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
        hidHandler.post {
            val sent = hidDevice?.sendReport(device, reportId, pressReport)
            Log.d(TAG, "sendConsumerReport: press sent=$sent reportId=$reportId usage=0x${usageCode.toString(16)}")
            if (sent == true) {
                _reportsSent.value = _reportsSent.value + 1
            }
        }
        hidHandler.postDelayed({
            val relSent = hidDevice?.sendReport(device, reportId, releaseReport)
            Log.d(TAG, "sendConsumerReport: release sent=$relSent")
        }, 70L)
    }
}
