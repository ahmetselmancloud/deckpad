package com.dokunmatikekosistem.app.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MacroBar(
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onCut: () -> Unit,
    onUndo: () -> Unit,
    onSave: () -> Unit,
    onSelectAll: () -> Unit,
    onShowDesktop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MacroChip(label = "📋 Kopyala", shortcut = "Ctrl+C", onClick = onCopy)
        MacroChip(label = "📥 Yapıştır", shortcut = "Ctrl+V", onClick = onPaste)
        MacroChip(label = "✂️ Kes", shortcut = "Ctrl+X", onClick = onCut)
        MacroChip(label = "↩️ Geri Al", shortcut = "Ctrl+Z", onClick = onUndo)
        MacroChip(label = "💾 Kaydet", shortcut = "Ctrl+S", onClick = onSave)
        MacroChip(label = "🔲 Tümü", shortcut = "Ctrl+A", onClick = onSelectAll)
        MacroChip(label = "🖥️ Masaüstü", shortcut = "Win+D", onClick = onShowDesktop)
    }
}

@Composable
private fun MacroChip(
    label: String,
    shortcut: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF262630),
            contentColor = Color.White
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
