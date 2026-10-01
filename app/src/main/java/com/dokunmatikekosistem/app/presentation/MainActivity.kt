package com.dokunmatikekosistem.app.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dokunmatikekosistem.app.data.gesture.GestureRecognizer
import com.dokunmatikekosistem.app.data.gesture.RawTouchEvent
import com.dokunmatikekosistem.app.data.notification.NotificationHelper
import com.dokunmatikekosistem.app.presentation.keyboard.VirtualKeyboard
import com.dokunmatikekosistem.app.presentation.service.HidForegroundService
import dagger.hilt.android.AndroidEntryPoint

private const val DISCOVERABLE_DURATION_SECONDS = 300

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestDiscoverable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Regardless of the result code (duration granted or cancelled), proceed:
        // registerApp() itself doesn't require discoverability, only pairing does.
        viewModel.onConnectClicked()
        ContextCompat.startForegroundService(this, Intent(this, HidForegroundService::class.java))
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // POST_NOTIFICATIONS being denied only means the persistent notification stays
        // hidden — it doesn't block starting the foreground service, so only
        // BLUETOOTH_CONNECT gates whether we proceed to connect.
        val bluetoothGranted = results[Manifest.permission.BLUETOOTH_CONNECT] ?: true
        if (bluetoothGranted) {
            requestDiscoverableAndConnect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TouchpadScreen(viewModel, onConnectRequested = ::connectWithPermissionCheck)
            }
        }
    }

    private fun connectWithPermissionCheck() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissionsToRequest.isEmpty()) {
            requestDiscoverableAndConnect()
        } else {
            requestPermissions.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun requestDiscoverableAndConnect() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION_SECONDS)
        }
        requestDiscoverable.launch(discoverableIntent)
    }
}

@Composable
fun TouchpadScreen(viewModel: MainViewModel, onConnectRequested: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()
    val reportsSent by viewModel.reportsSent.collectAsState()
    val activeLayout by viewModel.activeLayout.collectAsState()
    val modifierState by viewModel.modifierState.collectAsState()
    val zoomEnabled by viewModel.zoomEnabled.collectAsState()
    var keyboardVisible by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Durum: ${NotificationHelper.titleFor(connectionState)}")
            Text("Gönderilen rapor: $reportsSent")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onConnectRequested) { Text("Eşleştir/Bağlan") }
                Button(onClick = { keyboardVisible = !keyboardVisible }) { Text("Klavye") }
                Button(onClick = { viewModel.onLayoutToggleClicked() }) {
                    Text(if (activeLayout is com.dokunmatikekosistem.app.data.keyboard.TurkishQLayout) "TR Q" else "EN US")
                }
                Button(onClick = { viewModel.onZoomToggleClicked() }) {
                    Text(if (zoomEnabled) "Zoom: Açık" else "Zoom: Kapalı")
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp)
                    .background(Color.DarkGray)
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
            )
            if (keyboardVisible) {
                Box(modifier = Modifier.weight(1f)) {
                    VirtualKeyboard(
                        layout = activeLayout,
                        modifierState = modifierState,
                        onKeyTyped = { viewModel.onKeyTyped(it) },
                        onShiftClicked = { viewModel.onShiftClicked() },
                        onCapsLockClicked = { viewModel.onCapsLockClicked() },
                        onCtrlClicked = { viewModel.onCtrlClicked() },
                        onAltClicked = { viewModel.onAltClicked() },
                        onWinClicked = { viewModel.onWinClicked() },
                        onCtrlAltDelClicked = { viewModel.onCtrlAltDelClicked() }
                    )
                }
            }
        }
    }
}
