package com.dokunmatikekosistem.app.presentation.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- 1. MEDYA KONTROLLERİ ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1B1B22),
            border = BorderStroke(1.dp, Color(0xFF282832))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "MEDYA OYNATICI",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f)
                )

                // Oynatma Kontrolleri (Symmetrical 3-Way Split)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MediaButton(
                        title = "◀ Önceki",
                        modifier = Modifier.weight(1f),
                        height = 46.dp
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.SCAN_PREV_TRACK)
                    }

                    MediaButton(
                        title = "Oynat / Duraklat",
                        modifier = Modifier.weight(1.2f),
                        height = 46.dp,
                        isPrimary = true
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.PLAY_PAUSE)
                    }

                    MediaButton(
                        title = "Sonraki ▶",
                        modifier = Modifier.weight(1f),
                        height = 46.dp
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.SCAN_NEXT_TRACK)
                    }
                }

                // Ses Kontrolleri (Symmetrical 3-Way Split)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MediaButton(
                        title = "Sesi Kıs -",
                        modifier = Modifier.weight(1f),
                        height = 42.dp
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.VOLUME_DECREMENT)
                    }

                    MediaButton(
                        title = "Sustur",
                        modifier = Modifier.weight(1f),
                        height = 42.dp
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.MUTE)
                    }

                    MediaButton(
                        title = "Sesi Aç +",
                        modifier = Modifier.weight(1f),
                        height = 42.dp
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.VOLUME_INCREMENT)
                    }
                }
            }
        }

        // --- 2. SUNUM (SLAYT) KONTROLLERİ ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1B1B22),
            border = BorderStroke(1.dp, Color(0xFF282832))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "SUNUM (SLAYT KONTROLÜ)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f)
                )

                // Slayt İleri / Geri (Geniş & Ergonomik 50-50 Split)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MediaButton(
                        title = "◀  Önceki Slayt",
                        modifier = Modifier.weight(1f),
                        height = 64.dp,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ) {
                        viewModel.sendKey(HidUsageCodes.KEY_LEFT_ARROW)
                    }

                    MediaButton(
                        title = "Sonraki Slayt  ▶",
                        modifier = Modifier.weight(1f),
                        height = 64.dp,
                        fontSize = 14.sp,
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
                        title = "Başlat (F5)",
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                        fontSize = 11.sp
                    ) {
                        viewModel.sendKey(HidUsageCodes.KEY_F5)
                    }

                    MediaButton(
                        title = "Mevcut (⇧F5)",
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                        fontSize = 11.sp
                    ) {
                        viewModel.sendKey(HidUsageCodes.KEY_F5, HidKeyboardReport.MODIFIER_SHIFT)
                    }

                    MediaButton(
                        title = "Karart (B)",
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                        fontSize = 11.sp
                    ) {
                        viewModel.sendKey(HidUsageCodes.KEY_B)
                    }

                    MediaButton(
                        title = "Bitir (Esc)",
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                        fontSize = 11.sp,
                        backgroundColor = Color(0xFF382024),
                        textColor = Color(0xFFFF8A80)
                    ) {
                        viewModel.sendKey(HidUsageCodes.KEY_ESC)
                    }
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
            maxLines = 1
        )
    }
}
