# Arka Planda Bluetooth Bağlantısının Korunması — Foreground Service (Tasarım)

## Bağlam

Faz 2'nin kalan manuel cihaz testi checklist'i sürerken (2026-09-28) kullanıcı şu sorunu bildirdi: uygulama önde/aktifken Bluetooth HID bağlantısı hiç kopmuyor, ama ekran kilitlenince, uygulama arka plana alınınca, hatta bildirim çekmecesine bakarken bile **her zaman** kopuyor.

`systematic-debugging` ile kök neden doğrulandı: `MainActivity`/`MainViewModel`/`BluetoothHidManager` yalnızca Activity'nin (ve onunla birlikte process'in) canlı kalmasına bağlı çalışıyor; hiçbir foreground service veya benzeri bir mekanizma Android'e "bu process arka planda önemli bir iş yapıyor, öldürme" demiyor. Bu, Android'in genel arka plan kısıtlamaları ve özellikle Samsung'un agresif pil yönetimi (test cihazı: Samsung Galaxy S23, Android 16) ile process'in hemen durdurulmasına/öldürülmesine yol açıyor, bu da Bluetooth HID kaydını (`BluetoothHidDevice` proxy + `registerApp`) düşürüyor.

## Kapsam

- Kullanıcı "Eşleştir/Bağlan"a bastığında başlayan, kalıcı bir bildirim gösteren bir **foreground service** eklemek — bu sayede process arka planda (ekran kilitli/uygulama arkada/bildirim çekmecesi açık) canlı kalır ve Bluetooth HID kaydı/bağlantısı düşmez.
- Kullanıcı uygulamayı görevlerden (recent apps) kapattığında servis de durur, bağlantı temiz şekilde bırakılır — "arka plandan silmediği sürece bağlı kalsın" davranışı.
- Kapsam dışı: otomatik yeniden bağlanma mantığı (kullanıcı elle "Eşleştir/Bağlan"a tekrar basarak `ERROR`/`DISCONNECTED` durumundan çıkabiliyor zaten, bu davranış değişmiyor), bildirimde ek istatistik gösterme, izin reddi için özel bir yönlendirme ekranı.

## Mimari

`BluetoothHidManager` (Hilt `@Singleton`, `data/bluetooth/BluetoothHidManager.kt`) **hiç değiştirilmiyor** — mevcut `BluetoothHidManagerTest.kt` bozulmadan kalır. Yanına ince, bağımsız bir `HidForegroundService` eklenir. `MainActivity`, kullanıcı "Eşleştir/Bağlan"a bastığında (izinler tamamsa) hem mevcut `viewModel.onConnectClicked()`'ı hem de bu yeni servisi başlatır. Servis, zaten var olan `hidManager.connectionState: StateFlow<ConnectionState>`'i gözlemleyip bildirim metnini günceller; kendi bağlantı mantığı yoktur, sadece "bu process'i öldürme" sinyalini Android'e taşır ve durumu bildirimde yansıtır.

## Bileşenler

### 1. `data/notification/NotificationHelper.kt` (yeni, saf/test edilebilir)

```kotlin
package com.dokunmatikekosistem.app.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dokunmatikekosistem.app.domain.ConnectionState

const val HID_SERVICE_NOTIFICATION_CHANNEL_ID = "hid_service_channel"
const val HID_SERVICE_NOTIFICATION_ID = 1

object NotificationHelper {

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

    fun titleFor(state: ConnectionState): String = when (state) {
        ConnectionState.DISCONNECTED -> "Bağlı değil"
        ConnectionState.REGISTERING -> "Bağlanıyor..."
        ConnectionState.REGISTERED -> "Kayıtlı, bağlantı bekleniyor"
        ConnectionState.CONNECTED -> "Bağlı"
        ConnectionState.ERROR -> "Hata"
    }

    fun build(context: Context, state: ConnectionState): Notification =
        NotificationCompat.Builder(context, HID_SERVICE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Dokunmatik Ekosistem")
            .setContentText(titleFor(state))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
```

