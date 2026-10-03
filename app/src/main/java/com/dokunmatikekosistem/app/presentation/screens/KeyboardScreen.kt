package com.dokunmatikekosistem.app.presentation.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dokunmatikekosistem.app.R
import com.dokunmatikekosistem.app.data.keyboard.TurkishQLayout
import com.dokunmatikekosistem.app.presentation.MainViewModel
import com.dokunmatikekosistem.app.presentation.keyboard.VirtualKeyboard

@Composable
fun KeyboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val activeLayout by viewModel.activeLayout.collectAsState()
    val modifierState by viewModel.modifierState.collectAsState()
    val isSymbolMode by viewModel.isSymbolMode.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF121214)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .padding(top = 2.dp),
                verticalArrangement = if (isLandscape) Arrangement.SpaceBetween else Arrangement.Bottom
            ) {
            if (isLandscape) {
                // In Landscape: Unified top bar with [TR Q] toggle and all PC shortcuts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF262632))
                            .clickable { viewModel.onLayoutToggleClicked() }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (activeLayout is TurkishQLayout) "TR Q" else "EN US",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    KeyboardShortcutButton("Esc", modifier = Modifier.weight(1f), height = 28.dp) {
                        viewModel.sendKey(com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_ESC)
                    }
                    KeyboardShortcutButton("Tab", modifier = Modifier.weight(1f), height = 28.dp) {
                        viewModel.sendKey(com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_TAB)
                    }
                    KeyboardShortcutButton(stringResource(R.string.shortcut_copy), modifier = Modifier.weight(1.3f), height = 28.dp) {
                        viewModel.macroCopy()
                    }
                    KeyboardShortcutButton(stringResource(R.string.shortcut_paste), modifier = Modifier.weight(1.3f), height = 28.dp) {
                        viewModel.macroPaste()
                    }
                    KeyboardShortcutButton(stringResource(R.string.shortcut_undo), modifier = Modifier.weight(1.2f), height = 28.dp) {
                        viewModel.macroUndo()
                    }
                    KeyboardShortcutButton("C+A+D", modifier = Modifier.weight(1.2f), height = 28.dp) {
                        viewModel.onCtrlAltDelClicked()
                    }
                }
            } else {
                // Header Bar (Portrait)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.keyboard_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF262632))
                            .clickable { viewModel.onLayoutToggleClicked() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (activeLayout is TurkishQLayout) "TR Q" else "EN US",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Quick PC Shortcuts (Portrait)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    KeyboardShortcutButton("Esc", modifier = Modifier.weight(1f), height = 34.dp) {
                        viewModel.sendKey(com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_ESC)
                    }
                    KeyboardShortcutButton("Tab", modifier = Modifier.weight(1f), height = 34.dp) {
                        viewModel.sendKey(com.dokunmatikekosistem.app.data.hid.HidUsageCodes.KEY_TAB)
                    }
                    KeyboardShortcutButton(stringResource(R.string.shortcut_copy), modifier = Modifier.weight(1.3f), height = 34.dp) {
                        viewModel.macroCopy()
                    }
                    KeyboardShortcutButton(stringResource(R.string.shortcut_paste), modifier = Modifier.weight(1.3f), height = 34.dp) {
                        viewModel.macroPaste()
                    }
                    KeyboardShortcutButton(stringResource(R.string.shortcut_undo), modifier = Modifier.weight(1.2f), height = 34.dp) {
                        viewModel.macroUndo()
                    }
                    KeyboardShortcutButton("C+A+D", modifier = Modifier.weight(1.2f), height = 34.dp) {
                        viewModel.onCtrlAltDelClicked()
                    }
                }
            }

            // Keyboard Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isLandscape) Modifier.weight(1f) else Modifier.height(260.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                VirtualKeyboard(
                    layout = activeLayout,
                    modifierState = modifierState,
                    isSymbolMode = isSymbolMode,
                    onToggleSymbolMode = { viewModel.onToggleSymbolMode() },
                    onKeyTyped = { viewModel.onKeyTyped(it) },
                    onShiftClicked = { viewModel.onShiftClicked() },
                    onCtrlClicked = { viewModel.onCtrlClicked() },
                    onAltClicked = { viewModel.onAltClicked() },
                    onWinClicked = { viewModel.onWinClicked() },
                    onWinLongClicked = { viewModel.onWinLongClicked() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
}

@Composable
private fun KeyboardShortcutButton(
    label: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 34.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF22222C))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
