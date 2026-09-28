# Arka Planda Bluetooth Bağlantısının Korunması Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Uygulama arka plana alındığında/ekran kilitlendiğinde Bluetooth HID bağlantısının Android tarafından öldürülmesini önlemek — ince bir foreground service ile process'i canlı tutmak, kullanıcı uygulamayı görevlerden kapatana kadar.

**Architecture:** `BluetoothHidManager` (Hilt `@Singleton`) hiç değişmiyor. Yeni, bağımsız bir `HidForegroundService`, kullanıcı "Eşleştir/Bağlan"a bastığında başlar, `hidManager.connectionState`'i izleyip kalıcı bir bildirimde yansıtır, kullanıcı uygulamayı görevlerden kapatınca (`onTaskRemoved`) kendini durdurur. Bildirim metni üretimi ayrı, saf/test edilebilir bir `NotificationHelper.titleFor()` fonksiyonunda toplanır — ekran metni de aynı fonksiyonu kullanır (tek doğruluk kaynağı).

**Tech Stack:** Kotlin, Hilt (`@AndroidEntryPoint` Service desteği zaten `hilt-android` bağımlılığında mevcut), `androidx.core.app.ServiceCompat` (core-ktx 1.15.0, API 28-36 arası foreground service type uyumluluğunu otomatik yönetir), JUnit4, `./gradlew test`/`assembleDebug` (JAVA_HOME=Android Studio JBR).

## Global Constraints

- Mevcut 93 unit testin tamamı geçmeye devam etmeli; hiçbiri bu değişiklikle bozulmamalı.
- `BluetoothHidManager.kt` ve `BluetoothHidManagerTest.kt`'ye **dokunulmuyor** — foreground service bu sınıfın bağlantı mantığına karışmıyor, sadece `HidManager` arayüzünü (mevcut `connectionState: StateFlow<ConnectionState>`) tüketiyor.
- Bildirim metinleri (`ConnectionState` → kullanıcıya gösterilen Türkçe metin) **tek yerde** tanımlanır: `NotificationHelper.titleFor(state)`. Hem ekrandaki "Durum: ..." metni hem bildirim metni bu fonksiyonu çağırır — birbirinden bağımsız iki kopya olmaz.
- `minSdk = 28`, `compileSdk = 36`, `targetSdk = 36` (`app/build.gradle.kts`). `POST_NOTIFICATIONS` izni yalnızca `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU` (33) olduğunda runtime'da istenir; `BLUETOOTH_CONNECT` izni yalnızca `>= Build.VERSION_CODES.S` (31) olduğunda istenir (mevcut davranış korunur).
- Foreground service tipi Android'in "connectedDevice" kategorisi (`ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`) olmalı; `ServiceCompat.startForeground(...)` üzerinden çağrılmalı (ham platform API'si değil) — bu, minSdk 28'den itibaren güvenli çalışır.
- Test komutu: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest` (proje kökü `D:\projects\touchpad-ekosistem`, Bash'ten).
- Bu projede Compose UI testi ve Android `Service`/`Context` gerektiren kod için unit test altyapısı (Robolectric vb.) **yok** — `app/build.gradle.kts`'te sadece `testImplementation(libs.junit)` var. `NotificationHelper.titleFor()` gibi saf Kotlin fonksiyonlar unit test edilir; `HidForegroundService`, `NotificationHelper.ensureChannel()`/`build()` (Context/NotificationManager/NotificationCompat kullanan kısımlar) manuel cihaz testiyle doğrulanır.

---

### Task 1: `NotificationHelper` — bildirim metni ve bildirim inşası

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/data/notification/NotificationHelper.kt`
- Test: `app/src/test/java/com/dokunmatikekosistem/app/data/notification/NotificationHelperTest.kt`

