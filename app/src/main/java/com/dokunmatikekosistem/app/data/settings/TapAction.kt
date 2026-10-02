package com.dokunmatikekosistem.app.data.settings

import androidx.annotation.StringRes
import com.dokunmatikekosistem.app.R

/**
 * Defines available actions that can be mapped to 3-finger or 4-finger taps.
 */
enum class TapAction(@param:StringRes val titleRes: Int, val displayName: String) {
    MIDDLE_CLICK(R.string.action_middle_click, "Middle Click (New Tab)"),
    WINDOWS_SEARCH(R.string.action_windows_search, "Windows Search (Win + S)"),
    SHOW_DESKTOP(R.string.action_show_desktop, "Show Desktop (Win + D)"),
    NOTIFICATION_CENTER(R.string.action_notification_center, "Action Center (Win + N)"),
    DISABLED(R.string.action_disabled, "Disabled")
}

