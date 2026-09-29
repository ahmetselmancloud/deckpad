# Faz 3 — 3/4 Parmak Windows Makroları ve Zoom Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dokunmatik alanda 3/4 parmak kaydırma/dokunma jestleriyle Windows'un yerleşik touchpad makrolarını (Görev Görünümü, Masaüstünü Göster, uygulama/sanal masaüstü geçişi, Arama, Bildirim Merkezi) ve 2 parmak pinch jestiyle Ctrl+Scroll yakınlaştırmayı tetiklemek.

**Architecture:** Mevcut `GestureRecognizer` (saf Kotlin, framework bağımsız) genişletilir — yeni parmak sayısı (3/4) swipe/tap sınıflandırması ve pinch (mesafe değişimi vs. ortak hareket) sınıflandırması eklenir. Tüm yeni makrolar mevcut `HidManager.sendKeyboardReport(modifierBits, usageCode)` + `releaseKeyboardReport()` API'siyle gönderilir — HID katmanında hiçbir değişiklik yok. Pinch-zoom, mevcut sürükleme-kilidi (`DragLockEngaged`/`DragMove`/`DragLockReleased`) desenindeki gibi üçlü bir yaşam döngüsüyle (`PinchZoomStarted`/`PinchZoomDelta`/`PinchZoomEnded`) modellenir.

**Tech Stack:** Kotlin (saf, framework'süz `GestureRecognizer`), JUnit4, `./gradlew test`/`assembleDebug` (JAVA_HOME=Android Studio JBR).

## Global Constraints

- Mevcut 98 unit testin tamamı geçmeye devam etmeli; hiçbiri bu değişiklikle bozulmamalı (bir istisna: mevcut `three finger tap does not produce RightClick` testi, bu işin kendisi tarafından kasıtlı olarak değiştiriliyor — bkz. Task 1).
- HID katmanında (`BluetoothHidManager.kt`, `HidManager.kt` arayüzü, `HidKeyboardReport.kt`, `HidMouseReport.kt`) **hiçbir değişiklik yok** — tüm makrolar mevcut `sendKeyboardReport(modifierBits: Int, usageCode: Int)` + `releaseKeyboardReport()` ve `sendMouseReport(dx, dy, wheelDelta, panDelta, leftButtonPressed, rightButtonPressed)` API'leriyle gönderiliyor.
- USB HID usage code'ları (spec'ten, exact): Tab=`0x2B`, D=`0x07`, S=`0x16`, N=`0x11`, Sol Ok=`0x50`, Sağ Ok=`0x4F`.
- Modifier bit sabitleri zaten mevcut: `HidKeyboardReport.MODIFIER_CTRL = 0x01`, `MODIFIER_SHIFT = 0x02`, `MODIFIER_ALT = 0x04`, `MODIFIER_WIN = 0x08`.
- Makro eşlemesi (spec'ten, exact): 3/4 parmak yukarı=Win+Tab, aşağı=Win+D; 3 parmak sol=Alt+Tab, sağ=Alt+Shift+Tab, dokunma=Win+S; 4 parmak sol=Ctrl+Win+Sağ Ok (sonraki sanal masaüstü), sağ=Ctrl+Win+Sol Ok (önceki), dokunma=Win+N; pinch=Ctrl basılı + fare tekerleği.
- Bu projede Compose UI testi yok; `GestureRecognizer` saf Kotlin olduğu için tam unit test edilir, `MainViewModel.onGesture` da mevcut `FakeHidManager` deseniyle tam test edilir — bu task'larda test kapsamı eksiksiz olmalı (Faz 2'deki "Service/Context kodu test edilmiyor" istisnası bu task'larda GEÇERLİ DEĞİL, çünkü hiçbir Android framework bağımlılığı yok).
- Test komutu: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest` (proje kökü `D:\projects\touchpad-ekosistem`, Bash'ten).

---

### Task 1: 3/4 parmak swipe/tap sınıflandırması + Windows makroları

**Files:**
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizer.kt` (tam dosya aşağıda)
- Modify: `app/src/test/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizerTest.kt` (mevcut bir test değiştiriliyor + 8 yeni test ekleniyor)
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt` (tam dosya aşağıda)
- Modify: `app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt` (8 yeni test ekleniyor)

**Interfaces:**
- Produces: `enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }` (paket: `com.dokunmatikekosistem.app.data.gesture`); `RecognizedGesture` sealed interface'ine yeni üyeler: `data class ThreeFingerSwipe(val direction: SwipeDirection)`, `object ThreeFingerTap`, `data class FourFingerSwipe(val direction: SwipeDirection)`, `object FourFingerTap`. `MainViewModel`'e yeni private `sendShortcut(modifierBits: Int, usageCode: Int)` yardımcı fonksiyonu (Task 2 bunu tekrar kullanacak, ama kendi jestleri için doğrudan `hidManager` çağıracak, `sendShortcut` sadece iki-adımlı — gönder+bırak — makrolar için).

- [ ] **Step 1: Mevcut `three finger tap does not produce RightClick` testini güncelle, yeni testleri ekle (RED)**

`GestureRecognizerTest.kt`'de şu mevcut testi:

```kotlin
    @Test
    fun `three finger tap does not produce RightClick`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 100f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 100f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 100f, timeMs = 120))
        assertNull(result)
    }
```

şununla değiştir (aynı olay dizisi, artık `ThreeFingerTap` bekleniyor — bu, bu task'ın kasıtlı davranış değişikliği):

```kotlin
    @Test
    fun `three finger tap produces ThreeFingerTap`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 100f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 100f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 100f, timeMs = 120))
        assertEquals(RecognizedGesture.ThreeFingerTap, result)
    }
