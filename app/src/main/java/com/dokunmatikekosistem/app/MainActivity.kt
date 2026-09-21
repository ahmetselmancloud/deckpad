package com.dokunmatikekosistem.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dokunmatikekosistem.app.bluetooth.BluetoothHidManager
import com.dokunmatikekosistem.app.bluetooth.ConnectionState

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    private val requestBluetoothConnect = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onConnectClicked()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hidManager = BluetoothHidManager(applicationContext)
        viewModel = MainViewModel(hidManager)
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
            viewModel.onConnectClicked()
        } else {
            requestBluetoothConnect.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
}

@Composable
fun TouchpadScreen(viewModel: MainViewModel, onConnectRequested: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()
    val reportsSent by viewModel.reportsSent.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Durum: ${connectionState.label()}")
            Text("Gönderilen rapor: $reportsSent")
            Button(onClick = onConnectRequested) {
                Text("Eşleştir/Bağlan")
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
                    .background(Color.DarkGray)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            viewModel.onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { viewModel.onTap() }
                    }
            )
        }
    }
}

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.DISCONNECTED -> "Bağlı değil"
    ConnectionState.REGISTERING -> "Eşleştiriliyor"
    ConnectionState.CONNECTED -> "Bağlı"
    ConnectionState.ERROR -> "Hata"
}
