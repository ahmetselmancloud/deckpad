package com.dokunmatikekosistem.app.presentation.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    onWinLongClicked: () -> Unit = {},
    onCtrlAltDelClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val upper = modifierState.isUpperCaseEffective()
    val displayRows = layout.displayRows()
    val row0 = displayRows.getOrElse(0) { emptyList() } // Digits
    val row1 = displayRows.getOrElse(1) { emptyList() } // Top letters
    val row2 = displayRows.getOrElse(2) { emptyList() } // Middle letters
    val row3 = displayRows.getOrElse(3) { emptyList() } // Bottom letters

    // Max keys in letter rows determines the base unit width M
    val maxRowUnits = row1.size.toFloat().coerceAtLeast(10f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        // --- Row 0: Digits (1 2 3 4 5 6 7 8 9 0) centered with exact same key width ---
        val digitSpacer = (maxRowUnits - row0.size.toFloat()) / 2f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (digitSpacer > 0f) {
                Spacer(modifier = Modifier.weight(digitSpacer))
            }
            for (char in row0) {
                val displayChar = if (upper) layout.shiftedChar(char) else char
                KeyButton(
                    label = displayChar.toString(),
                    weight = 1.0f,
                    onClick = { onKeyTyped(displayChar) }
                )
            }
            if (digitSpacer > 0f) {
                Spacer(modifier = Modifier.weight(digitSpacer))
            }
        }

        // --- Row 1: Top letters (q w e r t y u ı o p ğ ü) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            for (char in row1) {
                val displayChar = if (upper) layout.shiftedChar(char) else char
                KeyButton(
                    label = displayChar.toString(),
                    weight = 1.0f,
                    onClick = { onKeyTyped(displayChar) }
                )
            }
        }

        // --- Row 2: Middle letters (a s d f g h j k l ş i) with equal half-key margins ---
        val midSpacer = (maxRowUnits - row2.size.toFloat()) / 2f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (midSpacer > 0f) {
                Spacer(modifier = Modifier.weight(midSpacer))
            }
            for (char in row2) {
                val displayChar = if (upper) layout.shiftedChar(char) else char
                KeyButton(
                    label = displayChar.toString(),
                    weight = 1.0f,
                    onClick = { onKeyTyped(displayChar) }
                )
            }
            if (midSpacer > 0f) {
                Spacer(modifier = Modifier.weight(midSpacer))
            }
        }

        // --- Row 3: Shift + Bottom letters (z x c v b n m ö ç) + Sil (Backspace) ---
        // Letters have exact same 1.0f width as Row 1 and Row 2! Shift and Sil balance the row!
        val sideKeyWeight = ((maxRowUnits - row3.size.toFloat()) / 2f).coerceAtLeast(1.2f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            KeyButton(
                label = "Shift",
                weight = sideKeyWeight,
                isPrimary = modifierState.shiftState != ShiftState.Off,
                fontSize = 13.sp,
                onClick = onShiftClicked
            )
            for (char in row3) {
                val displayChar = if (upper) layout.shiftedChar(char) else char
                KeyButton(
                    label = displayChar.toString(),
                    weight = 1.0f,
                    onClick = { onKeyTyped(displayChar) }
                )
            }
            KeyButton(
                label = "Sil",
                weight = sideKeyWeight,
                fontSize = 13.sp,
                backgroundColor = Color(0xFF382024),
                textColor = Color(0xFFFF8A80),
                onClick = { onKeyTyped('\b') }
            )
        }

        // --- Row 4: Modifiers, Space, Enter ---
        val isTwelve = maxRowUnits >= 11.5f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            KeyButton(
                label = "Caps",
                weight = if (isTwelve) 1.2f else 1.1f,
                isPrimary = modifierState.capsLockActive,
                fontSize = 12.sp,
                onClick = onCapsLockClicked
            )
            KeyButton(
                label = "Ctrl",
                weight = if (isTwelve) 1.1f else 1.0f,
                isPrimary = modifierState.ctrlActive,
                fontSize = 12.sp,
                onClick = onCtrlClicked
            )
            KeyButton(
                label = "Alt",
                weight = if (isTwelve) 1.1f else 1.0f,
                isPrimary = modifierState.altActive,
                fontSize = 12.sp,
                onClick = onAltClicked
            )
            KeyButton(
                label = "Boşluk",
                weight = if (isTwelve) 4.8f else 3.9f,
                fontSize = 13.sp,
                onClick = { onKeyTyped(' ') }
            )
            KeyButton(
                label = "Enter",
                weight = if (isTwelve) 2.2f else 1.8f,
                isPrimary = true,
                fontSize = 13.sp,
                onClick = { onKeyTyped('\n') }
            )
            KeyButton(
                label = "Win",
                weight = if (isTwelve) 1.6f else 1.2f,
                isPrimary = modifierState.winActive,
                fontSize = 12.sp,
                onClick = onWinClicked,
                onLongClick = onWinLongClicked
            )
        }
    }
}

/** A compact keyboard key that renders with sharp corners and clean uniform sizing */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun androidx.compose.foundation.layout.RowScope.KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    isPrimary: Boolean = false,
    backgroundColor: Color? = null,
    textColor: Color? = null,
    fontSize: TextUnit = 14.sp,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val bg = backgroundColor ?: if (isPrimary) MaterialTheme.colorScheme.primary else Color(0xFF262632)
    val textCol = textColor ?: if (isPrimary) MaterialTheme.colorScheme.onPrimary else Color.White

    Box(
        modifier = modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(1.2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White),
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White),
                        onClick = onClick
                    )
                }
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
