package com.dokunmatikekosistem.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dokunmatikekosistem.app.data.gesture.RecognizedGesture
import com.dokunmatikekosistem.app.data.gesture.SwipeDirection
import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import com.dokunmatikekosistem.app.data.keyboard.EnglishUsLayout
import com.dokunmatikekosistem.app.data.keyboard.TurkishQLayout
import com.dokunmatikekosistem.app.data.settings.SettingsRepository
import com.dokunmatikekosistem.app.data.settings.TapAction
import com.dokunmatikekosistem.app.data.settings.UserSettings
import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.domain.Haptics
import com.dokunmatikekosistem.app.domain.HidManager
import com.dokunmatikekosistem.app.domain.KeyboardLayout
import com.dokunmatikekosistem.app.domain.KeyboardModifierState
import com.dokunmatikekosistem.app.domain.ShiftState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SHIFT_DOUBLE_TAP_WINDOW_MS = 300L
private const val DELETE_FORWARD_USAGE_CODE = 0x4C
private const val TAB_USAGE_CODE = 0x2B
private const val D_USAGE_CODE = 0x07
private const val S_USAGE_CODE = 0x16
private const val N_USAGE_CODE = 0x11
private const val LEFT_ARROW_USAGE_CODE = 0x50
private const val RIGHT_ARROW_USAGE_CODE = 0x4F
private const val NO_KEY_USAGE_CODE = 0

