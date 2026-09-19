/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ToggleButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.gaurav.avnc.ui.vnc.input.InputHandler
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.util.getClipboardText
import com.gaurav.avnc.util.isTrue
import com.gaurav.avnc.util.toggleKeyboard
import com.gaurav.avnc.vnc.XKeySym
import com.gaurav.avnc.vnc.XKeySymUnicode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sign

/**
 * Virtual keys allow the user to input keys which are not normally found on
 * keyboards but can be useful for controlling remote server.
 *
 * This class manages the state and actions of virtual keys, rendered via Jetpack Compose.
 */
class VirtualKeys(private val activity: VncActivity, private val inputHandler: InputHandler) {

    val viewModel = activity.viewModel
    val pref = activity.viewModel.pref
    private val inputView get() = activity.binding.inputView

    val isVisibleState = mutableStateOf(false)
    val isTextModeState = mutableStateOf(false)
    val textInputState = mutableStateOf("")

    val activeToggleKeys = mutableStateMapOf<VirtualKey, Boolean>()
    val lockedToggleKeys = mutableStateMapOf<VirtualKey, Boolean>()

    private var openedWithKb = false
    private var closedByPiPMode = false
    private var isInitialized = false
    private var sendTextJob: Job? = null

    val container: View? get() = runCatching { activity.binding.virtualKeysComposeView }.getOrNull()
    val isVisible: Boolean get() = isVisibleState.value

    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        isTextModeState.value = pref.runInfo.virtualKeysTextBoxVisible
        inputHandler.onAfterKeyEventListeners += ::onAfterKeyEvent
        viewModel.inPiPMode.observe(activity) { onPiPModeChanged(it) }
    }

    fun show(saveVisibility: Boolean = false) {
        initialize()
        isVisibleState.value = true
        container?.visibility = View.VISIBLE
        if (saveVisibility) pref.runInfo.showVirtualKeys = true
    }

    fun hide(saveVisibility: Boolean = false) {
        isVisibleState.value = false
        container?.visibility = View.GONE
        openedWithKb = false
        if (saveVisibility) pref.runInfo.showVirtualKeys = false
    }

    fun setTextMode(enabled: Boolean) {
        isTextModeState.value = enabled
        pref.runInfo.virtualKeysTextBoxVisible = enabled
        if (!enabled) {
            inputView.requestFocus()
        }
    }

    fun onKeyboardOpen() {
        if (pref.input.vkOpenWithKeyboard && !isVisible) {
            show()
            openedWithKb = true
        }
    }

    fun onKeyboardClose() {
        if (openedWithKb) {
            hide()
            openedWithKb = false
        }
        if (isTextModeState.value) {
            inputView.requestFocus()
        }
    }

    fun onConnected() {
        if (pref.runInfo.showVirtualKeys && !viewModel.inPiPMode.isTrue) {
            show()
        }
    }

    fun toggleKeyboard() {
        toggleKeyboard(inputView)
    }

    fun releaseMetaKeys() {
        val activeKeys = activeToggleKeys.filter { it.value }.keys.toList()
        activeToggleKeys.clear()
        lockedToggleKeys.clear()
        activeKeys.forEach { vk ->
            vk.keyCode?.let { sendKey(it, false) }
        }
        viewModel.messenger?.releaseAllModifiers()
    }

    private fun releaseUnlockedMetaKeys() {
        val unlockedKeys = activeToggleKeys.filter { it.value && lockedToggleKeys[it.key] != true }.keys.toList()
        if (unlockedKeys.isNotEmpty()) {
            unlockedKeys.forEach { vk ->
                activeToggleKeys[vk] = false
                vk.keyCode?.let { sendKey(it, false) }
            }
            if (activeToggleKeys.values.none { it }) {
                viewModel.messenger?.releaseAllModifiers()
            }
        }
    }

    private fun onAfterKeyEvent(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_UP && !KeyEvent.isModifierKey(event.keyCode)) {
            releaseUnlockedMetaKeys()
        }
    }

    private fun onPiPModeChanged(inPiPMode: Boolean) {
        if (inPiPMode && isVisible) {
            hide()
            closedByPiPMode = true
        } else if (!inPiPMode && closedByPiPMode) {
            show()
            closedByPiPMode = false
        }
    }

    fun onToggleKeyClick(vk: VirtualKey) {
        val keyCode = vk.keyCode ?: return
        if ((keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT) && pref.input.vkUseSuperWithSingleTap) {
            sendKey(keyCode)
            return
        }

        val isCurrentlyChecked = activeToggleKeys[vk] == true
        if (isCurrentlyChecked) {
            activeToggleKeys[vk] = false
            lockedToggleKeys[vk] = false
            sendKey(keyCode, false)
            if (activeToggleKeys.values.none { it }) {
                viewModel.messenger?.releaseAllModifiers()
            }
        } else {
            activeToggleKeys[vk] = true
            sendKey(keyCode, true)
        }
    }

    fun onToggleKeyLongClick(vk: VirtualKey) {
        val keyCode = vk.keyCode ?: return
        val isLocked = lockedToggleKeys[vk] == true
        if (isLocked) {
            lockedToggleKeys[vk] = false
            activeToggleKeys[vk] = false
            sendKey(keyCode, false)
            if (activeToggleKeys.values.none { it }) {
                viewModel.messenger?.releaseAllModifiers()
            }
        } else {
            lockedToggleKeys[vk] = true
            activeToggleKeys[vk] = true
            sendKey(keyCode, true)
        }
    }

    fun handleTextBoxAction(text: String) {
        if (text.isEmpty()) {
            sendKey(KeyEvent.KEYCODE_ENTER)
            return
        }
        sendTextToServer(text)
        textInputState.value = ""
    }

    fun onPasteClick() {
        activity.lifecycleScope.launch {
            val clipText = getClipboardText(activity)
            if (!clipText.isNullOrEmpty()) {
                val clamped = if (clipText.length > 1000) clipText.substring(0, 1000) else clipText
                textInputState.value = clamped
            }
        }
    }

    fun sendTextToServer(text: String) {
        if (text.isEmpty()) return
        val clampedText = if (text.length > 1000) text.substring(0, 1000) else text

        // Replace all embedded newlines and carriage returns with spaces so copy-paste NEVER automatically hits Enter!
        // This ensures the entire text is pasted as a single continuous line, preventing commands or input
        // fields from prematurely executing at line breaks (e.g. at 200-300 characters).
        val sanitizedText = clampedText
            .replace("\r\n", " ")
            .replace('\r', ' ')
            .replace('\n', ' ')

        if (sanitizedText.isEmpty()) return

        // Release Meta keys to avoid interference with these key events
        releaseMetaKeys()
        viewModel.messenger?.releaseAllModifiers()

        // 1. Immediately sync full text to remote clipboard for direct paste
        viewModel.messenger?.sendClipboardText(sanitizedText)

        // Cancel previous streaming job so multiple paste/send actions never interleave keystrokes
        sendTextJob?.cancel()

        // 2. Stream individual keysyms directly to remote VNC server using native RFB keysyms
        sendTextJob = activity.lifecycleScope.launch(Dispatchers.Default) {
            val messenger = viewModel.messenger ?: return@launch
            // Standard USB HID polling intervals on physical BMCs/servers are 10-16ms.
            // 22ms pacing between key release and next key press ensures the remote controller
            // registers the key release and prevents buffer overflow.
            val pacingDelay = 22L

            try {
                var idx = 0
                while (idx < sanitizedText.length) {
                    if (!isActive) break
                    val codePoint = sanitizedText.codePointAt(idx)
                    idx += Character.charCount(codePoint)

                    val keySym = when (codePoint) {
                        '\t'.code -> XKeySym.XK_Tab
                        '\b'.code -> XKeySym.XK_BackSpace
                        else -> {
                            val legacy = XKeySymUnicode.getLegacyKeySymForUnicodeChar(codePoint)
                            if (legacy != 0) legacy else XKeySymUnicode.getKeySymForUnicodeChar(codePoint)
                        }
                    }

                    if (keySym != 0) {
                        messenger.sendKeyPress(keySym, 0, pressDurationMs = 18L)
                        delay(pacingDelay)
                    }
                }
            } finally {
                messenger.releaseAllModifiers()
            }
        }
    }

    fun sendKey(keyCode: Int) {
        activity.lifecycleScope.launch(Dispatchers.Default) {
            sendKey(keyCode, true)
            try {
                delay(18L)
            } catch (_: Exception) {
            }
            sendKey(keyCode, false)
        }
    }

    fun sendKey(keyCode: Int, isDown: Boolean) {
        val action = if (isDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        inputHandler.onVkKeyEvent(KeyEvent(action, keyCode))
    }
}

