package com.dokunmatikekosistem.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_THREE_FINGER_TAP = stringPreferencesKey("three_finger_tap_action")
        val KEY_FOUR_FINGER_TAP = stringPreferencesKey("four_finger_tap_action")
        val KEY_ZOOM_ENABLED = booleanPreferencesKey("zoom_enabled")
        val KEY_TURKISH_LAYOUT = booleanPreferencesKey("is_turkish_layout")
        val KEY_CURSOR_SPEED = floatPreferencesKey("cursor_speed")
        val KEY_SCROLL_SPEED = floatPreferencesKey("scroll_speed")
        val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KEY_LAST_DEVICE_ADDRESS = stringPreferencesKey("last_connected_device_address")
    }

    val userSettingsFlow: Flow<UserSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val threeTapName = prefs[KEY_THREE_FINGER_TAP] ?: TapAction.MIDDLE_CLICK.name
            val fourTapName = prefs[KEY_FOUR_FINGER_TAP] ?: TapAction.NOTIFICATION_CENTER.name
            val zoom = prefs[KEY_ZOOM_ENABLED] ?: true
            val trLayout = prefs[KEY_TURKISH_LAYOUT] ?: true
            val cursor = prefs[KEY_CURSOR_SPEED] ?: 1.0f
            val scroll = prefs[KEY_SCROLL_SPEED] ?: 1.0f
            val autoRec = prefs[KEY_AUTO_RECONNECT] ?: true
            val lastAddr = prefs[KEY_LAST_DEVICE_ADDRESS]

            UserSettings(
                threeFingerTapAction = runCatching { TapAction.valueOf(threeTapName) }.getOrDefault(TapAction.MIDDLE_CLICK),
                fourFingerTapAction = runCatching { TapAction.valueOf(fourTapName) }.getOrDefault(TapAction.NOTIFICATION_CENTER),
                zoomEnabled = zoom,
                isTurkishLayout = trLayout,
                cursorSpeed = cursor,
                scrollSpeed = scroll,
                autoReconnect = autoRec,
                lastDeviceAddress = lastAddr
            )
        }

    suspend fun setThreeFingerTapAction(action: TapAction) {
        dataStore.edit { prefs ->
            prefs[KEY_THREE_FINGER_TAP] = action.name
        }
    }

    suspend fun setFourFingerTapAction(action: TapAction) {
        dataStore.edit { prefs ->
            prefs[KEY_FOUR_FINGER_TAP] = action.name
        }
    }

    suspend fun setZoomEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ZOOM_ENABLED] = enabled
        }
    }

    suspend fun setTurkishLayout(isTurkish: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_TURKISH_LAYOUT] = isTurkish
        }
    }

    suspend fun setCursorSpeed(speed: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_CURSOR_SPEED] = speed
        }
    }

    suspend fun setScrollSpeed(speed: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_SCROLL_SPEED] = speed
        }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_RECONNECT] = enabled
        }
    }

    suspend fun setLastDeviceAddress(address: String?) {
        dataStore.edit { prefs ->
            if (address != null) {
                prefs[KEY_LAST_DEVICE_ADDRESS] = address
            } else {
                prefs.remove(KEY_LAST_DEVICE_ADDRESS)
            }
        }
    }
}
