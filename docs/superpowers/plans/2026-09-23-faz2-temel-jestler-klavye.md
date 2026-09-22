# Faz 2: Temel Jestler & Klavye Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 2-finger scroll (vertical + horizontal), 2-finger tap (right click), a virtual keyboard (Turkish Q + English US) that sends correct Turkish characters, tap-to-drag (drag lock), and haptic feedback — on top of Faz 1's Hilt/Clean Architecture foundation.

**Architecture:** Extends the existing `domain`/`data`/`presentation` layers. `HidMouseReport` grows from 3 to 5 bytes (adds wheel + AC Pan). A new `HidKeyboardReport` builder sends Report ID 2 for the first time. A new pure-Kotlin `GestureRecognizer` classifies raw multi-touch events into `RecognizedGesture`s, replacing Compose's single-pointer `detectDragGestures`/`detectTapGestures` with `awaitPointerEventScope`-based raw handling. Two `KeyboardLayout` implementations (Turkish Q, English US) map characters to HID usage codes.

**Tech Stack:** Kotlin, Jetpack Compose (`awaitPointerEventScope`), Hilt, `BluetoothHidDevice` API, `VibratorManager`, JUnit4.

## Global Constraints

- Package root: `com.dokunmatikekosistem.app`; layers: `domain`, `data`, `presentation` (unchanged from Faz 1).
- No pointer-acceleration math on cursor movement — raw deltas only (carried from Faz 0/1).
- `register()` idempotency (Faz 1) must not regress.
- **Keyboard character scope for Faz 2 is intentionally limited**, per the plan's exit criterion ("Türkçe karakterleri doğru gönderiyor" — no punctuation requirement): `A`–`Z`/`a`–`z`, `0`–`9`, the 7 Turkish letters (`ç`/`Ç`, `ğ`/`Ğ`, `ı`/`I`, `ö`/`Ö`, `ş`/`Ş`, `ü`/`Ü`, `i`/`İ`), Space, Enter, Backspace. Comma, period, and other punctuation are explicitly OUT of scope for Faz 2 (their exact Turkish-Q physical-key mapping needs separate verification against a real Turkish Q keyboard before being added — do not guess at it in this plan).
- `GestureRecognizer` and `KeyboardLayout` must be pure Kotlin with no Android framework imports, so they're unit-testable without the Android SDK (this environment has none).
- HID usage codes below are from the standard USB HID Usage Tables (Usage Page 0x07, Keyboard/Keypad) — these are physical-key-position codes, not characters. The OS (Windows, set to Turkish Q) maps them to the displayed character; this is why sending usage `0x2F` (the physical `[` key position) produces `ğ` on a Turkish-Q-configured Windows PC, not `[`.

---

## File Structure

- `app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidDescriptor.kt` — mouse section extended with wheel + AC Pan usages. (Task 1)
- `app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidMouseReport.kt` — `build()` signature grows to 5 params, 5-byte output. (Task 1)
- `app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidMouseReportTest.kt` — updated for new signature/format. (Task 1)
- `app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReport.kt` — new, builds the 8-byte keyboard report. (Task 2)
- `app/src/main/java/com/dokunmatikekosistem/app/domain/KeyboardLayout.kt` — new, `KeyboardLayout` interface + `HidKeyChord`. (Task 2)
- `app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReportTest.kt` — new. (Task 2)
- `app/src/main/java/com/dokunmatikekosistem/app/data/keyboard/TurkishQLayout.kt` — new. (Task 3)
- `app/src/main/java/com/dokunmatikekosistem/app/data/keyboard/EnglishUsLayout.kt` — new. (Task 3)
- `app/src/test/java/com/dokunmatikekosistem/app/data/keyboard/TurkishQLayoutTest.kt`, `EnglishUsLayoutTest.kt` — new. (Task 3)
- `app/src/main/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizer.kt` — new, `RawTouchEvent`, `RecognizedGesture`, `GestureRecognizer` class. (Task 4)
- `app/src/test/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizerTest.kt` — new. (Task 4)
- `app/src/main/java/com/dokunmatikekosistem/app/domain/HidManager.kt` — interface signature grows (mouse report params, keyboard report methods). (Task 5)
- `app/src/main/java/com/dokunmatikekosistem/app/data/bluetooth/BluetoothHidManager.kt` — implements the grown interface. (Task 5)
- `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt` — `onDrag`/`onTap` updated to new signature; `FakeHidManager` in the test file updated to match. (Task 5)
- `app/src/main/java/com/dokunmatikekosistem/app/data/haptics/HapticManager.kt` — new, `VibratorManager` wrapper. (Task 6)
- `app/src/main/java/com/dokunmatikekosistem/app/domain/Haptics.kt` — new, `Haptics` interface (`click()`, `dragLockEngaged()`). (Task 6)
- `app/src/main/java/com/dokunmatikekosistem/app/di/AppModule.kt` — add `@Binds Haptics`. (Task 6)
- `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt` — add gesture/keyboard/layout-switch handling. (Task 7)
- `app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt` — extended. (Task 7)
- `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt` — raw pointer processing, virtual keyboard panel UI, layout switch button. (Task 8)

---

### Task 1: Extend HID Mouse Report (Wheel + AC Pan + Right Button)

