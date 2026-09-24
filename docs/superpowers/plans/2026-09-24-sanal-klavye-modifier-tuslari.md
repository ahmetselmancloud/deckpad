# Sanal Klavye Modifier Tuşları Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sanal klavyeye (`VirtualKeyboard`) Windows OSK'ya yakın modifier tuş davranışı eklemek — Shift one-shot+kilit, bağımsız CapsLock, sticky Ctrl/Alt/Win kombinasyonları, Ctrl+Alt+Del butonu — ve TR/EN dil geçişinde ekran etiketlerinin senkron olmamasını düzeltmek.

**Architecture:** Modifier state (`KeyboardModifierState`) `MainViewModel`'de tek doğruluk kaynağı olarak tutulur. `VirtualKeyboard` composable'ı `MainActivity.kt`'den ayrı bir dosyaya çıkarılır, tuş dizilimini artık `KeyboardLayout.displayRows()`'tan dinamik üretir (TR/EN etiket fix), ve tüm modifier tuş event'lerini ViewModel'e iletir. HID rapor formatı değişmez — Ctrl+Alt+Del gibi "1 tuş + çoklu modifier bit" senaryosu mevcut `[modifier, reserved, key1..key6]` formatıyla zaten desteklenir; sadece `HidKeyboardReport`'a yeni modifier sabitleri (`MODIFIER_CTRL/ALT/WIN`) eklenir.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, JUnit4 (unit test), `./gradlew test` (JAVA_HOME=Android Studio JBR).

## Global Constraints

