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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dokunmatikekosistem.app.data.settings.TapAction
import com.dokunmatikekosistem.app.data.settings.UserSettings
import com.dokunmatikekosistem.app.domain.ConnectionState
import com.dokunmatikekosistem.app.presentation.screens.KeyboardScreen
import com.dokunmatikekosistem.app.presentation.screens.MediaPresentationScreen
import com.dokunmatikekosistem.app.presentation.screens.NumpadScreen
import com.dokunmatikekosistem.app.presentation.screens.TouchpadScreen
import com.dokunmatikekosistem.app.presentation.service.HidForegroundService
import dagger.hilt.android.AndroidEntryPoint

private const val DISCOVERABLE_DURATION_SECONDS = 300

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestDiscoverable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onConnectClicked()
        ContextCompat.startForegroundService(this, Intent(this, HidForegroundService::class.java))
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val bluetoothGranted = results[Manifest.permission.BLUETOOTH_CONNECT] ?: true
        if (bluetoothGranted) {
            requestDiscoverableAndConnect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onConnectClicked()
            ContextCompat.startForegroundService(this, Intent(this, HidForegroundService::class.java))
        }
        setContent {
            MaterialTheme {
                MainAppScreen(viewModel, onConnectRequested = ::connectWithPermissionCheck)
            }
        }
    }

    fun startLockMode() {
        try {
            startLockTask()
        } catch (e: Exception) {
            // Lock task not permitted or already active
        }
    }

    fun stopLockMode() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            // Lock task not active
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel, onConnectRequested: () -> Unit) {
    val currentTab by viewModel.currentTab.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    var showSettingsSheet by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = if (isFullscreen) Color.Black else Color(0xFF121214)) {
        if (isFullscreen) {
            TouchpadScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Bar: Connection status & buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when (connectionState) {
                        ConnectionState.CONNECTED -> "Bağlandı"
                        ConnectionState.REGISTERING -> "Bağlanıyor..."
                        ConnectionState.REGISTERED -> "Hazır"
                        ConnectionState.DISCONNECTED -> "Bağlı Değil"
                        ConnectionState.ERROR -> "Hata"
                    }
                    Text(
                        text = "Durum: $statusText",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (connectionState == ConnectionState.CONNECTED) Color(0xFF4CAF50) else Color(0xFFE53935)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (currentTab == AppTab.TOUCHPAD) {
                            Button(onClick = { viewModel.setFullscreen(true) }) {
                                Text("Tam Ekran", fontSize = 12.sp)
                            }
                        }
                        Button(onClick = onConnectRequested) {
                            Text("Eşleştir", fontSize = 12.sp)
                        }
                        Button(onClick = { showSettingsSheet = true }) {
                            Text("Ayarlar", fontSize = 12.sp)
                        }
                    }
                }

                // Simple Tab Row (Text only, zero icons, zero animations)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppTab.entries.forEach { tab ->
                        val selected = tab == currentTab
                        FilledTonalButton(
                            onClick = { viewModel.selectTab(tab) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF22222A),
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White
                            )
                        ) {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Active Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (currentTab) {
                        AppTab.TOUCHPAD -> TouchpadScreen(viewModel = viewModel)
                        AppTab.NUMPAD -> NumpadScreen(viewModel = viewModel)
                        AppTab.MEDIA -> MediaPresentationScreen(viewModel = viewModel)
                        AppTab.KEYBOARD -> KeyboardScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            SettingsSheetContent(
                userSettings = userSettings,
                onThreeFingerActionSelected = { viewModel.setThreeFingerTapAction(it) },
                onFourFingerActionSelected = { viewModel.setFourFingerTapAction(it) },
                onZoomToggled = { viewModel.setZoomEnabled(it) },
                onTurkishLayoutToggled = { viewModel.setTurkishLayout(it) },
                onCursorSpeedChanged = { viewModel.setCursorSpeed(it) },
                onScrollSpeedChanged = { viewModel.setScrollSpeed(it) },
                onAutoReconnectToggled = { viewModel.setAutoReconnect(it) },
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}

@Composable
private fun SettingsSheetContent(
    userSettings: UserSettings,
    onThreeFingerActionSelected: (TapAction) -> Unit,
    onFourFingerActionSelected: (TapAction) -> Unit,
    onZoomToggled: (Boolean) -> Unit,
    onTurkishLayoutToggled: (Boolean) -> Unit,
    onCursorSpeedChanged: (Float) -> Unit,
    onScrollSpeedChanged: (Float) -> Unit,
    onAutoReconnectToggled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Touchpad Ayarları",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        // Cursor Speed Slider
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "İmleç Hızı",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${"%.2f".format(userSettings.cursorSpeed)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Fare imlecinin ekrandaki hareket hızı ve hassasiyeti",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Slider(
                value = userSettings.cursorSpeed,
                onValueChange = onCursorSpeedChanged,
                valueRange = 0.5f..2.5f,
                steps = 7
            )
        }

        HorizontalDivider()

        // Scroll Speed Slider
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kaydırma (Scroll) Hızı",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${"%.2f".format(userSettings.scrollSpeed)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "İki parmakla sayfa kaydırma hızı ve adımı",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Slider(
                value = userSettings.scrollSpeed,
                onValueChange = onScrollSpeedChanged,
                valueRange = 0.5f..2.5f,
                steps = 7
            )
        }

        HorizontalDivider()

        // Auto-reconnect Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Otomatik Yeniden Bağlan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Eşleşmiş bilgisayar kapsama alanına girdiğinde otomatik bağlanır",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = userSettings.autoReconnect,
                onCheckedChange = onAutoReconnectToggled
            )
        }

        HorizontalDivider()

        // 3 Finger Tap Section
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "3 Parmak Dokunma (3-Finger Tap)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Ekrana 3 parmakla tek dokunulduğunda tetiklenecek işlem",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            TapAction.entries.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThreeFingerActionSelected(action) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = userSettings.threeFingerTapAction == action,
                        onClick = { onThreeFingerActionSelected(action) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = action.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        HorizontalDivider()

        // 4 Finger Tap Section
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "4 Parmak Dokunma (4-Finger Tap)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Ekrana 4 parmakla tek dokunulduğunda tetiklenecek işlem",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            TapAction.entries.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFourFingerActionSelected(action) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = userSettings.fourFingerTapAction == action,
                        onClick = { onFourFingerActionSelected(action) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = action.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        HorizontalDivider()

        // Zoom Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "İki Parmakla Zoom (Pinch)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "İki parmakla çimdikleme yaparak sayfayı büyüt / küçült",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = userSettings.zoomEnabled,
                onCheckedChange = onZoomToggled
            )
        }

        HorizontalDivider()

        // Layout Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sanal Klavye Düzeni",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (userSettings.isTurkishLayout) "Türkçe Q Klavye Aktif" else "İngilizce US Klavye Aktif",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Button(onClick = { onTurkishLayoutToggled(!userSettings.isTurkishLayout) }) {
                Text(if (userSettings.isTurkishLayout) "TR Q" else "EN US")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Kaydet & Kapat")
        }
    }
}
