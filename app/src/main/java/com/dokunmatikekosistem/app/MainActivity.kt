package com.dokunmatikekosistem.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dokunmatikekosistem.app.bluetooth.BluetoothHidManager
import com.dokunmatikekosistem.app.bluetooth.ConnectionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hidManager = BluetoothHidManager(applicationContext)
        val viewModel = MainViewModel(hidManager)
        setContent {
            MaterialTheme {
                TouchpadScreen(viewModel)
            }
        }
    }
}

@Composable
fun TouchpadScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val reportsSent by viewModel.reportsSent.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Durum: ${connectionState.label()}")
            Text("Gönderilen rapor: $reportsSent")
            Button(onClick = { viewModel.onConnectClicked() }) {
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
