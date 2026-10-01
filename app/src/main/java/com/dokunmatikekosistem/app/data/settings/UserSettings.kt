package com.dokunmatikekosistem.app.data.settings

/**
 * Holds user-configurable preferences.
 */
data class UserSettings(
    val threeFingerTapAction: TapAction = TapAction.MIDDLE_CLICK,
    val fourFingerTapAction: TapAction = TapAction.NOTIFICATION_CENTER,
    val zoomEnabled: Boolean = true,
    val isTurkishLayout: Boolean = true
)
