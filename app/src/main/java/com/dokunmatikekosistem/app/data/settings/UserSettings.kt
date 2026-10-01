package com.dokunmatikekosistem.app.data.settings

/**
 * Holds user-configurable preferences.
 */
data class UserSettings(
    val threeFingerTapAction: TapAction = TapAction.MIDDLE_CLICK,
    val fourFingerTapAction: TapAction = TapAction.NOTIFICATION_CENTER,
    val zoomEnabled: Boolean = true,
    val isTurkishLayout: Boolean = true,
    val cursorSpeed: Float = 1.0f,
    val scrollSpeed: Float = 1.0f,
    val autoReconnect: Boolean = true,
    val lastDeviceAddress: String? = null
)
