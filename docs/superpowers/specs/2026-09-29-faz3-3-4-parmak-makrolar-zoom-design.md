# Faz 3 — 3/4 Parmak Windows Makroları ve Zoom (Tasarım)

## Bağlam

Faz 0-2 tamamlandı (Bluetooth Core, temel jestler + sanal klavye + modifier tuşları, arka planda bağlantı korunması — hepsi cihazda doğrulandı). Orijinal plan PDF'inde (`touchpad_ekosistem_plan_v3.pdf`, artık repoda yok) Faz 3 kapsamı "3/4 parmak Windows makroları (Win+Tab vb.), zoom/Ctrl+Scroll" olarak özetlenmişti. Bu spec, o kapsamı somut bir tasarıma dönüştürüyor.

Ayrıca Faz 2'den parklanmış küçük bir bulgu (`releaseKeyboardReport` sayaç/hata kontrolü yapmıyordu) bu iş kapsamında **ayrı ve önce** düzeltildi (commit `821d19f`) — bu spec'in konusu değil, sadece bağlamsal not.

## Kapsam

Windows'un kendi resmi hassas touchpad (precision touchpad) jest standardı ([Microsoft — Touchpad Gestures for Windows 11](https://www.microsoft.com/en-us/windows/learning-center/touchpad-gestures)) referans alınarak:

- **3 parmak:** yukarı=Görev Görünümü, aşağı=Masaüstünü Göster, sol=sonraki uygulama, sağ=önceki uygulama, dokunma=Arama.
- **4 parmak:** yukarı/aşağı 3 parmakla aynı (Görev Görünümü/Masaüstü), sol/sağ=sanal masaüstü geçişi, dokunma=Bildirim Merkezi.
- **Zoom:** 2 parmak pinch (birbirinden uzaklaşma/yaklaşma) = basılı Ctrl + fare tekerleği (çoğu Windows uygulamasında standart yakınlaştırma kısayolu).

Kapsam dışı: jest davranışının Windows Ayarlar'dan özelleştirilmiş olma ihtimali (kullanıcının kendi Windows touchpad ayarları farklıysa bizim gönderdiğimiz kısayollar yine de standart davranışı tetikler, bu konfigüre edilebilir değil), 5+ parmak jestleri.

## Mimari

Mevcut `GestureRecognizer` (saf Kotlin, framework bağımsız, `app/src/main/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizer.kt`) genişletilir — yeni parmak sayısı (3/4) swipe/tap sınıflandırması ve pinch (mesafe değişimi) sınıflandırması eklenir. **Hiçbir HID katman/arayüz değişikliği gerekmiyor**: tüm makrolar mevcut `HidManager.sendKeyboardReport(modifierBits, usageCode)` + `releaseKeyboardReport()` çiftiyle gönderilebiliyor (Faz 2'nin modifier tuşları işinde zaten `MODIFIER_CTRL/ALT/WIN` sabitleri eklenmişti). Zoom (Ctrl+Scroll) için de aynı API yeterli — Ctrl, "sadece modifier, tuş yok" (`usageCode=0`) bir klavye raporuyla basılı tutulur, o sırada fare tekerleği raporları gönderilir; bu, mevcut sürükleme-kilidi (`DragLockEngaged`/`DragMove`/`DragLockReleased`) desenindeki gibi üçlü bir yaşam döngüsüyle (`PinchZoomStarted`/`PinchZoomDelta`/`PinchZoomEnded`) modellenir.

## Bileşenler

### 1. `GestureRecognizer.kt` — yeni `RecognizedGesture` türleri

```kotlin
enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

// RecognizedGesture sealed interface'ine eklenecek yeni üyeler:
data class ThreeFingerSwipe(val direction: SwipeDirection) : RecognizedGesture
object ThreeFingerTap : RecognizedGesture
data class FourFingerSwipe(val direction: SwipeDirection) : RecognizedGesture
object FourFingerTap : RecognizedGesture
object PinchZoomStarted : RecognizedGesture
data class PinchZoomDelta(val units: Int) : RecognizedGesture  // + = parmaklar uzaklaşıyor (yakınlaştır), - = yaklaşıyor (uzaklaştır)
object PinchZoomEnded : RecognizedGesture
```

**Swipe/tap algılama:** Oturum tüm parmaklar kalkınca (mevcut `RightClick` oturum-sonu deseniyle aynı yerde, `onUp`'ın son bloğunda) değerlendirilir. `sessionMaxPointers` 3 veya 4 ise: hareket tap eşiğindeyse (`TAP_MAX_MOVEMENT_PX`, `TAP_MAX_DURATION_MS` — mevcut sabitler) Tap; değilse, oturum boyunca canonical (en düşük id'li) parmağın net yer değiştirmesi hesaplanır — `|netDx|` ve `|netDy|`'den büyük olan eksen baskın yön olur, ve o eksendeki net mesafe yeni bir `SWIPE_MIN_DISTANCE_PX` (örn. 60f — mevcut `TAP_MAX_MOVEMENT_PX`=10f'ten belirgin büyük, kazara tetiklenmeyi önlemek için) eşiğini geçiyorsa `ThreeFingerSwipe`/`FourFingerSwipe` üretilir; geçmiyorsa hiçbir şey üretilmez (ne tap ne swipe — yarım kalan/belirsiz jest sessizce yutulur, mevcut kodun "else -> null" tarzına tutarlı).

