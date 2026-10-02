package com.dokunmatikekosistem.app.presentation

import androidx.annotation.StringRes
import com.dokunmatikekosistem.app.R

enum class AppTab(@param:StringRes val titleRes: Int, val title: String) {
    TOUCHPAD(R.string.tab_touchpad, "Touchpad"),
    NUMPAD(R.string.tab_numpad, "Numpad"),
    MEDIA(R.string.tab_media, "Media"),
    KEYBOARD(R.string.tab_keyboard, "Keyboard")
}