/**
 * NOTE: Names of these enums may be persisted in app preferences. So if any key name
 *       is ever modified, add a migration to handle old name.
 */
enum class VirtualKey(
        /**
         * [KeyEvent] keycode to be generated when this key is pressed.
         */
        val keyCode: Int? = null,

        /**
         * If key name is not appropriate for UI, use this to set the label.
         */
        val label: String? = null,

        /**
         * If icon is set, this key will be rendered as an ImageButton.
         */
        val icon: Int? = null,

        /**
         * Short description of the key, if the label itself isn't sufficient.
         */
        val description: String? = null,

        val isToggle: Boolean = false,
) {

    // Special actions
    ToggleKeyboard(description = "Toggle keyboard", icon = R.drawable.ic_keyboard),
    CloseKeys(description = "Close virtual keys", icon = R.drawable.ic_clear),

    // Meta keys
    LeftShift(keyCode = KeyEvent.KEYCODE_SHIFT_LEFT, label = "Shift", isToggle = true),
    LeftCtrl(keyCode = KeyEvent.KEYCODE_CTRL_LEFT, label = "Ctrl", isToggle = true),
    LeftAlt(keyCode = KeyEvent.KEYCODE_ALT_LEFT, label = "Alt", isToggle = true),
    LeftSuper(keyCode = KeyEvent.KEYCODE_META_LEFT, label = "Super", icon = R.drawable.ic_super_key, isToggle = true),
    CapsLock(keyCode = KeyEvent.KEYCODE_CAPS_LOCK, label = "Caps", isToggle = true),

    Esc(keyCode = KeyEvent.KEYCODE_ESCAPE),
    Tab(keyCode = KeyEvent.KEYCODE_TAB),
    Home(keyCode = KeyEvent.KEYCODE_MOVE_HOME),
    End(keyCode = KeyEvent.KEYCODE_MOVE_END),
    PgUp(keyCode = KeyEvent.KEYCODE_PAGE_UP),
    PgDn(keyCode = KeyEvent.KEYCODE_PAGE_DOWN),
    Insert(keyCode = KeyEvent.KEYCODE_INSERT),
    Delete(keyCode = KeyEvent.KEYCODE_FORWARD_DEL),

    // Arrow keys
    Left(keyCode = KeyEvent.KEYCODE_DPAD_LEFT, icon = R.drawable.ic_keyboard_arrow_left),
    Right(keyCode = KeyEvent.KEYCODE_DPAD_RIGHT, icon = R.drawable.ic_keyboard_arrow_right),
    Up(keyCode = KeyEvent.KEYCODE_DPAD_UP, icon = R.drawable.ic_keyboard_arrow_up),
    Down(keyCode = KeyEvent.KEYCODE_DPAD_DOWN, icon = R.drawable.ic_keyboard_arrow_down),

    F1(keyCode = KeyEvent.KEYCODE_F1),
    F2(keyCode = KeyEvent.KEYCODE_F2),
    F3(keyCode = KeyEvent.KEYCODE_F3),
    F4(keyCode = KeyEvent.KEYCODE_F4),
    F5(keyCode = KeyEvent.KEYCODE_F5),
    F6(keyCode = KeyEvent.KEYCODE_F6),
    F7(keyCode = KeyEvent.KEYCODE_F7),
    F8(keyCode = KeyEvent.KEYCODE_F8),
    F9(keyCode = KeyEvent.KEYCODE_F9),
    F10(keyCode = KeyEvent.KEYCODE_F10),
    F11(keyCode = KeyEvent.KEYCODE_F11),
    F12(keyCode = KeyEvent.KEYCODE_F12),
}