**Pinch vs. scroll ayrımı (2 parmak, en kritik teknik nokta):** Mevcut `onMove`'daki `active.size == 2 && dragLockPointerId == null` bloğu genişletilir. Her hareket karesinde:
- `centroidDx = (dx1 + dx2) / 2`, `centroidDy = (dy1 + dy2) / 2` (iki parmağın bu karedeki ham delta'larının ortalaması — "birlikte nereye kaydılar")
- `distanceNow = hypot(x1-x2, y1-y2)` (iki parmak arası anlık mesafe), oturumda tutulan `lastPinchDistance` ile karşılaştırılır: `distanceChange = distanceNow - lastPinchDistance`

`|distanceChange|` > `hypot(centroidDx, centroidDy)` ise **pinch** (parmaklar birbirine göre baskın şekilde uzaklaşıyor/yaklaşıyor); aksi halde **scroll** (parmaklar birlikte aynı yöne kayıyor, mesafe ~sabit) — mevcut scroll kodu şu an bu ayrımı hiç yapmıyor (iki parmağın da ayrı ayrı hareket ettiğini kontrol ediyor ama yönlerini karşılaştırmıyor), bu iş kapsamında eklenen tek gerçek davranış değişikliği. Pinch modunda: ilk kareye `PinchZoomStarted` (bir kez), sonraki karelerde `distanceChange`'in `SCROLL_PX_PER_UNIT` benzeri bir sabite (`PINCH_PX_PER_UNIT`) bölünüp tam sayıya yuvarlanan kısmı `PinchZoomDelta(units)` olarak (mevcut `emitResidualMove`/scroll'daki residual-biriktirme deseniyle, alt-piksel kaybı olmadan), parmaklardan biri kalkınca `PinchZoomEnded`.

**`reset()` genişlemesi:** Yeni state (`lastPinchDistance`, pinch/swipe oturum bayrakları) da `reset()`'te temizlenir.

### 2. `MainViewModel.kt` — `onGesture` genişlemesi (yeni dosya yok, mevcut `when` bloğuna yeni dallar)

