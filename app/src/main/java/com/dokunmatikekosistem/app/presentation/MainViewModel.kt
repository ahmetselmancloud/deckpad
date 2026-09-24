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
}

private fun KeyboardModifierState.stickyHidModifierBits(): Int {
    var bits = 0
    if (ctrlActive) bits = bits or HidKeyboardReport.MODIFIER_CTRL
    if (altActive) bits = bits or HidKeyboardReport.MODIFIER_ALT
    if (winActive) bits = bits or HidKeyboardReport.MODIFIER_WIN
    return bits
}
