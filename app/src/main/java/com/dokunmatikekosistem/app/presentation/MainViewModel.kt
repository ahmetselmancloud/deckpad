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
