# Faz 2: Temel Jestler & Klavye — Tasarım

**Kaynak plan:** `touchpad_ekosistem_plan_v3.pdf`, Bölüm 14 (Faz 2 satırı), Bölüm 4 (Jest Haritası), Bölüm 6 (Klavye Layout), Bölüm 7 (Jest Ayrıştırma), Bölüm 8 (Haptik).
**Önceki fazlar:** Faz 0 (spike, doğrulandı), Faz 1 (Hilt/Clean Architecture mimarisi, doğrulandı).

## Amaç

Faz 1'in üzerine 2 parmak scroll, 2 parmak dokunma (sağ tık), sanal klavye (Türkçe Q + İngilizce US) ve tap-to-drag (sürükleme kilidi) eklemek; haptik geri bildirim altyapısını kurmak.

## Kapsam Dışı

3/4 parmak Windows makroları (Win+Tab vb. — Faz 3), zoom/Ctrl+Scroll (Faz 3), ayarlar ekranı ve tam klavye düzeni yönetimi (Faz 5), Türkçe F ve Almanca QWERTZ düzenleri (ihtiyaç doğarsa sonra), palm rejection'ın gelişmiş hali (temas alanı/basınç analizi — Faz 4), imleç ivme kalibrasyonu (Faz 4).

## 1. HID Descriptor Genişletmesi

`HidDescriptor.DESCRIPTOR`'daki mouse bölümü (Report ID 1) şu an 3 byte (buttons, X, Y). Buna şunlar eklenecek:
- **Wheel (dikey scroll):** Usage Page Generic Desktop, Usage Wheel (0x38), 8-bit signed relative değer.
- **AC Pan (yatay scroll):** Usage Page Consumer (0x0C), Usage AC Pan (0x0238), 8-bit signed relative değer.

Yeni rapor formatı: `[buttons, X, Y, wheel, AC Pan]` (5 byte). `HidMouseReport.build()` imzası güncellenecek: `build(dx, dy, wheelDelta, panDelta, leftButtonPressed, rightButtonPressed)`. Sağ tık için buttons byte'ının bit 1'i kullanılacak (bit 0 = sol, bit 1 = sağ — descriptor zaten "Usage Maximum (3)" ile 3 butonu destekliyor, Faz 0'dan beri hazır).

Klavye raporu (Report ID 2) formatı Faz 0'dan beri descriptor'da var (modifier byte + reserved byte + 6 key array) ve **değişmeyecek** — ilk kez Faz 2'de gerçek veri göndermek için kullanılacak.

## 2. Klavye Düzeni Katmanı

`domain.KeyboardLayout` interface:
```kotlin
interface KeyboardLayout {
    fun mapChar(char: Char): HidKeyChord?
}
data class HidKeyChord(val modifierBits: Int, val usageCode: Int)
```
`data.keyboard.TurkishQLayout` ve `data.keyboard.EnglishUsLayout` bunu implement eder — her biri karakter→(modifier, HID usage) eşleme tablosu tutar. Türkçe karakterler (ğ, ü, ş, ı, ö, ç, İ) için doğru HID usage kodları ve gerekiyorsa Shift/AltGr modifier bitleri tanımlanır (kaynak: USB HID Usage Tables, Windows Türkçe Q sürücüsünün scancode eşlemesi).

Sanal klavyede aktif düzeni tutan basit bir state (`MainViewModel` içinde) ve düzen değiştirme butonu — tam "Ayarlar Ekranı" (Bölüm 10) Faz 5'te gelecek, burada sadece iki düzen arası hızlı geçiş var.

## 3. GestureRecognizer

`data.gesture.GestureRecognizer`, saf Kotlin (Android bağımsız, unit test edilebilir):

```kotlin
sealed interface RawTouchEvent {
    data class PointerDown(val id: Int, val x: Float, val y: Float, val timeMs: Long) : RawTouchEvent
    data class PointerMove(val id: Int, val x: Float, val y: Float, val timeMs: Long) : RawTouchEvent
    data class PointerUp(val id: Int, val x: Float, val y: Float, val timeMs: Long) : RawTouchEvent
}

sealed interface RecognizedGesture {
    data class CursorMove(val dx: Int, val dy: Int) : RecognizedGesture
    object LeftClick : RecognizedGesture
    object RightClick : RecognizedGesture
    data class Scroll(val vDelta: Int, val hDelta: Int) : RecognizedGesture
    object DragLockEngaged : RecognizedGesture
    data class DragMove(val dx: Int, val dy: Int) : RecognizedGesture
    object DragLockReleased : RecognizedGesture
}

class GestureRecognizer {
    fun onEvent(event: RawTouchEvent): RecognizedGesture?
}
```

Compose UI, `pointerInput` içinde `awaitPointerEventScope` ile ham `PointerEvent`'leri okuyup `RawTouchEvent`'e çevirir ve `GestureRecognizer.onEvent()`'e iletir; dönen `RecognizedGesture` `MainViewModel`'e gönderilir.

**Eşik değerleri (plan Bölüm 7, ilk tahmin — cihazda test edilip ayarlanacak):**
- 2 parmak dokunma → sağ tık ayrımı: parmaklar arası max 150ms fark, dokunma süresi max 200ms, hareket mesafesi max 10dp.
- Tap-to-drag: iki dokunma arası max 300ms (debounce), ikinci dokunuşta min 150ms basılı tutma.
- 2 parmak scroll: parmaklar 10dp'den fazla hareket ederse dokunma değil scroll olarak sınıflandırılır.

## 4. BluetoothHidManager Genişletmesi

```kotlin
fun sendMouseReport(dx: Int, dy: Int, wheelDelta: Int, panDelta: Int, leftButtonPressed: Boolean, rightButtonPressed: Boolean)
fun sendKeyboardReport(modifierBits: Int, usageCode: Int)  // key down
fun releaseKeyboardReport()  // key up (tüm sıfır rapor)
```

## 5. UI Değişiklikleri

- Dokunmatik alan: `detectDragGestures`/`detectTapGestures` yerine `awaitPointerEventScope` tabanlı ham işleme (GestureRecognizer'a besleme).
- Açılır sanal klavye paneli: göster/gizle butonu, QWERTY tuş düzeni (Compose), düzen değiştirme butonu (TR Q ↔ EN US).
- Sağ tık artık ayrı bir "buton" değil, jestin kendisi (2 parmak dokunma).

## 6. Haptik Geri Bildirim

`data.haptics.HapticManager` (Android `VibratorManager` sarmalayıcısı), Hilt `@Singleton`. Tetiklenme noktaları: sol tık, sağ tık, sürükleme kilidi başlangıcı (plan Bölüm 8'in örneklediği 3 olay).

## Test Planı

- `HidMouseReportTest` genişletilecek: yeni 5-byte format, wheel/pan/right-button senaryoları.
- Yeni `GestureRecognizerTest`: sentetik `RawTouchEvent` dizileri ile her jest tipinin doğru sınıflandırıldığını test eder (saf Kotlin, Android framework gerekmez).
- Yeni `TurkishQLayoutTest`, `EnglishUsLayoutTest`: karakter→HID usage eşlemesinin doğruluğu.
- Gerçek cihaz testi: Windows'ta scroll, sağ tık, Türkçe karakter girişi, düzen değişimi, sürükleme kilidi.

## Çıkış Kriteri (plandan)

Klavye Türkçe karakterleri doğru gönderiyor; scroll çalışıyor.
