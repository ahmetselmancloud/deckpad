# Faz 1: Bluetooth Core & MVP — Tasarım

**Kaynak plan:** `touchpad_ekosistem_plan_v3.pdf`, Bölüm 14 (Faz 1 satırı), Bölüm 3.2 (Mimari Katmanlar).
**Önceki faz:** Faz 0 (bkz. `2026-09-21-faz0-fizibilite-prototip-design.md`, `2026-09-21-faz0-test-sonuclari.md`) — spike kodla temel varsayım doğrulandı.

## Amaç

Faz 0'ın spike kodunda kanıtlanmış işlevi (HID mouse+keyboard kaydı, Windows ile eşleşme, tek parmak hareketi, sol tık) plan Bölüm 3.2'de tanımlı asıl mimariyle yeniden inşa etmek: MVVM + Clean Architecture, Hilt (bağımlılık enjeksiyonu), Coroutines/Flow (asenkron), DataStore (ayarlar altyapısı).

## Kapsam Dışı

GestureRecognizer'ın asıl işlevi (çok parmaklı jest sınıflandırma — Faz 2), DeviceRepository'nin asıl işlevi (çoklu cihaz hafızası — Faz 5), ayarlar ekranının kendisi (Faz 5), scroll/klavye/çok parmaklı jestler (Faz 2-3). Bu bileşenler plan Bölüm 3.2'de mimarinin parçası olarak adı geçiyor ama gerçek ihtiyaçları henüz doğmadığı için Faz 1'de kurulmuyor — YAGNI.

## Katman Yapısı (Paket Bazlı Clean Architecture)

- **`com.dokunmatikekosistem.app.data`**
  - `BluetoothHidManager` — Hilt `@Singleton`, HID kaydı/bağlantı/rapor gönderme. Faz 0'daki sınıfın yeniden yazımı.
  - `HidReportBuilder` paketi (`hid/` alt paket korunur) — `HidDescriptor`, `HidMouseReport`. Faz 0'dan **değişmeden** taşınır (zaten pure Kotlin, test edilmiş).
  - `SettingsDataStore` — Hilt injected, Preferences DataStore sarmalayıcısı. Faz 1'de içi boş/iskelet; Faz 5'te gerçek ayarlarla doldurulacak.

- **`com.dokunmatikekosistem.app.domain`**
  - `ConnectionState` — Faz 0'daki enum, artık burada (data katmanına bağımlı olmayan bir model).
  - `HidManager` interface — Faz 0'daki soyutlama, Hilt `@Binds` ile `BluetoothHidManager`'a bağlanır.

- **`com.dokunmatikekosistem.app.presentation`**
  - `MainViewModel` — `@HiltViewModel`, constructor injection ile `HidManager` alır.
  - `MainActivity` + `TouchpadScreen` (Compose) — ViewModel artık `by viewModels()` ile alınır (Hilt), elle `MainViewModel(hidManager)` oluşturma kalkar.

## register() Idempotency (Faz 0'da bulunan mimari not, şimdi çözülüyor)

Faz 0'da art arda "Eşleştir/Bağlan" tıklamaları `BluetoothHidManager.register()`'ı birden fazla kez tetikleyip alttaki Bluetooth yığınını çakıştırıyordu; geçici çözüm olarak guard UI/Activity katmanına eklenmişti (kırılgan).

Faz 1'de: `register()` artık manager'ın kendi `_connectionState.value` durumuna bakar — yalnızca `DISCONNECTED` veya `ERROR` durumundayken gerçek bir kayıt işlemi başlatır; `REGISTERING`/`REGISTERED`/`CONNECTED` durumundaysa no-op olarak döner (log'lanır ama hiçbir yeni `getProfileProxy`/`registerApp` çağrısı yapılmaz). Guard mantığı tamamen `BluetoothHidManager`'a taşınır; `MainViewModel`/`MainActivity` artık bu konuda hiçbir şey bilmez — sadece butona her tıklamada `register()`'ı çağırabilir, güvenlidir.

## Hilt Kurulumu

- `@HiltAndroidApp` ile işaretli bir `Application` sınıfı eklenir (Faz 0'da yoktu).
- `AppModule` (`di/` paketi, `data` katmanı altında ya da ayrı `di` paketi): `@Binds` ile `HidManager` → `BluetoothHidManager`; `@Provides` ile `SettingsDataStore` için `DataStore<Preferences>` örneği.
- `MainActivity` → `@AndroidEntryPoint`.
- `MainViewModel` → `@HiltViewModel`, `@Inject constructor(private val hidManager: HidManager)`.

## Faz 0'dan Taşınıp Korunacak Davranışlar (aynen)

- `ACTION_REQUEST_DISCOVERABLE` akışı (Ayarlar ekranına gitmeden görünürlük istemi).
- Runtime `BLUETOOTH_CONNECT` izin akışı.
- `onTap()` press+release (iki ayrı rapor).
- Sürüklemede alt-piksel residual birikimi (yavaş hareketlerin kaybolmaması).
- `android:configChanges` — Hilt ViewModel artık config change'de yeniden oluşmayacağı için bu geçici çözüm **kaldırılabilir** (gerçek çözüm geldi); `MainActivity` config change'de yeniden yaratılsa bile `MainViewModel` `by viewModels()` üzerinden korunur, `BluetoothHidManager` Hilt `@Singleton` olduğu için de hayatta kalır.

## Test Planı

- `HidMouseReportTest` — Faz 0'dan değişmeden taşınır.
- `MainViewModelTest` — `FakeHidManager` ile Faz 0'daki testler korunur (constructor injection'a uyacak şekilde küçük düzenleme).
- **Yeni:** `BluetoothHidManager`'ın idempotent `register()` davranışı için unit test — gerçek `BluetoothHidDevice`/Android framework olmadan test edilebilmesi için, `register()`'ın içindeki state-guard mantığı ayrı, saf bir fonksiyon olarak çıkarılıp (`shouldAttemptRegister(currentState: ConnectionState): Boolean` gibi) o test edilir; tam entegrasyon (gerçek Bluetooth çağrıları) yine yalnızca fiziksel cihazda doğrulanabilir.

## Çıkış Kriteri (plandan)

Temel imleç hareketi ve tıklama çalışıyor — Hilt/Clean Architecture ile yeniden inşa edilmiş, config change'e dayanıklı, idempotent register() ile.

## Riskler

| Risk | Not |
|---|---|
| Hilt kurulum hatası (KSP/kapt uyumsuzluğu) | Bu ortamda derleme yapılamıyor (Android SDK yok); kullanıcı Android Studio'da build alıp doğrulayacak |
| `by viewModels()` geçişinde constructor injection hatası | Manuel kod incelemesiyle (subagent) doğrulanacak, gerçek derleme kullanıcı tarafında |