**Files:**
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidDescriptor.kt`
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidMouseReport.kt`
- Modify: `app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidMouseReportTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces (consumed by Task 5): `HidMouseReport.build(dx: Int, dy: Int, wheelDelta: Int, panDelta: Int, leftButtonPressed: Boolean, rightButtonPressed: Boolean): ByteArray` — 5-byte report `[buttons, dx, dy, wheel, pan]`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidMouseReportTest.kt
package com.dokunmatikekosistem.app.data.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HidMouseReportTest {

    @Test
    fun `all zero produces zeroed 5-byte report`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, wheelDelta = 0, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0), report)
    }

    @Test
    fun `left button sets bit 0`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, wheelDelta = 0, panDelta = 0, leftButtonPressed = true, rightButtonPressed = false)
        assertArrayEquals(byteArrayOf(1, 0, 0, 0, 0), report)
    }

    @Test
    fun `right button sets bit 1`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, wheelDelta = 0, panDelta = 0, leftButtonPressed = false, rightButtonPressed = true)
        assertArrayEquals(byteArrayOf(2, 0, 0, 0, 0), report)
    }

    @Test
    fun `both buttons set bits 0 and 1`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, wheelDelta = 0, panDelta = 0, leftButtonPressed = true, rightButtonPressed = true)
        assertArrayEquals(byteArrayOf(3, 0, 0, 0, 0), report)
    }

    @Test
    fun `positive dx dy wheel pan pass through unchanged`() {
        val report = HidMouseReport.build(dx = 10, dy = 20, wheelDelta = 3, panDelta = 4, leftButtonPressed = false, rightButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 10, 20, 3, 4), report)
    }

    @Test
    fun `negative values encode as signed bytes`() {
        val report = HidMouseReport.build(dx = -10, dy = -20, wheelDelta = -3, panDelta = -4, leftButtonPressed = false, rightButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, (-10).toByte(), (-20).toByte(), (-3).toByte(), (-4).toByte()), report)
    }

    @Test
    fun `all four movement fields clamp to -127 and 127 independently`() {
        val report = HidMouseReport.build(dx = 500, dy = -500, wheelDelta = 500, panDelta = -500, leftButtonPressed = false, rightButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 127, (-127).toByte(), 127, (-127).toByte()), report)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

In Android Studio: right-click `HidMouseReportTest.kt` → Run.
Expected: FAIL — `HidMouseReport.build` signature mismatch (compile error, old 3-arg overload).

- [ ] **Step 3: Update `HidMouseReport.build`**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidMouseReport.kt
package com.dokunmatikekosistem.app.data.hid

/** Builds the 5-byte relative-mouse HID report: [buttons, dx, dy, wheel, pan]. */
object HidMouseReport {
    fun build(
        dx: Int,
        dy: Int,
        wheelDelta: Int,
        panDelta: Int,
        leftButtonPressed: Boolean,
        rightButtonPressed: Boolean
    ): ByteArray {
        var buttons = 0
        if (leftButtonPressed) buttons = buttons or 0x01
        if (rightButtonPressed) buttons = buttons or 0x02
        return byteArrayOf(
            buttons.toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheelDelta.coerceIn(-127, 127).toByte(),
            panDelta.coerceIn(-127, 127).toByte()
        )
    }
}
```

- [ ] **Step 4: Update `HidDescriptor` — add wheel and AC Pan usages to the mouse section**

Replace the mouse section of `HidDescriptor.DESCRIPTOR` (everything from the start of the byte array up to the first `0xC0.toByte(), 0xC0.toByte()` pair that closes the mouse collection) with this extended version; leave the keyboard section (Report ID 2, starting at the second `0x05, 0x01, // Usage Page (Generic Desktop)` block) completely unchanged:

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidDescriptor.kt
package com.dokunmatikekosistem.app.data.hid

/**
 * Composite HID report descriptor: Report ID 1 = relative mouse (buttons + X/Y +
 * wheel + AC Pan), Report ID 2 = standard boot keyboard (modifier byte + 6-key array).
 */
object HidDescriptor {
    const val MOUSE_REPORT_ID: Byte = 1
    const val KEYBOARD_REPORT_ID: Byte = 2

    val DESCRIPTOR: ByteArray = byteArrayOf(
        // Mouse (Report ID 1)
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x02,             // Usage (Mouse)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), MOUSE_REPORT_ID, // Report ID (1)
        0x09, 0x01,             // Usage (Pointer)
        0xA1.toByte(), 0x00,    // Collection (Physical)
        0x05, 0x09,             //   Usage Page (Buttons)
        0x19, 0x01,             //   Usage Minimum (1)
        0x29, 0x03,             //   Usage Maximum (3)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x95.toByte(), 0x03,    //   Report Count (3)
        0x75, 0x01,             //   Report Size (1)
        0x81.toByte(), 0x02,    //   Input (Data,Var,Abs)
        0x95.toByte(), 0x01,    //   Report Count (1)
        0x75, 0x05,             //   Report Size (5) - padding
        0x81.toByte(), 0x03,    //   Input (Const,Var,Abs)
        0x05, 0x01,             //   Usage Page (Generic Desktop)
        0x09, 0x30,             //   Usage (X)
        0x09, 0x31,             //   Usage (Y)
        0x15, 0x81.toByte(),    //   Logical Minimum (-127)
        0x25, 0x7F,             //   Logical Maximum (127)
        0x75, 0x08,             //   Report Size (8)
        0x95.toByte(), 0x02,    //   Report Count (2)
        0x81.toByte(), 0x06,    //   Input (Data,Var,Rel)
        0x09, 0x38,             //   Usage (Wheel)
        0x15, 0x81.toByte(),    //   Logical Minimum (-127)
        0x25, 0x7F,             //   Logical Maximum (127)
        0x75, 0x08,             //   Report Size (8)
        0x95.toByte(), 0x01,    //   Report Count (1)
        0x81.toByte(), 0x06,    //   Input (Data,Var,Rel)
        0x05, 0x0C,             //   Usage Page (Consumer)
        0x0A, 0x38, 0x02,       //   Usage (AC Pan)
        0x15, 0x81.toByte(),    //   Logical Minimum (-127)
        0x25, 0x7F,             //   Logical Maximum (127)
        0x75, 0x08,             //   Report Size (8)
        0x95.toByte(), 0x01,    //   Report Count (1)
        0x81.toByte(), 0x06,    //   Input (Data,Var,Rel)
        0xC0.toByte(),          // End Collection (Physical)
        0xC0.toByte(),          // End Collection (Application)

        // Keyboard (Report ID 2) - boot format, unchanged from Faz 0/1
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x06,             // Usage (Keyboard)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), KEYBOARD_REPORT_ID, // Report ID (2)
        0x05, 0x07,             //   Usage Page (Key Codes)
        0x19, 0xE0.toByte(),    //   Usage Minimum (224)
        0x29, 0xE7.toByte(),    //   Usage Maximum (231)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95.toByte(), 0x08,    //   Report Count (8) - modifier byte
        0x81.toByte(), 0x02,    //   Input (Data,Var,Abs)
        0x95.toByte(), 0x01,    //   Report Count (1)
        0x75, 0x08,             //   Report Size (8) - reserved byte
        0x81.toByte(), 0x01,    //   Input (Const)
        0x95.toByte(), 0x06,    //   Report Count (6)
        0x75, 0x08,             //   Report Size (8)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x65,             //   Logical Maximum (101)
        0x05, 0x07,             //   Usage Page (Key Codes)
        0x19, 0x00,             //   Usage Minimum (0)
        0x29, 0x65,             //   Usage Maximum (101)
        0x81.toByte(), 0x00,    //   Input (Data,Array)
        0xC0.toByte()           // End Collection
    )
}
```

- [ ] **Step 5: Run tests to verify they pass**

In Android Studio: run `HidMouseReportTest.kt`.
Expected: PASS (7 tests green).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/data/hid app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidMouseReportTest.kt
git commit -m "feat: extend HID mouse report with wheel, AC Pan, and right button"
```

---

