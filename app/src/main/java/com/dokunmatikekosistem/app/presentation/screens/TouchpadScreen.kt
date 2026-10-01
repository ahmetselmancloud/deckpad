package com.dokunmatikekosistem.app.presentation.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dokunmatikekosistem.app.data.gesture.GestureRecognizer
import com.dokunmatikekosistem.app.data.gesture.RawTouchEvent
import com.dokunmatikekosistem.app.presentation.MainActivity
import com.dokunmatikekosistem.app.presentation.MainViewModel

@Composable
fun TouchpadScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val zoomEnabled by viewModel.zoomEnabled.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val context = LocalContext.current

    // Immersive system bars control & App Pinning for locked fullscreen
    DisposableEffect(isFullscreen) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                (activity as? MainActivity)?.startLockMode()
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                (activity as? MainActivity)?.stopLockMode()
            }
        }
        onDispose {
            val activity = context as? Activity
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                (activity as? MainActivity)?.stopLockMode()
            }
        }
    }

    // Android edge navigation back gesture protection
    BackHandler(enabled = isFullscreen) {
        // Intercepts back swipe on edges so user doesn't accidentally exit
    }

    val shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp)
    val bgColor = if (isFullscreen) Color.Black else Color(0xFF1B1B1F)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(bgColor)
            .then(if (isFullscreen) Modifier.systemGestureExclusion() else Modifier.border(1.dp, Color(0xFF2E2E36), shape))
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
        if (isFullscreen) {
            Button(
                onClick = {
                    (context as? MainActivity)?.stopLockMode()
                    viewModel.setFullscreen(false)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF282830).copy(alpha = 0.85f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Text("Çıkış", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = if (isFullscreen) "TAM EKRAN (KİLİTLİ)" else "DOKUNMATİK YÜZEY",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.12f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
