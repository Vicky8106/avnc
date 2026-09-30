/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.util

import android.view.View
import androidx.core.view.SoftwareKeyboardControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


fun isKeyboardVisible(view: View): Boolean {
    return ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
}

fun showKeyboard(view: View) {
    SoftwareKeyboardControllerCompat(view).show()
}

fun hideKeyboard(view: View) {
    SoftwareKeyboardControllerCompat(view).hide()
}

fun toggleKeyboard(view: View) {
    if (isKeyboardVisible(view))
        hideKeyboard(view)
    else
        showKeyboard(view)
}

/**
 * Determines whether typing the given character requires the Shift modifier
 * on a standard keyboard layout (US QWERTY / physical BMC HID).
 */
fun isShiftNeeded(codePoint: Int): Boolean {
    if (codePoint in 'A'.code..'Z'.code) return true
    if (Character.isUpperCase(codePoint)) return true
    if (codePoint < 128) {
        val c = codePoint.toChar()
        return c in "~!@#$%^&*()_+{}|:\"<>?"
    }
    if (Character.isBmpCodePoint(codePoint)) {
        try {
            val kcm = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD)
            val events = kcm.getEvents(charArrayOf(codePoint.toChar()))
            if (events != null && events.size == 4 &&
                (events[0].keyCode == android.view.KeyEvent.KEYCODE_SHIFT_LEFT || events[0].keyCode == android.view.KeyEvent.KEYCODE_SHIFT_RIGHT)) {
                return true
            }
        } catch (_: Exception) {
        }
    }
    return false
}