Yeni bir yardımcı fonksiyon `sendShortcut(modifierBits: Int, usageCode: Int)` eklenir (mevcut `onKeyTyped`'daki `sendKeyboardReport`+`releaseKeyboardReport` çiftini tekrar etmemek için, DRY):
```kotlin
private fun sendShortcut(modifierBits: Int, usageCode: Int) {
    hidManager.sendKeyboardReport(modifierBits, usageCode)
    hidManager.releaseKeyboardReport()
}
```

Makro eşlemesi (USB HID usage code'ları: Tab=`0x2B`, D=`0x07`, S=`0x16`, N=`0x11`, Sol Ok=`0x50`, Sağ Ok=`0x4F` — harfler `EnglishUsLayout`'taki `0x04 + (char - 'a')` formülüyle tutarlı):

```kotlin
is RecognizedGesture.ThreeFingerSwipe -> when (gesture.direction) {
    SwipeDirection.UP -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, 0x2B) // Win+Tab: Görev Görünümü
    SwipeDirection.DOWN -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, 0x07) // Win+D: Masaüstünü Göster
    SwipeDirection.LEFT -> sendShortcut(HidKeyboardReport.MODIFIER_ALT, 0x2B) // Alt+Tab: sonraki uygulama
    SwipeDirection.RIGHT -> sendShortcut(HidKeyboardReport.MODIFIER_ALT or HidKeyboardReport.MODIFIER_SHIFT, 0x2B) // Alt+Shift+Tab: önceki uygulama
}
RecognizedGesture.ThreeFingerTap -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, 0x16) // Win+S: Arama
is RecognizedGesture.FourFingerSwipe -> when (gesture.direction) {
    SwipeDirection.UP -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, 0x2B)
    SwipeDirection.DOWN -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, 0x07)
    SwipeDirection.LEFT -> sendShortcut(HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_WIN, 0x4F) // Ctrl+Win+Sağ Ok: sonraki sanal masaüstü
    SwipeDirection.RIGHT -> sendShortcut(HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_WIN, 0x50) // Ctrl+Win+Sol Ok: önceki sanal masaüstü
}
RecognizedGesture.FourFingerTap -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, 0x11) // Win+N: Bildirim Merkezi
RecognizedGesture.PinchZoomStarted ->
    hidManager.sendKeyboardReport(HidKeyboardReport.MODIFIER_CTRL, 0) // Ctrl'i basılı tut, tuş yok
is RecognizedGesture.PinchZoomDelta ->
    hidManager.sendMouseReport(0, 0, wheelDelta = gesture.units, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)
RecognizedGesture.PinchZoomEnded ->
    hidManager.releaseKeyboardReport() // Ctrl'i bırak
```

(Not: 4 parmak sol/sağ için "sol=sonraki, sağ=önceki" yönü, Faz 2'deki `virtualKeyboardShiftMap` gibi bir kural değil, bu spec'in kendi tercihi — "sola kaydır = bir sonrakini getir" sezgisiyle seçildi, sayfa çevirme/karusel jestleriyle tutarlı.)

## Hata Yönetimi

- Pinch sırasında bağlantı koparsa veya HID raporu başarısız olursa: mevcut desende olduğu gibi sessizce yutulur (`sendMouseReport`/`sendKeyboardReport` zaten `connectedDevice ?: return` ile korumalı), Ctrl basılı kalmaz çünkü `PinchZoomEnded` (parmaklardan biri kalkınca) her durumda `releaseKeyboardReport()` tetikler.
- Beklenmedik 5. parmak eklenirse: mevcut "üçüncü parmak aktif drag-lock'u bozmasın" deseniyle tutarlı olarak, 5 parmak durumu tanımsız bırakılır (hiçbir jest tetiklenmez), kapsam dışı.
- `GestureRecognizer.reset()` (Compose jest iptali): swipe/pinch için de yeni state (pinch mesafesi, oturum sayaçları) temizlenecek şekilde genişletilir.

## Test Planı

- Yeni `GestureRecognizerTest` senaryoları: 3/4 parmak yukarı/aşağı/sol/sağ swipe (sentetik `RawTouchEvent` dizileriyle), 3/4 parmak tap, pinch-açılma (zoom in) ve pinch-kapanma (zoom out) start/delta/end üçlüsü, **pinch'in scroll ile karışmadığını** doğrulayan test (parmaklar birbirinden uzaklaşırken `Scroll` DEĞİL `PinchZoomDelta` üretmeli) ve tersi (paralel hareket `Scroll` üretmeli, pinch değil) — bu ayrım testi en kritik olanı.
- `MainViewModelTest` genişletmesi: her yeni `RecognizedGesture` için doğru modifier+usage code kombinasyonunun `FakeHidManager`'a gittiğini doğrulayan testler (mevcut `onGesture` test desenine uygun, `fake.allKeyPresses`/`fake.allReports` üzerinden).
- Manuel cihaz testi (checklist): tüm 9 makronun gerçekten Windows'ta beklenen eylemi tetiklediği (Görev Görünümü, Masaüstü, uygulama geçişi, sanal masaüstü geçişi, Arama, Bildirim Merkezi) + pinch-zoom'un Chrome/Fotoğraflar gibi bir uygulamada gerçekten yakınlaştırma yaptığı.

## Kabul Kriterleri

- 3 parmak yukarı/aşağı/sol/sağ swipe ve dokunma, Windows'ta doğru eylemi tetikliyor.
- 4 parmak yukarı/aşağı/sol/sağ swipe ve dokunma, Windows'ta doğru eylemi tetikliyor.
- 2 parmak pinch, scroll ile karışmadan Ctrl+Scroll (zoom) gönderiyor; mevcut 2 parmak scroll davranışı bozulmuyor.
- Mevcut 98 unit test + bu spec'in yeni testleri geçiyor.
