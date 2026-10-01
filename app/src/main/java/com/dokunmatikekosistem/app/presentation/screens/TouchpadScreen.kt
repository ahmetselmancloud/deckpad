package com.dokunmatikekosistem.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dokunmatikekosistem.app.data.gesture.GestureRecognizer
import com.dokunmatikekosistem.app.data.gesture.RawTouchEvent
import com.dokunmatikekosistem.app.presentation.MainViewModel

@Composable
fun TouchpadScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val zoomEnabled by viewModel.zoomEnabled.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1B1F))
            .border(1.dp, Color(0xFF2E2E36), RoundedCornerShape(16.dp))
            .pointerInput(zoomEnabled) {
                val recognizer = GestureRecognizer(zoomEnabled = zoomEnabled)
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        val timeMs = System.currentTimeMillis()
                        for (change in event.changes) {
                            val raw: RawTouchEvent? = when {
                                change.pressed && change.previousPressed.not() ->
                                    RawTouchEvent.PointerDown(change.id.value.toInt(), change.position.x, change.position.y, timeMs)
                                change.pressed && change.previousPressed ->
                                    RawTouchEvent.PointerMove(change.id.value.toInt(), change.position.x, change.position.y, timeMs)
                                !change.pressed && change.previousPressed ->
                                    RawTouchEvent.PointerUp(change.id.value.toInt(), change.position.x, change.position.y, timeMs)
                                else -> null
                            }
                            if (raw != null) {
                                change.consume()
                                recognizer.zoomEnabled = zoomEnabled
                                recognizer.onEvent(raw)?.let { viewModel.onGesture(it) }
                            }
                        }
                        if (event.type == PointerEventType.Release && event.changes.all { !it.pressed }) break
                    }
                }
            }
    ) {
        Text(
            text = "DOKUNMATİK YÜZEY",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.12f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
