package com.dokunmatikekosistem.app.presentation.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        for (row in layout.displayRows()) {
            KeyRow {
                for (baseChar in row) {
                    val displayChar = if (upper) layout.shiftedChar(baseChar) else baseChar
                    KeyButton(label = displayChar.toString(), onClick = { onKeyTyped(displayChar) })
                }
            }
        }
        KeyRow {
            KeyButton(label = shiftLabel(modifierState.shiftState), onClick = onShiftClicked)
            KeyButton(label = if (modifierState.capsLockActive) "Caps ●" else "Caps", onClick = onCapsLockClicked)
            KeyButton(label = "Boşluk", onClick = { onKeyTyped(' ') })
            KeyButton(label = "Enter", onClick = { onKeyTyped('\n') })
            KeyButton(label = "Sil", onClick = { onKeyTyped('\b') })
        }
        KeyRow {
            KeyButton(label = if (modifierState.ctrlActive) "Ctrl ●" else "Ctrl", onClick = onCtrlClicked)
            KeyButton(label = if (modifierState.altActive) "Alt ●" else "Alt", onClick = onAltClicked)
            KeyButton(label = if (modifierState.winActive) "Win ●" else "Win", onClick = onWinClicked)
            KeyButton(label = "Ctrl+Alt+Del", onClick = onCtrlAltDelClicked)
        }
    }
}

/** A key row that always fits the screen width — children divide it evenly via [KeyButton]'s weight. */
@Composable
private fun KeyRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), content = content)
}

/** A compact keyboard key that shrinks to share its row's width instead of overflowing the screen. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.KeyButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.weight(1f).padding(1.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

private fun shiftLabel(state: ShiftState): String = when (state) {
    ShiftState.Off -> "Shift"
    ShiftState.OneShot -> "⇧ Aktif"
    ShiftState.Locked -> "⇧ Kilit"
}