- Mevcut 62 unit testin tamamı geçmeye devam etmeli; hiçbiri bu değişiklikle bozulmamalı.
- `KeyboardLayout.mapChar(char: Char): HidKeyChord?` imzası **değişmiyor** — spec taslağında önerilen "her zaman küçük harf al" fikri, Türkçe ı/İ noktalı-noktasız harf çiftlerinin (`ı`↔`I` vs `i`↔`İ`) standart Unicode/Kotlin case-folding ile doğru ayrıştırılamaması nedeniyle **uygulanmıyor** (bkz. "Spec'ten Sapma" notu altta). Bunun yerine `KeyboardLayout`'a `shiftedChar(baseChar: Char): Char` eklenir, mevcut `mapChar` davranışı (zaten-büyütülmüş char alıp kendi Shift bitini üretmesi) korunur.
- `KeyboardModifierState`, `HidKeyboardReport` (data katmanı) sabitlerine bağımlı olmaz — Ctrl/Alt/Win HID bitlerinin hesaplanması `MainViewModel` (presentation katmanı) içinde yapılır, mevcut kod tabanındaki presentation→data bağımlılık yönüyle (ViewModel zaten `data.keyboard.TurkishQLayout`/`EnglishUsLayout`'u import ediyor) tutarlı.
- Compose UI testi bu projede yok (mevcut konvansiyon), bu plan da eklemiyor — doğrulama unit test + manuel cihaz testi ile yapılıyor.
- Test komutu: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest` (proje kökünde, Bash'ten).

**Spec'ten Sapma Notu:** Onaylanan spec (`docs/superpowers/specs/2026-09-24-sanal-klavye-modifier-tuslari-design.md`) `mapChar`'ın her zaman küçük harf alacağını ve büyük/küçük harf seçiminin tamamen `KeyboardModifierState`'e taşınacağını öngörüyordu. Planlama sırasında incelenen `TurkishQLayoutTest.kt` ve `TurkishQLayout.kt` şunu ortaya çıkardı: Türkçe'de `ı` (noktasız küçük i) büyütülünce `I` olur, ama `i` (noktalı küçük i) büyütülünce `İ` olur — bu, Kotlin'in `Char.uppercaseChar()`'ı locale'siz çağırdığında doğru ayrıştıramayacağı klasik "Turkish-I problemi"dir. Mevcut kod bunu `VirtualKeyboard`'daki `virtualKeyboardShiftMap`'te (MainActivity.kt:154-156) açık bir tabloyla doğru çözüyordu. Bu plan, spec'in amacını (ViewModel modifier state'i tek doğruluk kaynağı) korurken, bu doğru-çalışan tablo yaklaşımını `KeyboardLayout.shiftedChar()` olarak katman bazında (TR/EN) yeniden konumlandırıyor — `mapChar`'ı bozmadan. Sonuç davranışsal olarak spec'in kabul kriterleriyle birebir aynı.

---

### Task 1: `KeyboardModifierState` domain sınıfı

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/domain/KeyboardModifierState.kt`
- Test: `app/src/test/java/com/dokunmatikekosistem/app/domain/KeyboardModifierStateTest.kt`

**Interfaces:**
- Produces: `enum class ShiftState { Off, OneShot, Locked }`; `data class KeyboardModifierState(val shiftState: ShiftState = ShiftState.Off, val capsLockActive: Boolean = false, val ctrlActive: Boolean = false, val altActive: Boolean = false, val winActive: Boolean = false)` ile `fun isUpperCaseEffective(): Boolean`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dokunmatikekosistem.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardModifierStateTest {

    @Test
    fun `default state is not uppercase`() {
        assertEquals(false, KeyboardModifierState().isUpperCaseEffective())
    }

    @Test
    fun `shift one-shot makes it uppercase`() {
        val state = KeyboardModifierState(shiftState = ShiftState.OneShot)
        assertEquals(true, state.isUpperCaseEffective())
    }

    @Test
    fun `shift locked makes it uppercase`() {
        val state = KeyboardModifierState(shiftState = ShiftState.Locked)
        assertEquals(true, state.isUpperCaseEffective())
    }

    @Test
    fun `caps lock alone makes it uppercase`() {
        val state = KeyboardModifierState(capsLockActive = true)
        assertEquals(true, state.isUpperCaseEffective())
    }

    @Test
    fun `shift and caps lock together cancel out to lowercase`() {
        val state = KeyboardModifierState(shiftState = ShiftState.OneShot, capsLockActive = true)
        assertEquals(false, state.isUpperCaseEffective())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.domain.KeyboardModifierStateTest"`
Expected: FAIL — derleme hatası, `KeyboardModifierState`/`ShiftState` sınıfları yok.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.dokunmatikekosistem.app.domain

enum class ShiftState { Off, OneShot, Locked }

/** Sanal klavyenin modifier tuş durumu. Tek doğruluk kaynağı MainViewModel'de tutulur. */
data class KeyboardModifierState(
    val shiftState: ShiftState = ShiftState.Off,
    val capsLockActive: Boolean = false,
    val ctrlActive: Boolean = false,
    val altActive: Boolean = false,
    val winActive: Boolean = false
) {
    /** Shift (one-shot veya locked) ve CapsLock birbirini XOR mantığıyla etkiler — gerçek klavye davranışı. */
    fun isUpperCaseEffective(): Boolean = (shiftState != ShiftState.Off) xor capsLockActive
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.domain.KeyboardModifierStateTest"`
Expected: PASS (5/5)

- [ ] **Step 5: Commit**

```bash
cd "D:\projects\touchpad-ekosistem"
git add app/src/main/java/com/dokunmatikekosistem/app/domain/KeyboardModifierState.kt app/src/test/java/com/dokunmatikekosistem/app/domain/KeyboardModifierStateTest.kt
git commit -m "feat: add KeyboardModifierState domain model for Shift/CapsLock XOR logic"
```

---

### Task 2: `HidKeyboardReport`'a Ctrl/Alt/Win modifier sabitleri

**Files:**
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReport.kt`
- Test: `app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReportTest.kt`

**Interfaces:**
- Consumes: mevcut `HidKeyboardReport.MODIFIER_SHIFT = 0x02` deseni (aynı dosyada, satır 7).
- Produces: `HidKeyboardReport.MODIFIER_CTRL = 0x01`, `HidKeyboardReport.MODIFIER_ALT = 0x04`, `HidKeyboardReport.MODIFIER_WIN = 0x08` (USB HID boot-keyboard modifier byte bit sırası: bit0=LCtrl, bit1=LShift, bit2=LAlt, bit3=LGui/Win).

- [ ] **Step 1: Write the failing test**

Dosyanın sonuna (mevcut `release produces all-zero 8-byte report` testinden sonra) ekle:

```kotlin
    @Test
    fun `build with combined ctrl and alt modifiers sets both bits`() {
        val report = HidKeyboardReport.build(
            HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_ALT, usageCode = 0x4C)
        )
        assertArrayEquals(byteArrayOf(0x05, 0, 0x4C, 0, 0, 0, 0, 0), report)
    }

    @Test
    fun `win modifier constant is bit 3`() {
        assertEquals(0x08, HidKeyboardReport.MODIFIER_WIN)
    }
```

Dosyanın importlarına `org.junit.Assert.assertEquals` ekle (mevcutta sadece `assertArrayEquals` var).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.data.hid.HidKeyboardReportTest"`
Expected: FAIL — derleme hatası, `MODIFIER_CTRL`/`MODIFIER_ALT`/`MODIFIER_WIN` yok.

- [ ] **Step 3: Write minimal implementation**

`HidKeyboardReport.kt`'de `const val MODIFIER_SHIFT = 0x02` satırının yanına ekle:

```kotlin
    const val MODIFIER_CTRL = 0x01
    const val MODIFIER_SHIFT = 0x02
    const val MODIFIER_ALT = 0x04
    const val MODIFIER_WIN = 0x08
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.data.hid.HidKeyboardReportTest"`
Expected: PASS (5/5)

- [ ] **Step 5: Commit**

```bash
cd "D:\projects\touchpad-ekosistem"
git add app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReport.kt app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReportTest.kt
git commit -m "feat: add Ctrl/Alt/Win HID modifier bit constants"
```

---

### Task 3: `KeyboardLayout` — `displayRows()` ve `shiftedChar()`

**Files:**
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/domain/KeyboardLayout.kt`
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/data/keyboard/EnglishUsLayout.kt`
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/data/keyboard/TurkishQLayout.kt`
- Test: `app/src/test/java/com/dokunmatikekosistem/app/data/keyboard/EnglishUsLayoutTest.kt`
- Test: `app/src/test/java/com/dokunmatikekosistem/app/data/keyboard/TurkishQLayoutTest.kt`

**Interfaces:**
- Consumes: Task 1'den `import com.dokunmatikekosistem.app.domain.KeyboardModifierState` YOK — bu task modifier state'e dokunmuyor, sadece layout'a iki yeni metod ekliyor.
- Produces: `KeyboardLayout.displayRows(): List<List<Char>>` (fiziksel klavye satırları, küçük harf baz karakterler); `KeyboardLayout.shiftedChar(baseChar: Char): Char` (baz karakterin büyük harf/shift karşılığı, Türkçe ı/İ ayrımını doğru yapar).

- [ ] **Step 1: Write the failing test**

`EnglishUsLayoutTest.kt`'nin sonuna ekle:

```kotlin
    @Test
    fun `display rows are the four physical QWERTY rows`() {
        assertEquals(
            listOf(
                "1234567890".toList(),
                "qwertyuiop".toList(),
                "asdfghjkl".toList(),
                "zxcvbnm".toList()
            ),
            layout.displayRows()
        )
    }

    @Test
    fun `shiftedChar uppercases a regular letter`() {
        assertEquals('A', layout.shiftedChar('a'))
    }
```

`TurkishQLayoutTest.kt`'nin sonuna ekle:

```kotlin
    @Test
    fun `display rows show turkish letters in their physical positions`() {
        assertEquals(
            listOf(
                "1234567890".toList(),
                "qwertyuıopğü".toList(),
                "asdfghjklşi".toList(),
                "zxcvbnmöç".toList()
            ),
            layout.displayRows()
        )
    }

    @Test
    fun `shiftedChar maps dotless i to dotless I`() {
        assertEquals('I', layout.shiftedChar('ı'))
    }

    @Test
    fun `shiftedChar maps dotted i to dotted I`() {
        assertEquals('İ', layout.shiftedChar('i'))
    }

    @Test
    fun `shiftedChar maps g breve to uppercase g breve`() {
        assertEquals('Ğ', layout.shiftedChar('ğ'))
    }

    @Test
    fun `shiftedChar falls back to english uppercasing for regular letters`() {
        assertEquals('Q', layout.shiftedChar('q'))
    }
```

(Her iki test dosyasında da `import com.dokunmatikekosistem.app.domain.KeyboardLayout` gerekmiyor — sınıflar zaten aynı test dosyasında `layout` değişkeni üzerinden erişiliyor; `assertEquals` zaten import edilmiş durumda, `toList()` için ekstra import gerekmez.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.data.keyboard.*"`
Expected: FAIL — derleme hatası, `displayRows()`/`shiftedChar()` `KeyboardLayout` arayüzünde yok.

- [ ] **Step 3: Write minimal implementation**

`KeyboardLayout.kt`'yi güncelle:

```kotlin
package com.dokunmatikekosistem.app.domain

/** A physical HID key press: modifier bits (see HidKeyboardReport.MODIFIER_*) + USB HID usage code. */
data class HidKeyChord(val modifierBits: Int, val usageCode: Int)

/** Maps a displayed character to the physical key chord that produces it under this layout. */
interface KeyboardLayout {
    fun mapChar(char: Char): HidKeyChord?

    /** The four physical letter/digit rows, as lowercase/base display characters, for rendering the on-screen keyboard. */
    fun displayRows(): List<List<Char>>

    /** The shifted (uppercase) form of a base display character under this layout. */
    fun shiftedChar(baseChar: Char): Char
}
```

`EnglishUsLayout.kt`'ye ekle (sınıf gövdesine, `mapChar`'dan sonra):

```kotlin
    override fun displayRows(): List<List<Char>> = listOf(
        "1234567890".toList(),
        "qwertyuiop".toList(),
        "asdfghjkl".toList(),
        "zxcvbnm".toList()
    )

    override fun shiftedChar(baseChar: Char): Char = baseChar.uppercaseChar()
```

`TurkishQLayout.kt`'ye ekle (sınıf gövdesine, `mapChar`'dan sonra):

```kotlin
    private val turkishShiftMap: Map<Char, Char> = mapOf(
        'ğ' to 'Ğ', 'ü' to 'Ü', 'ş' to 'Ş', 'i' to 'İ', 'ö' to 'Ö', 'ç' to 'Ç', 'ı' to 'I'
    )

    override fun displayRows(): List<List<Char>> = listOf(
        "1234567890".toList(),
        "qwertyuıopğü".toList(),
        "asdfghjklşi".toList(),
        "zxcvbnmöç".toList()
    )

    override fun shiftedChar(baseChar: Char): Char = turkishShiftMap[baseChar] ?: base.shiftedChar(baseChar)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.data.keyboard.*"`
Expected: PASS (tüm testler)

- [ ] **Step 5: Commit**

```bash
cd "D:\projects\touchpad-ekosistem"
git add app/src/main/java/com/dokunmatikekosistem/app/domain/KeyboardLayout.kt app/src/main/java/com/dokunmatikekosistem/app/data/keyboard/EnglishUsLayout.kt app/src/main/java/com/dokunmatikekosistem/app/data/keyboard/TurkishQLayout.kt app/src/test/java/com/dokunmatikekosistem/app/data/keyboard/EnglishUsLayoutTest.kt app/src/test/java/com/dokunmatikekosistem/app/data/keyboard/TurkishQLayoutTest.kt
git commit -m "feat: add displayRows and shiftedChar to KeyboardLayout for dynamic TR/EN key labels"
```

---

### Task 4: `MainViewModel` — modifier state ve event handler'lar

**Files:**
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt`
- Test: `app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1'den `KeyboardModifierState`, `ShiftState`; Task 2'den `HidKeyboardReport.MODIFIER_CTRL/ALT/WIN`; mevcut `HidManager.sendKeyboardReport(modifierBits: Int, usageCode: Int)`, `releaseKeyboardReport()`; mevcut `KeyboardLayout.mapChar(char: Char): HidKeyChord?`.
- Produces: `val modifierState: StateFlow<KeyboardModifierState>`; `fun onShiftClicked(atMillis: Long = System.currentTimeMillis())`; `fun onCapsLockClicked()`; `fun onCtrlClicked()`; `fun onAltClicked()`; `fun onWinClicked()`; `fun onCtrlAltDelClicked()`; `onKeyTyped(char: Char)` güncellenmiş davranış (aşağıya bakınız) — imza değişmiyor.

- [ ] **Step 1: Write the failing test**

`MainViewModelTest.kt`'ye şu importları ekle (mevcut importların yanına):

```kotlin
import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import com.dokunmatikekosistem.app.domain.KeyboardModifierState
import com.dokunmatikekosistem.app.domain.ShiftState
```

Dosyanın sonuna (mevcut son testten sonra, sınıf kapanışından önce) ekle:

```kotlin
    @Test
    fun `modifierState starts as default (all off)`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        assertEquals(KeyboardModifierState(), viewModel.modifierState.value)
    }

    @Test
    fun `onShiftClicked toggles Off to OneShot`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)

        assertEquals(ShiftState.OneShot, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onShiftClicked toggles OneShot back to Off when taps are far apart`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)
        viewModel.onShiftClicked(atMillis = 1000L)

        assertEquals(ShiftState.Off, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onShiftClicked twice within 300ms locks shift`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)
        viewModel.onShiftClicked(atMillis = 200L)

        assertEquals(ShiftState.Locked, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onShiftClicked while locked unlocks to Off`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)
        viewModel.onShiftClicked(atMillis = 200L)
        viewModel.onShiftClicked(atMillis = 400L)

        assertEquals(ShiftState.Off, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onCapsLockClicked toggles independently of shift`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onCapsLockClicked()

        assertEquals(true, viewModel.modifierState.value.capsLockActive)
        assertEquals(ShiftState.Off, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onCtrlClicked onAltClicked onWinClicked toggle sticky state`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onCtrlClicked()
        viewModel.onAltClicked()
        viewModel.onWinClicked()

        assertEquals(true, viewModel.modifierState.value.ctrlActive)
        assertEquals(true, viewModel.modifierState.value.altActive)
        assertEquals(true, viewModel.modifierState.value.winActive)
    }

    @Test
    fun `onKeyTyped combines sticky ctrl and alt bits with the chord's own modifier bits`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onCtrlClicked()
        viewModel.onAltClicked()
        viewModel.onKeyTyped('a')

        assertEquals(
            listOf((HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_ALT) to 0x04),
            fake.allKeyPresses
        )
    }

    @Test
    fun `onKeyTyped clears one-shot shift and sticky ctrl alt win but keeps caps lock`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)
        viewModel.onCapsLockClicked()
        viewModel.onCtrlClicked()
        viewModel.onKeyTyped('a')

        val state = viewModel.modifierState.value
        assertEquals(ShiftState.Off, state.shiftState)
        assertEquals(true, state.capsLockActive)
        assertEquals(false, state.ctrlActive)
    }

    @Test
    fun `onKeyTyped keeps locked shift after typing`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onShiftClicked(atMillis = 0L)
        viewModel.onShiftClicked(atMillis = 200L)
        viewModel.onKeyTyped('a')

        assertEquals(ShiftState.Locked, viewModel.modifierState.value.shiftState)
    }

    @Test
    fun `onKeyTyped with unmapped character does not touch modifier state`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onCtrlClicked()
        viewModel.onKeyTyped('#')

        assertEquals(true, viewModel.modifierState.value.ctrlActive)
    }

    @Test
    fun `onCtrlAltDelClicked sends ctrl+alt+delete and does not touch sticky state`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onCtrlAltDelClicked()

        assertEquals(
            listOf((HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_ALT) to 0x4C),
            fake.allKeyPresses
        )
        assertEquals(true, fake.releaseKeyboardCalled)
        assertEquals(false, viewModel.modifierState.value.ctrlActive)
        assertEquals(false, viewModel.modifierState.value.altActive)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.presentation.MainViewModelTest"`
