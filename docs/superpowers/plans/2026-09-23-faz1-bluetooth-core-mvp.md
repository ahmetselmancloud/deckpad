# Faz 1: Bluetooth Core & MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Faz 0's proven functionality (HID mouse+keyboard registration, Windows pairing, single-finger cursor movement, left click) on the project's real architecture — MVVM + Clean Architecture, Hilt, Coroutines/Flow, DataStore — replacing the Faz 0 spike code.

**Architecture:** Three packages — `data` (BluetoothHidManager, HID descriptor/report builder, DataStore wrapper), `domain` (ConnectionState, HidManager interface), `presentation` (Hilt-injected MainViewModel, Compose UI). Hilt provides DI; `BluetoothHidManager` becomes `@Singleton` so it survives Activity recreation; `MainViewModel` becomes `@HiltViewModel` obtained via `by viewModels()` so it survives configuration changes without the Faz 0 `android:configChanges` workaround.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt 2.51.1 (kapt), AndroidX DataStore Preferences 1.1.1, `BluetoothHidDevice` API (minSdk 28), JUnit4.

## Global Constraints

- Package root: `com.dokunmatikekosistem.app` (existing, unchanged).
- `minSdkVersion` = 28 (unchanged from Faz 0).
- No pointer-acceleration math on the phone side — raw deltas only (carried over from Faz 0, still applies).
- `register()` on `BluetoothHidManager` must be idempotent: calling it while already `REGISTERING`, `REGISTERED`, or `CONNECTED` must be a safe no-op, with the guard logic living in the manager itself, not in the ViewModel or Activity.
- GestureRecognizer and DeviceRepository are explicitly OUT OF SCOPE for this phase (YAGNI — their real responsibilities belong to Faz 2 and Faz 5 respectively). Do not create stub classes for them.
- `SettingsDataStore` is scaffolded in this phase but holds no real settings yet (Faz 5 fills it in) — it exists only so Hilt's DI graph and the DataStore dependency are wired and provable now, per the approved design.
- This agent's shell has no Android SDK and only JDK 8 — it cannot run `./gradlew build`/`test`. Every build/run/test-execution step in this plan is a manual, user-performed checkpoint in Android Studio; task steps that "run tests" mean tracing the assertions by hand against the implementation, exactly as done throughout Faz 0.

---

## File Structure

- `app/src/main/java/com/dokunmatikekosistem/app/DokunmatikEkosistemApplication.kt` — new, `@HiltAndroidApp` Application class. (Task 1)
- `app/build.gradle.kts`, `gradle/libs.versions.toml`, `build.gradle.kts` (root) — Hilt + kapt + DataStore dependencies. (Task 1)
- `app/src/main/AndroidManifest.xml` — register the new Application class; later (Task 6) update the activity's package path and drop `android:configChanges`.
- `app/src/main/java/com/dokunmatikekosistem/app/domain/ConnectionState.kt` — moved from `data/bluetooth` package, unchanged enum body plus `REGISTERED`. (Task 2)
- `app/src/main/java/com/dokunmatikekosistem/app/domain/HidManager.kt` — moved from `MainViewModel.kt`, unchanged interface body. (Task 2)
- `app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidDescriptor.kt`, `HidMouseReport.kt` — moved from `hid/` package, unchanged bodies, package renamed. (Task 3)
- `app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidMouseReportTest.kt` — moved, package renamed. (Task 3)
- `app/src/main/java/com/dokunmatikekosistem/app/data/bluetooth/BluetoothHidManager.kt` — moved + rewritten: `@Singleton`, `@Inject constructor(@ApplicationContext ...)`, idempotent `register()`. (Task 3)
- `app/src/test/java/com/dokunmatikekosistem/app/data/bluetooth/BluetoothHidManagerTest.kt` — new, tests the extracted idempotency-guard function. (Task 3)
- `app/src/main/java/com/dokunmatikekosistem/app/data/settings/SettingsDataStore.kt` — new, empty Preferences DataStore wrapper. (Task 4)
- `app/src/main/java/com/dokunmatikekosistem/app/di/AppModule.kt` — new, Hilt module binding `HidManager` → `BluetoothHidManager` and providing `DataStore<Preferences>`. (Task 4)
- `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt` — moved from root package, `@HiltViewModel`. (Task 5)
- `app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt` — moved, package renamed. (Task 5)
- `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt` — moved from root package, `@AndroidEntryPoint`, `by viewModels()`. (Task 6)
- Deleted at the end of Task 6: the old root-package `MainActivity.kt`, `MainViewModel.kt`, and the old `hid/`, `bluetooth/` package directories.