### Task 2: HID Keyboard Report Builder + KeyboardLayout Interface

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReport.kt`
- Create: `app/src/main/java/com/dokunmatikekosistem/app/domain/KeyboardLayout.kt`
- Create: `app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReportTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces (consumed by Task 3's layouts and Task 5's `BluetoothHidManager`):
  - `com.dokunmatikekosistem.app.domain.HidKeyChord` — `data class HidKeyChord(val modifierBits: Int, val usageCode: Int)`
  - `com.dokunmatikekosistem.app.domain.KeyboardLayout` — `interface KeyboardLayout { fun mapChar(char: Char): HidKeyChord? }`
  - `com.dokunmatikekosistem.app.data.hid.HidKeyboardReport.build(chord: HidKeyChord): ByteArray` — 8-byte report `[modifier, 0, usageCode, 0, 0, 0, 0, 0]`
  - `com.dokunmatikekosistem.app.data.hid.HidKeyboardReport.release(): ByteArray` — 8 zero bytes (key-up)
  - Modifier bit constants: `HidKeyboardReport.MODIFIER_SHIFT = 0x02`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReportTest.kt
package com.dokunmatikekosistem.app.data.hid

import com.dokunmatikekosistem.app.domain.HidKeyChord
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HidKeyboardReportTest {

    @Test
    fun `build with no modifier produces modifier byte 0`() {
        val report = HidKeyboardReport.build(HidKeyChord(modifierBits = 0, usageCode = 0x04))
        assertArrayEquals(byteArrayOf(0, 0, 0x04, 0, 0, 0, 0, 0), report)
    }

    @Test
    fun `build with shift modifier sets modifier byte`() {
        val report = HidKeyboardReport.build(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x04))
        assertArrayEquals(byteArrayOf(0x02, 0, 0x04, 0, 0, 0, 0, 0), report)
    }

    @Test
    fun `release produces all-zero 8-byte report`() {
        val report = HidKeyboardReport.release()
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0), report)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

In Android Studio: right-click `HidKeyboardReportTest.kt` → Run.
Expected: FAIL — `HidKeyboardReport` unresolved.

- [ ] **Step 3: Write `KeyboardLayout`/`HidKeyChord` and `HidKeyboardReport`**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/domain/KeyboardLayout.kt
package com.dokunmatikekosistem.app.domain

/** A physical HID key press: modifier bits (see HidKeyboardReport.MODIFIER_*) + USB HID usage code. */
data class HidKeyChord(val modifierBits: Int, val usageCode: Int)

/** Maps a displayed character to the physical key chord that produces it under this layout. */
interface KeyboardLayout {
    fun mapChar(char: Char): HidKeyChord?
}
```

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReport.kt
package com.dokunmatikekosistem.app.data.hid

import com.dokunmatikekosistem.app.domain.HidKeyChord

/** Builds the 8-byte boot-keyboard HID report: [modifier, reserved, key1..key6]. */
object HidKeyboardReport {
    const val MODIFIER_SHIFT = 0x02

    fun build(chord: HidKeyChord): ByteArray = byteArrayOf(
        chord.modifierBits.toByte(),
        0,
        chord.usageCode.toByte(),
        0, 0, 0, 0, 0
    )

    fun release(): ByteArray = ByteArray(8)
}
```

- [ ] **Step 4: Run tests to verify they pass**

In Android Studio: run `HidKeyboardReportTest.kt`.
Expected: PASS (3 tests green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReport.kt app/src/main/java/com/dokunmatikekosistem/app/domain/KeyboardLayout.kt app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidKeyboardReportTest.kt
git commit -m "feat: add HidKeyboardReport builder and KeyboardLayout interface"
```

---

### Task 3: Turkish Q and English US Keyboard Layouts

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/data/keyboard/TurkishQLayout.kt`
- Create: `app/src/main/java/com/dokunmatikekosistem/app/data/keyboard/EnglishUsLayout.kt`
- Create: `app/src/test/java/com/dokunmatikekosistem/app/data/keyboard/TurkishQLayoutTest.kt`
- Create: `app/src/test/java/com/dokunmatikekosistem/app/data/keyboard/EnglishUsLayoutTest.kt`

**Interfaces:**
- Consumes: `com.dokunmatikekosistem.app.domain.KeyboardLayout`, `com.dokunmatikekosistem.app.domain.HidKeyChord` (Task 2).
- Produces (consumed by Task 7's `MainViewModel`): `com.dokunmatikekosistem.app.data.keyboard.TurkishQLayout : KeyboardLayout`, `com.dokunmatikekosistem.app.data.keyboard.EnglishUsLayout : KeyboardLayout`.

**Character scope for both layouts (per Global Constraints):** `a`-`z`, `A`-`Z`, `0`-`9`, space, Enter (`\n`), Backspace (`\b`), and — Turkish layout only — `ç Ç ğ Ğ ı I ö Ö ş Ş ü Ü i İ`. `mapChar` returns `null` for anything else.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/dokunmatikekosistem/app/data/keyboard/EnglishUsLayoutTest.kt
package com.dokunmatikekosistem.app.data.keyboard

import com.dokunmatikekosistem.app.domain.HidKeyChord
import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishUsLayoutTest {
    private val layout = EnglishUsLayout()

    @Test
    fun `lowercase a maps to usage 0x04 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x04), layout.mapChar('a'))
    }

    @Test
    fun `uppercase A maps to usage 0x04 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x04), layout.mapChar('A'))
    }

    @Test
    fun `digit 1 maps to usage 0x1E with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x1E), layout.mapChar('1'))
    }

    @Test
    fun `space maps to usage 0x2C`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x2C), layout.mapChar(' '))
    }

    @Test
    fun `turkish character returns null on english layout`() {
        assertEquals(null, layout.mapChar('ğ'))
    }
}
```

