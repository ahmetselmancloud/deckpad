package com.dokunmatikekosistem.app.presentation.screens

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF121214)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Compact Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = if (isLandscape) 2.dp else 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SANAL KLAVYE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = if (isLandscape) 11.sp else 12.sp
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

            // Keyboard Container (Takes all remaining height evenly)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                    onCtrlAltDelClicked = { viewModel.onCtrlAltDelClicked() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