@HiltViewModel
class MainViewModel @Inject constructor(
    private val hidManager: HidManager,
    private val haptics: Haptics,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = hidManager.connectionState
    val reportsSent: StateFlow<Int> = hidManager.reportsSent

    private val _userSettings = MutableStateFlow(UserSettings())
    val userSettings: StateFlow<UserSettings> = _userSettings

    private val _activeLayout = MutableStateFlow<KeyboardLayout>(EnglishUsLayout())
    val activeLayout: StateFlow<KeyboardLayout> = _activeLayout

    private val _modifierState = MutableStateFlow(KeyboardModifierState())
    val modifierState: StateFlow<KeyboardModifierState> = _modifierState

    private val _zoomEnabled = MutableStateFlow(true)
    val zoomEnabled: StateFlow<Boolean> = _zoomEnabled

    private val _currentTab = MutableStateFlow(AppTab.TOUCHPAD)
    val currentTab: StateFlow<AppTab> = _currentTab

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setFullscreen(enabled: Boolean) {
        _isFullscreen.value = enabled
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    private var lastShiftClickMillis = Long.MIN_VALUE
    private var cursorResidualX = 0f
    private var cursorResidualY = 0f
    private var scrollResidualV = 0f
    private var scrollResidualH = 0f

    init {
        settingsRepository?.let { repo ->
            viewModelScope.launch {
                repo.userSettingsFlow.collect { settings ->
                    _userSettings.value = settings
                    _zoomEnabled.value = settings.zoomEnabled
                    _activeLayout.value = if (settings.isTurkishLayout) TurkishQLayout() else EnglishUsLayout()
                }
            }
        }
    }

    fun onConnectClicked() {
        hidManager.register()
    }

    fun onGesture(gesture: RecognizedGesture) {
        when (gesture) {
            is RecognizedGesture.CursorMove -> {
                val speed = _userSettings.value.cursorSpeed
                if (speed == 1.0f) {
                    hidManager.sendMouseReport(gesture.dx, gesture.dy, wheelDelta = 0, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)
                } else {
                    val totalDx = gesture.dx * speed + cursorResidualX
                    val totalDy = gesture.dy * speed + cursorResidualY
                    val sendDx = kotlin.math.round(totalDx).toInt()
                    val sendDy = kotlin.math.round(totalDy).toInt()
                    cursorResidualX = totalDx - sendDx
                    cursorResidualY = totalDy - sendDy
                    if (sendDx != 0 || sendDy != 0) {
                        hidManager.sendMouseReport(sendDx, sendDy, wheelDelta = 0, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)
                    }
                }
            }

            RecognizedGesture.LeftClick -> {
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = true, rightButtonPressed = false)
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false)
                triggerClickHaptic()
            }

            RecognizedGesture.RightClick -> {
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = true)
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false)
                triggerClickHaptic()
            }

            is RecognizedGesture.Scroll -> {
                val speed = _userSettings.value.scrollSpeed
                val directionMultiplier = if (_userSettings.value.reverseScroll) -1 else 1
                val baseV = gesture.vDelta * directionMultiplier
                val baseH = gesture.hDelta
                if (speed == 1.0f) {
                    hidManager.sendMouseReport(0, 0, wheelDelta = baseV, panDelta = baseH, leftButtonPressed = false, rightButtonPressed = false)
                } else {
                    val totalV = baseV * speed + scrollResidualV
                    val totalH = baseH * speed + scrollResidualH
                    val sendV = kotlin.math.round(totalV).toInt()
                    val sendH = kotlin.math.round(totalH).toInt()
                    scrollResidualV = totalV - sendV
                    scrollResidualH = totalH - sendH
                    if (sendV != 0 || sendH != 0) {
                        hidManager.sendMouseReport(0, 0, wheelDelta = sendV, panDelta = sendH, leftButtonPressed = false, rightButtonPressed = false)
                    }
                }
            }

            RecognizedGesture.DragLockEngaged -> {
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = true, rightButtonPressed = false)
                triggerDragLockHaptic()
            }

            is RecognizedGesture.DragMove -> {
                val speed = _userSettings.value.cursorSpeed
                if (speed == 1.0f) {
                    hidManager.sendMouseReport(gesture.dx, gesture.dy, wheelDelta = 0, panDelta = 0, leftButtonPressed = true, rightButtonPressed = false)
                } else {
                    val totalDx = gesture.dx * speed + cursorResidualX
                    val totalDy = gesture.dy * speed + cursorResidualY
                    val sendDx = kotlin.math.round(totalDx).toInt()
                    val sendDy = kotlin.math.round(totalDy).toInt()
                    cursorResidualX = totalDx - sendDx
                    cursorResidualY = totalDy - sendDy
                    if (sendDx != 0 || sendDy != 0) {
                        hidManager.sendMouseReport(sendDx, sendDy, wheelDelta = 0, panDelta = 0, leftButtonPressed = true, rightButtonPressed = false)
                    }
                }
            }

            RecognizedGesture.DragLockReleased ->
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false)

            is RecognizedGesture.TwoFingerSwipe -> {
                if (_userSettings.value.twoFingerNavEnabled) {
                    when (gesture.direction) {
                        SwipeDirection.RIGHT ->
                            sendShortcut(HidKeyboardReport.MODIFIER_ALT, LEFT_ARROW_USAGE_CODE)
                        SwipeDirection.LEFT ->
                            sendShortcut(HidKeyboardReport.MODIFIER_ALT, RIGHT_ARROW_USAGE_CODE)
                        else -> {}
                    }
                }
            }

            is RecognizedGesture.ThreeFingerSwipe -> when (gesture.direction) {
                SwipeDirection.UP -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, TAB_USAGE_CODE)
                SwipeDirection.DOWN -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, D_USAGE_CODE)
                SwipeDirection.LEFT -> sendShortcut(HidKeyboardReport.MODIFIER_ALT, TAB_USAGE_CODE)
                SwipeDirection.RIGHT -> sendShortcut(HidKeyboardReport.MODIFIER_ALT or HidKeyboardReport.MODIFIER_SHIFT, TAB_USAGE_CODE)
            }

            RecognizedGesture.ThreeFingerTap ->
                executeTapAction(_userSettings.value.threeFingerTapAction)

            is RecognizedGesture.FourFingerSwipe -> when (gesture.direction) {
                SwipeDirection.UP -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, TAB_USAGE_CODE)
                SwipeDirection.DOWN -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, D_USAGE_CODE)
                SwipeDirection.LEFT -> sendShortcut(HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_WIN, RIGHT_ARROW_USAGE_CODE)
                SwipeDirection.RIGHT -> sendShortcut(HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_WIN, LEFT_ARROW_USAGE_CODE)
            }

            RecognizedGesture.FourFingerTap ->
                executeTapAction(_userSettings.value.fourFingerTapAction)

            RecognizedGesture.PinchZoomStarted ->
                hidManager.sendKeyboardReport(HidKeyboardReport.MODIFIER_CTRL, NO_KEY_USAGE_CODE)

            is RecognizedGesture.PinchZoomDelta ->
                hidManager.sendMouseReport(0, 0, wheelDelta = gesture.units, panDelta = 0, leftButtonPressed = false, rightButtonPressed = false)

            RecognizedGesture.PinchZoomEnded ->
                hidManager.releaseKeyboardReport()
        }
    }

    private fun executeTapAction(action: TapAction) {
        when (action) {
            TapAction.MIDDLE_CLICK -> {
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false, middleButtonPressed = true)
                hidManager.sendMouseReport(0, 0, 0, 0, leftButtonPressed = false, rightButtonPressed = false, middleButtonPressed = false)
                triggerClickHaptic()
            }
            TapAction.WINDOWS_SEARCH -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, S_USAGE_CODE)
            TapAction.SHOW_DESKTOP -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, D_USAGE_CODE)
            TapAction.NOTIFICATION_CENTER -> sendShortcut(HidKeyboardReport.MODIFIER_WIN, N_USAGE_CODE)
            TapAction.DISABLED -> { /* No-op */ }
        }
    }

    fun setThreeFingerTapAction(action: TapAction) {
        _userSettings.value = _userSettings.value.copy(threeFingerTapAction = action)
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.setThreeFingerTapAction(action) }
        }
    }

    fun setFourFingerTapAction(action: TapAction) {
        _userSettings.value = _userSettings.value.copy(fourFingerTapAction = action)
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.setFourFingerTapAction(action) }
        }
    }

    fun setZoomEnabled(enabled: Boolean) {
        _zoomEnabled.value = enabled
        _userSettings.value = _userSettings.value.copy(zoomEnabled = enabled)
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.setZoomEnabled(enabled) }
        }
    }

    fun setCursorSpeed(speed: Float) {
        _userSettings.value = _userSettings.value.copy(cursorSpeed = speed)
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.setCursorSpeed(speed) }
        }
    }

    fun setScrollSpeed(speed: Float) {
        _userSettings.value = _userSettings.value.copy(scrollSpeed = speed)
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.setScrollSpeed(speed) }
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        _userSettings.value = _userSettings.value.copy(autoReconnect = enabled)
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.setAutoReconnect(enabled) }
        }
    }

    fun setTurkishLayout(isTurkish: Boolean) {
        _activeLayout.value = if (isTurkish) TurkishQLayout() else EnglishUsLayout()
        _userSettings.value = _userSettings.value.copy(isTurkishLayout = isTurkish)
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.setTurkishLayout(isTurkish) }
        }
    }

    fun setAppLanguage(languageCode: String) {
        val isTurkish = languageCode == "tr"
        setTurkishLayout(isTurkish)
        _userSettings.value = _userSettings.value.copy(appLanguage = languageCode)
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.setAppLanguage(languageCode) }
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _userSettings.value = _userSettings.value.copy(hapticsEnabled = enabled)
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.setHapticsEnabled(enabled) }
        }
    }

    fun setReverseScroll(enabled: Boolean) {
        _userSettings.value = _userSettings.value.copy(reverseScroll = enabled)
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.setReverseScroll(enabled) }
        }
    }

    fun setTwoFingerNav(enabled: Boolean) {
        _userSettings.value = _userSettings.value.copy(twoFingerNavEnabled = enabled)
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.setTwoFingerNav(enabled) }
        }
    }

    fun onLayoutToggleClicked() {
        val nextIsTurkish = _activeLayout.value !is TurkishQLayout
        setTurkishLayout(nextIsTurkish)
    }

    fun onZoomToggleClicked() {
        val next = !_zoomEnabled.value
        setZoomEnabled(next)
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
        if (_modifierState.value.winActive) {
            // İkinci kez basıldı: Başka tuşa basılmadan tekrar Win'e basıldı -> Doğrudan Başlat Menüsünü aç/kapat
            sendShortcutWithHaptics(HidKeyboardReport.MODIFIER_WIN, 0)
            _modifierState.value = _modifierState.value.copy(winActive = false)
        } else {
            // İlk basış: Win kombinasyon modu (Win+D, Win+E, Win+R vb.) için aktif et
            _modifierState.value = _modifierState.value.copy(winActive = true)
            triggerClickHaptic()
        }
    }

    fun onWinLongClicked() {
        // Uzun basış: Beklemeden doğrudan tek başına Başlat Menüsünü aç/kapat
        sendShortcutWithHaptics(HidKeyboardReport.MODIFIER_WIN, 0)
        _modifierState.value = _modifierState.value.copy(winActive = false)
    }

    fun onCtrlAltDelClicked() {
        hidManager.sendKeyboardReport(HidKeyboardReport.MODIFIER_CTRL or HidKeyboardReport.MODIFIER_ALT, DELETE_FORWARD_USAGE_CODE)
        hidManager.releaseKeyboardReport()
    }

    fun sendKey(usageCode: Int, modifierBits: Int = 0) {
        viewModelScope.launch {
            hidManager.sendKeyboardReport(modifierBits, usageCode)
            kotlinx.coroutines.delay(50L)
            hidManager.releaseKeyboardReport()
        }
        triggerClickHaptic()
    }

    fun sendConsumer(usageCode: Int) {
        hidManager.sendConsumerReport(usageCode)
        triggerClickHaptic()
    }

    // Macro actions
    fun macroCopy() = sendShortcutWithHaptics(HidKeyboardReport.MODIFIER_CTRL, com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_C)
    fun macroPaste() = sendShortcutWithHaptics(HidKeyboardReport.MODIFIER_CTRL, com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_V)
    fun macroCut() = sendShortcutWithHaptics(HidKeyboardReport.MODIFIER_CTRL, com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_X)
    fun macroUndo() = sendShortcutWithHaptics(HidKeyboardReport.MODIFIER_CTRL, com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_Z)
    fun macroSave() = sendShortcutWithHaptics(HidKeyboardReport.MODIFIER_CTRL, com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_S)
    fun macroSelectAll() = sendShortcutWithHaptics(HidKeyboardReport.MODIFIER_CTRL, com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_A)
    fun macroShowDesktop() = sendShortcutWithHaptics(HidKeyboardReport.MODIFIER_WIN, com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_D)

    private fun sendShortcutWithHaptics(modifierBits: Int, usageCode: Int) {
        hidManager.sendKeyboardReport(modifierBits, usageCode)
        hidManager.releaseKeyboardReport()
        triggerClickHaptic()
    }

    private fun sendShortcut(modifierBits: Int, usageCode: Int) {
        hidManager.sendKeyboardReport(modifierBits, usageCode)
        hidManager.releaseKeyboardReport()
        triggerClickHaptic()
    }

    private fun triggerClickHaptic() {
        if (_userSettings.value.hapticsEnabled) {
            haptics.click()
        }
    }

    private fun triggerDragLockHaptic() {
        if (_userSettings.value.hapticsEnabled) {
            haptics.dragLockEngaged()
        }
    }
}

private fun KeyboardModifierState.stickyHidModifierBits(): Int {
    var bits = 0
    if (ctrlActive) bits = bits or HidKeyboardReport.MODIFIER_CTRL
    if (altActive) bits = bits or HidKeyboardReport.MODIFIER_ALT
    if (winActive) bits = bits or HidKeyboardReport.MODIFIER_WIN
    return bits
}
