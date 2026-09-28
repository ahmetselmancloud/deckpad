package com.dokunmatikekosistem.app.presentation.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Process
import androidx.core.app.ServiceCompat
import com.dokunmatikekosistem.app.data.notification.HID_SERVICE_NOTIFICATION_ID
import com.dokunmatikekosistem.app.data.notification.NotificationHelper
import com.dokunmatikekosistem.app.domain.HidManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Keeps this process alive while a Bluetooth HID connection is active, so Android/OEM
 * background-kill policies don't drop the connection when the screen locks or the app is
 * backgrounded. Owns no connection logic itself — only mirrors [HidManager.connectionState]
 * into a persistent notification, and stops itself when the user removes the app from recents.
 */
@AndroidEntryPoint
class HidForegroundService : Service() {

    @Inject
    lateinit var hidManager: HidManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            HID_SERVICE_NOTIFICATION_ID,
            NotificationHelper.build(this, hidManager.connectionState.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        hidManager.connectionState
            .onEach { state ->
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(HID_SERVICE_NOTIFICATION_ID, NotificationHelper.build(this, state))
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The app has no way to reset a HidManager singleton left mid-teardown (e.g. the
        // Bluetooth stack reporting onAppStatusChanged(registered=false) as the host
        // disconnects, which BluetoothHidManager surfaces as ConnectionState.ERROR) — so
        // when the user explicitly removes the app from recents, kill this process outright
        // instead of leaving it running. The next launch then starts from a real, clean
        // DISCONNECTED state rather than an ERROR left over from the previous session.
        stopSelf()
        super.onTaskRemoved(rootIntent)
        Process.killProcess(Process.myPid())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
