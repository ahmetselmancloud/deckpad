package com.dokunmatikekosistem.app.presentation.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.dokunmatikekosistem.app.presentation.MainViewModel
import com.dokunmatikekosistem.app.presentation.components.MacroBar
import com.dokunmatikekosistem.app.presentation.components.TopStatusBar
import kotlinx.coroutines.delay

private const val OLED_IDLE_DIM_TIMEOUT_MS = 20_000L

@Composable
fun TouchpadScreen(
    viewModel: MainViewModel,
    onConnectRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val reportsSent by viewModel.reportsSent.collectAsState()
    val zoomEnabled by viewModel.zoomEnabled.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()

    val context = LocalContext.current
    var lastTouchTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isOledDimmed by remember { mutableStateOf(false) }

    // Immersive Mode & System Bars Control for Fullscreen
    DisposableEffect(isFullscreen) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val window = (context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Samsung & Android Edge Swipe Protection:
    // When in fullscreen mode, consume system back gesture so sliding fingers at edge never exits the app!
    BackHandler(enabled = isFullscreen) {
        // Accidental back gesture intercepted and prevented
    }

    // OLED Idle Dimming timer
    LaunchedEffect(lastTouchTime, isFullscreen) {
        if (isFullscreen) {
            isOledDimmed = false
            delay(OLED_IDLE_DIM_TIMEOUT_MS)
            isOledDimmed = true
        } else {
            isOledDimmed = false
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = if (isFullscreen) Color.Black else Color(0xFF121214)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isFullscreen) {
                // ==================== FULLSCREEN AMOLED TOUCHPAD ====================
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .pointerInput(zoomEnabled) {
                            val recognizer = GestureRecognizer(zoomEnabled = zoomEnabled)
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val timeMs = System.currentTimeMillis()
                                    lastTouchTime = timeMs

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
                    // Floating Corner Exit Button (Top Right, semi-transparent to prevent burn-in)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222226).copy(alpha = 0.6f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = Color.White),
                                onClick = { viewModel.setFullscreen(false) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Faint watermark in center
                    Text(
                        text = "KESİNTİSİZ DOKUNMA KALKANI (AMOLED)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.08f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // OLED Idle Dimming Overlay
                AnimatedVisibility(
                    visible = isOledDimmed,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OLED Koruma • Dokunarak Uyandır",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }
            } else {
                // ==================== NORMAL TOUCHPAD MODE ====================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Top Status Bar
                    TopStatusBar(
                        connectionState = connectionState,
                        reportsSent = reportsSent,
                        onConnectClicked = onConnectRequested,
                        onSettingsClicked = onSettingsRequested,
                        showFullscreenButton = true,
                        onFullscreenClicked = { viewModel.setFullscreen(true) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Macro Bar
                    MacroBar(
                        onCopy = { viewModel.macroCopy() },
                        onPaste = { viewModel.macroPaste() },
                        onCut = { viewModel.macroCut() },
                        onUndo = { viewModel.macroUndo() },
                        onSave = { viewModel.macroSave() },
                        onSelectAll = { viewModel.macroSelectAll() },
                        onShowDesktop = { viewModel.macroShowDesktop() }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Main Touchpad Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1B1B22))
                            .border(1.dp, Color(0xFF282835), RoundedCornerShape(20.dp))
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
                            color = Color.White.copy(alpha = 0.15f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dedicated Left & Right Click Physical Buttons at bottom
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF22222C))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = Color.White),
                                    onClick = { viewModel.onGesture(com.dokunmatikekosistem.app.data.gesture.RecognizedGesture.LeftClick) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sol Tık",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF22222C))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = Color.White),
                                    onClick = { viewModel.onGesture(com.dokunmatikekosistem.app.data.gesture.RecognizedGesture.RightClick) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sağ Tık",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