/**
 * Users can change the layout of keys in app settings.
 * Layout configuration is stored as a simple list of key-names.
 */
object VirtualKeyLayoutConfig {

    private val DEFAULT_LAYOUT = listOf(VirtualKey.ToggleKeyboard, VirtualKey.CloseKeys, VirtualKey.Esc, VirtualKey.LeftSuper,
                                        VirtualKey.Tab, VirtualKey.LeftCtrl, VirtualKey.LeftShift, VirtualKey.LeftAlt,
                                        VirtualKey.CapsLock,
                                        VirtualKey.Home, VirtualKey.Left, VirtualKey.Up, VirtualKey.Down, VirtualKey.End,
                                        VirtualKey.Right, VirtualKey.PgUp, VirtualKey.PgDn)

    /**
     * In older versions, before users could customize key layout, there was a pref to
     * 'Show all' keys. This layout is used for compatibility with that pref.
     */
    private val DEFAULT_LAYOUT_ALL = DEFAULT_LAYOUT +
                                     listOf(VirtualKey.Insert, VirtualKey.Delete, VirtualKey.F1, VirtualKey.F2, VirtualKey.F3,
                                            VirtualKey.F4, VirtualKey.F5, VirtualKey.F6, VirtualKey.F7, VirtualKey.F8,
                                            VirtualKey.F9, VirtualKey.F10, VirtualKey.F11, VirtualKey.F12)


