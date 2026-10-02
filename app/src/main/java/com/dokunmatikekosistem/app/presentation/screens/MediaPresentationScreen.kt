package com.dokunmatikekosistem.app.presentation.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dokunmatikekosistem.app.R
import com.dokunmatikekosistem.app.data.hid.HidConsumerReport
import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import com.dokunmatikekosistem.app.data.hid.HidUsageCodes
import com.dokunmatikekosistem.app.presentation.MainViewModel

@Composable
fun MediaPresentationScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    MediaControlsCard(viewModel = viewModel, isLandscape = true)
                }
                Box(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
                    PresentationControlsCard(viewModel = viewModel, isLandscape = true)
                }
            }
        }
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 500.dp)
                    .padding(top = 4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaControlsCard(viewModel = viewModel, isLandscape = false)
                PresentationControlsCard(viewModel = viewModel, isLandscape = false)
            }
        }
    }
}

@Composable
private fun MediaControlsCard(
    viewModel: MainViewModel,
    isLandscape: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1B1B22),
        border = BorderStroke(1.dp, Color(0xFF282832))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp)
        ) {
            Text(
                text = stringResource(R.string.media_player_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = if (isLandscape) 11.sp else 12.sp
            )

            // Oynatma Kontrolleri (Symmetrical 3-Way Split)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaButton(
                    title = stringResource(R.string.media_prev),
                    modifier = Modifier.weight(1f),
                    height = if (isLandscape) 42.dp else 46.dp
                ) {
                    viewModel.sendConsumer(HidConsumerReport.SCAN_PREV_TRACK)
                }

                MediaButton(
                    title = stringResource(R.string.media_play_pause),
                    modifier = Modifier.weight(1.2f),
                    height = if (isLandscape) 42.dp else 46.dp,
                    isPrimary = true
                ) {
                    viewModel.sendConsumer(HidConsumerReport.PLAY_PAUSE)
                }

                MediaButton(
                    title = stringResource(R.string.media_next),
                    modifier = Modifier.weight(1f),
                    height = if (isLandscape) 42.dp else 46.dp
                ) {
                    viewModel.sendConsumer(HidConsumerReport.SCAN_NEXT_TRACK)
                }
            }

            // Ses Kontrolleri (Symmetrical 3-Way Split)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaButton(
                    title = stringResource(R.string.media_vol_down),
                    modifier = Modifier.weight(1f),
                    height = if (isLandscape) 38.dp else 42.dp
                ) {
                    viewModel.sendConsumer(HidConsumerReport.VOLUME_DECREMENT)
                }

                MediaButton(
                    title = stringResource(R.string.media_mute),
                    modifier = Modifier.weight(1f),
                    height = if (isLandscape) 38.dp else 42.dp
                ) {
                    viewModel.sendConsumer(HidConsumerReport.MUTE)
                }

                MediaButton(
                    title = stringResource(R.string.media_vol_up),
                    modifier = Modifier.weight(1f),
                    height = if (isLandscape) 38.dp else 42.dp
                ) {
                    viewModel.sendConsumer(HidConsumerReport.VOLUME_INCREMENT)
                }
            }
        }
    }
}

@Composable
private fun PresentationControlsCard(
    viewModel: MainViewModel,
    isLandscape: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1B1B22),
        border = BorderStroke(1.dp, Color(0xFF282832))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp)
        ) {
            Text(
                text = stringResource(R.string.presentation_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = if (isLandscape) 11.sp else 12.sp
            )

            // Slayt İleri / Geri (Geniş & Ergonomik 50-50 Split)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaButton(
                    title = stringResource(R.string.presentation_prev_slide),
                    modifier = Modifier.weight(1f),
                    height = if (isLandscape) 52.dp else 64.dp,
                    fontSize = if (isLandscape) 13.sp else 14.sp,
                    fontWeight = FontWeight.Bold
                ) {
                    viewModel.sendKey(HidUsageCodes.KEY_LEFT_ARROW)
                }

                MediaButton(
                    title = stringResource(R.string.presentation_next_slide),
                    modifier = Modifier.weight(1f),
                    height = if (isLandscape) 52.dp else 64.dp,
                    fontSize = if (isLandscape) 13.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    isPrimary = true
                ) {
                    viewModel.sendKey(HidUsageCodes.KEY_RIGHT_ARROW)
                }
            }

            // Sunum Fonksiyonları (Eşit 4'lü Dağılım)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaButton(
                    title = stringResource(R.string.presentation_start),
                    modifier = Modifier.weight(1f),
                    height = if (isLandscape) 36.dp else 42.dp,
                    fontSize = 10.5.sp
                ) {
                    viewModel.sendKey(HidUsageCodes.KEY_F5)
                }

                MediaButton(
                    title = stringResource(R.string.presentation_from_current),
                    modifier = Modifier.weight(1f),
                    height = if (isLandscape) 36.dp else 42.dp,
                    fontSize = 10.5.sp
                ) {
                    viewModel.sendKey(HidUsageCodes.KEY_F5, HidKeyboardReport.MODIFIER_SHIFT)
                }

                MediaButton(
                    title = stringResource(R.string.presentation_black),
                    modifier = Modifier.weight(1f),
                    height = if (isLandscape) 36.dp else 42.dp,
                    fontSize = 10.5.sp
                ) {
                    viewModel.sendKey(HidUsageCodes.KEY_B)
                }

                MediaButton(
                    title = stringResource(R.string.presentation_end),
                    modifier = Modifier.weight(1f),
                    height = if (isLandscape) 36.dp else 42.dp,
                    fontSize = 10.5.sp,
                    backgroundColor = Color(0xFF382024),
                    textColor = Color(0xFFFF8A80)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEY_ESC)
                }
            }
        }
    }
}

@Composable
private fun MediaButton(
    title: String,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    isPrimary: Boolean = false,
    backgroundColor: Color? = null,
    textColor: Color? = null,
    onClick: () -> Unit
) {
    val bgColor = backgroundColor ?: if (isPrimary) MaterialTheme.colorScheme.primary else Color(0xFF262632)
    val txtColor = textColor ?: if (isPrimary) MaterialTheme.colorScheme.onPrimary else Color.White

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = txtColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