```

Dosyanın sonuna (kapanış `}`'den hemen önce) şu 8 yeni testi ekle:

```kotlin
    @Test
    fun `three finger swipe up produces ThreeFingerSwipe UP`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 300f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 300f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 300f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 200f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 300f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 300f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 200f, timeMs = 120))
        assertEquals(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.UP), result)
    }

    @Test
    fun `three finger swipe down produces ThreeFingerSwipe DOWN`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 100f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 200f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 100f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 100f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 200f, timeMs = 120))
        assertEquals(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.DOWN), result)
    }

    @Test
    fun `three finger swipe left produces ThreeFingerSwipe LEFT`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 300f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 300f, y = 200f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 300f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 200f, y = 100f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 300f, y = 200f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 300f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 200f, y = 100f, timeMs = 120))
        assertEquals(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.LEFT), result)
    }

    @Test
    fun `three finger swipe right produces ThreeFingerSwipe RIGHT`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 100f, y = 200f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 100f, y = 300f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 200f, y = 100f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 100f, y = 200f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 100f, y = 300f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 200f, y = 100f, timeMs = 120))
        assertEquals(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.RIGHT), result)
    }

    @Test
    fun `four finger swipe up produces FourFingerSwipe UP`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 300f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 300f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 300f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 3, x = 400f, y = 300f, timeMs = 30))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 200f, timeMs = 60))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 300f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 300f, timeMs = 110))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 3, x = 400f, y = 300f, timeMs = 120))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 200f, timeMs = 130))
        assertEquals(RecognizedGesture.FourFingerSwipe(SwipeDirection.UP), result)
    }

    @Test
    fun `four finger swipe left produces FourFingerSwipe LEFT`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 400f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 400f, y = 200f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 400f, y = 300f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 3, x = 400f, y = 400f, timeMs = 30))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 300f, y = 100f, timeMs = 60))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 400f, y = 200f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 400f, y = 300f, timeMs = 110))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 3, x = 400f, y = 400f, timeMs = 120))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 300f, y = 100f, timeMs = 130))
        assertEquals(RecognizedGesture.FourFingerSwipe(SwipeDirection.LEFT), result)
    }

    @Test
    fun `four finger tap produces FourFingerTap`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 100f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 3, x = 400f, y = 100f, timeMs = 30))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 100f, timeMs = 110))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 100f, timeMs = 120))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 3, x = 400f, y = 100f, timeMs = 130))
        assertEquals(RecognizedGesture.FourFingerTap, result)
    }

    @Test
    fun `three finger session with movement below the swipe threshold and above the tap threshold produces nothing`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 2, x = 300f, y = 100f, timeMs = 20))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 130f, timeMs = 50))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 200f, y = 100f, timeMs = 100))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 2, x = 300f, y = 100f, timeMs = 110))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 130f, timeMs = 120))
        assertNull(result)
    }
```

- [ ] **Step 2: Testlerin başarısız olduğunu doğrula**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.data.gesture.GestureRecognizerTest"`
Expected: FAIL — derleme hatası, `SwipeDirection`/`ThreeFingerSwipe`/`ThreeFingerTap`/`FourFingerSwipe`/`FourFingerTap` yok.

- [ ] **Step 3: `GestureRecognizer.kt`'yi tam olarak şununla değiştir**