Expected: FAIL — derleme hatası, `modifierState`/`onShiftClicked`/`onCapsLockClicked`/`onCtrlClicked`/`onAltClicked`/`onWinClicked`/`onCtrlAltDelClicked` `MainViewModel`'de yok.

- [ ] **Step 3: Write minimal implementation**

`MainViewModel.kt`'nin tamamını şu şekilde güncelle:

```kotlin
package com.dokunmatikekosistem.app.presentation

import androidx.lifecycle.ViewModel
import com.dokunmatikekosistem.app.data.gesture.RecognizedGesture
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
        }
    }

    fun onLayoutToggleClicked() {
        _activeLayout.value = if (_activeLayout.value is TurkishQLayout) EnglishUsLayout() else TurkishQLayout()
    }

    fun onKeyTyped(char: Char) {
        val state = _modifierState.value
        val chord = _activeLayout.value.mapChar(char) ?: return
        val modifierBits = chord.modifierBits or state.stickyHidModifierBits()
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
}

private fun KeyboardModifierState.stickyHidModifierBits(): Int {
    var bits = 0
    if (ctrlActive) bits = bits or HidKeyboardReport.MODIFIER_CTRL
    if (altActive) bits = bits or HidKeyboardReport.MODIFIER_ALT
    if (winActive) bits = bits or HidKeyboardReport.MODIFIER_WIN
    return bits
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --tests "com.dokunmatikekosistem.app.presentation.MainViewModelTest"`
Expected: PASS (tüm testler, eski + yeni)

