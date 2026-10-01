package com.dokunmatikekosistem.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ripple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ==================== MEDYA KONTROLLERİ ====================
            ControlSection(
                title = "MEDYA KONTROLLERİ",
                subtitle = "Spotify, YouTube, VLC & Windows Ses"
            ) {
                // Ses Kontrolleri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlButton(
                        title = "Sesi Kıs",
                        icon = "🔉",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.VOLUME_DECREMENT)
                    }

                    ControlButton(
                        title = "Sustur",
                        icon = "🔇",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.MUTE)
                    }

                    ControlButton(
                        title = "Sesi Aç",
                        icon = "🔊",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.VOLUME_INCREMENT)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Oynatma Kontrolleri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlButton(
                        title = "Önceki",
                        icon = "⏮️",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.SCAN_PREV_TRACK)
                    }

                    ControlButton(
                        title = "Oynat / Duraklat",
                        icon = "⏯️",
                        color = MaterialTheme.colorScheme.primaryContainer,
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1.4f)
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.PLAY_PAUSE)
                    }

                    ControlButton(
                        title = "Sonraki",
                        icon = "⏭️",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendConsumer(HidConsumerReport.SCAN_NEXT_TRACK)
                    }
                }
            }

            // ==================== SUNUM MODU ====================
            ControlSection(
                title = "SUNUM KUMANDASI",
                subtitle = "PowerPoint, Keynote & Google Slides"
            ) {
                // Başlat / Kaldığı Yerden Başlat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlButton(
                        title = "Baştan Başlat",
                        subtitle = "F5",
                        icon = "▶️",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendKey(HidUsageCodes.KEY_F5)
                    }

                    ControlButton(
                        title = "Mevcut Slayt",
                        subtitle = "Shift + F5",
                        icon = "⏩",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendKey(HidUsageCodes.KEY_F5, HidKeyboardReport.MODIFIER_SHIFT)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Büyük İleri / Geri Butonları (Kolay tıklanabilir sunum butonları)
                Row(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlButton(
                        title = "Önceki Slayt",
                        icon = "◀",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendKey(HidUsageCodes.KEY_LEFT_ARROW)
                    }

                    ControlButton(
                        title = "Sonraki Slayt",
                        icon = "▶",
                        color = Color(0xFF2E3B55),
                        textColor = Color(0xFF90CAF9),
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendKey(HidUsageCodes.KEY_RIGHT_ARROW)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Siyah Ekran & Çıkış
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlButton(
                        title = "Siyah Ekran",
                        subtitle = "B",
                        icon = "⬛",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendKey(HidUsageCodes.KEY_B)
                    }

                    ControlButton(
                        title = "Sunumu Bitir",
                        subtitle = "Esc",
                        icon = "⏹️",
                        color = Color(0xFF3E2723),
                        textColor = Color(0xFFFF8A80),
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendKey(HidUsageCodes.KEY_ESC)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = Color(0xFF1B1B22)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun ControlButton(
    title: String,
    icon: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    color: Color = Color(0xFF282832),
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}
