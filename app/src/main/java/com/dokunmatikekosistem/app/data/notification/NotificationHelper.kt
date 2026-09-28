package com.dokunmatikekosistem.app.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dokunmatikekosistem.app.R
import com.dokunmatikekosistem.app.domain.ConnectionState

const val HID_SERVICE_NOTIFICATION_CHANNEL_ID = "hid_service_channel"
const val HID_SERVICE_NOTIFICATION_ID = 1

/**
 * Single source of truth for how each [ConnectionState] is described to the user —
 * both the on-screen "Durum: ..." text and the persistent foreground-service notification
 * read from [titleFor] so the two never drift apart.
 */
object NotificationHelper {

    fun titleFor(state: ConnectionState): String = when (state) {
        ConnectionState.DISCONNECTED -> "Bağlı değil"
        ConnectionState.REGISTERING -> "Eşleştiriliyor"
        ConnectionState.REGISTERED -> "Kayıtlı, bağlantı bekleniyor"
        ConnectionState.CONNECTED -> "Bağlı"
        ConnectionState.ERROR -> "Hata"
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            HID_SERVICE_NOTIFICATION_CHANNEL_ID,
            "Bağlantı Durumu",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, state: ConnectionState): Notification =
        NotificationCompat.Builder(context, HID_SERVICE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Dokunmatik Ekosistem")
            .setContentText(titleFor(state))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
