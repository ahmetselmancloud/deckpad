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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.dokunmatikekosistem.app.domain.ConnectionState
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
    }

    private val requestBluetoothConnect = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
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
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val alreadyGranted = !needsRuntimePermission ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            requestDiscoverableAndConnect()
        } else {
            requestBluetoothConnect.launch(Manifest.permission.BLUETOOTH_CONNECT)
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
    var keyboardVisible by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Durum: ${connectionState.label()}")
            Text("Gönderilen rapor: $reportsSent")
            Row {
                Button(onClick = onConnectRequested) { Text("Eşleştir/Bağlan") }
                Button(onClick = { keyboardVisible = !keyboardVisible }) { Text("Klavye") }
                Button(onClick = { viewModel.onLayoutToggleClicked() }) {
                    Text(if (activeLayout is com.dokunmatikekosistem.app.data.keyboard.TurkishQLayout) "TR Q" else "EN US")
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
                    .background(Color.DarkGray)
                    .pointerInput(Unit) {
                        val recognizer = GestureRecognizer()
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
                                        recognizer.onEvent(raw)?.let { viewModel.onGesture(it) }
                                    }
                                }
                                if (event.type == PointerEventType.Release && event.changes.all { !it.pressed }) break
                            }
                        }
                    }
            )
            if (keyboardVisible) {
                VirtualKeyboard(onKeyTyped = { viewModel.onKeyTyped(it) })
            }
        }
    }
}

private val virtualKeyboardShiftMap: Map<Char, Char> =
    mapOf('ç' to 'Ç', 'ğ' to 'Ğ', 'ı' to 'I', 'ö' to 'Ö', 'ş' to 'Ş', 'ü' to 'Ü', 'i' to 'İ') +
        ('a'..'z').associateWith { it.uppercaseChar() }

@Composable
private fun VirtualKeyboard(onKeyTyped: (Char) -> Unit) {
    val rows = listOf(
        "1234567890",
        "qwertyuıopğü",
        "asdfghjklşi",
        "zxcvbnmöç"
    )
    var shiftActive by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        for (row in rows) {
            Row {
                for (char in row) {
                    val displayChar = if (shiftActive) virtualKeyboardShiftMap[char] ?: char else char
                    Button(onClick = { onKeyTyped(displayChar) }) { Text(displayChar.toString()) }
                }
            }
        }
        Row {
            Button(onClick = { shiftActive = !shiftActive }) { Text(if (shiftActive) "⇧ Aktif" else "Shift") }
            Button(onClick = { onKeyTyped(' ') }) { Text("Boşluk") }
            Button(onClick = { onKeyTyped('\n') }) { Text("Enter") }
            Button(onClick = { onKeyTyped('\b') }) { Text("Sil") }
        }
    }
}

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.DISCONNECTED -> "Bağlı değil"
    ConnectionState.REGISTERING -> "Eşleştiriliyor"
    ConnectionState.REGISTERED -> "Kayıtlı, bağlantı bekleniyor"
    ConnectionState.CONNECTED -> "Bağlı"
    ConnectionState.ERROR -> "Hata"
}