---

### Task 1: Hilt, kapt, and DataStore Dependencies + Application Class

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/dokunmatikekosistem/app/DokunmatikEkosistemApplication.kt`

**Interfaces:**
- Consumes: nothing (first task)
- Produces: a Hilt-ready project — the `com.google.dagger.hilt.android` plugin applied, `kapt` enabled, `DataStore` preferences dependency available, and `DokunmatikEkosistemApplication` registered as the app's `<application android:name>`. Later tasks add `@HiltViewModel`/`@AndroidEntryPoint`/`@Inject` annotations that require this.

- [ ] **Step 1: Add Hilt, kapt, and DataStore entries to the version catalog**

```toml
# gradle/libs.versions.toml — add to [versions]:
hilt = "2.51.1"
datastorePreferences = "1.1.1"
```

```toml
# gradle/libs.versions.toml — add to [libraries]:
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
```

```toml
# gradle/libs.versions.toml — add to [plugins]:
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 2: Apply the Hilt plugin at the root project level**

```kotlin
// build.gradle.kts (project root) — add inside the existing plugins { } block:
alias(libs.plugins.hilt.android) apply false
```

- [ ] **Step 3: Apply Hilt + kapt in the app module and add dependencies**

```kotlin
// app/build.gradle.kts — plugins block becomes:
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    id("kotlin-kapt")
}
```

```kotlin
// app/build.gradle.kts — add inside the dependencies { } block, alongside the existing entries:
implementation(libs.hilt.android)
kapt(libs.hilt.compiler)
implementation(libs.androidx.hilt.navigation.compose)
implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 4: Create the Hilt Application class**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/DokunmatikEkosistemApplication.kt
package com.dokunmatikekosistem.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DokunmatikEkosistemApplication : Application()
```

- [ ] **Step 5: Register the Application class in the manifest**

```xml
<!-- app/src/main/AndroidManifest.xml — add android:name to the <application> tag: -->
<application
    android:name=".DokunmatikEkosistemApplication"
    ...
```

(Keep every other existing attribute on `<application>` as-is; only add `android:name`.)

- [ ] **Step 6: Sync and build in Android Studio (user-performed)**

In Android Studio: Sync Project with Gradle Files, then Build → Make Project.
Expected: build succeeds with no errors. (If kapt/Hilt version mismatch errors appear, report the exact error text before continuing — do not guess at version bumps blindly.)

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/dokunmatikekosistem/app/DokunmatikEkosistemApplication.kt
git commit -m "chore: add Hilt, kapt, and DataStore dependencies; register Application class"
```

---

### Task 2: Domain Layer — ConnectionState and HidManager

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/domain/ConnectionState.kt`
- Create: `app/src/main/java/com/dokunmatikekosistem/app/domain/HidManager.kt`

**Interfaces:**
- Consumes: nothing new (pure Kotlin, no Android framework dependency)
- Produces (consumed by Task 3's `BluetoothHidManager` and Task 5's `MainViewModel`):
  - `com.dokunmatikekosistem.app.domain.ConnectionState` — `enum class ConnectionState { DISCONNECTED, REGISTERING, REGISTERED, CONNECTED, ERROR }`
  - `com.dokunmatikekosistem.app.domain.HidManager` — interface with `connectionState: StateFlow<ConnectionState>`, `reportsSent: StateFlow<Int>`, `fun register()`, `fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean)`