- [ ] **Step 5: Commit**

```bash
cd "D:\projects\touchpad-ekosistem"
git add app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt
git commit -m "feat: add Shift one-shot/lock, CapsLock, and sticky Ctrl/Alt/Win modifier handling to MainViewModel"
```

---

### Task 5: `VirtualKeyboard`'ı ayrı dosyaya çıkar, modifier tuşlarını ve dinamik TR/EN etiketlerini bağla

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/presentation/keyboard/VirtualKeyboard.kt`
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt`

**Interfaces:**
- Consumes: Task 1 `KeyboardModifierState`/`ShiftState`; Task 3 `KeyboardLayout.displayRows()`/`shiftedChar()`; Task 4 `MainViewModel.modifierState`, `onShiftClicked()`, `onCapsLockClicked()`, `onCtrlClicked()`, `onAltClicked()`, `onWinClicked()`, `onCtrlAltDelClicked()`, `onKeyTyped(Char)` (mevcut).
- Produces: `@Composable fun VirtualKeyboard(layout: KeyboardLayout, modifierState: KeyboardModifierState, onKeyTyped: (Char) -> Unit, onShiftClicked: () -> Unit, onCapsLockClicked: () -> Unit, onCtrlClicked: () -> Unit, onAltClicked: () -> Unit, onWinClicked: () -> Unit, onCtrlAltDelClicked: () -> Unit)`.

