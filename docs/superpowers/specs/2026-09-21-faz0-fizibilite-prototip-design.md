# Faz 0: Fizibilite & Prototip — Tasarım

**Kaynak plan:** `touchpad_ekosistem_plan_v3.pdf`, Bölüm 14 (Faz 0 satırı), Bölüm 3, Bölüm 5, Bölüm 15.

## Amaç

Windows'un bir Android telefonu, Bluetooth HID mouse+keyboard cihazı olarak gerçekten tanıyıp tanımadığını, bağlantının stabil kalıp kalmadığını ve gecikmenin kabul edilebilir olup olmadığını doğrulamak. Test cihazı bir Samsung telefon olacak — plan bunu risk listesinde ("bazı Samsung/Xiaomi sürümleri HID Device rolünü kısıtlı destekler") açıkça vurguluyor, bu yüzden Faz 0'ın asıl amacı bu varsayımı erken doğrulamak/çürütmek.

## Kapsam Dışı

Scroll, zoom, çok parmaklı jestler, klavye karakter gönderimi, ayarlar ekranı, güvenlik önlemleri (idle disconnect vb.), çoklu cihaz hafızası. Bunların hepsi sonraki fazlarda ele alınacak.

## Mimari Yaklaşımı

Plan, bu fazı açıkça "Fizibilite & **Prototip**" olarak adlandırıyor ve çıkış kriterini yalnızca "stabil bağlantı + gecikme ölçümü" olarak tanımlıyor. Bölüm 3.2'deki MVVM + Clean Architecture, Hilt, Coroutines/Flow, DataStore katmanları projenin genel mimarisini tanımlıyor ama hangi fazda kurulacağını belirtmiyor.

Karar: Faz 0 **hızlı bir spike** olarak yazılacak — tek modül, minimal katmanlama, Hilt/DataStore yok. Amaç mümkün olan en hızlı şekilde temel varsayımı test etmek. Faz 1'de asıl mimari (`HidReportBuilder`, `GestureRecognizer`, `BluetoothManager`, `DeviceRepository`, Hilt DI) kurulurken bu spike kodu referans alınacak ama yeniden yazılacağı kabul ediliyor — bu normal ve beklenen bir maliyet.

## Proje Kurulumu

- **Konum:** `D:\claude code\touchpad-ekosistem` (kendi git deposu, `D:\claude code` kök dizini git deposu değil, her alt proje kendi deposuna sahip).
- **Paket adı:** `com.dokunmatikekosistem.app`
- **Dil/Çatı:** Kotlin, Jetpack Compose.
- **minSdkVersion:** 28 (Android 9, `BluetoothHidDevice` API gereksinimi — plan Bölüm 3.1).

## Bileşenler

- `MainActivity` — tek Activity, Compose UI barındırır.
- `BluetoothHidManager` — plain Kotlin sınıf (henüz Hilt inject edilmiyor):
  - Kompozit HID descriptor kaydı: mouse report (X/Y delta + button 1) + keyboard report iskeleti (descriptor'da yer alıyor ama Faz 0'da gerçek tuş verisi göndermiyor — amaç Windows'un kompozit descriptor'ı kabul edip etmediğini test etmek, plan Bölüm 3.3).
  - Eşleştirme/bağlantı durumu yönetimi (`BluetoothHidDevice.registerApp`, callback'ler).
  - Rapor gönderme fonksiyonu (`sendMouseReport(dx, dy, button)`).
- `MainViewModel` — basit state holder (`StateFlow` ile bağlantı durumu, rapor sayacı), Hilt olmadan doğrudan `ViewModel()` miras alacak.

## UI Akışı

Tek ekran:
1. **Bağlantı durumu göstergesi:** Bağlı değil / Eşleştiriliyor / Bağlı / Hata (renkli metin/ikon).
2. **"Eşleştir/Bağlan" butonu:** HID uygulamasını kaydeder, eşleştirilmiş cihazlar arasından seçim yapılmasını sağlar (sistem eşleştirme diyaloğu tetiklenir).
3. **Dokunmatik alan:** Ekranın büyük bir kısmı — `pointerInput` ile parmak sürükleme algılanır, `MotionEvent` delta'sı ham haliyle (OS ivmesi eklenmeden, plan Bölüm 5 uyarısına uygun) `sendMouseReport`'a iletilir.
4. **Dokunma (tap):** Sol tık olarak gönderilir (button 1 press+release).
5. **Log/sayaç alanı:** Gönderilen rapor sayısı ve son gönderim zaman damgası ekranda gösterilir — hassas latency ölçüm aracı değil, "bağlantı canlı mı, gecikme gözle fark ediliyor mu" seviyesinde gözlem içindir.

## Test Planı (Faz 0 çıkış kriteri)

- Samsung cihazda: HID uygulaması kaydı başarılı mı?
- Windows 10/11'de cihaz Bluetooth ayarlarında mouse+keyboard olarak görünüyor mu?
- Parmak hareketi Windows'ta imleç hareketine dönüşüyor mu?
- Dokunma sol tık olarak çalışıyor mu?
- Bağlantı en az birkaç dakika kopmadan stabil kalıyor mu?
- Gözle gecikme fark ediliyor mu (kabaca "anlık" mı, yoksa belirgin gecikme mi)?

Sonuç ne olursa olsun (başarı ya da başarısızlık) bulgular not edilecek — plan Bölüm 15'teki "Cihaz/OS uyumsuzluğu" riskine karşı fallback stratejisi (companion app üzerinden WiFi/RFCOMM) gerekirse bu aşamada değerlendirilecek.

## Riskler (bu faza özel)

| Risk | Not |
|---|---|
| Samsung'un HID Device rolünü desteklememesi/kısıtlı desteklemesi | Plan'da adı geçen bilinen risk; Faz 0'ın var oluş nedeni |
| Kompozit descriptor'ın Windows tarafından reddedilmesi | Gerekirse mouse-only descriptor ile geri düşülüp ayrı test edilir |
| Çift imleç ivmesi gözlemi | Faz 0'da ham delta gönderilecek, kalibrasyon Faz 4'e bırakılıyor |