**Interfaces:**
- Consumes: mevcut `com.dokunmatikekosistem.app.domain.ConnectionState` enum'ı (`DISCONNECTED, REGISTERING, REGISTERED, CONNECTED, ERROR`, `app/src/main/java/com/dokunmatikekosistem/app/domain/ConnectionState.kt`).
- Produces: `const val HID_SERVICE_NOTIFICATION_CHANNEL_ID: String`, `const val HID_SERVICE_NOTIFICATION_ID: Int`; `NotificationHelper.titleFor(state: ConnectionState): String` (saf fonksiyon); `NotificationHelper.ensureChannel(context: Context): Unit`; `NotificationHelper.build(context: Context, state: ConnectionState): Notification`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dokunmatikekosistem.app.data.notification

import com.dokunmatikekosistem.app.domain.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationHelperTest {

    @Test
    fun `titleFor DISCONNECTED is Bagli degil`() {
        assertEquals("Bağlı değil", NotificationHelper.titleFor(ConnectionState.DISCONNECTED))
    }

    @Test
    fun `titleFor REGISTERING is Esleştiriliyor`() {
        assertEquals("Eşleştiriliyor", NotificationHelper.titleFor(ConnectionState.REGISTERING))
    }

    @Test
    fun `titleFor REGISTERED is Kayitli bekliyor`() {
        assertEquals("Kayıtlı, bağlantı bekleniyor", NotificationHelper.titleFor(ConnectionState.REGISTERED))
    }

    @Test
    fun `titleFor CONNECTED is Bagli`() {
        assertEquals("Bağlı", NotificationHelper.titleFor(ConnectionState.CONNECTED))
    }

    @Test
    fun `titleFor ERROR is Hata`() {
        assertEquals("Hata", NotificationHelper.titleFor(ConnectionState.ERROR))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.data.notification.NotificationHelperTest"`
Expected: FAIL — derleme hatası, `NotificationHelper` sınıfı/paketi yok.

- [ ] **Step 3: Write minimal implementation**

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.data.notification.NotificationHelperTest"`
Expected: PASS (5/5)

- [ ] **Step 5: Commit**

```bash
cd "D:\projects\touchpad-ekosistem"
git add app/src/main/java/com/dokunmatikekosistem/app/data/notification/NotificationHelper.kt app/src/test/java/com/dokunmatikekosistem/app/data/notification/NotificationHelperTest.kt
git commit -m "feat: add NotificationHelper as single source of truth for connection-state text"
```

---

### Task 2: `HidForegroundService`, manifest, ve `MainActivity` bağlantısı

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/presentation/service/HidForegroundService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt` (tam dosya değişimi aşağıda)

**Interfaces:**
- Consumes: Task 1'den `NotificationHelper.ensureChannel`, `NotificationHelper.build`, `NotificationHelper.titleFor`, `HID_SERVICE_NOTIFICATION_ID`; mevcut `domain.HidManager` arayüzü (`connectionState: StateFlow<ConnectionState>`), Hilt DI (`HidManager`'ın gerçek implementasyonu `BluetoothHidManager` zaten `@Singleton` + `@Inject constructor` ile bağlı, `@AndroidEntryPoint` sınıflar `@Inject lateinit var hidManager: HidManager` ile erişebilir — mevcut `MainViewModel`'in yaptığı gibi).
- Produces: `HidForegroundService` (Android `Service`, manifest'te kayıtlı, `MainActivity` tarafından `startForegroundService` ile başlatılır).

Bu task'ta yeni unit test yok (Global Constraints'te belirtildiği gibi `Service`/`Context` kullanan kod bu projede unit test edilmiyor). Doğrulama: `./gradlew assembleDebug` (derleme) + `./gradlew testDebugUnitTest` (mevcut testlerin bozulmadığını doğrulama). Cihaz doğrulaması Task 3'te.

- [ ] **Step 1: `HidForegroundService.kt` dosyasını oluştur**

```kotlin
package com.dokunmatikekosistem.app.presentation.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
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
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

- [ ] **Step 2: `AndroidManifest.xml`'i güncelle**

Mevcut izin bloğuna ekle (dosyanın en üstündeki `<uses-permission>` listesine):

```xml
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`<application>` etiketi içine, `<activity>` bloğundan sonra ekle:

```xml
        <service
            android:name=".presentation.service.HidForegroundService"
            android:foregroundServiceType="connectedDevice"
            android:exported="false" />
```

Güncellenmiş dosyanın tamamı şöyle görünmeli:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".DokunmatikEkosistemApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.TouchpadEkosistem">
        <activity
            android:name=".presentation.MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.TouchpadEkosistem">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".presentation.service.HidForegroundService"
            android:foregroundServiceType="connectedDevice"
            android:exported="false" />
    </application>

</manifest>
```

- [ ] **Step 3: `MainActivity.kt`'yi güncelle (tam dosya)**

Dosyanın tamamını şununla değiştir:

```kotlin
package com.dokunmatikekosistem.app.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dokunmatikekosistem.app.data.gesture.GestureRecognizer
import com.dokunmatikekosistem.app.data.gesture.RawTouchEvent
import com.dokunmatikekosistem.app.data.notification.NotificationHelper
import com.dokunmatikekosistem.app.presentation.keyboard.VirtualKeyboard
import com.dokunmatikekosistem.app.presentation.service.HidForegroundService
import dagger.hilt.android.AndroidEntryPoint

private const val DISCOVERABLE_DURATION_SECONDS = 300

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestDiscoverable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Regardless of the result code (duration granted or cancelled), proceed:
        // registerApp() itself doesn't require discoverability, only pairing does.
        viewModel.onConnectClicked()
        ContextCompat.startForegroundService(this, Intent(this, HidForegroundService::class.java))
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // POST_NOTIFICATIONS being denied only means the persistent notification stays
        // hidden — it doesn't block starting the foreground service, so only
        // BLUETOOTH_CONNECT gates whether we proceed to connect.
        val bluetoothGranted = results[Manifest.permission.BLUETOOTH_CONNECT] ?: true
        if (bluetoothGranted) {
            requestDiscoverableAndConnect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TouchpadScreen(viewModel, onConnectRequested = ::connectWithPermissionCheck)
            }
        }
    }

    private fun connectWithPermissionCheck() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissionsToRequest.isEmpty()) {
            requestDiscoverableAndConnect()
        } else {
            requestPermissions.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun requestDiscoverableAndConnect() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION_SECONDS)
        }
        requestDiscoverable.launch(discoverableIntent)
    }
}

@Composable
fun TouchpadScreen(viewModel: MainViewModel, onConnectRequested: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()
    val reportsSent by viewModel.reportsSent.collectAsState()
    val activeLayout by viewModel.activeLayout.collectAsState()
    val modifierState by viewModel.modifierState.collectAsState()
    var keyboardVisible by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Durum: ${NotificationHelper.titleFor(connectionState)}")
            Text("Gönderilen rapor: $reportsSent")
            Row {
                Button(onClick = onConnectRequested) { Text("Eşleştir/Bağlan") }
                Button(onClick = { keyboardVisible = !keyboardVisible }) { Text("Klavye") }
                Button(onClick = { viewModel.onLayoutToggleClicked() }) {
                    Text(if (activeLayout is com.dokunmatikekosistem.app.data.keyboard.TurkishQLayout) "TR Q" else "EN US")
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp)
                    .background(Color.DarkGray)
                    .pointerInput(Unit) {
                        val recognizer = GestureRecognizer()
                        awaitEachGesture {
                            while (true) {
                                val event = awaitPointerEvent()
                                val timeMs = System.currentTimeMillis()
                                for (change in event.changes) {
                                    val raw: RawTouchEvent? = when {
                                        change.pressed && change.previousPressed.not() ->
                                            RawTouchEvent.PointerDown(change.id.value.toInt(), change.position.x, change.position.y, timeMs)
                                        change.pressed && change.previousPressed ->
                                            RawTouchEvent.PointerMove(change.id.value.toInt(), change.position.x, change.position.y, timeMs)
                                        !change.pressed && change.previousPressed ->
                                            RawTouchEvent.PointerUp(change.id.value.toInt(), change.position.x, change.position.y, timeMs)
                                        else -> null
                                    }
                                    if (raw != null) {
                                        change.consume()
                                        recognizer.onEvent(raw)?.let { viewModel.onGesture(it) }
                                    }
                                }
                                if (event.type == PointerEventType.Release && event.changes.all { !it.pressed }) break
                            }
                        }
                    }
            )
            if (keyboardVisible) {
                Box(modifier = Modifier.weight(1f)) {
                    VirtualKeyboard(
                        layout = activeLayout,
                        modifierState = modifierState,
                        onKeyTyped = { viewModel.onKeyTyped(it) },
                        onShiftClicked = { viewModel.onShiftClicked() },
                        onCapsLockClicked = { viewModel.onCapsLockClicked() },
                        onCtrlClicked = { viewModel.onCtrlClicked() },
                        onAltClicked = { viewModel.onAltClicked() },
                        onWinClicked = { viewModel.onWinClicked() },
                        onCtrlAltDelClicked = { viewModel.onCtrlAltDelClicked() }
                    )
                }
            }
        }
    }
}
```

(Not: eski dosyanın sonundaki `private fun ConnectionState.label()` extension fonksiyonu ve onun `import com.dokunmatikekosistem.app.domain.ConnectionState` satırı tamamen kaldırıldı — artık `NotificationHelper.titleFor()` tek doğruluk kaynağı.)

- [ ] **Step 4: Derlemeyi doğrula**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Tüm unit testleri çalıştır**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tüm testler (93 eski + Task 1'in 5 yenisi = 98) yeşil.

- [ ] **Step 6: Commit**

```bash
cd "D:\projects\touchpad-ekosistem"
git add app/src/main/java/com/dokunmatikekosistem/app/presentation/service/HidForegroundService.kt app/src/main/AndroidManifest.xml app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt
git commit -m "feat: add HidForegroundService to keep Bluetooth HID connection alive in background"
```

---

### Task 3: Cihaz Testi Checklist'i (manuel, kod değişikliği yok)

**Files:** Yok — bu task saf manuel doğrulama.

- [ ] **Step 1: Uygulamayı gerçek cihaza kur**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew installDebug`

- [ ] **Step 2: Bağlan ve izinleri onayla**

Uygulamayı aç, "Eşleştir/Bağlan"a bas. Bluetooth iznine ek olarak artık bir bildirim izni isteği de çıkmalı (Android 13+ cihazlarda) — ikisini de onayla. Windows'a bağlan.

- [ ] **Step 3: Bildirimi doğrula**

Bildirim çekmecesini aç: "Dokunmatik Ekosistem" başlıklı, kalıcı (kaydırarak kapatılamayan), sessiz bir bildirim görünmeli, metni ekrandaki "Durum: ..." metniyle aynı olmalı (örn. ikisi de "Bağlı" demeli).

- [ ] **Step 4: Aşağıdaki senaryoları doğrula**

1. Bağlıyken telefonun ekranını kilitle, 2-3 dakika bekle, ekranı aç → uygulama hâlâ "Bağlı" göstermeli, Windows'ta touchpad/klavye hâlâ çalışmalı.
2. Bağlıyken başka bir uygulamaya geç (örn. Chrome), birkaç dakika orada kal, geri dön → hâlâ "Bağlı".
3. Bildirim çekmecesini açıp birkaç saniye orada kal → bağlantı kopmamalı.
4. Uygulamayı görevlerden (recent apps) kapat → bildirim kalkmalı; uygulamayı tekrar açtığında "Bağlı değil"den başlamalı (temiz sıfırlama, beklenen davranış).

- [ ] **Step 5: Memory'yi güncelle**

Tüm senaryolar doğrulandıktan sonra `touchpad-ekosistem-proje-durumu.md` memory dosyasını güncelle: "Arka planda Bluetooth bağlantısının korunması (foreground service) TAMAMLANDI+cihazda doğrulandı" ve tarih ekle.