Bu task'ta Compose UI testi yazılmıyor (projede Compose UI test altyapısı yok, mevcut konvansiyon). Doğrulama: `./gradlew assembleDebug` ile derleme + unit test suite'inin bütünüyle geçmesi + Task 6'daki manuel cihaz checklist'i.

- [ ] **Step 1: `VirtualKeyboard.kt` dosyasını oluştur**

```kotlin
package com.dokunmatikekosistem.app.presentation.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dokunmatikekosistem.app.domain.KeyboardLayout
import com.dokunmatikekosistem.app.domain.KeyboardModifierState
import com.dokunmatikekosistem.app.domain.ShiftState

@Composable
fun VirtualKeyboard(
    layout: KeyboardLayout,
    modifierState: KeyboardModifierState,
    onKeyTyped: (Char) -> Unit,
    onShiftClicked: () -> Unit,
    onCapsLockClicked: () -> Unit,
    onCtrlClicked: () -> Unit,
    onAltClicked: () -> Unit,
    onWinClicked: () -> Unit,
    onCtrlAltDelClicked: () -> Unit
) {
    val upper = modifierState.isUpperCaseEffective()

    Column(modifier = Modifier.padding(top = 8.dp)) {
        for (row in layout.displayRows()) {
            Row {
                for (baseChar in row) {
                    val displayChar = if (upper) layout.shiftedChar(baseChar) else baseChar
                    Button(onClick = { onKeyTyped(displayChar) }) { Text(displayChar.toString()) }
                }
            }
        }
        Row {
            Button(onClick = onShiftClicked) { Text(shiftLabel(modifierState.shiftState)) }
            Button(onClick = onCapsLockClicked) { Text(if (modifierState.capsLockActive) "Caps ●" else "Caps") }
            Button(onClick = { onKeyTyped(' ') }) { Text("Boşluk") }
            Button(onClick = { onKeyTyped('\n') }) { Text("Enter") }
            Button(onClick = { onKeyTyped('\b') }) { Text("Sil") }
        }
        Row {
            Button(onClick = onCtrlClicked) { Text(if (modifierState.ctrlActive) "Ctrl ●" else "Ctrl") }
            Button(onClick = onAltClicked) { Text(if (modifierState.altActive) "Alt ●" else "Alt") }
            Button(onClick = onWinClicked) { Text(if (modifierState.winActive) "Win ●" else "Win") }
            Button(onClick = onCtrlAltDelClicked) { Text("Ctrl+Alt+Del") }
        }
    }
}

private fun shiftLabel(state: ShiftState): String = when (state) {
    ShiftState.Off -> "Shift"
    ShiftState.OneShot -> "⇧ Aktif"
    ShiftState.Locked -> "⇧ Kilit"
}
```