This task only relocates existing, already-tested declarations into the domain package — no new logic, no new tests (the existing `MainViewModelTest.kt`, moved in Task 5, continues to cover `HidManager`'s contract through `MainViewModel`).

- [ ] **Step 1: Create the domain ConnectionState**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/domain/ConnectionState.kt
package com.dokunmatikekosistem.app.domain

enum class ConnectionState { DISCONNECTED, REGISTERING, REGISTERED, CONNECTED, ERROR }
```

- [ ] **Step 2: Create the domain HidManager interface**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/domain/HidManager.kt
package com.dokunmatikekosistem.app.domain

import kotlinx.coroutines.flow.StateFlow

interface HidManager {
    val connectionState: StateFlow<ConnectionState>
    val reportsSent: StateFlow<Int>
    fun register()
    fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean)
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/domain
git commit -m "refactor: extract ConnectionState and HidManager into domain package"
```

(The old `com.dokunmatikekosistem.app.bluetooth.ConnectionState` enum and the old `com.dokunmatikekosistem.app.HidManager` interface are deleted in Task 3 and Task 5 respectively, once their consumers are updated to import from `domain` instead — deleting them now would break the still-unmigrated Faz 0 files and leave the project in a non-compiling state between tasks.)

---

### Task 3: Data Layer — HID Descriptor/Report Builder and BluetoothHidManager

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidDescriptor.kt`
- Create: `app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidMouseReport.kt`
- Create: `app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidMouseReportTest.kt`
- Create: `app/src/main/java/com/dokunmatikekosistem/app/data/bluetooth/BluetoothHidManager.kt`
- Create: `app/src/test/java/com/dokunmatikekosistem/app/data/bluetooth/BluetoothHidManagerTest.kt`
- Delete: `app/src/main/java/com/dokunmatikekosistem/app/hid/HidDescriptor.kt`, `HidMouseReport.kt`
- Delete: `app/src/test/java/com/dokunmatikekosistem/app/hid/HidMouseReportTest.kt`
- Delete: `app/src/main/java/com/dokunmatikekosistem/app/bluetooth/BluetoothHidManager.kt`

**Interfaces:**
- Consumes: `com.dokunmatikekosistem.app.domain.ConnectionState`, `com.dokunmatikekosistem.app.domain.HidManager` (Task 2).
- Produces (consumed by Task 4's `AppModule` and Task 5's `MainViewModel`):
  - `com.dokunmatikekosistem.app.data.hid.HidDescriptor.DESCRIPTOR: ByteArray`, `.MOUSE_REPORT_ID: Byte`
  - `com.dokunmatikekosistem.app.data.hid.HidMouseReport.build(dx: Int, dy: Int, leftButtonPressed: Boolean): ByteArray`
  - `com.dokunmatikekosistem.app.data.bluetooth.BluetoothHidManager` — `@Singleton class BluetoothHidManager @Inject constructor(@ApplicationContext context: Context) : HidManager`
  - `com.dokunmatikekosistem.app.data.bluetooth.shouldAttemptRegister(currentState: ConnectionState): Boolean` (internal, package-visible pure function — the idempotency guard, unit-testable without any Android framework class)

- [ ] **Step 1: Move HidDescriptor and HidMouseReport (package rename only, bodies unchanged)**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidDescriptor.kt
package com.dokunmatikekosistem.app.data.hid

/**
 * Composite HID report descriptor: Report ID 1 = relative mouse (buttons + X/Y),
 * Report ID 2 = standard boot keyboard (modifier byte + 6-key array).
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

        // Keyboard (Report ID 2) - boot format, not populated with real data yet
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
// app/src/main/java/com/dokunmatikekosistem/app/data/hid/HidMouseReport.kt
package com.dokunmatikekosistem.app.data.hid

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

```kotlin
// app/src/test/java/com/dokunmatikekosistem/app/data/hid/HidMouseReportTest.kt
package com.dokunmatikekosistem.app.data.hid

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

- [ ] **Step 2: Delete the old `hid/` package files**

Delete `app/src/main/java/com/dokunmatikekosistem/app/hid/HidDescriptor.kt`, `app/src/main/java/com/dokunmatikekosistem/app/hid/HidMouseReport.kt`, and `app/src/test/java/com/dokunmatikekosistem/app/hid/HidMouseReportTest.kt` (and the now-empty `hid/` directories, if your tooling leaves them behind).

- [ ] **Step 3: Write the failing idempotency-guard test**

```kotlin
// app/src/test/java/com/dokunmatikekosistem/app/data/bluetooth/BluetoothHidManagerTest.kt
package com.dokunmatikekosistem.app.data.bluetooth

import com.dokunmatikekosistem.app.domain.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothHidManagerTest {

    @Test
    fun `register is allowed from DISCONNECTED`() {
        assertEquals(true, shouldAttemptRegister(ConnectionState.DISCONNECTED))
    }

    @Test
    fun `register is allowed from ERROR`() {
        assertEquals(true, shouldAttemptRegister(ConnectionState.ERROR))
    }

    @Test
    fun `register is blocked while REGISTERING`() {
        assertEquals(false, shouldAttemptRegister(ConnectionState.REGISTERING))
    }

    @Test
    fun `register is blocked while REGISTERED`() {
        assertEquals(false, shouldAttemptRegister(ConnectionState.REGISTERED))
    }

    @Test
    fun `register is blocked while CONNECTED`() {
        assertEquals(false, shouldAttemptRegister(ConnectionState.CONNECTED))
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

In Android Studio: right-click `BluetoothHidManagerTest.kt` → Run.
Expected: FAIL — `shouldAttemptRegister` is unresolved.

- [ ] **Step 5: Write BluetoothHidManager with the idempotency guard, Hilt annotations, and Log/SecurityException handling carried over from Faz 0**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/data/bluetooth/BluetoothHidManager.kt
package com.dokunmatikekosistem.app.data.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.HidManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BluetoothHidManager"

/** Pure guard: register() may only start a new registration from these states. */
internal fun shouldAttemptRegister(currentState: ConnectionState): Boolean =
    currentState == ConnectionState.DISCONNECTED || currentState == ConnectionState.ERROR

@Singleton
class BluetoothHidManager @Inject constructor(
    @ApplicationContext private val context: Context
) : HidManager {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _reportsSent = MutableStateFlow(0)
    override val reportsSent: StateFlow<Int> = _reportsSent

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "TouchpadEkosistem",
        "Dokunmatik Ekosistem",
        "DokunmatikEkosistem",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        com.dokunmatikekosistem.app.data.hid.HidDescriptor.DESCRIPTOR
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered pluggedDevice=$pluggedDevice")
            _connectionState.value = if (registered) ConnectionState.REGISTERED else ConnectionState.ERROR
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: device=$device state=$state")
            connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            _connectionState.value = when (state) {
                BluetoothProfile.STATE_CONNECTED -> ConnectionState.CONNECTED
                BluetoothProfile.STATE_CONNECTING -> ConnectionState.REGISTERING
                else -> ConnectionState.DISCONNECTED
            }
        }
    }

    override fun register() {
        if (!shouldAttemptRegister(_connectionState.value)) {
            Log.d(TAG, "register() ignored, already ${_connectionState.value}")
            return
        }
        Log.d(TAG, "register() called")
        _connectionState.value = ConnectionState.REGISTERING
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val executor = Executor { command -> command.run() }
        try {
            val proxyRequested = manager.adapter.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        Log.d(TAG, "onServiceConnected: profile=$profile proxy=$proxy")
                        hidDevice = proxy as BluetoothHidDevice
                        try {
                            val registerRequested = hidDevice?.registerApp(sdpSettings, null, null, executor, callback)
                            Log.d(TAG, "registerApp() called, requested=$registerRequested")
                        } catch (e: SecurityException) {
                            Log.e(TAG, "registerApp() threw SecurityException", e)
                            _connectionState.value = ConnectionState.ERROR
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        Log.d(TAG, "onServiceDisconnected: profile=$profile")
                        hidDevice = null
                        connectedDevice = null
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                },
                BluetoothProfile.HID_DEVICE
            )
            Log.d(TAG, "getProfileProxy() called, requested=$proxyRequested")
        } catch (e: SecurityException) {
            Log.e(TAG, "getProfileProxy() threw SecurityException", e)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    override fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean) {
        val device = connectedDevice ?: return
        val report = com.dokunmatikekosistem.app.data.hid.HidMouseReport.build(dx, dy, leftButtonPressed)
        val sent = hidDevice?.sendReport(device, com.dokunmatikekosistem.app.data.hid.HidDescriptor.MOUSE_REPORT_ID.toInt(), report)
        if (sent == true) {
            _reportsSent.value = _reportsSent.value + 1
        }
    }
}
```

- [ ] **Step 6: Delete the old bluetooth package file**

Delete `app/src/main/java/com/dokunmatikekosistem/app/bluetooth/BluetoothHidManager.kt` (which also contained the old `com.dokunmatikekosistem.app.bluetooth.ConnectionState` enum — now superseded by `com.dokunmatikekosistem.app.domain.ConnectionState` from Task 2).

- [ ] **Step 7: Run tests to verify they pass**

In Android Studio: run `HidMouseReportTest.kt` (5 tests) and `BluetoothHidManagerTest.kt` (5 tests).
Expected: PASS (10 tests green).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/data app/src/test/java/com/dokunmatikekosistem/app/data
git rm -r app/src/main/java/com/dokunmatikekosistem/app/hid app/src/main/java/com/dokunmatikekosistem/app/bluetooth app/src/test/java/com/dokunmatikekosistem/app/hid
git commit -m "refactor: move HID descriptor/report + BluetoothHidManager into data layer, make register() idempotent"
```

---

### Task 4: SettingsDataStore Stub and Hilt AppModule

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/data/settings/SettingsDataStore.kt`
- Create: `app/src/main/java/com/dokunmatikekosistem/app/di/AppModule.kt`

**Interfaces:**
- Consumes: `com.dokunmatikekosistem.app.domain.HidManager` (Task 2), `com.dokunmatikekosistem.app.data.bluetooth.BluetoothHidManager` (Task 3).
- Produces (consumed by Task 5's `MainViewModel` via Hilt, and available for Faz 5 to extend): a Hilt-provided `DataStore<Preferences>` singleton, and the `HidManager` → `BluetoothHidManager` binding that makes `@Inject constructor(hidManager: HidManager)` resolvable anywhere in the app.

- [ ] **Step 1: Create the empty DataStore wrapper**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/data/settings/SettingsDataStore.kt
package com.dokunmatikekosistem.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Faz 1 scaffold: no settings are read or written yet.
 * Faz 5 (Ayarlar Ekranı) will add real keys and read/write functions here.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
```

- [ ] **Step 2: Create the Hilt module**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/di/AppModule.kt
package com.dokunmatikekosistem.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dokunmatikekosistem.app.data.bluetooth.BluetoothHidManager
import com.dokunmatikekosistem.app.data.settings.settingsDataStore
import com.dokunmatikekosistem.app.domain.HidManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindHidManager(impl: BluetoothHidManager): HidManager

    companion object {
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.settingsDataStore
    }
}
```

- [ ] **Step 3: Sync and build in Android Studio (user-performed)**

Sync Project with Gradle Files, Build → Make Project.
Expected: build succeeds — no compile errors (this task adds no new tests; its correctness is verified by the DI graph compiling and by `MainViewModel` successfully resolving `HidManager` in Task 5).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/data/settings app/src/main/java/com/dokunmatikekosistem/app/di
git commit -m "feat: add SettingsDataStore scaffold and Hilt AppModule"
```

---

### Task 5: Presentation Layer — MainViewModel

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt`
- Create: `app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt`
- Delete: `app/src/main/java/com/dokunmatikekosistem/app/MainViewModel.kt`
- Delete: `app/src/test/java/com/dokunmatikekosistem/app/MainViewModelTest.kt`

**Interfaces:**
- Consumes: `com.dokunmatikekosistem.app.domain.HidManager`, `com.dokunmatikekosistem.app.domain.ConnectionState` (Task 2); resolved via Hilt using Task 4's `AppModule` binding.
- Produces (consumed by Task 6's `MainActivity`):
  - `com.dokunmatikekosistem.app.presentation.MainViewModel` — `@HiltViewModel class MainViewModel @Inject constructor(private val hidManager: HidManager) : ViewModel()`
  - `MainViewModel.connectionState: StateFlow<ConnectionState>`, `.reportsSent: StateFlow<Int>`, `fun onConnectClicked()`, `fun onDrag(dx: Int, dy: Int)`, `fun onTap()`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt
package com.dokunmatikekosistem.app.presentation

import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.HidManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeHidManager : HidManager {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val reportsSent = MutableStateFlow(0)
    var registerCalled = false
    val allReports = mutableListOf<Triple<Int, Int, Boolean>>()

    override fun register() {
        registerCalled = true
    }

    override fun sendMouseReport(dx: Int, dy: Int, leftButtonPressed: Boolean) {
        allReports.add(Triple(dx, dy, leftButtonPressed))
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

        assertEquals(listOf(Triple(5, -3, false)), fake.allReports)
    }

    @Test
    fun `onTap sends a press followed by a release`() {
        val fake = FakeHidManager()
        val viewModel = MainViewModel(fake)

        viewModel.onTap()

        assertEquals(
            listOf(Triple(0, 0, true), Triple(0, 0, false)),
            fake.allReports
        )
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

- [ ] **Step 2: Run test to verify it fails**

In Android Studio: right-click `MainViewModelTest.kt` (in `presentation`) → Run.
Expected: FAIL — `MainViewModel` (in `presentation` package) is unresolved.

- [ ] **Step 3: Write the presentation MainViewModel**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt
package com.dokunmatikekosistem.app.presentation

import androidx.lifecycle.ViewModel
import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.HidManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val hidManager: HidManager
) : ViewModel() {

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
        hidManager.sendMouseReport(dx = 0, dy = 0, leftButtonPressed = false)
    }
}
```

- [ ] **Step 4: Delete the old root-package MainViewModel and its test**

Delete `app/src/main/java/com/dokunmatikekosistem/app/MainViewModel.kt` (this also removes the old `com.dokunmatikekosistem.app.HidManager` interface it contained — superseded by `com.dokunmatikekosistem.app.domain.HidManager` from Task 2) and `app/src/test/java/com/dokunmatikekosistem/app/MainViewModelTest.kt`.

- [ ] **Step 5: Run tests to verify they pass**

In Android Studio: run `MainViewModelTest.kt` (in `presentation`).
Expected: PASS (4 tests green).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/presentation/MainViewModel.kt app/src/test/java/com/dokunmatikekosistem/app/presentation/MainViewModelTest.kt
git rm app/src/main/java/com/dokunmatikekosistem/app/MainViewModel.kt app/src/test/java/com/dokunmatikekosistem/app/MainViewModelTest.kt
git commit -m "refactor: move MainViewModel to presentation package as @HiltViewModel, fix onTap press+release"
```

---

### Task 6: Presentation Layer — MainActivity and Manifest Cleanup

**Files:**
- Create: `app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt`
- Delete: `app/src/main/java/com/dokunmatikekosistem/app/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `com.dokunmatikekosistem.app.presentation.MainViewModel` (Task 5), `com.dokunmatikekosistem.app.domain.ConnectionState` (Task 2).
- Produces: the app's launcher screen. No further tasks in this plan consume this file.

- [ ] **Step 1: Write the presentation MainActivity**

```kotlin
// app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt
package com.dokunmatikekosistem.app.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.core.content.ContextCompat
import com.dokunmatikekosistem.app.domain.ConnectionState
import dagger.hilt.android.AndroidEntryPoint

private const val DISCOVERABLE_DURATION_SECONDS = 300

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestDiscoverable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Regardless of the result code (duration granted or cancelled), proceed:
        // registerApp() itself doesn't require discoverability, only pairing does.
        viewModel.onConnectClicked()
    }

    private val requestBluetoothConnect = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestDiscoverableAndConnect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TouchpadScreen(viewModel, onConnectRequested = ::connectWithPermissionCheck)
            }
        }
    }

    private fun connectWithPermissionCheck() {
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val alreadyGranted = !needsRuntimePermission ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            requestDiscoverableAndConnect()
        } else {
            requestBluetoothConnect.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun requestDiscoverableAndConnect() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION_SECONDS)
        }
        requestDiscoverable.launch(discoverableIntent)
    }
}

