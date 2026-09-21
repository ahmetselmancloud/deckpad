# Faz 0: Fizibilite & Prototip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove that a Samsung Android phone can register as a Bluetooth HID mouse+keyboard device, that Windows recognizes and connects to it, and that finger-drag on the phone screen moves the Windows cursor with acceptable latency — via a minimal, throwaway spike (not the final architecture).

**Architecture:** Single-module Android app (Kotlin + Jetpack Compose), no DI framework, no Clean Architecture layers. `MainActivity` hosts one Compose screen; a plain `BluetoothHidManager` class owns HID registration/connection/report-sending; `MainViewModel` bridges UI events to the manager and exposes state via `StateFlow`. Report bytes are built by a pure, unit-testable `HidMouseReport` object.

**Tech Stack:** Kotlin, Jetpack Compose, Android `BluetoothHidDevice` API (minSdk 28), JUnit4 for pure-Kotlin unit tests.

## Global Constraints

- Package name: `com.dokunmatikekosistem.app` (per approved design doc).
- `minSdkVersion` = 28 (Android 9) — required by `BluetoothHidDevice` API (spec Bölüm 3.1).
- No Hilt, no DataStore, no Clean Architecture layers in this phase — spike code, will be rewritten in Faz 1 (per approved design decision).
- No pointer-acceleration math applied on the phone side — raw `MotionEvent` deltas are sent as-is (spec Bölüm 5: avoid double cursor acceleration).
- Scope excludes: scroll, zoom, multi-finger gestures, keyboard character input, settings screen, security/idle-disconnect, multi-device memory.
- This agent's shell has **no Android SDK and only JDK 8** — it cannot run `gradlew build`/`gradlew test`. Every build/run/test-execution step in this plan must be performed by the user in Android Studio. Tasks that produce pure-Kotlin unit tests still include the test code and run instructions, but "run and confirm" is a manual, user-performed checkpoint, not something the agent verifies itself.

---

## File Structure

- `app/src/main/AndroidManifest.xml` — Bluetooth permissions, single activity declaration. (created by Android Studio project wizard in Task 1, edited in Task 3)
- `app/src/main/java/com/dokunmatikekosistem/app/MainActivity.kt` — Compose host activity. (created by wizard, replaced in Task 5)
- `app/src/main/java/com/dokunmatikekosistem/app/hid/HidDescriptor.kt` — composite mouse+keyboard HID report descriptor bytes. (Task 2)
- `app/src/main/java/com/dokunmatikekosistem/app/hid/HidMouseReport.kt` — pure function building the 3-byte mouse report. (Task 2)
- `app/src/test/java/com/dokunmatikekosistem/app/hid/HidMouseReportTest.kt` — unit tests for report byte-building. (Task 2)
- `app/src/main/java/com/dokunmatikekosistem/app/bluetooth/BluetoothHidManager.kt` — HID registration, connection state, report sending. (Task 3)
- `app/src/main/java/com/dokunmatikekosistem/app/MainViewModel.kt` — UI state holder, bridges gestures to `BluetoothHidManager`. (Task 4)
- `app/src/test/java/com/dokunmatikekosistem/app/MainViewModelTest.kt` — unit tests using a fake HID manager. (Task 4)
- `app/src/main/java/com/dokunmatikekosistem/app/MainActivity.kt` — final Compose UI (status, connect button, touch area, counter). (Task 5)
- `docs/superpowers/plans/2026-09-21-faz0-test-sonuclari.md` — manual test findings on Samsung device + Windows. (Task 6)

---

### Task 1: Android Studio Project Scaffold