- [ ] **Step 2: `MainActivity.kt`'yi güncelle**

`TouchpadScreen` fonksiyonunu ve dosyanın altındaki eski `VirtualKeyboard`/`virtualKeyboardShiftMap` tanımlarını değiştir:

1. Importlara ekle: `import com.dokunmatikekosistem.app.presentation.keyboard.VirtualKeyboard`
2. `TouchpadScreen` içinde `val activeLayout by viewModel.activeLayout.collectAsState()` satırından sonra ekle: `val modifierState by viewModel.modifierState.collectAsState()`
3. `if (keyboardVisible) { VirtualKeyboard(onKeyTyped = { viewModel.onKeyTyped(it) }) }` bloğunu şununla değiştir:

```kotlin
            if (keyboardVisible) {
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
```

4. Dosyanın altındaki şu iki bloğu **tamamen sil** (artık `presentation/keyboard/VirtualKeyboard.kt`'de yaşıyorlar):

```kotlin
private val virtualKeyboardShiftMap: Map<Char, Char> =
    ('a'..'z').associateWith { it.uppercaseChar() } +
        mapOf('ç' to 'Ç', 'ğ' to 'Ğ', 'ı' to 'I', 'ö' to 'Ö', 'ş' to 'Ş', 'ü' to 'Ü', 'i' to 'İ')

@Composable
private fun VirtualKeyboard(onKeyTyped: (Char) -> Unit) {
    val rows = listOf(
        "1234567890",
        "qwertyuıopğü",
        "asdfghjklşi",
        "zxcvbnmöç"
    )
    var shiftActive by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        for (row in rows) {
            Row {
                for (char in row) {
                    val displayChar = if (shiftActive) virtualKeyboardShiftMap[char] ?: char else char
                    Button(onClick = { onKeyTyped(displayChar) }) { Text(displayChar.toString()) }
                }
            }
        }
        Row {
            Button(onClick = { shiftActive = !shiftActive }) { Text(if (shiftActive) "⇧ Aktif" else "Shift") }
            Button(onClick = { onKeyTyped(' ') }) { Text("Boşluk") }
            Button(onClick = { onKeyTyped('\n') }) { Text("Enter") }
            Button(onClick = { onKeyTyped('\b') }) { Text("Sil") }
        }
    }
}
```

5. `mutableStateOf`/`remember`/`setValue` importları hâlâ `keyboardVisible` için `TouchpadScreen`'de kullanıldığından **kalmalı** — silme. `Row`/`Column`/`Button`/`Text`/`Modifier.padding`/`dp` importları da `TouchpadScreen` içinde hâlâ kullanılıyor, kalmalı.

- [ ] **Step 3: Derlemeyi doğrula**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Tüm unit testleri çalıştır**

Run: `cd "D:\projects\touchpad-ekosistem" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tüm testler (eski 62 + bu planda eklenen ~25) yeşil.

- [ ] **Step 5: Commit**

```bash
cd "D:\projects\touchpad-ekosistem"
git add app/src/main/java/com/dokunmatikekosistem/app/presentation/keyboard/VirtualKeyboard.kt app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt
git commit -m "feat: extract VirtualKeyboard into its own file, add modifier key UI, fix TR/EN label sync"
```

---

### Task 6: Cihaz Testi Checklist'i (manuel, kod değişikliği yok)

**Files:** Yok — bu task saf manuel doğrulama.

- [ ] **Step 1: Uygulamayı gerçek cihaza kur ve Windows'a bağlan**

Mevcut cihaz test prosedürünü kullan (Faz 2'de kurulmuş): `./gradlew installDebug`, telefonu Bluetooth HID ile Windows'a bağla, sanal klavyeyi aç.

- [ ] **Step 2: Aşağıdaki senaryoları Windows'ta gözlemleyerek doğrula**

1. Shift'e bir kez bas, bir harfe bas → yalnızca o harf büyük, Shift otomatik kapanıyor (etiket "Shift"e dönüyor).
2. Shift'e hızlıca iki kez bas (kilit) → etiket "⇧ Kilit" gösteriyor, birden fazla harf büyük yazılıyor, tekrar Shift'e basınca "Shift"e dönüyor.
3. Caps'e bas → harfler büyük yazılıyor, Shift'e dokunmadan; Caps açıkken Shift'e bas → o tek harf küçük yazılıyor (XOR).
4. Ctrl'e bas (aktif görünüyor), C'ye bas → Windows'ta Ctrl+C tetikleniyor, Ctrl otomatik kapanıyor.
5. Ctrl+Alt+Del butonuna bas → Windows güvenli masaüstü/Görev Yöneticisi ekranı açılıyor.
6. EN US'a geç → ekrandaki tuş etiketleri İngilizce karakterlere dönüyor (ğ/ü/ş/ı/ö/ç görünmüyor); TR Q'ya geri dön → Türkçe etiketler geri geliyor.

- [ ] **Step 3: Memory'yi güncelle**

Tüm senaryolar doğrulandıktan sonra `touchpad-ekosistem-proje-durumu.md` memory dosyasını güncelle: "Sanal klavye modifier tuşları (Shift one-shot/lock, CapsLock, Ctrl/Alt/Win, Ctrl+Alt+Del) TAMAMLANDI+cihazda doğrulandı" ve tarih ekle.
