package com.dokunmatikekosistem.app.presentation.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onCtrlAltDelClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val upper = modifierState.isUpperCaseEffective()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 2.dp)
    ) {
        // Rows 1-3: Character rows (Each row evenly divides vertical space)
        for (row in layout.displayRows()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                for (baseChar in row) {
                    val displayChar = if (upper) layout.shiftedChar(baseChar) else baseChar
                    KeyButton(
                        label = displayChar.toString(),
                        onClick = { onKeyTyped(displayChar) }
                    )
                }
            }
        }

        // Row 4: Shift, Caps, Space, Enter, Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            KeyButton(
                label = shiftLabel(modifierState.shiftState),
                weight = 1.3f,
                isPrimary = modifierState.shiftState != ShiftState.Off,
                onClick = onShiftClicked
            )
            KeyButton(
                label = if (modifierState.capsLockActive) "Caps ●" else "Caps",
                weight = 1.1f,
                isPrimary = modifierState.capsLockActive,
                onClick = onCapsLockClicked
            )
            KeyButton(
                label = "Boşluk",
                weight = 3.5f,
                onClick = { onKeyTyped(' ') }
            )
            KeyButton(
                label = "Enter",
                weight = 1.5f,
                isPrimary = true,
                onClick = { onKeyTyped('\n') }
            )
            KeyButton(
                label = "Sil",
                weight = 1.2f,
                backgroundColor = Color(0xFF382024),
                textColor = Color(0xFFFF8A80),
                onClick = { onKeyTyped('\b') }
            )
        }

        // Row 5: Modifiers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            KeyButton(
                label = if (modifierState.ctrlActive) "Ctrl ●" else "Ctrl",
                weight = 1f,
                isPrimary = modifierState.ctrlActive,
                onClick = onCtrlClicked
            )
            KeyButton(
                label = if (modifierState.altActive) "Alt ●" else "Alt",
                weight = 1f,
                isPrimary = modifierState.altActive,
                onClick = onAltClicked
            )
            KeyButton(
                label = if (modifierState.winActive) "Win ●" else "Win",
                weight = 1f,
                isPrimary = modifierState.winActive,
                onClick = onWinClicked
            )
            KeyButton(
                label = "Ctrl+Alt+Del",
                weight = 1.8f,
                fontSize = 11.sp,
                onClick = onCtrlAltDelClicked
            )
        }
    }
}

/** A compact keyboard key that shares height and width evenly without squishing or clipping */
@Composable
private fun androidx.compose.foundation.layout.RowScope.KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    isPrimary: Boolean = false,
    backgroundColor: Color? = null,
    textColor: Color? = null,
    fontSize: TextUnit = 14.sp,
    onClick: () -> Unit
) {
    val bg = backgroundColor ?: if (isPrimary) MaterialTheme.colorScheme.primary else Color(0xFF262632)
    val textCol = textColor ?: if (isPrimary) MaterialTheme.colorScheme.onPrimary else Color.White

    Box(
        modifier = modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(1.5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = textCol,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

private fun shiftLabel(state: ShiftState): String = when (state) {
    ShiftState.Off -> "Shift"
    ShiftState.OneShot -> "⇧ Aktif"
    ShiftState.Locked -> "⇧ Kilit"
}