```kotlin
package com.dokunmatikekosistem.app.data.gesture

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val TAP_MAX_MOVEMENT_PX = 10f
private const val TAP_MAX_DURATION_MS = 200L
private const val TWO_FINGER_DOWN_WINDOW_MS = 150L
private const val DRAG_LOCK_TAP_GAP_MS = 300L
private const val DRAG_LOCK_HOLD_MS = 150L
private const val SCROLL_PX_PER_UNIT = 24f
private const val SWIPE_MIN_DISTANCE_PX = 60f

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

sealed interface RawTouchEvent {
    val id: Int
    val x: Float
    val y: Float
    val timeMs: Long

    data class PointerDown(override val id: Int, override val x: Float, override val y: Float, override val timeMs: Long) : RawTouchEvent
    data class PointerMove(override val id: Int, override val x: Float, override val y: Float, override val timeMs: Long) : RawTouchEvent
    data class PointerUp(override val id: Int, override val x: Float, override val y: Float, override val timeMs: Long) : RawTouchEvent
}

sealed interface RecognizedGesture {
    data class CursorMove(val dx: Int, val dy: Int) : RecognizedGesture
    object LeftClick : RecognizedGesture
    object RightClick : RecognizedGesture
    data class Scroll(val vDelta: Int, val hDelta: Int) : RecognizedGesture
    object DragLockEngaged : RecognizedGesture
    data class DragMove(val dx: Int, val dy: Int) : RecognizedGesture
    object DragLockReleased : RecognizedGesture
    data class ThreeFingerSwipe(val direction: SwipeDirection) : RecognizedGesture
    object ThreeFingerTap : RecognizedGesture
    data class FourFingerSwipe(val direction: SwipeDirection) : RecognizedGesture
    object FourFingerTap : RecognizedGesture
}

private class ActivePointer(var lastX: Float, var lastY: Float, val downX: Float, val downY: Float, val downTimeMs: Long) {
    var totalMovement = 0f
    var residualX = 0f
    var residualY = 0f
}

/**
 * Classifies raw multi-touch pointer events into recognized gestures.
 *
 * Call [reset] if the caller detects that touch input was interrupted
 * without a matching PointerUp for every currently-down pointer (e.g. a
 * Compose gesture cancellation) — otherwise a "stuck" pointer entry would
 * corrupt classification for the rest of this instance's lifetime.
 */
class GestureRecognizer {

    private val active = mutableMapOf<Int, ActivePointer>()
    private var lastTapUpTimeMs: Long? = null
    private var dragLockPointerId: Int? = null
    private var dragLockEngagedSent = false

    // Tracks the current multi-touch "session": from the first PointerDown
    // after all pointers were previously up, until all pointers are up again.
    private var sessionMaxPointers = 0
    private var sessionAllTapsSoFar = true
    private var firstDownTimeMsInSession: Long? = null

    // Net displacement of the very first finger down this session, used to
    // classify 3/4-finger swipe direction once the session ends.
    private var sessionFirstPointerId: Int? = null
    private var sessionFirstNetDx = 0f
    private var sessionFirstNetDy = 0f

    // Pixel-to-detent accumulation for two-finger scroll, shared across
    // whichever pointer is currently the canonical (lowest-id) reporter.
    private var scrollResidualV = 0f
    private var scrollResidualH = 0f

    /** Clears all tracked pointer/session/drag-lock state. Safe to call at any time. */
    fun reset() {
        active.clear()
        lastTapUpTimeMs = null
        dragLockPointerId = null
        dragLockEngagedSent = false
        resetSession()
    }

    fun onEvent(event: RawTouchEvent): RecognizedGesture? {
        return when (event) {
            is RawTouchEvent.PointerDown -> onDown(event)
            is RawTouchEvent.PointerMove -> onMove(event)
            is RawTouchEvent.PointerUp -> onUp(event)
        }
    }

    private fun onDown(event: RawTouchEvent.PointerDown): RecognizedGesture? {
        active[event.id] = ActivePointer(event.x, event.y, event.x, event.y, event.timeMs)
        sessionMaxPointers = maxOf(sessionMaxPointers, active.size)

        if (active.size == 1) {
            firstDownTimeMsInSession = event.timeMs
            sessionFirstPointerId = event.id
            // Drag-lock candidate: a second down shortly after the previous tap's up.
            val gap = lastTapUpTimeMs?.let { event.timeMs - it }
            if (gap != null && gap in 0..DRAG_LOCK_TAP_GAP_MS) {
                dragLockPointerId = event.id
            }
        } else if (active.size == 2) {
            val gap = event.timeMs - (firstDownTimeMsInSession ?: event.timeMs)
            if (gap > TWO_FINGER_DOWN_WINDOW_MS) {
                sessionAllTapsSoFar = false
            }
            // A second finger arriving turns this into a two-finger gesture
            // (scroll or tap) rather than a drag — but never interrupt a
            // drag-lock that has ALREADY engaged (a stray extra contact
            // shouldn't cancel an in-progress drag).
            if (!dragLockEngagedSent) {
                dragLockPointerId = null
            }
        }
        return null
    }

    private fun onMove(event: RawTouchEvent.PointerMove): RecognizedGesture? {
        val pointer = active[event.id] ?: return null
        val rawDx = event.x - pointer.lastX
        val rawDy = event.y - pointer.lastY
        pointer.totalMovement += hypot(rawDx, rawDy)
        pointer.lastX = event.x
        pointer.lastY = event.y

        if (event.id == sessionFirstPointerId) {
            sessionFirstNetDx += rawDx
            sessionFirstNetDy += rawDy
        }

        if (pointer.totalMovement > TAP_MAX_MOVEMENT_PX) {
            sessionAllTapsSoFar = false
        }

        if (event.id == dragLockPointerId) {
            val heldMs = event.timeMs - pointer.downTimeMs
            if (!dragLockEngagedSent) {
                // Require real movement, not just elapsed time, before sending the mouse
                // button-down to the host: engaging on a near-stationary hold lets Windows'
                // own double-click detection mistake a fast, tiny drag for a second click
                // (e.g. un-maximizing a snapped window instead of dragging it).
                if (heldMs >= DRAG_LOCK_HOLD_MS && pointer.totalMovement > TAP_MAX_MOVEMENT_PX) {
                    dragLockEngagedSent = true
                    return RecognizedGesture.DragLockEngaged
                }
                return null
            }
            return emitResidualMove(pointer, rawDx, rawDy) { dx, dy -> RecognizedGesture.DragMove(dx, dy) }
        }

        if (active.size == 2 && dragLockPointerId == null) {
            val other = active.entries.first { it.key != event.id }.value
            if (other.totalMovement > TAP_MAX_MOVEMENT_PX && pointer.totalMovement > TAP_MAX_MOVEMENT_PX) {
                // Only the canonical (lowest-id) pointer emits Scroll, so a
                // two-finger move doesn't fire twice per frame.
                val canonicalId = active.keys.min()
                if (event.id != canonicalId) return null
                scrollResidualV += rawDy
                scrollResidualH += rawDx
                val vUnits = (scrollResidualV / SCROLL_PX_PER_UNIT).toInt()
                val hUnits = (scrollResidualH / SCROLL_PX_PER_UNIT).toInt()
                if (vUnits == 0 && hUnits == 0) return null
                scrollResidualV -= vUnits * SCROLL_PX_PER_UNIT
                scrollResidualH -= hUnits * SCROLL_PX_PER_UNIT
                return RecognizedGesture.Scroll(vDelta = vUnits, hDelta = hUnits)
            }
            return null
        }

        if (active.size == 1 && sessionMaxPointers <= 1) {
            return emitResidualMove(pointer, rawDx, rawDy) { dx, dy -> RecognizedGesture.CursorMove(dx, dy) }
        }
        // A lone remaining pointer from what WAS a multi-touch session (e.g.
        // the tail end of a two-finger scroll after one finger lifted) must
        // not suddenly start moving the cursor.
        return null
    }

    private inline fun emitResidualMove(pointer: ActivePointer, rawDx: Float, rawDy: Float, build: (Int, Int) -> RecognizedGesture): RecognizedGesture? {
        pointer.residualX += rawDx
        pointer.residualY += rawDy
        val dx = pointer.residualX.roundToInt()
        val dy = pointer.residualY.roundToInt()
        pointer.residualX -= dx
        pointer.residualY -= dy
        return if (dx != 0 || dy != 0) build(dx, dy) else null
    }

    private fun onUp(event: RawTouchEvent.PointerUp): RecognizedGesture? {
        val pointer = active.remove(event.id) ?: return null
        val durationMs = event.timeMs - pointer.downTimeMs
        val isTap = pointer.totalMovement <= TAP_MAX_MOVEMENT_PX && durationMs <= TAP_MAX_DURATION_MS
        if (!isTap) sessionAllTapsSoFar = false

        if (event.id == dragLockPointerId) {
            val wasEngaged = dragLockEngagedSent
            dragLockPointerId = null
            dragLockEngagedSent = false
            if (active.isEmpty()) resetSession()
            return when {
                wasEngaged -> RecognizedGesture.DragLockReleased
                isTap -> {
                    lastTapUpTimeMs = event.timeMs
                    RecognizedGesture.LeftClick
                }
                else -> null
            }
        }

        if (active.isNotEmpty()) return null

        // All pointers are now up: decide what this session was.
        val direction = classifySwipeDirection()
        val result = when {
            sessionMaxPointers == 4 && sessionAllTapsSoFar -> RecognizedGesture.FourFingerTap
            sessionMaxPointers == 4 && direction != null -> RecognizedGesture.FourFingerSwipe(direction)
            sessionMaxPointers == 3 && sessionAllTapsSoFar -> RecognizedGesture.ThreeFingerTap
            sessionMaxPointers == 3 && direction != null -> RecognizedGesture.ThreeFingerSwipe(direction)
            sessionMaxPointers == 2 && sessionAllTapsSoFar -> RecognizedGesture.RightClick
            sessionMaxPointers == 1 && isTap -> {
                lastTapUpTimeMs = event.timeMs
                RecognizedGesture.LeftClick
            }
            else -> null
        }
        resetSession()
        return result
    }

    private fun classifySwipeDirection(): SwipeDirection? {
        val absDx = abs(sessionFirstNetDx)
        val absDy = abs(sessionFirstNetDy)
        if (maxOf(absDx, absDy) < SWIPE_MIN_DISTANCE_PX) return null
        return if (absDx >= absDy) {
            if (sessionFirstNetDx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
        } else {
            if (sessionFirstNetDy > 0) SwipeDirection.DOWN else SwipeDirection.UP
        }
    }

    private fun resetSession() {
        sessionMaxPointers = 0
        sessionAllTapsSoFar = true
        firstDownTimeMsInSession = null
        scrollResidualV = 0f
        scrollResidualH = 0f
        sessionFirstPointerId = null
        sessionFirstNetDx = 0f
        sessionFirstNetDy = 0f
    }
}
```