```kotlin
// app/src/test/java/com/dokunmatikekosistem/app/data/keyboard/TurkishQLayoutTest.kt
package com.dokunmatikekosistem.app.data.keyboard

import com.dokunmatikekosistem.app.domain.HidKeyChord
import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import org.junit.Assert.assertEquals
import org.junit.Test

class TurkishQLayoutTest {
    private val layout = TurkishQLayout()

    @Test
    fun `lowercase a maps the same as english (unchanged physical position)`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x04), layout.mapChar('a'))
    }

    @Test
    fun `g breve lowercase maps to usage 0x2F with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x2F), layout.mapChar('ğ'))
    }

    @Test
    fun `G breve uppercase maps to usage 0x2F with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x2F), layout.mapChar('Ğ'))
    }

    @Test
    fun `u umlaut lowercase maps to usage 0x30 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x30), layout.mapChar('ü'))
    }

    @Test
    fun `U umlaut uppercase maps to usage 0x30 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x30), layout.mapChar('Ü'))
    }

    @Test
    fun `s cedilla lowercase maps to usage 0x33 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x33), layout.mapChar('ş'))
    }

    @Test
    fun `S cedilla uppercase maps to usage 0x33 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x33), layout.mapChar('Ş'))
    }

    @Test
    fun `dotted lowercase i maps to usage 0x34 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x34), layout.mapChar('i'))
    }

    @Test
    fun `dotted uppercase I maps to usage 0x34 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x34), layout.mapChar('İ'))
    }

    @Test
    fun `o umlaut lowercase maps to usage 0x36 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x36), layout.mapChar('ö'))
    }

    @Test
    fun `O umlaut uppercase maps to usage 0x36 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x36), layout.mapChar('Ö'))
    }

    @Test
    fun `c cedilla lowercase maps to usage 0x37 with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x37), layout.mapChar('ç'))
    }

    @Test
    fun `C cedilla uppercase maps to usage 0x37 with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x37), layout.mapChar('Ç'))
    }

    @Test
    fun `dotless lowercase i maps to usage 0x0C with no modifier`() {
        assertEquals(HidKeyChord(modifierBits = 0, usageCode = 0x0C), layout.mapChar('ı'))
    }

    @Test
    fun `dotless uppercase I maps to usage 0x0C with shift`() {
        assertEquals(HidKeyChord(modifierBits = HidKeyboardReport.MODIFIER_SHIFT, usageCode = 0x0C), layout.mapChar('I'))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

In Android Studio: run both test files.
Expected: FAIL — `TurkishQLayout`/`EnglishUsLayout` unresolved.

- [ ] **Step 3: Write `EnglishUsLayout`**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/data/keyboard/EnglishUsLayout.kt
package com.dokunmatikekosistem.app.data.keyboard

import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import com.dokunmatikekosistem.app.domain.HidKeyChord
import com.dokunmatikekosistem.app.domain.KeyboardLayout

/** Standard US QWERTY: HID usage codes match physical key labels directly. */
class EnglishUsLayout : KeyboardLayout {

    private val letterUsage: Map<Char, Int> = ('a'..'z').mapIndexed { index, char -> char to (0x04 + index) }.toMap()
    private val digitUsage: Map<Char, Int> = mapOf(
        '1' to 0x1E, '2' to 0x1F, '3' to 0x20, '4' to 0x21, '5' to 0x22,
        '6' to 0x23, '7' to 0x24, '8' to 0x25, '9' to 0x26, '0' to 0x27
    )

    override fun mapChar(char: Char): HidKeyChord? {
        letterUsage[char.lowercaseChar()]?.let { usage ->
            val shift = if (char.isUpperCase()) HidKeyboardReport.MODIFIER_SHIFT else 0
            return HidKeyChord(modifierBits = shift, usageCode = usage)
        }
        digitUsage[char]?.let { usage -> return HidKeyChord(modifierBits = 0, usageCode = usage) }
        return when (char) {
            ' ' -> HidKeyChord(modifierBits = 0, usageCode = 0x2C)
            '\n' -> HidKeyChord(modifierBits = 0, usageCode = 0x28)
            '\b' -> HidKeyChord(modifierBits = 0, usageCode = 0x2A)
            else -> null
        }
    }
}
```

- [ ] **Step 4: Write `TurkishQLayout`**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/data/keyboard/TurkishQLayout.kt
package com.dokunmatikekosistem.app.data.keyboard

import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import com.dokunmatikekosistem.app.domain.HidKeyChord
import com.dokunmatikekosistem.app.domain.KeyboardLayout

/**
 * Turkish Q layout. Regular a-z letters, digits, space/enter/backspace share the
 * same physical-key usage codes as English US (Turkish Q is QWERTY-based). The
 * dotless 'ı'/'I' share the physical position of the English "I" key (usage
 * 0x0C). Seven Turkish letters replace punctuation-key positions on the
 * physical Turkish Q keyboard:
 *   ğ/Ğ -> usage 0x2F (physical '[' key)
 *   ü/Ü -> usage 0x30 (physical ']' key)
 *   ş/Ş -> usage 0x33 (physical ';' key)
 *   i/İ -> usage 0x34 (physical ''' key) -- dotted i, distinct from dotless ı/I
 *   ö/Ö -> usage 0x36 (physical ',' key)
 *   ç/Ç -> usage 0x37 (physical '.' key)
 */
class TurkishQLayout : KeyboardLayout {

    private val base = EnglishUsLayout()

    private val turkishUsage: Map<Char, Int> = mapOf(
        'ğ' to 0x2F, 'Ğ' to 0x2F,
        'ü' to 0x30, 'Ü' to 0x30,
        'ş' to 0x33, 'Ş' to 0x33,
        'i' to 0x34, 'İ' to 0x34,
        'ö' to 0x36, 'Ö' to 0x36,
        'ç' to 0x37, 'Ç' to 0x37,
        'ı' to 0x0C, 'I' to 0x0C
    )

    private val turkishUppercase = setOf('Ğ', 'Ü', 'Ş', 'İ', 'Ö', 'Ç', 'I')

