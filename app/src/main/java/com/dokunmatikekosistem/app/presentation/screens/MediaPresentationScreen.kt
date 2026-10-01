package com.dokunmatikekosistem.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dokunmatikekosistem.app.data.hid.HidConsumerReport
import com.dokunmatikekosistem.app.data.hid.HidKeyboardReport
import com.dokunmatikekosistem.app.data.hid.HidUsageCodes
import com.dokunmatikekosistem.app.presentation.MainViewModel

@Composable
fun MediaPresentationScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF121214)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // MEDYA KONTROLLERİ
            Text(
                text = "MEDYA",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SimpleButton(title = "Sesi Kıs", modifier = Modifier.weight(1f)) {
                    viewModel.sendConsumer(HidConsumerReport.VOLUME_DECREMENT)
                }
                SimpleButton(title = "Sustur", modifier = Modifier.weight(1f)) {
                    viewModel.sendConsumer(HidConsumerReport.MUTE)
                }
                SimpleButton(title = "Sesi Aç", modifier = Modifier.weight(1f)) {
                    viewModel.sendConsumer(HidConsumerReport.VOLUME_INCREMENT)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SimpleButton(title = "Önceki", modifier = Modifier.weight(1f)) {
                    viewModel.sendConsumer(HidConsumerReport.SCAN_PREV_TRACK)
                }
                SimpleButton(
                    title = "Oynat / Duraklat",
                    color = MaterialTheme.colorScheme.primary,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1.3f)
                ) {
                    viewModel.sendConsumer(HidConsumerReport.PLAY_PAUSE)
                }
                SimpleButton(title = "Sonraki", modifier = Modifier.weight(1f)) {
                    viewModel.sendConsumer(HidConsumerReport.SCAN_NEXT_TRACK)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SUNUM MODU
            Text(
                text = "SUNUM (POWERPOINT / SLAYT)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SimpleButton(title = "Başlat (F5)", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEY_F5)
                }
                SimpleButton(title = "Mevcut Slayt (Shift+F5)", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEY_F5, HidKeyboardReport.MODIFIER_SHIFT)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SimpleButton(
                    title = "Önceki Slayt (Sol)",
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEY_LEFT_ARROW)
                }
                SimpleButton(
                    title = "Sonraki Slayt (Sağ)",
                    fontSize = 15.sp,
                    color = Color(0xFF283593),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEY_RIGHT_ARROW)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SimpleButton(title = "Siyah Ekran (B)", modifier = Modifier.weight(1f)) {
                    viewModel.sendKey(HidUsageCodes.KEY_B)
                }
                SimpleButton(
                    title = "Bitir (Esc)",
                    color = Color(0xFF4E342E),
                    textColor = Color(0xFFFFCCBC),
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.sendKey(HidUsageCodes.KEY_ESC)
                }
            }
        }
    }
}

@Composable
private fun SimpleButton(
    title: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    color: Color = Color(0xFF22222A),
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