**Files:**
- Create (via Android Studio wizard, then commit as-is): `app/` module, `build.gradle.kts` (root + app), `settings.gradle.kts`, `gradle/wrapper/*`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/dokunmatikekosistem/app/MainActivity.kt`, `app/src/main/res/**`
- Modify: none

**Interfaces:**
- Consumes: nothing (first task)
- Produces: a buildable, runnable Android project that later tasks add files into. Package root: `com.dokunmatikekosistem.app`.

This task is performed by the user in Android Studio (this agent has no Android SDK to generate/verify a Gradle project). Steps:

- [ ] **Step 1: Create the project in Android Studio**

In Android Studio: File → New → New Project → "Empty Activity" (Compose) template.
- Name: `TouchpadEkosistem`
- Package name: `com.dokunmatikekosistem.app`
- Save location: `D:\claude code\touchpad-ekosistem`
- Minimum SDK: API 28 ("Android 9.0 Pie")
- Language: Kotlin, Build configuration language: Kotlin DSL (`build.gradle.kts`)

- [ ] **Step 2: Let Gradle sync finish, then run the default template on the Samsung device**

Connect the Samsung phone via USB with USB debugging enabled, select it as the run target, click Run. Confirm the default "Hello Android" Compose screen appears on the phone.

Expected: app installs and launches without errors.

- [ ] **Step 3: Add a `.gitignore` for Android if the wizard didn't create one covering `/build`, `/.gradle`, `/local.properties`, `/.idea`**

Verify `.gitignore` at project root excludes at least: `*.iml`, `.gradle`, `/local.properties`, `/.idea`, `.DS_Store`, `/build`, `/captures`, `.externalNativeBuild`, `.cxx`, `local.properties`.

- [ ] **Step 4: Commit the scaffold**

```bash
git add -A
git commit -m "chore: scaffold Android Studio Compose project (com.dokunmatikekosistem.app)"
```

---

### Task 2: HID Descriptor and Mouse Report Builder

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/hid/HidDescriptor.kt`
- Create: `app/src/main/java/com/dokunmatikekosistem/app/hid/HidMouseReport.kt`
- Test: `app/src/test/java/com/dokunmatikekosistem/app/hid/HidMouseReportTest.kt`

**Interfaces:**
- Consumes: nothing (pure Kotlin, no Android framework dependency)
- Produces:
  - `HidDescriptor.DESCRIPTOR: ByteArray` — composite report descriptor (Report ID 1 = mouse, Report ID 2 = keyboard boot format), consumed by `BluetoothHidManager` in Task 3.
  - `HidDescriptor.MOUSE_REPORT_ID: Byte = 1`
  - `HidMouseReport.build(dx: Int, dy: Int, leftButtonPressed: Boolean): ByteArray` — 3-byte report `[buttons, dx, dy]`, dx/dy clamped to `-127..127`, consumed by `BluetoothHidManager.sendMouseReport` in Task 3.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/dokunmatikekosistem/app/hid/HidMouseReportTest.kt
package com.dokunmatikekosistem.app.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HidMouseReportTest {

    @Test
    fun `no movement no button produces zeroed report`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, leftButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 0, 0), report)
    }

    @Test
    fun `left button pressed sets bit 0 of buttons byte`() {
        val report = HidMouseReport.build(dx = 0, dy = 0, leftButtonPressed = true)
        assertArrayEquals(byteArrayOf(1, 0, 0), report)
    }

    @Test
    fun `positive dx dy pass through unchanged`() {
        val report = HidMouseReport.build(dx = 10, dy = 20, leftButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 10, 20), report)
    }

    @Test
    fun `negative dx dy encode as signed bytes`() {
        val report = HidMouseReport.build(dx = -10, dy = -20, leftButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, (-10).toByte(), (-20).toByte()), report)
    }

    @Test
    fun `dx dy beyond range clamp to -127 and 127`() {
        val report = HidMouseReport.build(dx = 500, dy = -500, leftButtonPressed = false)
        assertArrayEquals(byteArrayOf(0, 127, (-127).toByte()), report)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

In Android Studio: right-click `HidMouseReportTest.kt` → Run. (Agent cannot run this — no Android SDK/JDK 17 in this shell.)
Expected: FAIL — `HidMouseReport` is unresolved.

- [ ] **Step 3: Write the descriptor and report builder**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/hid/HidDescriptor.kt
package com.dokunmatikekosistem.app.hid

/**
 * Composite HID report descriptor: Report ID 1 = relative mouse (buttons + X/Y),
 * Report ID 2 = standard boot keyboard (modifier byte + 6-key array).
 * Faz 0 only sends mouse reports; the keyboard section exists so Windows is
 * tested against the same composite descriptor the final product will use.
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
        0xC0.toByte(),          // End Collection (Physical)
        0xC0.toByte(),          // End Collection (Application)

        // Keyboard (Report ID 2) - boot format, not populated with real data in Faz 0
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

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/hid/HidMouseReport.kt
package com.dokunmatikekosistem.app.hid

/** Builds the 3-byte relative-mouse HID report: [buttons, dx, dy]. */
object HidMouseReport {
    fun build(dx: Int, dy: Int, leftButtonPressed: Boolean): ByteArray {
        val clampedDx = dx.coerceIn(-127, 127).toByte()
        val clampedDy = dy.coerceIn(-127, 127).toByte()
        val buttons: Byte = if (leftButtonPressed) 1 else 0
        return byteArrayOf(buttons, clampedDx, clampedDy)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

In Android Studio: right-click `HidMouseReportTest.kt` → Run.
Expected: PASS (5 tests green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/hid app/src/test/java/com/dokunmatikekosistem/app/hid
git commit -m "feat: add composite HID descriptor and mouse report builder"
```

---

### Task 3: BluetoothHidManager

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/bluetooth/BluetoothHidManager.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `HidDescriptor.DESCRIPTOR`, `HidDescriptor.MOUSE_REPORT_ID`, `HidMouseReport.build(dx, dy, leftButtonPressed)` (Task 2).
- Produces (consumed by `MainViewModel` in Task 4):
  - `enum class ConnectionState { DISCONNECTED, REGISTERING, CONNECTED, ERROR }`
  - `class BluetoothHidManager(context: Context)`
  - `BluetoothHidManager.connectionState: StateFlow<ConnectionState>`
  - `BluetoothHidManager.reportsSent: StateFlow<Int>`
  - `fun BluetoothHidManager.register()` — registers the HID app with `HidDescriptor.DESCRIPTOR`, triggers system device-picker flow on registration success.
  - `fun BluetoothHidManager.sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean)` — builds a report via `HidMouseReport.build` and sends it to the connected host.

This task requires the Android `BluetoothHidDevice` framework API, which cannot be unit-tested without a device — no automated test step here. Manual verification happens in Task 6.

- [ ] **Step 1: Add Bluetooth permissions to the manifest**

```xml
<!-- app/src/main/AndroidManifest.xml, inside <manifest>, before <application> -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
```

- [ ] **Step 2: Write `BluetoothHidManager`**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/bluetooth/BluetoothHidManager.kt
package com.dokunmatikekosistem.app.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.dokunmatikekosistem.app.hid.HidDescriptor
import com.dokunmatikekosistem.app.hid.HidMouseReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor

enum class ConnectionState { DISCONNECTED, REGISTERING, CONNECTED, ERROR }

class BluetoothHidManager(private val context: Context) {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _reportsSent = MutableStateFlow(0)
    val reportsSent: StateFlow<Int> = _reportsSent

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "TouchpadEkosistem",
        "Dokunmatik Ekosistem Faz 0 Prototip",
        "DokunmatikEkosistem",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        HidDescriptor.DESCRIPTOR
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (!registered) {
                _connectionState.value = ConnectionState.ERROR
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            _connectionState.value = when (state) {
                BluetoothProfile.STATE_CONNECTED -> ConnectionState.CONNECTED
                BluetoothProfile.STATE_CONNECTING -> ConnectionState.REGISTERING
                else -> ConnectionState.DISCONNECTED
            }
        }
    }

    fun register() {
        _connectionState.value = ConnectionState.REGISTERING
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val executor = Executor { command -> command.run() }
        manager.adapter.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    hidDevice = proxy as BluetoothHidDevice
                    hidDevice?.registerApp(sdpSettings, null, null, executor, callback)
                }

                override fun onServiceDisconnected(profile: Int) {
                    hidDevice = null
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            },
            BluetoothProfile.HID_DEVICE
        )
    }

    fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean) {
        val device = connectedDevice ?: return
        val report = HidMouseReport.build(dx, dy, leftButtonPressed)
        val sent = hidDevice?.sendReport(device, HidDescriptor.MOUSE_REPORT_ID.toInt(), report)
        if (sent == true) {
            _reportsSent.value = _reportsSent.value + 1
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/bluetooth app/src/main/AndroidManifest.xml
git commit -m "feat: add BluetoothHidManager for HID registration and mouse reports"
```

---

### Task 4: MainViewModel

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/MainViewModel.kt`
- Test: `app/src/test/java/com/dokunmatikekosistem/app/MainViewModelTest.kt`

**Interfaces:**
- Consumes: `ConnectionState`, `BluetoothHidManager.connectionState: StateFlow<ConnectionState>`, `BluetoothHidManager.reportsSent: StateFlow<Int>`, `BluetoothHidManager.register()`, `BluetoothHidManager.sendMouseReport(dx, dy, leftButtonPressed)` (Task 3). To keep this testable without Android framework classes, `MainViewModel` depends on a small interface, not the concrete class directly.
- Produces (consumed by `MainActivity` in Task 5):
  - `interface HidManager { val connectionState: StateFlow<ConnectionState>; val reportsSent: StateFlow<Int>; fun register(); fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean) }` (implemented by `BluetoothHidManager`)
  - `class MainViewModel(private val hidManager: HidManager) : ViewModel()`
  - `MainViewModel.connectionState: StateFlow<ConnectionState>`
  - `MainViewModel.reportsSent: StateFlow<Int>`
  - `fun MainViewModel.onConnectClicked()`
  - `fun MainViewModel.onDrag(dx: Int, dy: Int)`
  - `fun MainViewModel.onTap()`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/dokunmatikekosistem/app/MainViewModelTest.kt
package com.dokunmatikekosistem.app

import com.dokunmatikekosistem.app.bluetooth.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeHidManager : HidManager {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val reportsSent = MutableStateFlow(0)
    var registerCalled = false
    var lastDrag: Pair<Int, Int>? = null
    var lastButton: Boolean? = null

    override fun register() {
        registerCalled = true
    }

    override fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean) {
        lastDrag = dx to dy
        lastButton = leftButtonPressed
        reportsSent.value = reportsSent.value + 1
    }
}

class MainViewModelTest {

    @Test
    fun `onConnectClicked delegates to hid manager register`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        viewModel.onConnectClicked()

        assertEquals(true, fake.registerCalled)
    }

    @Test
    fun `onDrag sends a mouse report with no button pressed`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        viewModel.onDrag(dx = 5, dy = -3)

        assertEquals(5 to -3, fake.lastDrag)
        assertEquals(false, fake.lastButton)
    }

    @Test
    fun `onTap sends a zero-delta report with left button pressed`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        viewModel.onTap()

        assertEquals(0 to 0, fake.lastDrag)
        assertEquals(true, fake.lastButton)
    }

    @Test
    fun `connectionState exposes the hid manager state`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        fake.connectionState.value = ConnectionState.CONNECTED

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState.value)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

In Android Studio: right-click `MainViewModelTest.kt` → Run.
Expected: FAIL — `HidManager` and `MainViewModel` are unresolved.

- [ ] **Step 3: Write `HidManager` interface and `MainViewModel`**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/MainViewModel.kt
package com.dokunmatikekosistem.app

import androidx.lifecycle.ViewModel
import com.dokunmatikekosistem.app.bluetooth.ConnectionState
import kotlinx.coroutines.flow.StateFlow

interface HidManager {
    val connectionState: StateFlow<ConnectionState>
    val reportsSent: StateFlow<Int>
    fun register()
    fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean)
}

class MainViewModel(private val hidManager: HidManager) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = hidManager.connectionState
    val reportsSent: StateFlow<Int> = hidManager.reportsSent

    fun onConnectClicked() {
        hidManager.register()
    }

    fun onDrag(dx: Int, dy: Int) {
        hidManager.sendMouseReport(dx, dy, leftButtonPressed = false)
    }

    fun onTap() {
        hidManager.sendMouseReport(dx = 0, dy = 0, leftButtonPressed = true)
    }
}
```

Then make `BluetoothHidManager` (Task 3) implement `HidManager`:

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/bluetooth/BluetoothHidManager.kt
// change the class declaration line to:
class BluetoothHidManager(private val context: Context) : com.dokunmatikekosistem.app.HidManager {
```

- [ ] **Step 4: Run tests to verify they pass**

In Android Studio: right-click `MainViewModelTest.kt` → Run.
Expected: PASS (4 tests green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/MainViewModel.kt app/src/test/java/com/dokunmatikekosistem/app/MainViewModelTest.kt app/src/main/java/com/dokunmatikekosistem/app/bluetooth/BluetoothHidManager.kt
git commit -m "feat: add MainViewModel bridging touch events to HidManager"
```

---

### Task 5: MainActivity UI

**Files:**
- Modify: `app/src/main/java/com/dokunmatikekosistem/app/MainActivity.kt` (replace wizard-generated content)

**Interfaces:**
- Consumes: `MainViewModel` (Task 4), `BluetoothHidManager` (Task 3), `ConnectionState` (Task 3).
- Produces: the Faz 0 UI screen. No further tasks in this plan consume this file.

- [ ] **Step 1: Replace `MainActivity.kt` with the Faz 0 screen**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/MainActivity.kt
package com.dokunmatikekosistem.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dokunmatikekosistem.app.bluetooth.BluetoothHidManager
import com.dokunmatikekosistem.app.bluetooth.ConnectionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hidManager = BluetoothHidManager(applicationContext)
        val viewModel = MainViewModel(hidManager)
        setContent {
            MaterialTheme {
                TouchpadScreen(viewModel)
            }
        }
    }
}