    override fun mapChar(char: Char): HidKeyChord? {
        // 'i'/'I' are looked up here too (dotted/dotless split), which shadows
        // EnglishUsLayout's plain a-z mapping for those two characters specifically.
        turkishUsage[char]?.let { usage ->
            val shift = if (char in turkishUppercase) HidKeyboardReport.MODIFIER_SHIFT else 0
            return HidKeyChord(modifierBits = shift, usageCode = usage)
        }
        // Everything else (a-z except i/I, digits, space/enter/backspace) is
        // physically unchanged from English US.
        return base.mapChar(char)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

In Android Studio: run both test files.
Expected: PASS (`EnglishUsLayoutTest` 5 tests, `TurkishQLayoutTest` 14 tests, all green).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/data/keyboard app/src/test/java/com/dokunmatikekosistem/app/data/keyboard
git commit -m "feat: add Turkish Q and English US keyboard layouts"
```

---

### Task 4: GestureRecognizer

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizer.kt`
- Create: `app/src/test/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizerTest.kt`

**Interfaces:**
- Consumes: nothing new (pure Kotlin).
- Produces (consumed by Task 8's `MainActivity`/`TouchpadScreen`):
  - `com.dokunmatikekosistem.app.data.gesture.RawTouchEvent` (sealed interface: `PointerDown`, `PointerMove`, `PointerUp`, each with `id: Int, x: Float, y: Float, timeMs: Long`)
  - `com.dokunmatikekosistem.app.data.gesture.RecognizedGesture` (sealed interface: `CursorMove(dx: Int, dy: Int)`, `LeftClick`, `RightClick`, `Scroll(vDelta: Int, hDelta: Int)`, `DragLockEngaged`, `DragMove(dx: Int, dy: Int)`, `DragLockReleased`)
  - `class GestureRecognizer { fun onEvent(event: RawTouchEvent): RecognizedGesture? }`

**Behavior (thresholds from the design doc, Bölüm 7):**
- One pointer down→up with total movement ≤ 10px and duration ≤ 200ms → `LeftClick`.
- One pointer moving (after down, before up) → `CursorMove` per move event, using the delta since the previous event for that pointer.
- Two pointers down within 150ms of each other, both lifted within 200ms of their own down with ≤10px movement each → `RightClick`.
- Two pointers down, both moving together (both have moved >10px total) → `Scroll`, using the average of the two pointers' per-event deltas (vertical = `Scroll.vDelta`, horizontal = `Scroll.hDelta`).
- Two single-pointer taps (down→up, each ≤10px/≤200ms) with the gap between the first tap's up and the second tap's down ≤300ms, AND the second tap's pointer stays down ≥150ms without lifting → `DragLockEngaged`, followed by `DragMove` for subsequent moves of that same pointer, followed by `DragLockReleased` on its `PointerUp`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizerTest.kt
package com.dokunmatikekosistem.app.data.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRecognizerTest {

    @Test
    fun `single tap under threshold produces LeftClick on up`() {
        val recognizer = GestureRecognizer()
        assertNull(recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0)))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 102f, y = 101f, timeMs = 100))
        assertEquals(RecognizedGesture.LeftClick, result)
    }

    @Test
    fun `single pointer move produces CursorMove with delta since last event`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 115f, y = 108f, timeMs = 16))
        assertEquals(RecognizedGesture.CursorMove(dx = 15, dy = 8), result)
    }

    @Test
    fun `two finger simultaneous tap produces RightClick`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 30))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 101f, y = 100f, timeMs = 120))
        val result = recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 201f, y = 100f, timeMs = 130))
        assertEquals(RecognizedGesture.RightClick, result)
    }

    @Test
    fun `two fingers moving together produce Scroll`() {
        val recognizer = GestureRecognizer()
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 200f, y = 100f, timeMs = 10))
        recognizer.onEvent(RawTouchEvent.PointerMove(id = 0, x = 100f, y = 130f, timeMs = 50))
        val result = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 200f, y = 130f, timeMs = 60))
        assertTrue(result is RecognizedGesture.Scroll)
    }

    @Test
    fun `tap then hold-and-drag engages drag lock then moves then releases`() {
        val recognizer = GestureRecognizer()
        // First tap
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 0, x = 100f, y = 100f, timeMs = 0))
        recognizer.onEvent(RawTouchEvent.PointerUp(id = 0, x = 100f, y = 100f, timeMs = 80))
        // Second touch within 300ms, held down
        recognizer.onEvent(RawTouchEvent.PointerDown(id = 1, x = 100f, y = 100f, timeMs = 200))
        val engaged = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 100f, y = 100f, timeMs = 360))
        assertEquals(RecognizedGesture.DragLockEngaged, engaged)
        val moved = recognizer.onEvent(RawTouchEvent.PointerMove(id = 1, x = 120f, y = 100f, timeMs = 400))
        assertEquals(RecognizedGesture.DragMove(dx = 20, dy = 0), moved)
        val released = recognizer.onEvent(RawTouchEvent.PointerUp(id = 1, x = 120f, y = 100f, timeMs = 500))
        assertEquals(RecognizedGesture.DragLockReleased, released)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

In Android Studio: right-click `GestureRecognizerTest.kt` → Run.
Expected: FAIL — `RawTouchEvent`/`RecognizedGesture`/`GestureRecognizer` unresolved.

- [ ] **Step 3: Write `GestureRecognizer`**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/data/gesture/GestureRecognizer.kt
package com.dokunmatikekosistem.app.data.gesture

import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAP_MAX_MOVEMENT_PX = 10f
private const val TAP_MAX_DURATION_MS = 200L
private const val TWO_FINGER_DOWN_WINDOW_MS = 150L
private const val DRAG_LOCK_TAP_GAP_MS = 300L
private const val DRAG_LOCK_HOLD_MS = 150L

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
}

private class ActivePointer(var lastX: Float, var lastY: Float, val downX: Float, val downY: Float, val downTimeMs: Long) {
    var totalMovement = 0f
}

class GestureRecognizer {

    private val active = mutableMapOf<Int, ActivePointer>()
    private var lastTapUpTimeMs: Long? = null
    private var dragLockPointerId: Int? = null
    private var dragLockEngagedSent = false

    // Tracks the current multi-touch "session": from the first PointerDown
    // after all pointers were previously up, until all pointers are up again.
    private var sessionMaxPointers = 0
    private var sessionAllTapsSoFar = true

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

        // Drag-lock candidate: a second down shortly after the previous tap's up,
        // and this is the only pointer down right now (not part of a 2-finger session).
        val gap = lastTapUpTimeMs?.let { event.timeMs - it }
        if (active.size == 1 && gap != null && gap in 0..DRAG_LOCK_TAP_GAP_MS) {
            dragLockPointerId = event.id
        }
        return null
    }

    private fun onMove(event: RawTouchEvent.PointerMove): RecognizedGesture? {
        val pointer = active[event.id] ?: return null
        val dx = (event.x - pointer.lastX)
        val dy = (event.y - pointer.lastY)
        pointer.totalMovement += kotlin.math.hypot(dx, dy)
        pointer.lastX = event.x
        pointer.lastY = event.y

        if (pointer.totalMovement > TAP_MAX_MOVEMENT_PX) {
            sessionAllTapsSoFar = false
        }

        if (event.id == dragLockPointerId) {
            val heldMs = event.timeMs - pointer.downTimeMs
            if (!dragLockEngagedSent) {
                if (heldMs >= DRAG_LOCK_HOLD_MS) {
                    dragLockEngagedSent = true
                    return RecognizedGesture.DragLockEngaged
                }
                return null
            }
            return RecognizedGesture.DragMove(dx.roundToInt(), dy.roundToInt())
        }

        if (active.size == 2) {
            val other = active.entries.first { it.key != event.id }.value
            if (other.totalMovement > TAP_MAX_MOVEMENT_PX && pointer.totalMovement > TAP_MAX_MOVEMENT_PX) {
                return RecognizedGesture.Scroll(vDelta = dy.roundToInt(), hDelta = dx.roundToInt())
            }
            return null
        }

        if (active.size == 1) {
            return RecognizedGesture.CursorMove(dx.roundToInt(), dy.roundToInt())
        }
        return null
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
            if (active.isEmpty()) { sessionMaxPointers = 0; sessionAllTapsSoFar = true }
            return if (wasEngaged) RecognizedGesture.DragLockReleased else null
        }

        if (active.isNotEmpty()) return null

        // All pointers are now up: decide what this session was.
        val result = when {
            sessionMaxPointers >= 2 && sessionAllTapsSoFar -> RecognizedGesture.RightClick
            sessionMaxPointers == 1 && isTap -> {
                lastTapUpTimeMs = event.timeMs
                RecognizedGesture.LeftClick
            }
            else -> null
        }
        sessionMaxPointers = 0
        sessionAllTapsSoFar = true
        return result
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

