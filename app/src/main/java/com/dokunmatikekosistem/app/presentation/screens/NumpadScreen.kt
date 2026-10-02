package com.dokunmatikekosistem.app.presentation.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dokunmatikekosistem.app.data.hid.HidUsageCodes
import com.dokunmatikekosistem.app.presentation.MainViewModel

@Composable
fun NumpadScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gap = if (isLandscape) 6.dp else 10.dp

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF121214)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(if (isLandscape) 0.96f else 1f)
                    .then(
                        if (isLandscape) {
                            Modifier
                                .aspectRatio(0.92f, matchHeightConstraintsFirst = true)
                                .widthIn(max = 480.dp)
                        } else {
                            Modifier
                                .widthIn(max = 460.dp)
                                .fillMaxWidth()
                        }
                    )
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NUMPAD",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = if (isLandscape) 11.sp else 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Excel & Hesaplama",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = if (isLandscape) 10.sp else 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Row 1: NumLk, /, *, Backspace
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                NumpadKey(
                    label = "NumLk",
                    color = Color(0xFF2C2D35),
                    textColor = MaterialTheme.colorScheme.primary,
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_NUM_LOCK)
                }
                NumpadKey(
                    label = "/",
                    color = Color(0xFF2C2D35),
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_SLASH)
                }
                NumpadKey(
                    label = "*",
                    color = Color(0xFF2C2D35),
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_ASTERISK)
                }
                NumpadKey(
                    label = "Sil",
                    color = Color(0xFF3E2723),
                    textColor = Color(0xFFFF8A80),
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEY_BACKSPACE)
                }
            }

            // Row 2: 7, 8, 9, -
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                NumpadKey(label = "7", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_7)
                }
                NumpadKey(label = "8", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_8)
                }
                NumpadKey(label = "9", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_9)
                }
                NumpadKey(
                    label = "-",
                    color = Color(0xFF2C2D35),
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_HYPHEN)
                }
            }

            // Row 3: 4, 5, 6, +
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                NumpadKey(label = "4", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_4)
                }
                NumpadKey(label = "5", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_5)
                }
                NumpadKey(label = "6", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_6)
                }
                NumpadKey(
                    label = "+",
                    color = Color(0xFF2C2D35),
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_PLUS)
                }
            }

            // Row 4: 1, 2, 3, Tab
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                NumpadKey(label = "1", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_1)
                }
                NumpadKey(label = "2", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_2)
                }
                NumpadKey(label = "3", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_3)
                }
                NumpadKey(
                    label = "Tab",
                    color = Color(0xFF2C2D35),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEY_TAB)
                }
            }

            // Row 5: 0 (span 2), ., Enter
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                NumpadKey(
                    label = "0",
                    modifier = Modifier.weight(2f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_0)
                }
                NumpadKey(
                    label = ".",
                    color = Color(0xFF2C2D35),
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_DOT)
                }
                NumpadKey(
                    label = "Enter",
                    color = MaterialTheme.colorScheme.primary,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEYPAD_ENTER)
                }
            }
        }
    }
}
}

@Composable
private fun NumpadKey(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1E1E24),
    textColor: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(color)
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
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}