    fun getDefaultLayout(pref: AppPreferences): List<VirtualKey> {
        return if (pref.input.vkShowAll) DEFAULT_LAYOUT_ALL else DEFAULT_LAYOUT
    }

    fun getLayout(pref: AppPreferences): List<VirtualKey> {
        runCatching {
            pref.input.vkLayout?.let { vkLayout ->
                vkLayout.split(',').map { VirtualKey.valueOf(it) }.let { keys ->
                    check(keys.isNotEmpty())
                    return keys
                }
            }
        }.onFailure { Log.e(javaClass.simpleName, "Error parsing key layout [${pref.input.vkLayout}]: ", it) }

        return getDefaultLayout(pref)
    }

    fun setLayout(pref: AppPreferences, keys: List<VirtualKey>) {
        if (keys == getDefaultLayout(pref) && pref.input.vkLayout != null) {
            // Restoring the defaults, so simply remove the pref.
            // Pref is only used if user changes the default layout.
            pref.input.vkLayout = null
            return
        }

        if (keys == getLayout(pref))
            return   // Nothing changed

        pref.input.vkLayout = keys.joinToString(",") { it.name }
    }
}

/**
 * Factory for creating individual key [View]s.
 * Retained for compatibility with [com.gaurav.avnc.ui.prefs.VirtualKeysEditor].
 */
object VirtualKeyViewFactory {

    /**
     * There are three types of Views that are generated:
     *
     * [ToggleButton] - if [key] is a toggle
     * [ImageButton]  - if [key] has an icon (label will be ignored)
     * [Button]       - in all other cases
     */
    fun create(context: Context, key: VirtualKey): View {
        val view = if (key.isToggle) createToggle(context, key) else createSimple(context, key)
        view.layoutParams = GridLayout.LayoutParams().apply {
            width = GridLayout.LayoutParams.WRAP_CONTENT
            height = GridLayout.LayoutParams.WRAP_CONTENT
            setGravity(Gravity.CENTER)
        }
        return view
    }

    private fun createSimple(context: Context, key: VirtualKey): View {
        return if (key.icon != null)
            ImageButton(context, null, 0, selectStyle(key))
                    .apply {
                        setImageDrawable(ContextCompat.getDrawable(context, key.icon))
                        contentDescription = getDescription(key)
                    }
        else
            Button(context, null, 0, selectStyle(key))
                    .apply { text = getLabel(key) }
    }

    private fun createToggle(context: Context, key: VirtualKey): View {
        val view = ToggleButton(context, null, 0, selectStyle(key))
        view.isClickable = true

        if (key.icon != null) {
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(key.icon, 0, 0, 0)
            view.contentDescription = getDescription(key)
        } else {
            val label = getLabel(key)
            view.text = label
            view.textOff = label
            view.textOn = label
        }

        return view
    }

    private fun selectStyle(key: VirtualKey): Int {
        if (key == VirtualKey.CloseKeys || key == VirtualKey.ToggleKeyboard)
            return R.style.VirtualKey_Special

        if (key.isToggle) {
            return if (key.icon != null) R.style.VirtualKey_Toggle_Image else R.style.VirtualKey_Toggle
        }

        return R.style.VirtualKey
    }

    private fun getLabel(virtualKey: VirtualKey) = virtualKey.label ?: virtualKey.name
    private fun getDescription(virtualKey: VirtualKey) = virtualKey.description ?: getLabel(virtualKey)
}

/**
 * Simple extension to add hook for Copy action.
 */
class VkEditText(context: Context, attributeSet: AttributeSet? = null) : AppCompatEditText(context, attributeSet) {

    var onTextCopyListener: (() -> Unit)? = null

    override fun onTextContextMenuItem(id: Int): Boolean {
        val result = super.onTextContextMenuItem(id)
        if (result && (id == android.R.id.cut || id == android.R.id.copy)) {
            onTextCopyListener?.invoke()
        }
        return result
    }
}

/**
 * Stock [HorizontalScrollView] intercepts all scroll events irrespective of whether
 * it can actually scroll or not. It makes it unsuitable for use as child/parent of
 * another horizontally scrollable View, e.g. ViewPager.
 *
 * [NestableHorizontalScrollView] fixes this by only intercepting events when it is scrollable.
 */
class NestableHorizontalScrollView(context: Context, attributeSet: AttributeSet? = null) :
        HorizontalScrollView(context, attributeSet) {
    private var hScrollDirection = 0
    private val gestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            hScrollDirection = 0
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            hScrollDirection = distanceX.sign.toInt()
            return true
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        if (hScrollDirection != 0 && !canScrollHorizontally(hScrollDirection))
            return false

        return super.onInterceptTouchEvent(ev)
    }
}
