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
