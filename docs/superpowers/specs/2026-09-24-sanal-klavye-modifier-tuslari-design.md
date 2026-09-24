# Sanal Klavye — Modifier Tuşları ve Windows OSK Davranışı (Tasarım)

## Bağlam

Faz 2 cihaz testinde kullanıcı, sanal klavyenin (`VirtualKeyboard`, şu an `MainActivity.kt` içine gömülü) modifier tuş davranışının Windows'un kendi ekran klavyesinden (OSK) geride olduğunu tespit etti:

- Shift tek bir sticky latch: bir kez basınca açık kalıyor, tekrar basana kadar tüm harfler büyük yazılıyor. Windows OSK'da Shift tek basışta yalnızca bir sonraki karakteri etkiler (one-shot), çift dokunma/kilit ile CapsLock gibi kalıcı hale gelir.
- Ayrı bir CapsLock tuşu yok.
- Ctrl / Alt / Win gibi modifier tuşları hiç yok, dolayısıyla Windows'ta klavye kısayolları (Ctrl+C, Alt+Tab vb.) veya Ctrl+Alt+Del gönderilemiyor.
- Ek olarak kod incelemesinde bulunan bir bug: EN US diline geçildiğinde ekrandaki tuş etiketleri hâlâ sabit Türkçe karakterleri gösteriyor (`VirtualKeyboard` içindeki dizilim hardcoded, `activeLayout` state'ine bağlı değil).

Bu spec, bu davranışları Windows OSK'ya yakın hale getirmeyi kapsar. Görsel/estetik tam yeniden tasarım (buton stilleri, genel görünüm) bu işin kapsamı **dışındadır** — yalnızca davranış (Shift/CapsLock/Ctrl/Alt/Win/Ctrl+Alt+Del) ve TR/EN etiket senkronizasyon bug'ı kapsanır.

## Mevcut Kod Durumu (inceleme özeti)

- `VirtualKeyboard` composable: `MainActivity.kt:158-183`, hardcoded TR karakter dizileri (satır 161-164), Material3 `Button` ile serbest `Row`/`Column` düzeni, explicit boyutlandırma yok.
- Tuş tıklama zinciri: `VirtualKeyboard.onClick` → `TouchpadScreen` → `MainViewModel.onKeyTyped(char)` (`MainViewModel.kt:69-73`) → `activeLayout.mapChar(char)` → `HidManager.sendKeyboardReport(modifierBits, usageCode)` → `releaseKeyboardReport()` (anlık down+up, basılı tutma yok).
- HID rapor formatı: `HidKeyboardReport.kt` — `[modifier, reserved, key1..key6]`, sadece `key1` kullanılıyor. `MODIFIER_SHIFT = 0x02` tek tanımlı modifier sabiti.
- TR/EN katmanları: `TurkishQLayout` (`EnglishUsLayout`'u sarmalıyor) / `EnglishUsLayout`, `domain/KeyboardLayout.mapChar(char): HidKeyChord?`. Shift/büyük-küçük harf kontrolü şu an katman içinde `char.isUpperCase()` ile yapılıyor.
- Shift state: `MainActivity.kt` içinde `remember { mutableStateOf(false) }`, sticky toggle, ViewModel'e önceden büyütülmüş karakter gönderiliyor (dolaylı modifier).
- `docs/superpowers/specs/2026-09-23-faz2-temel-jestler-klavye-design.md` — Faz 2 kapsamının temel TR/EN + basit Shift toggle olduğunu, tam Ayarlar Ekranı/gelişmiş klavye yönetiminin Faz 5'e bırakıldığını belirtiyor. Bu spec o kapsamı modifier tuşları yönünde genişletiyor.

## Kapsam

- Shift: one-shot (bir sonraki karakter için) + çift-tık ile kilit (Locked, CapsLock gibi kalıcı).
- Bağımsız bir CapsLock tuşu (Shift'ten ayrı, kendi başına toggle).
- Ctrl / Alt / Win: sticky modifier tuşları — birine basınca aktif kalır, bir sonraki karakter/tuşla birlikte gönderilip otomatik bırakılır (kombinasyon oluşturma).
- Özel Ctrl+Alt+Del butonu.
- TR/EN dil geçişinde ekrandaki tuş etiketlerinin doğru güncellenmesi (mevcut bug fix, aynı refactor'ın doğal parçası).
- Kapsam dışı: görsel/estetik tam klavye yeniden tasarımı, `HidManager`/`releaseKeyboardReport` sayaç/hata kontrolü (Faz 3'e bırakıldı), Ayarlar Ekranı (Faz 5).

## Mimari Yaklaşım

Modifier state tek doğruluk kaynağı olarak **`MainViewModel`**'de tutulur; `VirtualKeyboard` composable'ı sadece bu state'i gösterir ve tuş event'lerini yukarı iletir. Gerekçe: Ctrl/Alt/Win karakterin büyük/küçük harfini etkilemiyor, dolayısıyla mevcut "Shift açıkken harfi önceden büyüt" trick'i bu modifier'lar için işe yaramaz — ViewModel'in doğru HID modifier byte'ını üretebilmesi için gerçek modifier state'i bilmesi gerekiyor. Bu aynı zamanda mevcut `TurkishQLayout`/`EnglishUsLayout` + `activeLayout` StateFlow deseniyle tutarlıdır ve test edilebilirlik sağlar (Compose recomposition'a bağımlı olmadan ViewModel unit testleriyle doğrulanabilir).

## Bileşenler

### 1. `domain/keyboard/KeyboardModifierState.kt` (yeni)

```kotlin
enum class ShiftState { Off, OneShot, Locked }

data class KeyboardModifierState(
    val shiftState: ShiftState = ShiftState.Off,
    val capsLockActive: Boolean = false,
    val ctrlActive: Boolean = false,
    val altActive: Boolean = false,
    val winActive: Boolean = false,
) {
    fun isUpperCaseEffective(): Boolean =
        (shiftState != ShiftState.Off) xor capsLockActive

    /** Yalnızca Ctrl/Alt/Win bitlerini döner. Shift biti burada YOK — Shift HID biti
     *  yalnızca gönderilen karakter fiilen büyük harfse anlamlıdır, bu yüzden
     *  MainViewModel.onKeyTyped içinde isUpperCaseEffective() sonucuna göre ayrıca eklenir. */
    fun stickyModifierBits(): Int {
        var bits = 0
        if (ctrlActive) bits = bits or HidKeyboardReport.MODIFIER_CTRL
        if (altActive) bits = bits or HidKeyboardReport.MODIFIER_ALT
        if (winActive) bits = bits or HidKeyboardReport.MODIFIER_WIN
        return bits
    }
}
```

### 2. `presentation/keyboard/VirtualKeyboard.kt` (yeni dosya, `MainActivity.kt`'den çıkarılır)

- `activeLayout: KeyboardLayout` üzerinden `displayRows(): List<List<Char>>` ile dinamik tuş dizilimi üretir (TR/EN etiket bug fix'i).
- Yeni modifier satırı: `Ctrl`, `Alt`, `Win`, `CapsLock`, `Ctrl+Alt+Del` tuşları; her sticky modifier kendi aktif/pasif görsel durumunu `KeyboardModifierState`'ten okur.
- Shift tuşu: tek tık → `onShiftClicked()`; çift tık algılama (300ms eşik) composable içinde zamanlama, karar ViewModel'e devredilir.

### 3. `MainViewModel.kt` (genişletme)

- `_modifierState: MutableStateFlow<KeyboardModifierState>`
- `onShiftClicked()`: `Off → OneShot`, `OneShot → Off`; 300ms içindeki ikinci tık → `Locked`; `Locked` iken tık → `Off`.
- `onCapsLockClicked()`: `capsLockActive` bağımsız toggle.
- `onModifierClicked(modifier: Ctrl|Alt|Win)`: sticky toggle.
- `onKeyTyped(char: Char)`:
  1. `upper = modifierState.value.isUpperCaseEffective()`
  2. `chord = activeLayout.mapChar(char)` — **`mapChar` artık her zaman küçük harf `Char` alır**, büyük/küçük harf seçimi kaldırılır (katmandan kaldırılan sorumluluk).
  3. `modifierBits = modifierState.value.effectiveModifierBits() or (if (upper) HidKeyboardReport.MODIFIER_SHIFT else 0)`
  4. `hidManager.sendKeyboardReport(modifierBits, chord.usageCode)`; `releaseKeyboardReport()`
  5. Temizlik: `shiftState == OneShot → Off` (Locked ve Off değişmez); `ctrlActive/altActive/winActive → false`; `capsLockActive` dokunulmaz.
- `onCtrlAltDelClicked()`: `modifierBits = MODIFIER_CTRL or MODIFIER_ALT`, `usageCode = 0x4C` (Delete Forward), rapor gönder + bırak; mevcut sticky state'e dokunmaz.

### 4. `data/hid/HidKeyboardReport.kt`

Yeni sabitler: `MODIFIER_CTRL = 0x01`, `MODIFIER_ALT = 0x04`, `MODIFIER_WIN = 0x08` (mevcut `MODIFIER_SHIFT = 0x02` yanına; USB HID modifier byte bit sırasına uygun). HID rapor formatı (`[modifier, reserved, key1..key6]`) **değişmiyor** — Ctrl+Alt+Del gibi "1 tuş + çoklu modifier" senaryosu zaten mevcut formatla desteklenir.

### 5. `domain/KeyboardLayout.kt`, `TurkishQLayout.kt`, `EnglishUsLayout.kt`

`mapChar(char: Char)` imzası: artık her zaman küçük harf karakter alır, `isUpperCase()` kontrolü katmanlardan kaldırılır — büyük/küçük harf seçimi tamamen `MainViewModel`/`KeyboardModifierState` sorumluluğuna taşınır.

## Veri Akışı

**Normal karakter (örn. "s"):**
```
VirtualKeyboard.onClick('s')
  → viewModel.onKeyTyped('s')
    → upper = modifierState.isUpperCaseEffective()
    → chord = activeLayout.mapChar('s')
    → modifierBits = modifierState.stickyModifierBits() or (upper ? SHIFT : 0)
    → hidManager.sendKeyboardReport(modifierBits, chord.usageCode)
    → hidManager.releaseKeyboardReport()
    → modifierState güncelle: OneShot→Off, Ctrl/Alt/Win→false, Locked/CapsLock değişmez
```

**Ctrl+Alt+Del:**
```
onCtrlAltDelClicked()
  → hidManager.sendKeyboardReport(MODIFIER_CTRL or MODIFIER_ALT, 0x4C)
  → hidManager.releaseKeyboardReport()
  // sticky state etkilenmez
```

## Hata Yönetimi

- Mevcut `HidManager`/Bluetooth hata yolu değişmiyor.
- Çift-tık algılama basit zaman damgası karşılaştırması, ekstra coroutine/thread gerekmiyor.
- Bilinen mevcut eksik (`releaseKeyboardReport` sayaç/hata kontrolü yok) bu spec kapsamında ele alınmıyor — Faz 3'e bırakıldı.

## Test Planı

1. `KeyboardModifierState`/ViewModel davranış testleri: Shift Off→OneShot→Off; çift-tık→Locked→tık→Off; CapsLock bağımsız toggle; CapsLock açıkken Shift'e basınca küçük harf üretimi (XOR).
2. `onKeyTyped` testleri: karakter sonrası OneShot/Ctrl/Alt/Win temizleniyor mu, Locked/CapsLock temizlenmiyor mu, doğru modifier bit kombinasyonu HID'e gidiyor mu (mock `HidManager` ile capture).
3. `onCtrlAltDelClicked` testi: doğru modifier+usage code gönderiliyor mu, sticky state etkilenmiyor mu.
4. TR/EN etiket geçişi: `VirtualKeyboard` artık `activeLayout`'tan dinamik ürettiği için `displayRows()` fonksiyonu unit test edilebilir (TR/EN için doğru karakterleri döndürüyor mu).
5. Cihaz testi (checklist, manuel): Windows'ta gerçek Ctrl+Alt+Del tetiklenmesi, Ctrl+C/V gibi kombinasyonlar, Shift one-shot/lock davranışı, CapsLock, TR/EN etiket geçişinin görsel doğrulaması.

## Kabul Kriterleri

- Shift tek tıkta yalnızca bir sonraki karakteri etkiler, çift tıkta kilitlenir.
- Bağımsız CapsLock tuşu büyük/küçük harfi kalıcı değiştirir, Shift ile birlikte XOR mantığıyla çalışır.
- Ctrl/Alt/Win sticky olarak aktifleşir, bir sonraki tuşla kombinasyon gönderilip otomatik bırakılır.
- Ctrl+Alt+Del butonu Windows'ta gerçek Görev Yöneticisi ekranını (veya güvenli masaüstü ekranını) tetikler.
- EN US moduna geçince ekrandaki tuş etiketleri İngilizce karakterleri gösterir.
- Mevcut 62 unit test + bu spec'in yeni testleri geçer.