@Composable
fun TouchpadScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val reportsSent by viewModel.reportsSent.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Durum: ${connectionState.label()}")
            Text("Gönderilen rapor: $reportsSent")
            Button(onClick = { viewModel.onConnectClicked() }) {
                Text("Eşleştir/Bağlan")
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
                    .background(Color.DarkGray)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            viewModel.onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { viewModel.onTap() }
                    }
            )
        }
    }
}

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.DISCONNECTED -> "Bağlı değil"
    ConnectionState.REGISTERING -> "Eşleştiriliyor"
    ConnectionState.CONNECTED -> "Bağlı"
    ConnectionState.ERROR -> "Hata"
}
```

- [ ] **Step 2: Run on the Samsung device**

In Android Studio, run the app on the connected Samsung phone.
Expected: screen shows "Durum: Bağlı değil", "Gönderilen rapor: 0", an "Eşleştir/Bağlan" button, and a dark touch area below it.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/MainActivity.kt
git commit -m "feat: add Faz 0 touchpad screen (status, connect button, touch area, counter)"
```

---

### Task 6: Manual End-to-End Verification

**Files:**
- Create: `docs/superpowers/plans/2026-09-21-faz0-test-sonuclari.md`

**Interfaces:**
- Consumes: the running app from Task 5.
- Produces: recorded findings that determine whether Faz 0's exit criterion is met (per design doc's Test Planı section).