`titleFor(state)` saf bir fonksiyon olduğu için unit test edilebilir; `ConnectionState.label()` (`MainActivity.kt`'deki mevcut ekran-metni fonksiyonu) ile aynı Türkçe etiketleri kullanır — iki yerde ayrı ayrı bakım gerektirmemesi için `MainActivity.kt`'deki `ConnectionState.label()` bu fonksiyona yönlendirilecek şekilde birleştirilir (tek doğruluk kaynağı `NotificationHelper.titleFor`).

### 2. `presentation/service/HidForegroundService.kt` (yeni)

```kotlin
package com.dokunmatikekosistem.app.presentation.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
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

@AndroidEntryPoint
class HidForegroundService : Service() {

    @Inject lateinit var hidManager: HidManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        startForeground(
            HID_SERVICE_NOTIFICATION_ID,
            NotificationHelper.build(this, hidManager.connectionState.value),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        hidManager.connectionState
            .onEach { state ->
                val manager = getSystemService(android.app.NotificationManager::class.java)
                manager.notify(HID_SERVICE_NOTIFICATION_ID, NotificationHelper.build(this, state))
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

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

### 3. `MainActivity.kt` (değişiklik)

- `connectWithPermissionCheck()`: `needsRuntimePermission` kontrolü artık `BLUETOOTH_CONNECT` VE (Android 13+ için) `POST_NOTIFICATIONS` iznini birlikte kapsayacak şekilde genişletilir (iki izin de eksikse `ActivityResultContracts.RequestMultiplePermissions()` ile birlikte istenir; mevcut tek-izinlik `requestBluetoothConnect` launcher'ı `RequestMultiplePermissions` kontratına geçirilir).
- `requestDiscoverable` sonucu callback'inde (mevcut `viewModel.onConnectClicked()` çağrısının hemen yanına): `ContextCompat.startForegroundService(this, Intent(this, HidForegroundService::class.java))`.
- `MainActivity.kt`'nin sonundaki özel `ConnectionState.label()` extension fonksiyonu silinir; `TouchpadScreen`'de `connectionState.label()` çağrısı yerine `NotificationHelper.titleFor(connectionState)` çağrılır — ekran metni ve bildirim metni artık tek doğruluk kaynağından (`NotificationHelper.titleFor`) geliyor.

### 4. `AndroidManifest.xml` (değişiklik)

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
...
<service
    android:name=".presentation.service.HidForegroundService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false" />
```

## Veri Akışı

```
Kullanıcı "Eşleştir/Bağlan"a basar
  → MainActivity.connectWithPermissionCheck()
    → BLUETOOTH_CONNECT + POST_NOTIFICATIONS izinleri kontrol/istenir (tek launcher, RequestMultiplePermissions)
    → izinler tamamsa: requestDiscoverableAndConnect() (mevcut akış, değişmiyor)
    → requestDiscoverable sonucu geldiğinde (mevcut callback):
        viewModel.onConnectClicked()  // hidManager.register() — DEĞİŞMEDİ
        ContextCompat.startForegroundService(context, HidForegroundService intent)

HidForegroundService.onCreate()
  → NotificationHelper.ensureChannel(this)
  → startForeground(ID, bildirim(mevcut connectionState.value), TYPE_CONNECTED_DEVICE)
  → hidManager.connectionState.onEach { state -> bildirimi güncelle }.launchIn(scope)

Kullanıcı uygulamayı görevlerden kapatır
  → Android Activity'yi yok eder; Service foreground olduğu için hemen ölmez
  → onTaskRemoved(rootIntent) tetiklenir → stopSelf() → bildirim kalkar, process artık
    arka planda tutulmuyor, Bluetooth bağlantısı normal şekilde düşer (beklenen davranış)
```

## Hata Yönetimi

- `POST_NOTIFICATIONS` reddedilirse: mevcut `BLUETOOTH_CONNECT` reddi davranışıyla aynı — `register()`/servis hiç başlatılmaz, kullanıcı "Eşleştir/Bağlan"a tekrar basıp izin isteğini yeniden tetikleyebilir. Ekstra bir "ayarlardan aç" yönlendirmesi bu kapsamda eklenmiyor.
- `connectionState` `ERROR` olursa: bildirim "Hata" gösterir ama servis kendini durdurmaz — kullanıcı elle "Eşleştir/Bağlan"a tekrar basıp `register()`'ı tetikleyebilir (mevcut `shouldAttemptRegister` zaten `ERROR`'dan yeniden denemeye izin veriyor).
- `startForegroundService` birden fazla kez çağrılırsa (kullanıcı "Eşleştir/Bağlan"a birden fazla kez basarsa): `Service.onCreate()` yalnızca servis ilk kez oluşturulduğunda çalışır (Android işletim sistemi kuralı), sonraki `startForegroundService` çağrıları zaten çalışan servise `onStartCommand` üzerinden ulaşır — `onStartCommand` yalnızca `START_STICKY` döner, tekrar `startForeground`/`ensureChannel` çağırmaz, bu yüzden idempotent'tir.

## Test Planı

- `NotificationHelper.titleFor()` unit test edilir: her `ConnectionState` değeri için doğru Türkçe metnin üretildiği doğrulanır (5 test, mevcut `ConnectionState` enum değerleri kadar).
- `HidForegroundService`'in kendisi (Android `Service` sınıfı, gerçek `NotificationManager`/`startForeground` çağırıyor) bu projenin konvansiyonuna uygun olarak unit test edilmez (Compose UI gibi) — manuel cihaz testiyle doğrulanır.
- Mevcut 93 unit testin tamamı geçmeye devam etmeli; `BluetoothHidManagerTest.kt`, `MainViewModelTest.kt` gibi dosyalarda değişiklik gerekmiyor (bu sınıflar dokunulmuyor).
- Manuel cihaz testi (checklist): (1) bağlan → ekranı kilitle → 2-3 dakika bekle → ekranı aç, hâlâ "Bağlı" mı kontrol et; (2) bağlıyken başka bir uygulamaya geç, birkaç dakika kullan, geri dön → hâlâ bağlı mı; (3) bildirim çekmecesini aç/kontrol et, bağlantı kopmuyor mu; (4) uygulamayı görevlerden kapat → bildirim kalkıyor mu, uygulamayı tekrar açınca "Bağlı değil"den başlıyor mu (beklenen, temiz sıfırlama).

## Kabul Kriterleri

- Ekran kilitlenince veya uygulama arka plana alınınca (görevlerden kapatılmadığı sürece) Bluetooth HID bağlantısı canlı kalır.
- Kullanıcı uygulamayı görevlerden kapattığında bildirim kalkar ve bağlantı temiz şekilde düşer.
- Mevcut 93 unit test + yeni `NotificationHelper` testleri geçer.
- Bildirim sessiz (LOW öncelik, ses/titreşim yok) ve tek satır durum metni gösterir.
