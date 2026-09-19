/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.ui.vnc.input

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import com.vncandroid.free.ui.vnc.VncActivity
import com.vncandroid.free.util.getClipboardText
import com.vncandroid.free.viewmodel.VncViewModel
import kotlinx.coroutines.launch

import android.view.KeyCharacterMap
import com.vncandroid.free.vnc.XKeySym
import com.vncandroid.free.vnc.XKeySymUnicode

/**
 * This is a simple, transparent view to handle input events.
 * It acts as an edit box to handle key events.
 */
class InputView(context: Context?, attrs: AttributeSet? = null) : View(context, attrs) {

    private var inputHandler: InputHandler? = null

    /**
     * Input connection used for intercepting key events
     */
    inner class InputConnection : BaseInputConnection(this, false) {
        private var lastPasteTime = 0L
        private var lastPasteText = ""

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    sendEnterKey()
                }
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_DEL) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    sendBackspaceKey()
                }
                return true
            }
            return inputHandler?.onKeyEvent(event) == true || super.sendKeyEvent(event)
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            sendEnterKey()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength > 0) {
                repeat(beforeLength) {
                    sendBackspaceKey()
                }
                return true
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (text.isNullOrEmpty()) {
                return super.commitText(text, newCursorPosition)
            }
            val str = text.toString()

            // 1. Enter key from keyboard (newline / carriage return)
            if (str == "\n" || str == "\r" || str == "\r\n") {
                sendEnterKey()
                return true
            }

            // 2. Space key
            if (str == " ") {
                sendSpaceKey()
                return true
            }

            // 3. Tab key
            if (str == "\t") {
                sendTabKey()
                return true
            }

            // 4. Backspace
            if (str == "\b") {
                sendBackspaceKey()
                return true
            }

            // 5. Single character or special symbol typed directly on keyboard
            if (str.codePointCount(0, str.length) == 1) {
                sendCharKey(str.codePointAt(0))
                return true
            }

            // 6. Bulk text / clipboard paste (length > 1)
            val now = SystemClock.uptimeMillis()
            if (now - lastPasteTime < 400L && lastPasteText == str) {
                return true
            }
            lastPasteTime = now
            lastPasteText = str
            (context as? VncActivity)?.sendTextToServer(str)
            return true
        }

        private fun sendEnterKey() {
            val messenger = (context as? VncActivity)?.viewModel?.messenger
            if (messenger != null) {
                messenger.sendKeyPress(XKeySym.XK_Return, 0, 18L)
            } else {
                inputHandler?.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                inputHandler?.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }

        private fun sendSpaceKey() {
            val messenger = (context as? VncActivity)?.viewModel?.messenger
            if (messenger != null) {
                messenger.sendKeyPress(XKeySym.XK_space, 0, 18L)
            } else {
                inputHandler?.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
                inputHandler?.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE))
            }
        }

        private fun sendTabKey() {
            val messenger = (context as? VncActivity)?.viewModel?.messenger
            if (messenger != null) {
                messenger.sendKeyPress(XKeySym.XK_Tab, 0, 18L)
            } else {
                inputHandler?.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
                inputHandler?.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB))
            }
        }

        private fun sendBackspaceKey() {
            val messenger = (context as? VncActivity)?.viewModel?.messenger
            if (messenger != null) {
                messenger.sendKeyPress(XKeySym.XK_BackSpace, 0, 18L)
            } else {
                inputHandler?.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                inputHandler?.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        }

        private fun sendCharKey(codePoint: Int) {
            val messenger = (context as? VncActivity)?.viewModel?.messenger
            val keySym = when (codePoint) {
                '\n'.code, '\r'.code -> XKeySym.XK_Return
                ' '.code -> XKeySym.XK_space
                '\t'.code -> XKeySym.XK_Tab
                '\b'.code -> XKeySym.XK_BackSpace
                else -> {
                    val legacy = XKeySymUnicode.getLegacyKeySymForUnicodeChar(codePoint)
                    if (legacy != 0) legacy else XKeySymUnicode.getKeySymForUnicodeChar(codePoint)
                }
            }
            if (keySym != 0) {
                if (messenger != null) {
                    messenger.sendKeyPress(keySym, 0, 18L)
                } else {
                    inputHandler?.onKeyEvent(KeyEvent(0L, String(Character.toChars(codePoint)), KeyCharacterMap.VIRTUAL_KEYBOARD, 0))
                }
            }
        }

        override fun performContextMenuAction(id: Int): Boolean {
            if (id == android.R.id.paste) {
                (context as? VncActivity)?.let { activity ->
                    activity.lifecycleScope.launch {
                        val clip = getClipboardText(activity)
                        if (!clip.isNullOrEmpty()) {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastPasteTime < 400L && lastPasteText == clip) {
                                return@launch
                            }
                            lastPasteTime = now
                            lastPasteText = clip
                            activity.sendTextToServer(clip)
                        }
                    }
                }
                return true
            }
            return super.performContextMenuAction(id)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setWillNotDraw(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
    }

    /**
     * Should be called from [com.vncandroid.free.ui.vnc.VncActivity.onCreate].
     */
    fun initialize(viewModel: VncViewModel, inputHandler: InputHandler) {
        this.inputHandler = inputHandler

        // Hide local cursor if requested and supported
        if (Build.VERSION.SDK_INT >= 24 && viewModel.pref.input.hideLocalCursor)
            pointerIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        return InputConnection()
    }

    override fun onCheckIsTextEditor(): Boolean {
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return inputHandler?.onTouchEvent(event) == true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return inputHandler?.onGenericMotionEvent(event) == true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        return inputHandler?.onHoverEvent(event) == true
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        return inputHandler?.onCapturedPointerEvent(event) == true
    }
}