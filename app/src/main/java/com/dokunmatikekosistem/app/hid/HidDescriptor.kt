package com.dokunmatikekosistem.app.hid

/**
 * Composite HID report descriptor: Report ID 1 = relative mouse (buttons + X/Y),
 * Report ID 2 = standard boot keyboard (modifier byte + 6-key array).
 * Faz 0 only sends mouse reports; the keyboard section exists so Windows is
 * tested against the same composite descriptor the final product will use.
 */
object HidDescriptor {
    const val MOUSE_REPORT_ID: Byte = 1
    const val KEYBOARD_REPORT_ID: Byte = 2

    val DESCRIPTOR: ByteArray = byteArrayOf(
        // Mouse (Report ID 1)
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x02,             // Usage (Mouse)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), MOUSE_REPORT_ID, // Report ID (1)
        0x09, 0x01,             // Usage (Pointer)
        0xA1.toByte(), 0x00,    // Collection (Physical)
        0x05, 0x09,             //   Usage Page (Buttons)
        0x19, 0x01,             //   Usage Minimum (1)
        0x29, 0x03,             //   Usage Maximum (3)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x95.toByte(), 0x03,    //   Report Count (3)
        0x75, 0x01,             //   Report Size (1)
        0x81.toByte(), 0x02,    //   Input (Data,Var,Abs)
        0x95.toByte(), 0x01,    //   Report Count (1)
        0x75, 0x05,             //   Report Size (5) - padding
        0x81.toByte(), 0x03,    //   Input (Const,Var,Abs)
        0x05, 0x01,             //   Usage Page (Generic Desktop)
        0x09, 0x30,             //   Usage (X)
        0x09, 0x31,             //   Usage (Y)
        0x15, 0x81.toByte(),    //   Logical Minimum (-127)
        0x25, 0x7F,             //   Logical Maximum (127)
        0x75, 0x08,             //   Report Size (8)
        0x95.toByte(), 0x02,    //   Report Count (2)
        0x81.toByte(), 0x06,    //   Input (Data,Var,Rel)
        0xC0.toByte(),          // End Collection (Physical)
        0xC0.toByte(),          // End Collection (Application)

        // Keyboard (Report ID 2) - boot format, not populated with real data in Faz 0
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x06,             // Usage (Keyboard)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), KEYBOARD_REPORT_ID, // Report ID (2)
        0x05, 0x07,             //   Usage Page (Key Codes)
        0x19, 0xE0.toByte(),    //   Usage Minimum (224)
        0x29, 0xE7.toByte(),    //   Usage Maximum (231)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95.toByte(), 0x08,    //   Report Count (8) - modifier byte
        0x81.toByte(), 0x02,    //   Input (Data,Var,Abs)
        0x95.toByte(), 0x01,    //   Report Count (1)
        0x75, 0x08,             //   Report Size (8) - reserved byte
        0x81.toByte(), 0x01,    //   Input (Const)
        0x95.toByte(), 0x06,    //   Report Count (6)
        0x75, 0x08,             //   Report Size (8)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x65,             //   Logical Maximum (101)
        0x05, 0x07,             //   Usage Page (Key Codes)
        0x19, 0x00,             //   Usage Minimum (0)
        0x29, 0x65,             //   Usage Maximum (101)
        0x81.toByte(), 0x00,    //   Input (Data,Array)
        0xC0.toByte()           // End Collection
    )
}