In Android Studio: run `GestureRecognizerTest.kt`.
Expected: PASS (5 tests green). If the two-finger-tap right-click test fails, trace `onDown`/`onUp` by hand against the test's exact timestamps (0, 30, 120, 130) and adjust the simultaneity check in `onDown`/`onUp` accordingly — the logic must track that pointer 1 went down within `TWO_FINGER_DOWN_WINDOW_MS` of pointer 0, and both came up as taps, before firing `RightClick` on the second `PointerUp`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/data/gesture app/src/test/java/com/dokunmatikekosistem/app/data/gesture
git commit -m "feat: add GestureRecognizer for multi-touch gesture classification"
```

---

### Task 5: Extend HidManager Interface and BluetoothHidManager

**Files:**
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/domain/HidManager.kt`
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/data/bluetooth/BluetoothHidManager.kt`
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt`
- Modify: `app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt`

**Interfaces:**
- Consumes: `HidMouseReport.build` (Task 1, new 6-arg signature), `HidKeyboardReport.build`/`release` (Task 2).
- Produces (consumed by Task 7's `MainViewModel` gesture handling):
  - `HidManager.sendMouseReport(dx: Int, dy: Int, wheelDelta: Int, panDelta: Int, leftButtonPressed: Boolean, rightButtonPressed: Boolean)`
  - `HidManager.sendKeyboardReport(modifierBits: Int, usageCode: Int)`
  - `HidManager.releaseKeyboardReport()`

- [ ] **Step 1: Update the `HidManager` interface**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/domain/HidManager.kt
package com.dokunmatikekosistem.app.domain

import kotlinx.coroutines.flow.StateFlow

interface HidManager {
    val connectionState: StateFlow<ConnectionState>
    val reportsSent: StateFlow<Int>
    fun register()
    fun sendMouseReport(dx: Int, dy: Int, wheelDelta: Int, panDelta: Int, leftButtonPressed: Boolean, rightButtonPressed: Boolean)
    fun sendKeyboardReport(modifierBits: Int, usageCode: Int)
    fun releaseKeyboardReport()
}
```

- [ ] **Step 2: Update `BluetoothHidManager`**

In `app/src/main/java/com/dokunmatikekosistem/app/data/bluetooth/BluetoothHidManager.kt`, replace the `sendMouseReport` function and add two new functions:

```kotlin
    override fun sendMouseReport(dx: Int, dy: Int, wheelDelta: Int, panDelta: Int, leftButtonPressed: Boolean, rightButtonPressed: Boolean) {
        val device = connectedDevice ?: return
        val report = com.dokunmatikekosistem.app.data.hid.HidMouseReport.build(dx, dy, wheelDelta, panDelta, leftButtonPressed, rightButtonPressed)
        val sent = hidDevice?.sendReport(device, com.dokunmatikekosistem.app.data.hid.HidDescriptor.MOUSE_REPORT_ID.toInt(), report)
        if (sent == true) {
            _reportsSent.value = _reportsSent.value + 1
        }
    }

    override fun sendKeyboardReport(modifierBits: Int, usageCode: Int) {
        val device = connectedDevice ?: return
        val report = com.dokunmatikekosistem.app.data.hid.HidKeyboardReport.build(
            com.dokunmatikekosistem.app.domain.HidKeyChord(modifierBits, usageCode)
        )
        val sent = hidDevice?.sendReport(device, com.dokunmatikekosistem.app.data.hid.HidDescriptor.KEYBOARD_REPORT_ID.toInt(), report)
        if (sent == true) {
            _reportsSent.value = _reportsSent.value + 1
        }
    }

    override fun releaseKeyboardReport() {
        val device = connectedDevice ?: return
        val report = com.dokunmatikekosistem.app.data.hid.HidKeyboardReport.release()
        hidDevice?.sendReport(device, com.dokunmatikekosistem.app.data.hid.HidDescriptor.KEYBOARD_REPORT_ID.toInt(), report)
    }
```

- [ ] **Step 3: Fix `MainViewModel`'s existing calls to the new `sendMouseReport` signature**

In `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt`, update `onDrag` and `onTap` (their behavior is unchanged — only the call now passes the two new params as neutral values):

```kotlin
    fun onDrag(dx: Int, dy: Int) {
        hidManager.sendMouseReport(dx, dy, wheelDelta = 0, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)
    }

    fun onTap() {
        hidManager.sendMouseReport(dx = 0, dy = 0, wheelDelta = 0, panDelta = 0, leftButtonPressed = true, rightButtonPressed = false)
        hidManager.sendMouseReport(dx = 0, dy = 0, wheelDelta = 0, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)
    }
```

- [ ] **Step 4: Fix `FakeHidManager` in `MainViewModelTest.kt` to implement the grown interface**

In `app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt`, update `FakeHidManager`:

```kotlin
private class FakeHidManager : HidManager {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val reportsSent = MutableStateFlow(0)
    var registerCalled = false
    val allReports = mutableListOf<Triple<Int, Int, Boolean>>()
    val allKeyPresses = mutableListOf<Pair<Int, Int>>()
    var releaseKeyboardCalled = false

    override fun register() {
        registerCalled = true
    }

    override fun sendMouseReport(dx: Int, dy: Int, wheelDelta: Int, panDelta: Int, leftButtonPressed: Boolean, rightButtonPressed: Boolean) {
        allReports.add(Triple(dx, dy, leftButtonPressed))
        reportsSent.value = reportsSent.value + 1
    }

    override fun sendKeyboardReport(modifierBits: Int, usageCode: Int) {
        allKeyPresses.add(modifierBits to usageCode)
        reportsSent.value = reportsSent.value + 1
    }

    override fun releaseKeyboardReport() {
        releaseKeyboardCalled = true
    }
}
```

Existing tests in this file (`onConnectClicked delegates...`, `onDrag sends...`, `onTap sends...`, `connectionState exposes...`) reference `fake.allReports` and should continue to compile and pass unchanged against this updated fake — verify each one still holds after the interface change; do not alter their assertions, only the fake's shape.

- [ ] **Step 5: Run tests to verify everything still passes**

In Android Studio: run `MainViewModelTest.kt` (existing 4 tests) plus Tasks 1-4's test files.
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/domain/HidManager.kt app/src/main/java/com/dokunmatikekosistem/app/data/bluetooth/BluetoothHidManager.kt app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt
git commit -m "feat: extend HidManager with scroll/right-click/keyboard report methods"
```

---

### Task 6: Haptic Feedback

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/domain/Haptics.kt`
- Create: `app/src/main/java/com/dokunmatikekosistem/app/data/haptics/HapticManager.kt`
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/di/AppModule.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces (consumed by Task 7's `MainViewModel`): `com.dokunmatikekosistem.app.domain.Haptics` — `interface Haptics { fun click(); fun dragLockEngaged() }`, bound via Hilt to `data.haptics.HapticManager`.

- [ ] **Step 1: Write the `Haptics` interface**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/domain/Haptics.kt
package com.dokunmatikekosistem.app.domain

interface Haptics {
    fun click()
    fun dragLockEngaged()
}
```

- [ ] **Step 2: Write `HapticManager`**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/data/haptics/HapticManager.kt
package com.dokunmatikekosistem.app.data.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.dokunmatikekosistem.app.domain.Haptics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticManager @Inject constructor(
    @ApplicationContext private val context: Context
) : Haptics {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun click() {
        vibrate(durationMs = 15)
    }

    override fun dragLockEngaged() {
        vibrate(durationMs = 30)
    }

    private fun vibrate(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }
}
```

- [ ] **Step 3: Bind `Haptics` in the Hilt module**

In `app/src/main/java/com/dokunmatikekosistem/app/di/AppModule.kt`, add a second `@Binds` function to the `abstract class AppModule`:

```kotlin
    @Binds
    @Singleton
    abstract fun bindHaptics(impl: com.dokunmatikekosistem.app.data.haptics.HapticManager): com.dokunmatikekosistem.app.domain.Haptics
```

- [ ] **Step 4: Sync and build in Android Studio (user-performed)**

Sync Project with Gradle Files, Build → Make Project.
Expected: build succeeds — no new unit tests in this task (Hilt DI wiring is verified by successful compilation and by `MainViewModel` successfully resolving `Haptics` in Task 7).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/domain/Haptics.kt app/src/main/java/com/dokunmatikekosistem/app/data/haptics app/src/main/java/com/dokunmatikekosistem/app/di/AppModule.kt
git commit -m "feat: add haptic feedback (Haptics interface + HapticManager)"
```

---

### Task 7: MainViewModel — Gesture, Keyboard, and Layout Handling

**Files:**
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt`
- Modify: `app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt`

**Interfaces:**
- Consumes: `RecognizedGesture` (Task 4), `KeyboardLayout`/`HidKeyChord` (Tasks 2-3), `Haptics` (Task 6), `HidManager`'s extended methods (Task 5).
- Produces (consumed by Task 8's `MainActivity`/`TouchpadScreen`):
  - `MainViewModel` constructor grows to `@Inject constructor(private val hidManager: HidManager, private val haptics: Haptics)`.
  - `fun onGesture(gesture: RecognizedGesture)` — replaces the old `onDrag`/`onTap` as the single entry point from the touch area (Task 8 wires `GestureRecognizer` output here).
  - `val activeLayout: StateFlow<KeyboardLayout>` — starts as `TurkishQLayout()`.
  - `fun onLayoutToggleClicked()` — switches between `TurkishQLayout()` and `EnglishUsLayout()`.
  - `fun onKeyTyped(char: Char)` — looks up the chord in `activeLayout.value`, sends key-down then key-up via `hidManager`; no-ops if `mapChar` returns null.

- [ ] **Step 1: Write the failing tests**

Replace the existing `onDrag sends...`/`onTap sends...` tests in `MainViewModelTest.kt` (their behavior moves into `onGesture` now) and add new ones. The full updated test file:

```kotlin
// app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt
package com.dokunmatikekosistem.app.presentation

import com.dokunmatikekosistem.app.data.gesture.RecognizedGesture
import com.dokunmatikekosistem.app.data.keyboard.EnglishUsLayout
import com.dokunmatikekosistem.app.data.keyboard.TurkishQLayout
import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.Haptics
import com.dokunmatikekosistem.app.domain.HidManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHidManager : HidManager {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val reportsSent = MutableStateFlow(0)
    var registerCalled = false
    val allReports = mutableListOf<Triple<Int, Int, Boolean>>()
    val allKeyPresses = mutableListOf<Pair<Int, Int>>()
    var releaseKeyboardCalled = false

    override fun register() {
        registerCalled = true
    }

    override fun sendMouseReport(dx: Int, dy: Int, wheelDelta: Int, panDelta: Int, leftButtonPressed: Boolean, rightButtonPressed: Boolean) {
        allReports.add(Triple(dx, dy, leftButtonPressed))
        reportsSent.value = reportsSent.value + 1
    }

    override fun sendKeyboardReport(modifierBits: Int, usageCode: Int) {
        allKeyPresses.add(modifierBits to usageCode)
        reportsSent.value = reportsSent.value + 1
    }

    override fun releaseKeyboardReport() {
        releaseKeyboardCalled = true
    }
}

private class FakeHaptics : Haptics {
    var clickCount = 0
    var dragLockEngagedCount = 0
    override fun click() { clickCount++ }
    override fun dragLockEngaged() { dragLockEngagedCount++ }
}

class MainViewModelTest {

    @Test
    fun `onConnectClicked delegates to hid manager register`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onConnectClicked()

        assertEquals(true, fake.registerCalled)
    }

    @Test
    fun `connectionState exposes the hid manager state`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        fake.connectionState.value = ConnectionState.CONNECTED

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState.value)
    }

    @Test
    fun `onGesture CursorMove sends a mouse report with no buttons`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onGesture(RecognizedGesture.CursorMove(dx = 5, dy = -3))

        assertEquals(listOf(Triple(5, -3, false)), fake.allReports)
    }

    @Test
    fun `onGesture LeftClick sends press then release and triggers haptic click`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.onGesture(RecognizedGesture.LeftClick)

        assertEquals(listOf(Triple(0, 0, true), Triple(0, 0, false)), fake.allReports)
        assertEquals(1, haptics.clickCount)
    }

    @Test
    fun `onGesture RightClick triggers haptic click`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.onGesture(RecognizedGesture.RightClick)

        assertEquals(1, haptics.clickCount)
    }

    @Test
    fun `onGesture DragLockEngaged triggers haptic dragLockEngaged`() {
        val fake = FakeHidManager()
        val haptics = FakeHaptics()
        val viewModel = MainViewModel(fake, haptics)

        viewModel.onGesture(RecognizedGesture.DragLockEngaged)

        assertEquals(1, haptics.dragLockEngagedCount)
    }

    @Test
    fun `activeLayout starts as Turkish Q`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        assertTrue(viewModel.activeLayout.value is TurkishQLayout)
    }

    @Test
    fun `onLayoutToggleClicked switches from Turkish Q to English US and back`() {
        val viewModel = MainViewModel(FakeHidManager(), FakeHaptics())

        viewModel.onLayoutToggleClicked()
        assertTrue(viewModel.activeLayout.value is EnglishUsLayout)

        viewModel.onLayoutToggleClicked()
        assertTrue(viewModel.activeLayout.value is TurkishQLayout)
    }

    @Test
    fun `onKeyTyped sends the mapped chord as key-down then key-up`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onKeyTyped('a')

        assertEquals(listOf(0 to 0x04), fake.allKeyPresses)
        assertEquals(true, fake.releaseKeyboardCalled)
    }

    @Test
    fun `onKeyTyped with unmapped character is a no-op`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake, FakeHaptics())

        viewModel.onKeyTyped('#')

        assertEquals(emptyList<Pair<Int, Int>>(), fake.allKeyPresses)
        assertEquals(false, fake.releaseKeyboardCalled)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

