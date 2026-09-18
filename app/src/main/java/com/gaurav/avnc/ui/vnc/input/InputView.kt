/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.input

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
import com.gaurav.avnc.ui.vnc.VncActivity
import com.gaurav.avnc.util.getClipboardText
import com.gaurav.avnc.viewmodel.VncViewModel
import kotlinx.coroutines.launch

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
            return inputHandler?.onKeyEvent(event) == true || super.sendKeyEvent(event)
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (!text.isNullOrEmpty()) {
                val str = text.toString()
                val now = SystemClock.uptimeMillis()
                val normalized = str.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').trim()
                if (now - lastPasteTime < 2000L && (lastPasteText == normalized || lastPasteText == str)) {
                    return true
                }
                lastPasteTime = now
                lastPasteText = normalized
                (context as? VncActivity)?.sendTextToServer(str)
                return true
            }
            return super.commitText(text, newCursorPosition)
        }

        override fun performContextMenuAction(id: Int): Boolean {
            if (id == android.R.id.paste) {
                (context as? VncActivity)?.let { activity ->
                    activity.lifecycleScope.launch {
                        val clip = getClipboardText(activity)
                        if (!clip.isNullOrEmpty()) {
                            val now = SystemClock.uptimeMillis()
                            val normalized = clip.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').trim()
                            if (now - lastPasteTime < 2000L && (lastPasteText == normalized || lastPasteText == clip)) {
                                return@launch
                            }
                            lastPasteTime = now
                            lastPasteText = normalized
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
     * Should be called from [com.gaurav.avnc.ui.vnc.VncActivity.onCreate].
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