package com.dokunmatikekosistem.app.data.settings

/**
 * Defines available actions that can be mapped to 3-finger or 4-finger taps.
 */
enum class TapAction(val displayName: String) {
    MIDDLE_CLICK("Orta Tık (Yeni Sekme)"),
    WINDOWS_SEARCH("Windows Arama (Win + S)"),
    SHOW_DESKTOP("Masaüstünü Göster (Win + D)"),
    NOTIFICATION_CENTER("Bildirim Merkezi (Win + N)"),
    DISABLED("Devre Dışı")
}