In Android Studio: run `MainViewModelTest.kt`.
Expected: FAIL — `MainViewModel` constructor/`onGesture`/`activeLayout`/`onLayoutToggleClicked`/`onKeyTyped` unresolved or mismatched.

- [ ] **Step 3: Rewrite `MainViewModel`**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt
package com.dokunmatikekosistem.app.presentation

import androidx.lifecycle.ViewModel
import com.dokunmatikekosistem.app.data.gesture.RecognizedGesture
import com.dokunmatikekosistem.app.data.keyboard.EnglishUsLayout
import com.dokunmatikekosistem.app.data.keyboard.TurkishQLayout
import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.Haptics
import com.dokunmatikekosistem.app.domain.HidManager
import com.dokunmatikekosistem.app.domain.KeyboardLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val hidManager: HidManager,
    private val haptics: Haptics
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = hidManager.connectionState
    val reportsSent: StateFlow<Int> = hidManager.reportsSent

    private val _activeLayout = MutableStateFlow<KeyboardLayout>(TurkishQLayout())
    val activeLayout: StateFlow<KeyboardLayout> = _activeLayout

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
        val chord = _activeLayout.value.mapChar(char) ?: return
        hidManager.sendKeyboardReport(chord.modifierBits, chord.usageCode)
        hidManager.releaseKeyboardReport()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

