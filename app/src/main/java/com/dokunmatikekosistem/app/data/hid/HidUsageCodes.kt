package com.dokunmatikekosistem.app.data.hid

/**
 * Standard USB HID Usage Tables (Page 0x07 - Keyboard/Keypad)
 */
object HidUsageCodes {
    // Alphanumeric / Editing
    const val KEY_A = 0x04
    const val KEY_B = 0x05
    const val KEY_C = 0x06
    const val KEY_D = 0x07
    const val KEY_N = 0x11
    const val KEY_S = 0x16
    const val KEY_V = 0x19
    const val KEY_X = 0x1B
    const val KEY_Z = 0x1D
    const val KEY_ESC = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_F5 = 0x3E
    const val KEY_PAGE_UP = 0x4B
    const val KEY_DELETE = 0x4C
    const val KEY_PAGE_DOWN = 0x4E
    const val KEY_RIGHT_ARROW = 0x4F
    const val KEY_LEFT_ARROW = 0x50

    // Keypad (Numpad)
    const val KEYPAD_NUM_LOCK = 0x53
    const val KEYPAD_SLASH = 0x54     // /
    const val KEYPAD_ASTERISK = 0x55  // *
    const val KEYPAD_HYPHEN = 0x56    // -
    const val KEYPAD_PLUS = 0x57      // +
    const val KEYPAD_ENTER = 0x58     // Enter
    const val KEYPAD_1 = 0x59
    const val KEYPAD_2 = 0x5A
    const val KEYPAD_3 = 0x5B
    const val KEYPAD_4 = 0x5C
    const val KEYPAD_5 = 0x5D
    const val KEYPAD_6 = 0x5E
    const val KEYPAD_7 = 0x5F
    const val KEYPAD_8 = 0x60
    const val KEYPAD_9 = 0x61
    const val KEYPAD_0 = 0x62
    const val KEYPAD_DOT = 0x63       // .
}
