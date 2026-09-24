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