- [ ] **Step 1: Pair and connect**

On the Samsung phone, tap "Eşleştir/Bağlan". On Windows, open Bluetooth settings, look for the device, pair it. Confirm in Windows Bluetooth settings whether it appears as a mouse/keyboard device.

- [ ] **Step 2: Test cursor movement and click**

Drag a finger across the phone's touch area; confirm whether the Windows cursor moves. Tap once; confirm whether it registers as a left click in Windows.

- [ ] **Step 3: Test connection stability**

Leave the app open and connected for at least 5 minutes, periodically dragging. Note any disconnects.

- [ ] **Step 4: Note latency impression and report counter behavior**

Observe whether cursor movement feels immediate or noticeably delayed. Confirm the on-screen "Gönderilen rapor" counter increments as expected during drags/taps.

- [ ] **Step 5: Record results**

```markdown
<!-- docs/superpowers/plans/2026-09-21-faz0-test-sonuclari.md -->
# Faz 0 Test Sonuçları

**Test cihazı:** [Samsung model, Android sürümü]
**Bilgisayar:** [Windows 10/11, sürüm]

## Sonuçlar

- HID kaydı başarılı mı: [evet/hayır + gözlem]
- Windows'ta mouse+keyboard olarak görünüyor mu: [evet/hayır]
- İmleç hareketi çalışıyor mu: [evet/hayır + gözlem]
- Sol tık çalışıyor mu: [evet/hayır]
- Bağlantı stabilitesi (5+ dakika): [stabil/koptu, kaç kez]
- Gecikme izlenimi: [anlık/hafif gecikmeli/belirgin gecikmeli]

## Değerlendirme

[Faz 0 çıkış kriteri karşılandı mı: evet/hayır. Karşılanmadıysa hangi risk gerçekleşti (spec Bölüm 15) ve Faz 1'e geçmeden önce ne yapılmalı.]
```

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/plans/2026-09-21-faz0-test-sonuclari.md
git commit -m "docs: record Faz 0 manual end-to-end test results"
```