@Composable
fun TouchpadScreen(viewModel: MainViewModel, onConnectRequested: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()
    val reportsSent by viewModel.reportsSent.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Durum: ${connectionState.label()}")
            Text("Gönderilen rapor: $reportsSent")
            Button(onClick = onConnectRequested) {
                Text("Eşleştir/Bağlan")
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
                    .background(Color.DarkGray)
                    .pointerInput(Unit) {
                        var residualX = 0f
                        var residualY = 0f
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            residualX += dragAmount.x
                            residualY += dragAmount.y
                            val dx = residualX.toInt()
                            val dy = residualY.toInt()
                            residualX -= dx
                            residualY -= dy
                            if (dx != 0 || dy != 0) {
                                viewModel.onDrag(dx, dy)
                            }
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
    ConnectionState.REGISTERED -> "Kayıtlı, bağlantı bekleniyor"
    ConnectionState.CONNECTED -> "Bağlı"
    ConnectionState.ERROR -> "Hata"
}
```

- [ ] **Step 2: Delete the old root-package MainActivity**

Delete `app/src/main/java/com/dokunmatikekosistem/app/MainActivity.kt`.

- [ ] **Step 3: Update the manifest — activity path and drop the Faz 0 configChanges workaround**

```xml
<!-- app/src/main/AndroidManifest.xml — update the <activity> element: -->
<activity
    android:name=".presentation.MainActivity"
    android:exported="true"
    android:label="@string/app_name"
    android:theme="@style/Theme.TouchpadEkosistem">
    <!-- android:configChanges removed: MainViewModel is now a @HiltViewModel obtained via
         by viewModels(), and BluetoothHidManager is a Hilt @Singleton — both now survive
         configuration changes (e.g. rotation) without recreating the HID session, making
         the Faz 0 configChanges stopgap unnecessary. -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

(Keep whatever exact `android:theme`/other attributes your current manifest already has on this element — only change `android:name` to `.presentation.MainActivity` and remove the `android:configChanges` attribute and its preceding comment from Faz 0.)

- [ ] **Step 4: Build and run on the Samsung device (user-performed)**

Sync Project with Gradle Files, Build → Make Project, then Run on the paired Samsung phone.
Expected: app launches showing the same Faz 1 screen (status/counter/button/touch area) as Faz 0. Tap "Eşleştir/Bağlan", confirm the phone reconnects to the already-paired Windows PC (or re-pairs if the old pairing was removed), confirm cursor movement and left click still work, and confirm rotating the phone screen no longer resets the connection state or report counter (this is the Task 6 proof that the Hilt rewrite actually fixed the Faz 0 config-change bug).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dokunmatikekosistem/app/presentation/MainActivity.kt app/src/main/AndroidManifest.xml
git rm app/src/main/java/com/dokunmatikekosistem/app/MainActivity.kt
git commit -m "refactor: move MainActivity to presentation package as @AndroidEntryPoint, drop configChanges workaround"
```