In Android Studio: run `MainViewModelTest.kt`.
Expected: PASS (10 tests green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt
git commit -m "feat: wire GestureRecognizer output, keyboard input, and haptics into MainViewModel"
```

---

### Task 8: MainActivity — Raw Multi-Touch Handling and Virtual Keyboard UI

**Files:**
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt`

**Interfaces:**
- Consumes: `MainViewModel.onGesture` (Task 7), `RawTouchEvent`/`GestureRecognizer` (Task 4), `MainViewModel.activeLayout`/`onLayoutToggleClicked`/`onKeyTyped` (Task 7).
- Produces: the Faz 2 UI. No further tasks in this plan consume this file.

- [ ] **Step 1: Replace the touch-area `pointerInput` blocks and add the keyboard panel**

Replace the `Box` touch area's two `pointerInput` modifiers (the old `detectDragGestures`/`detectTapGestures` pair) with raw multi-touch processing, and add a keyboard toggle button + panel below it. Full replacement of `TouchpadScreen` and its supporting code in `MainActivity.kt`:

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt
// (keep the existing imports for Manifest, BluetoothAdapter, Intent, PackageManager, Build, Bundle,
//  ComponentActivity, setContent, ActivityResultContracts, viewModels, layout/material3/runtime/ui
//  imports, ContextCompat, ConnectionState, AndroidEntryPoint, DISCOVERABLE_DURATION_SECONDS constant,
//  and the MainActivity class body exactly as Faz 1 left them — only TouchpadScreen and its imports change)

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventType
import com.dokunmatikekosistem.app.data.gesture.GestureRecognizer
import com.dokunmatikekosistem.app.data.gesture.RawTouchEvent

@Composable
fun TouchpadScreen(viewModel: MainViewModel, onConnectRequested: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()
    val reportsSent by viewModel.reportsSent.collectAsState()
    val activeLayout by viewModel.activeLayout.collectAsState()
    var keyboardVisible by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Durum: ${connectionState.label()}")
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
                    .fillMaxSize()
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
                VirtualKeyboard(onKeyTyped = { viewModel.onKeyTyped(it) })
            }
        }
    }
}

@Composable
private fun VirtualKeyboard(onKeyTyped: (Char) -> Unit) {
    val rows = listOf(
        "1234567890",
        "qwertyuıop",
        "asdfghjklş",
        "zxcvbnmöç"
    )
    Column(modifier = Modifier.padding(top = 8.dp)) {
        for (row in rows) {
            Row {
                for (char in row) {
                    Button(onClick = { onKeyTyped(char) }) { Text(char.toString()) }
                }
            }
        }
        Row {
            Button(onClick = { onKeyTyped(' ') }) { Text("Boşluk") }
            Button(onClick = { onKeyTyped('\n') }) { Text("Enter") }
            Button(onClick = { onKeyTyped('\b') }) { Text("Sil") }
        }
    }
}

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.DISCONNECTED -> "Bağlı değil"
    ConnectionState.REGISTERING -> "Eşleştiriliyor"
    ConnectionState.REGISTERED -> "Kayıtlı, bağlantı bekleniyor"
    ConnectionState.CONNECTED -> "Bağlı"
    ConnectionState.ERROR -> "Hata"
}
```

Keep the `MainActivity` class itself (the `@AndroidEntryPoint` class with `by viewModels()`, the discoverable/permission flow) exactly as Faz 1 left it — this task only replaces `TouchpadScreen`, adds `VirtualKeyboard`, and adds the new imports listed at the top of this step.

- [ ] **Step 2: Build and run on the Samsung device (user-performed)**

Sync Project with Gradle Files, Build → Make Project, Run on the paired Samsung phone, tap "Eşleştir/Bağlan" and confirm reconnection to Windows.

Manually verify against the plan's Faz 2 exit criterion:
- Two-finger scroll moves content up/down and left/right in a Windows app (e.g. a browser or File Explorer).
- Two-finger tap performs a right-click (e.g. opens a context menu).
- Tap "Klavye", type each Turkish letter (ğ, ü, ş, ı, ö, ç, i/İ via Shift) and confirm the correct character appears in a Windows text field — this is the exit criterion.
- Toggle "TR Q" / "EN US" and confirm English layout still produces correct plain ASCII letters.
- Tap once, then quickly tap-and-hold with a second finger and drag — confirm this drags a window/icon in Windows (drag lock engaged) and releases cleanly on lift.
- Confirm a short vibration on left-click, right-click, and drag-lock engagement.

Record findings honestly — if the two-finger tap/scroll disambiguation or drag-lock timing feels wrong on real hardware, note the exact symptom (not a code fix) for now; threshold tuning based on real touchscreen behavior is expected follow-up work, not a blocker for this task's commit.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt
git commit -m "feat: raw multi-touch gesture handling and virtual keyboard UI"
```
