package com.dokunmatikekosistem.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF121214)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SANAL KLAVYE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f)
                )

                FilledTonalButton(
                    onClick = { viewModel.onLayoutToggleClicked() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF262630),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (activeLayout is TurkishQLayout) "TR Q" else "EN US",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Keyboard Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.BottomCenter
            ) {
                VirtualKeyboard(
                    layout = activeLayout,
                    modifierState = modifierState,
                    onKeyTyped = { viewModel.onKeyTyped(it) },
                    onShiftClicked = { viewModel.onShiftClicked() },
                    onCapsLockClicked = { viewModel.onCapsLockClicked() },
                    onCtrlClicked = { viewModel.onCtrlClicked() },
                    onAltClicked = { viewModel.onAltClicked() },
                    onWinClicked = { viewModel.onWinClicked() },
                    onCtrlAltDelClicked = { viewModel.onCtrlAltDelClicked() }
                )
            }
        }
    }
}