- [ ] **Step 4: `GestureRecognizerTest` testlerinin geçtiğini doğrula**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.data.gesture.GestureRecognizerTest"`
Expected: PASS (tüm testler — dosyada eskiden 15 test vardı, 1 tanesi bu adımda güncellendi + 8 yeni eklendi = 23 test dosyada)

- [ ] **Step 5: `MainViewModelTest.kt`'ye 8 yeni test ekle**

Dosyanın sonuna (kapanış `}`'den hemen önce), importlara `import com.dokunmatikekosistem.app.data.gesture.SwipeDirection` ekleyerek:

```kotlin
    @Test
    fun `ThreeFingerSwipe UP sends Win+Tab`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.UP))
        assertEquals(listOf(HidKeyboardReport.MODIFIER_WIN to 0x2B), fake.allKeyPresses)
        assertEquals(true, fake.releaseKeyboardCalled)
    }

    @Test
    fun `ThreeFingerSwipe DOWN sends Win+D`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.DOWN))
        assertEquals(listOf(HidKeyboardReport.MODIFIER_WIN to 0x07), fake.allKeyPresses)
    }

    @Test
    fun `ThreeFingerSwipe LEFT sends Alt+Tab`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.LEFT))
        assertEquals(listOf(HidKeyboardReport.MODIFIER_ALT to 0x2B), fake.allKeyPresses)
    }

    @Test
    fun `ThreeFingerSwipe RIGHT sends Alt+Shift+Tab`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.ThreeFingerSwipe(SwipeDirection.RIGHT))
        assertEquals(listOf((HidKeyboardReport.MODIFIER_ALT or HidKeyboardReport.MODIFIER_SHIFT) to 0x2B), fake.allKeyPresses)
    }

    @Test
    fun `ThreeFingerTap sends Win+S`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.ThreeFingerTap)
        assertEquals(listOf(HidKeyboardReport.MODIFIER_WIN to 0x16), fake.allKeyPresses)
    }

    @Test
    fun `FourFingerSwipe LEFT sends Ctrl+Win+Right for next virtual desktop`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.FourFingerSwipe(SwipeDirection.LEFT))
        assertEquals(listOf((HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_WIN) to 0x4F), fake.allKeyPresses)
    }

    @Test
    fun `FourFingerSwipe RIGHT sends Ctrl+Win+Left for previous virtual desktop`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.FourFingerSwipe(SwipeDirection.RIGHT))
        assertEquals(listOf((HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_WIN) to 0x50), fake.allKeyPresses)
    }

    @Test
    fun `FourFingerTap sends Win+N`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.FourFingerTap)
        assertEquals(listOf(HidKeyboardReport.MODIFIER_WIN to 0x11), fake.allKeyPresses)
    }
```

- [ ] **Step 6: Testlerin başarısız olduğunu doğrula (derleme hatası)**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.presentation.MainViewModelTest"`
Expected: FAIL — derleme hatası, `MainViewModel.onGesture`'ın `when` bloğu yeni `RecognizedGesture` alt tiplerini kapsamıyor (exhaustive olmayan `when`), `ThreeFingerSwipe` vb. henüz yok.

- [ ] **Step 7: `MainViewModel.kt`'yi tam olarak şununla değiştir**

```kotlin
package com.dokunmatikekosistem.app.presentation

import androidx.lifecycle.ViewModel
import com.dokunmatikekosistem.app.data.gesture.RecognizedGesture
import com.dokunmatikekosistem.app.data.gesture.SwipeDirection
import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import com.dokunmatikekosistem.app.data.keyboard.EnglishUsLayout
import com.dokunmatikekosistem.app.data.keyboard.TurkishQLayout
import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.Haptics
import com.dokunmatikekosistem.app.domain.HidManager
import com.dokunmatikekosistem.app.domain.KeyboardLayout
import com.dokunmatikekosistem.app.domain.KeyboardModifierState
import com.dokunmatikekosistem.app.domain.ShiftState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

private const val SHIFT_DOUBLE_TAP_WINDOW_MS = 300L
private const val DELETE_FORWARD_USAGE_CODE = 0x4C
private const val TAB_USAGE_CODE = 0x2B
private const val D_USAGE_CODE = 0x07
private const val S_USAGE_CODE = 0x16
private const val N_USAGE_CODE = 0x11
private const val LEFT_ARROW_USAGE_CODE = 0x50
private const val RIGHT_ARROW_USAGE_CODE = 0x4F

@HiltViewModel
class MainViewModel @Inject constructor(
    private val hidManager: HidManager,
    private val haptics: Haptics
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = hidManager.connectionState
    val reportsSent: StateFlow<Int> = hidManager.reportsSent

    private val _activeLayout = MutableStateFlow<KeyboardLayout>(TurkishQLayout())
    val activeLayout: StateFlow<KeyboardLayout> = _activeLayout

    private val _modifierState = MutableStateFlow(KeyboardModifierState())
    val modifierState: StateFlow<KeyboardModifierState> = _modifierState

    private var lastShiftClickMillis = Long.MIN_VALUE

    fun onConnectClicked() {
        hidManager.register()
    }

    fun onGesture(gesture: RecognizedGesture) {
        when (gesture) {
            is RecognizedGesture.CursorMove ->
                hidManager.sendMouseReport(gesture.dx, gesture.dy, wheelDelta = 0, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)

            RecognizedGesture.LeftClick -> {
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = true, rightButtonPressed = false)
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false)
                haptics.click()
            }

            RecognizedGesture.RightClick -> {
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = true)
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false)
                haptics.click()
            }

            is RecognizedGesture.Scroll ->
                hidManager.sendMouseReport(0, 0, wheelDelta = gesture.vDelta, panDelta = gesture.hDelta, leftButtonPressed = false, rightButtonPressed = false)

            RecognizedGesture.DragLockEngaged -> {
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = true, rightButtonPressed = false)
                haptics.dragLockEngaged()
            }

            is RecognizedGesture.DragMove ->
                hidManager.sendMouseReport(gesture.dx, gesture.dy, wheelDelta = 0, panDelta = 0, leftButtonPressed = true, rightButtonPressed = false)

            RecognizedGesture.DragLockReleased ->
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false)

            is RecognizedGesture.ThreeFingerSwipe -> when (gesture.direction) {
                SwipeDirection.UP -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, TAB_USAGE_CODE)
                SwipeDirection.DOWN -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, D_USAGE_CODE)
                SwipeDirection.LEFT -> sendShortcut(HidKeyboardReport.MODIFIER_ALT, TAB_USAGE_CODE)
                SwipeDirection.RIGHT -> sendShortcut(HidKeyboardReport.MODIFIER_ALT or HidKeyboardReport.MODIFIER_SHIFT, TAB_USAGE_CODE)
            }

            RecognizedGesture.ThreeFingerTap -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, S_USAGE_CODE)

            is RecognizedGesture.FourFingerSwipe -> when (gesture.direction) {
                SwipeDirection.UP -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, TAB_USAGE_CODE)
                SwipeDirection.DOWN -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, D_USAGE_CODE)
                SwipeDirection.LEFT -> sendShortcut(HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_WIN, RIGHT_ARROW_USAGE_CODE)
                SwipeDirection.RIGHT -> sendShortcut(HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_WIN, LEFT_ARROW_USAGE_CODE)
            }

            RecognizedGesture.FourFingerTap -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, N_USAGE_CODE)
        }
    }

    fun onLayoutToggleClicked() {
        _activeLayout.value = if (_activeLayout.value is TurkishQLayout) EnglishUsLayout() else TurkishQLayout()
    }

    fun onKeyTyped(char: Char) {
        val state = _modifierState.value
        val chord = _activeLayout.value.mapChar(char) ?: return
        val stickyBits = state.stickyHidModifierBits()
        var modifierBits = chord.modifierBits or stickyBits
        if (stickyBits != 0 && state.shiftState == ShiftState.Off) {
            // CapsLock (not real Shift) added the Shift bit purely so the host renders an
            // uppercase letter; a real CapsLock key never combines with Ctrl/Alt/Win like that.
            modifierBits = modifierBits and HidKeyboardReport.MODIFIER_SHIFT.inv()
        }
        hidManager.sendKeyboardReport(modifierBits, chord.usageCode)
        hidManager.releaseKeyboardReport()
        _modifierState.value = state.copy(
            shiftState = if (state.shiftState == ShiftState.OneShot) ShiftState.Off else state.shiftState,
            ctrlActive = false,
            altActive = false,
            winActive = false
        )
    }

    fun onShiftClicked(atMillis: Long = System.currentTimeMillis()) {
        val state = _modifierState.value
        val isDoubleTap = state.shiftState == ShiftState.OneShot &&
            (atMillis - lastShiftClickMillis) <= SHIFT_DOUBLE_TAP_WINDOW_MS
        lastShiftClickMillis = atMillis
        _modifierState.value = state.copy(
            shiftState = when {
                isDoubleTap -> ShiftState.Locked
                state.shiftState == ShiftState.Off -> ShiftState.OneShot
                else -> ShiftState.Off
            }
        )
    }

    fun onCapsLockClicked() {
        _modifierState.value = _modifierState.value.let { it.copy(capsLockActive = !it.capsLockActive) }
    }

    fun onCtrlClicked() {
        _modifierState.value = _modifierState.value.let { it.copy(ctrlActive = !it.ctrlActive) }
    }

    fun onAltClicked() {
        _modifierState.value = _modifierState.value.let { it.copy(altActive = !it.altActive) }
    }

    fun onWinClicked() {
        _modifierState.value = _modifierState.value.let { it.copy(winActive = !it.winActive) }
    }

    fun onCtrlAltDelClicked() {
        hidManager.sendKeyboardReport(HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_ALT, DELETE_FORWARD_USAGE_CODE)
        hidManager.releaseKeyboardReport()
    }

    private fun sendShortcut(modifierBits: Int, usageCode: Int) {
        hidManager.sendKeyboardReport(modifierBits, usageCode)
        hidManager.releaseKeyboardReport()
    }
}

private fun KeyboardModifierState.stickyHidModifierBits(): Int {
    var bits = 0
    if (ctrlActive) bits = bits or HidKeyboardReport.MODIFIER_CTRL
    if (altActive) bits = bits or HidKeyboardReport.MODIFIER_ALT
    if (winActive) bits = bits or HidKeyboardReport.MODIFIER_WIN
    return bits
}
```

- [ ] **Step 8: Testlerin geçtiğini doğrula**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tüm testler yeşil (98 eski (1 tanesi bu adımda güncellendi, sayıya dahil) + 8 GestureRecognizer + 8 MainViewModel = 114 test).

- [ ] **Step 9: Commit**

```bash
cd "D:\projects\touchpad-ekosistem"
git add app/src/main/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizer.kt app/src/test/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizerTest.kt app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt
git commit -m "feat: add 3/4-finger swipe/tap classification and Windows macro shortcuts"
```

---

### Task 2: Pinch-to-zoom (Ctrl+Scroll)

**Files:**
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizer.kt`
- Modify: `app/src/test/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizerTest.kt` (2 yeni test)
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt`
- Modify: `app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt` (3 yeni test)

**Interfaces:**
- Consumes: Task 1'in `RecognizedGesture` sealed interface'i, `GestureRecognizer`'ın mevcut alan yapısı (`active`, `resetSession()`).
- Produces: `RecognizedGesture` sealed interface'ine yeni üyeler: `object PinchZoomStarted`, `data class PinchZoomDelta(val units: Int)`, `object PinchZoomEnded`.

- [ ] **Step 1: Yeni testleri `GestureRecognizerTest.kt`'nin sonuna ekle (RED)**

```kotlin
    @Test
    fun `pinch apart engages zoom, emits deltas, and ends when a finger lifts`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 150f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 250f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 280f, y = 100f, timeMs = 30))
        val seeded = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 120f, y = 100f, timeMs = 40))
        assertNull(seeded)
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 330f, y = 100f, timeMs = 50))
        val started = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 90f, y = 100f, timeMs = 60))
        assertEquals(RecognizedGesture.PinchZoomStarted, started)
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 380f, y = 100f, timeMs = 70))
        val delta = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 40f, y = 100f, timeMs = 80))
        assertEquals(RecognizedGesture.PinchZoomDelta(units = 8), delta)
        val ended = recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 380f, y = 100f, timeMs = 90))
        assertEquals(RecognizedGesture.PinchZoomEnded, ended)
    }

    @Test
    fun `two fingers moving together in parallel do not trigger pinch zoom`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 130f, timeMs = 30))
        val seeded = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 130f, timeMs = 40))
        assertNull(seeded)
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 160f, timeMs = 50))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 160f, timeMs = 60))
        assertTrue(result is RecognizedGesture.Scroll)
    }
```

- [ ] **Step 2: Testlerin başarısız olduğunu doğrula**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.data.gesture.GestureRecognizerTest"`
Expected: FAIL — derleme hatası, `PinchZoomStarted`/`PinchZoomDelta`/`PinchZoomEnded` yok.

- [ ] **Step 3: `GestureRecognizer.kt`'de aşağıdaki değişiklikleri uygula**

`RecognizedGesture` sealed interface'ine (`FourFingerTap`'ten sonra) ekle:

```kotlin
    object PinchZoomStarted : RecognizedGesture
    data class PinchZoomDelta(val units: Int) : RecognizedGesture
    object PinchZoomEnded : RecognizedGesture
```

Dosyanın en üstündeki sabitler bloğuna (`SWIPE_MIN_DISTANCE_PX`'ten sonra) ekle:

```kotlin
private const val PINCH_PX_PER_UNIT = 12f
```

`GestureRecognizer` sınıfının alanlarına (`scrollResidualH`'den sonra) ekle:

```kotlin
    // Pinch-zoom: sticky once engaged for the rest of this 2-finger session
    // (mirrors dragLockPointerId's sticky-until-release pattern).
    private var pinchEngaged = false
    private var lastPinchDistance = -1f
    private var pinchResidual = 0f
```

`onMove`'daki 2-parmak bloğunu (`if (active.size == 2 && dragLockPointerId == null) { ... }`) tamamen şununla değiştir:

```kotlin
        if (active.size == 2 && dragLockPointerId == null) {
            val other = active.entries.first { it.key != event.id }.value
            if (other.totalMovement > TAP_MAX_MOVEMENT_PX && pointer.totalMovement > TAP_MAX_MOVEMENT_PX) {
                // Only the canonical (lowest-id) pointer emits Scroll/Pinch, so a
                // two-finger move doesn't fire twice per frame.
                val canonicalId = active.keys.min()
                if (event.id != canonicalId) return null

                val distanceNow = hypot(pointer.lastX - other.lastX, pointer.lastY - other.lastY)
                val justSeeded = lastPinchDistance < 0f
                if (justSeeded) lastPinchDistance = distanceNow
                val distanceChange = distanceNow - lastPinchDistance
                lastPinchDistance = distanceNow
                // The first frame both fingers cross the movement threshold only
                // establishes a distance baseline — classifying here would be a coin
                // flip (distanceChange is always 0 on this exact frame).
                if (justSeeded) return null

                if (!pinchEngaged && abs(distanceChange) > hypot(rawDx, rawDy)) {
                    pinchEngaged = true
                    return RecognizedGesture.PinchZoomStarted
                }

                if (pinchEngaged) {
                    pinchResidual += distanceChange
                    val units = (pinchResidual / PINCH_PX_PER_UNIT).toInt()
                    if (units == 0) return null
                    pinchResidual -= units * PINCH_PX_PER_UNIT
                    return RecognizedGesture.PinchZoomDelta(units)
                }

                scrollResidualV += rawDy
                scrollResidualH += rawDx
                val vUnits = (scrollResidualV / SCROLL_PX_PER_UNIT).toInt()
                val hUnits = (scrollResidualH / SCROLL_PX_PER_UNIT).toInt()
                if (vUnits == 0 && hUnits == 0) return null
                scrollResidualV -= vUnits * SCROLL_PX_PER_UNIT
                scrollResidualH -= hUnits * SCROLL_PX_PER_UNIT
                return RecognizedGesture.Scroll(vDelta = vUnits, hDelta = hUnits)
            }
            return null
        }
```

`onUp` fonksiyonunun tamamını şununla değiştir (Task 1'deki haline iki yeni blok eklendi: en baştaki `wasPinching` satırı, ve `dragLockPointerId` bloğundan hemen sonraki `wasPinching` kontrolü — geri kalanı Task 1'deki ile birebir aynı):

```kotlin
    private fun onUp(event: RawTouchEvent.PointerUp): RecognizedGesture? {
        val wasPinching = pinchEngaged && active.size == 2
        val pointer = active.remove(event.id) ?: return null
        val durationMs = event.timeMs - pointer.downTimeMs
        val isTap = pointer.totalMovement <= TAP_MAX_MOVEMENT_PX && durationMs <= TAP_MAX_DURATION_MS
        if (!isTap) sessionAllTapsSoFar = false

        if (event.id == dragLockPointerId) {
            val wasEngaged = dragLockEngagedSent
            dragLockPointerId = null
            dragLockEngagedSent = false
            if (active.isEmpty()) resetSession()
            return when {
                wasEngaged -> RecognizedGesture.DragLockReleased
                isTap -> {
                    lastTapUpTimeMs = event.timeMs
                    RecognizedGesture.LeftClick
                }
                else -> null
            }
        }

        if (wasPinching) {
            pinchEngaged = false
            lastPinchDistance = -1f
            pinchResidual = 0f
            if (active.isEmpty()) resetSession()
            return RecognizedGesture.PinchZoomEnded
        }

        if (active.isNotEmpty()) return null

        // All pointers are now up: decide what this session was.
        val direction = classifySwipeDirection()
        val result = when {
            sessionMaxPointers == 4 && sessionAllTapsSoFar -> RecognizedGesture.FourFingerTap
            sessionMaxPointers == 4 && direction != null -> RecognizedGesture.FourFingerSwipe(direction)
            sessionMaxPointers == 3 && sessionAllTapsSoFar -> RecognizedGesture.ThreeFingerTap
            sessionMaxPointers == 3 && direction != null -> RecognizedGesture.ThreeFingerSwipe(direction)
            sessionMaxPointers == 2 && sessionAllTapsSoFar -> RecognizedGesture.RightClick
            sessionMaxPointers == 1 && isTap -> {
                lastTapUpTimeMs = event.timeMs
                RecognizedGesture.LeftClick
            }
            else -> null
        }
        resetSession()
        return result
    }
```

`resetSession()`'a (`sessionFirstNetDy = 0f`'ten sonra) ekle:

```kotlin
        pinchEngaged = false
        lastPinchDistance = -1f
        pinchResidual = 0f
```

`reset()` fonksiyonu zaten `resetSession()` çağırdığı için ek bir değişiklik gerekmiyor — pinch alanları otomatik temizlenir.

- [ ] **Step 4: Testlerin geçtiğini doğrula**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.data.gesture.GestureRecognizerTest"`
Expected: PASS (25/25 — Task 1 sonrası dosyada 23 test vardı + bu adımın 2 yenisi)

- [ ] **Step 5: `MainViewModelTest.kt`'ye 3 yeni test ekle**

```kotlin
    @Test
    fun `PinchZoomStarted holds Ctrl with no key`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.PinchZoomStarted)
        assertEquals(listOf(HidKeyboardReport.MODIFIER_CTRL to 0), fake.allKeyPresses)
        assertEquals(false, fake.releaseKeyboardCalled)
    }

    @Test
    fun `PinchZoomDelta sends a mouse wheel report with the given units`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.PinchZoomDelta(units = 3))
        assertEquals(listOf(FakeMouseReport(0, 0, wheelDelta = 3, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)), fake.allReports)
    }

    @Test
    fun `PinchZoomEnded releases the keyboard report`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())
        viewModel.onGesture(RecognizedGesture.PinchZoomEnded)
        assertEquals(true, fake.releaseKeyboardCalled)
        assertEquals(emptyList<Pair<Int, Int>>(), fake.allKeyPresses)
    }
```

- [ ] **Step 6: Testlerin başarısız olduğunu doğrula**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.presentation.MainViewModelTest"`
Expected: FAIL — derleme hatası, `MainViewModel.onGesture`'ın `when` bloğu `PinchZoomStarted`/`PinchZoomDelta`/`PinchZoomEnded`'i kapsamıyor.

- [ ] **Step 7: `MainViewModel.kt`'de `onGesture`'ı güncelle**

`FourFingerTap -> sendShortcut(...)` satırından hemen sonra, `when` bloğunun kapanışından ÖNCE ekle:

```kotlin
            RecognizedGesture.PinchZoomStarted ->
                hidManager.sendKeyboardReport(HidKeyboardReport.MODIFIER_CTRL, NO_KEY_USAGE_CODE)

            is RecognizedGesture.PinchZoomDelta ->
                hidManager.sendMouseReport(0, 0, wheelDelta = gesture.units, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)

            RecognizedGesture.PinchZoomEnded ->
                hidManager.releaseKeyboardReport()
```

Dosyanın en üstündeki sabitler bloğuna (`RIGHT_ARROW_USAGE_CODE`'dan sonra) ekle:

```kotlin
private const val NO_KEY_USAGE_CODE = 0
```

- [ ] **Step 8: Tüm testlerin geçtiğini doğrula**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tüm testler yeşil (114 + 2 GestureRecognizer + 3 MainViewModel = 119 test).

- [ ] **Step 9: Derlemeyi de doğrula**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
cd "D:\projects\touchpad-ekosistem"
git add app/src/main/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizer.kt app/src/test/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizerTest.kt app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt
git commit -m "feat: add pinch-to-zoom (Ctrl+Scroll) disambiguated from two-finger scroll"
```

---

### Task 3: Cihaz Testi Checklist'i (manuel, kod değişikliği yok)

**Files:** Yok — bu task saf manuel doğrulama.

- [ ] **Step 1: Uygulamayı gerçek cihaza kur ve Windows'a bağlan**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew installDebug`

Telefonu Bluetooth HID ile Windows'a bağla (mevcut prosedür).

- [ ] **Step 2: Aşağıdaki senaryoları Windows'ta gözlemleyerek doğrula**

1. 3 parmak yukarı kaydır → Görev Görünümü açılıyor.
2. 3 parmak aşağı kaydır → tüm pencereler küçülüp masaüstü görünüyor.
3. 3 parmak sola kaydır → sonraki uygulamaya geçiyor (Alt+Tab).
4. 3 parmak sağa kaydır → önceki uygulamaya geçiyor (Alt+Shift+Tab).
5. 3 parmak dokun → Windows Arama açılıyor.
6. 4 parmak sola kaydır → sonraki sanal masaüstüne geçiyor (birden fazla sanal masaüstü açıksa test edilebilir, yoksa önce Görev Görünümü'nden "Yeni masaüstü" ekle).
7. 4 parmak sağa kaydır → önceki sanal masaüstüne geçiyor.
8. 4 parmak dokun → Bildirim Merkezi açılıyor.
9. 2 parmak pinch (uzaklaştırma) bir web sayfasında/Fotoğraflar'da → gerçekten yakınlaştırıyor (zoom in).
10. 2 parmak pinch (yaklaştırma) → uzaklaştırıyor (zoom out).
11. Mevcut 2 parmak scroll'un pinch ile karışmadığını doğrula: normal 2 parmak dikey/yatay kaydırma hâlâ scroll olarak çalışıyor, yanlışlıkla zoom tetiklemiyor.
12. **(Final review'da bulunup düzeltilen bug, 1c1d412)** 2 parmakla kaydırırken bir parmağı kaldırıp farklı bir yere koyarak kaydırmaya devam et → hâlâ scroll olarak çalışmalı, yanlışlıkla zoom'a geçmemeli.
13. **(Final review, bilinen kapsam dışı davranış)** 2 parmak scroll/pinch sırasında kazara 3. bir parmak değerse (avuç içi vb.) ve sonra bırakılırsa, bazen o oturumun sonunda istenmeyen bir 3/4 parmak makrosu (örn. Alt+Tab) tetiklenebilir — cihazda gözlemlenirse not al, bu turda düzeltilmedi.
14. **(Final review, bilinen kapsam dışı davranış)** 3 parmak swipe başlarken ilk iki parmak üçüncü değmeden önce belirgin hareket ederse, swipe makrosundan önce birkaç Scroll/zoom-başlangıcı olayı tetiklenebilir — genelde zararsız, cihazda gözlemlenirse not al.

- [ ] **Step 3: Eşik değerlerini gerekirse ayarla**

`SWIPE_MIN_DISTANCE_PX` (60f) veya `PINCH_PX_PER_UNIT` (12f) cihazda çok hassas/çok duyarsız hissettirirse, `GestureRecognizer.kt`'deki bu sabitleri ayarla ve unit testleri tekrar çalıştır (mevcut testler tam piksel değerleriyle yazıldığı için sabit değiştirilirse ilgili testlerin de güncellenmesi gerekebilir).

- [ ] **Step 4: Memory'yi güncelle**

Tüm senaryolar doğrulandıktan sonra `touchpad-ekosistem-proje-durumu.md` memory dosyasını güncelle: "Faz 3 (3/4 parmak makrolar + pinch-zoom) TAMAMLANDI+cihazda doğrulandı" ve tarih ekle.
