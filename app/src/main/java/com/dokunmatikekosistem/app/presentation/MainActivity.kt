package com.dokunmatikekosistem.app.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dokunmatikekosistem.app.R
import java.util.Locale
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

    val locale = remember(userSettings.appLanguage) { Locale.forLanguageTag(userSettings.appLanguage) }
    val configuration = LocalConfiguration.current
    val localizedConfig = remember(configuration, locale) {
        Configuration(configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val context = LocalContext.current
    val localizedContext = remember(context, localizedConfig) {
        context.createConfigurationContext(localizedConfig)
    }

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
        LocalContext provides localizedContext
    ) {
        val isLandscape = localizedConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

        Surface(modifier = Modifier.fillMaxSize(), color = if (isFullscreen) Color.Black else Color(0xFF121214)) {
            if (isFullscreen) {
                TouchpadScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = if (isLandscape) 4.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 8.dp)
                ) {
                    if (isLandscape) {
                        // Landscape: Single consolidated top bar (Status, 4 Tabs, Action buttons)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1B1B22))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(
                                connectionState = connectionState,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            // Center 4 Tabs
                            Row(
                                modifier = Modifier
                                    .weight(2f, fill = false)
                                    .widthIn(max = 420.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF24242F))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                AppTab.entries.forEach { tab ->
                                    val selected = tab == currentTab
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { viewModel.selectTab(tab) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(tab.titleRes),
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            TopActionButtons(
                                currentTab = currentTab,
                                viewModel = viewModel,
                                onConnectRequested = onConnectRequested,
                                onOpenSettings = { showSettingsSheet = true }
                            )
                        }
                    } else {
                        // Portrait: Two separate rows
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1B1B22))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(
                                connectionState = connectionState,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TopActionButtons(
                                currentTab = currentTab,
                                viewModel = viewModel,
                                onConnectRequested = onConnectRequested,
                                onOpenSettings = { showSettingsSheet = true }
                            )
                        }

                        // Symmetrical 4-Tab Segmented Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1B1B22))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            AppTab.entries.forEach { tab ->
                                val selected = tab == currentTab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { viewModel.selectTab(tab) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(tab.titleRes),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
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
                CompositionLocalProvider(
                    LocalConfiguration provides localizedConfig,
                    LocalContext provides localizedContext
                ) {
                    SettingsSheetContent(
                        userSettings = userSettings,
                        onLanguageSelected = { viewModel.setAppLanguage(it) },
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
    }
}

@Composable
private fun StatusBadge(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (statusColor, statusText) = when (connectionState) {
        ConnectionState.CONNECTED -> Color(0xFF4CAF50) to stringResource(R.string.status_connected)
        ConnectionState.REGISTERING -> Color(0xFFFFB300) to stringResource(R.string.status_connecting)
        ConnectionState.REGISTERED -> Color(0xFF29B6F6) to stringResource(R.string.status_ready)
        ConnectionState.DISCONNECTED -> Color(0xFFE53935) to stringResource(R.string.status_disconnected)
        ConnectionState.ERROR -> Color(0xFFE53935) to stringResource(R.string.status_error)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF252530))
            .padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = statusText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopActionButtons(
    currentTab: AppTab,
    viewModel: MainViewModel,
    onConnectRequested: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentTab == AppTab.TOUCHPAD) {
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2B2B38))
                    .clickable { viewModel.setFullscreen(true) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.action_fullscreen),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Box(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onConnectRequested)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.action_pair),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                softWrap = false
            )
        }

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2B2B38))
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.action_settings),
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun SettingsSheetContent(
    userSettings: UserSettings,
    onLanguageSelected: (String) -> Unit,
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
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        // Language Selector
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_language_desc),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLanguageSelected("en") }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = userSettings.appLanguage == "en",
                    onClick = { onLanguageSelected("en") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_lang_en),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLanguageSelected("tr") }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = userSettings.appLanguage == "tr",
                    onClick = { onLanguageSelected("tr") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_lang_tr),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        HorizontalDivider()

        // Cursor Speed Slider
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_cursor_speed),
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
                text = stringResource(R.string.settings_cursor_speed_desc),
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
                    text = stringResource(R.string.settings_scroll_speed),
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
                text = stringResource(R.string.settings_scroll_speed_desc),
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
                    text = stringResource(R.string.settings_auto_reconnect),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_auto_reconnect_desc),
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
                text = stringResource(R.string.settings_three_finger),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_three_finger_desc),
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
                        text = stringResource(action.titleRes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        HorizontalDivider()

        // 4 Finger Tap Section
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.settings_four_finger),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_four_finger_desc),
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
                        text = stringResource(action.titleRes),
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
                    text = stringResource(R.string.settings_zoom_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_zoom_desc),
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
                    text = stringResource(R.string.settings_keyboard_layout),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (userSettings.isTurkishLayout) stringResource(R.string.settings_layout_turkish) else stringResource(R.string.settings_layout_english),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Button(
                onClick = { onTurkishLayoutToggled(!userSettings.isTurkishLayout) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (userSettings.isTurkishLayout) "TR Q" else "EN US")
            }
        }

        HorizontalDivider()

        // About & Support Section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E1E26)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "v1.0.0",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(R.string.settings_about_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                val context = LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/ahmetselmancloud/touchpad-ekosistem")
                            )
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_github), fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://buymeacoffee.com/ahmetselman")
                            )
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF813F)),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text(stringResource(R.string.settings_coffee), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_close))
        }
    }
}
