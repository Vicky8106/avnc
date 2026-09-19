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
