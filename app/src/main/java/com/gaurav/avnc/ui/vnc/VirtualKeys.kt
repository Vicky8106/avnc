/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ToggleButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.isVisible
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.VirtualKeysBinding
import com.gaurav.avnc.ui.vnc.input.InputHandler
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.util.addOnGlobalLayoutListener
import com.gaurav.avnc.util.isTrue
import com.gaurav.avnc.util.toggleKeyboard
import com.gaurav.avnc.util.getClipboardText
import com.gaurav.avnc.vnc.XKeySym
import com.gaurav.avnc.vnc.XKeySymUnicode
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sign


/**
 * Virtual keys allow the user to input keys which are not normally found on
 * keyboards but can be useful for controlling remote server.
 *
 * This class manages the inflation & visibility of virtual keys.
 */
class VirtualKeys(private val activity: VncActivity, private val inputHandler: InputHandler) {

    private val viewModel = activity.viewModel
    private val pref = activity.viewModel.pref
    private val inputView = activity.binding.inputView
    private val stub = activity.binding.virtualKeysStub
    private val toggleKeys = mutableSetOf<ToggleButton>()
    private val lockedToggleKeys = mutableSetOf<ToggleButton>()
    private val keyCharMap by lazy { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }
    private var openedWithKb = false
    private var closedByPiPMode = false

    val container: View? get() = stub.root

    fun show(saveVisibility: Boolean = false) {
        init()
        container?.visibility = View.VISIBLE
        if (saveVisibility) pref.runInfo.showVirtualKeys = true
    }

    fun hide(saveVisibility: Boolean = false) {
        container?.visibility = View.GONE
        openedWithKb = false //Reset flag
        if (saveVisibility) pref.runInfo.showVirtualKeys = false
    }

    fun onKeyboardOpen() {
        if (pref.input.vkOpenWithKeyboard && container?.visibility != View.VISIBLE) {
            show()
            openedWithKb = true
        }
    }

    fun onKeyboardClose() {
        if (openedWithKb) {
            hide()
            openedWithKb = false
        }

        // Scenario: User uses the TextBox to send text to server, and hides the keyboard. User
        // wants to end the session now, so he swipes-up from bottom to bring up the nav bar, but
        // the TextBox also sees that swipe-up and it shows the keyboard. Now tap on Back navigation
        // button will hide the keyboard instead of ending the session. User must switch away from
        // text-page to break this loop. So we clear the focus here to avoid this issue.
        (stub.binding as? VirtualKeysBinding)?.textBox?.let { if (it.isFocused) it.clearFocus() }
    }

    fun onConnected() {
        if (pref.runInfo.showVirtualKeys && !viewModel.inPiPMode.isTrue)
            show()
    }

    fun releaseMetaKeys() {
        toggleKeys.forEach {
            if (it.isChecked)
                it.isChecked = false
        }
    }

    private fun releaseUnlockedMetaKeys() {
        toggleKeys.forEach {
            if (it.isChecked && !lockedToggleKeys.contains(it))
                it.isChecked = false
        }
    }

    private fun onAfterKeyEvent(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_UP && !KeyEvent.isModifierKey(event.keyCode))
            releaseUnlockedMetaKeys()
    }

    private fun onPiPModeChanged(inPiPMode: Boolean) {
        if (inPiPMode && container?.isVisible == true) {
            hide()
            closedByPiPMode = true
        } else if (!inPiPMode && closedByPiPMode) {
            show()
            closedByPiPMode = false
        }
    }

    private fun init() {
        if (stub.isInflated)
            return

        stub.viewStub?.inflate()
        val binding = stub.binding as VirtualKeysBinding
        initTextPage(binding)
        initKeys(binding)
        initPager(binding)
        inputHandler.onAfterKeyEventListeners += ::onAfterKeyEvent
        viewModel.inPiPMode.observe(activity) { onPiPModeChanged(it) }
    }

    /**
     * To keep everything in single XML layout file, things are done in a slightly weird way.
     * Both keys & text pages are initially attached to temporary View. After inflation, they
     * are detached and passed onto ViewPager adapter. Adapter will insert them at proper place.
     */
    private fun initPager(binding: VirtualKeysBinding) {
        val root = binding.root
        val keys = binding.keys
        val pager = binding.pager
        val pages = listOf(binding.keysPage, binding.textPage)

        binding.tmpPageHost.apply {
            removeAllViews()
            (parent as ViewGroup).removeView(this)
        }

        // Setup pager
        pager.offscreenPageLimit = pages.size
        pager.adapter = object : PagerAdapter() {
            override fun getCount() = pages.size
            override fun isViewFromObject(view: View, obj: Any) = (view === obj)
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                pages[position].let {
                    container.addView(it)
                    return it
                }
            }

            override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
                container.removeView(obj as View)
            }
        }
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            val textPageIndex = pages.indexOf(binding.textPage)
            override fun onPageSelected(position: Int) {
                if (ViewCompat.getRootWindowInsets(root)?.isVisible(Type.ime()) == true) {
                    if (position == textPageIndex) binding.textBox.requestFocus()
                    else inputView.requestFocus()
                }
                pref.runInfo.virtualKeysTextBoxVisible = (position == textPageIndex)
            }
        })

        // Setup Layout. Keys grid is the primary View used for deciding size of Virtual keys.
        // All keys are shown if screen is wide enough. Otherwise width is limited to FrameView,
        // and HorizontalScrollView is relied upon to access all keys.
        // NOTE: Paddings in root/pager view is NOT handled by this code.

        // Start with something sane
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED).let { keys.measure(it, it) }
        root.layoutParams = root.layoutParams.apply { width = keys.measuredWidth; height = keys.measuredHeight }

        // Update size after layout changes
        addOnGlobalLayoutListener(activity, keys) {
            val w = min(keys.width, inputView.width)
            val h = keys.height
            if (w > 0 && h > 0 && (root.width != w || root.height != h))
                root.layoutParams = root.layoutParams.apply { width = w; height = h }
        }

        // Switch to text page if it was active last time
        if (pref.runInfo.virtualKeysTextBoxVisible)
            pager.setCurrentItem(pages.indexOf(binding.textPage), false)
    }


    private fun initTextPage(binding: VirtualKeysBinding) {
        binding.textPageBackBtn.setOnClickListener {
            binding.pager.setCurrentItem(0, true)
        }
        binding.textBox.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                handleTextBoxAction(binding.textBox)
                true
            } else {
                false
            }
        }
        binding.textBox.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) inputView.requestFocus()
        }
        binding.textBox.onTextCopyListener = {
            viewModel.sendClipboardText()
        }
        binding.textPageSendBtn.setOnClickListener {
            handleTextBoxAction(binding.textBox)
        }
        binding.textPagePasteBtn.setOnClickListener {
            activity.lifecycleScope.launch {
                val clipText = getClipboardText(activity)
                if (!clipText.isNullOrEmpty()) {
                    val clamped = if (clipText.length > 1000) clipText.substring(0, 1000) else clipText
                    binding.textBox.setText(clamped)
                    binding.textBox.setSelection(clamped.length)
                }
            }
        }
    }

    private fun initKeys(binding: VirtualKeysBinding) {
        binding.keys.rowCount = pref.input.vkRowCount
        VirtualKeyLayoutConfig.getLayout(pref).forEach { vk ->
            val view = VirtualKeyViewFactory.create(binding.root.context, vk)
            binding.keys.addView(view)

            if (vk == VirtualKey.ToggleKeyboard) {
                view.setOnClickListener { toggleKeyboard(inputView) }
            } else if (vk == VirtualKey.CloseKeys) {
                view.setOnClickListener { hide(true) }
            } else if (vk.keyCode != null) {
                if (view is ToggleButton)
                    initToggleKey(view, vk.keyCode)
                else
                    initNormalKey(view, vk.keyCode)
            }
        }
    }


    private fun initToggleKey(key: ToggleButton, keyCode: Int) {
        key.setOnCheckedChangeListener { _, isChecked ->
            sendKey(keyCode, isChecked)
            if (!isChecked) lockedToggleKeys.remove(key)
        }
        key.setOnLongClickListener {
            key.toggle()
            if (key.isChecked) lockedToggleKeys.add(key)
            true
        }

        if ((keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT) && pref.input.vkUseSuperWithSingleTap)
            key.setOnClickListener {
                key.isChecked = true
                key.isChecked = false
            }

        toggleKeys.add(key)
    }

    private fun initNormalKey(key: View, keyCode: Int) {
        check(key !is ToggleButton) { "use initToggleKey()" }
        key.setOnClickListener { sendKey(keyCode) }
        makeKeyRepeatable(key)
    }

    /**
     * When a View is touched, we schedule a callback to to simulate a click.
     * As long as finger stays on the view, we keep repeating this callback.
     */
    private fun makeKeyRepeatable(keyView: View) {
        keyView.setOnTouchListener(object : View.OnTouchListener {
            private var doRepeat = false

            private fun repeat(v: View) {
                if (doRepeat) {
                    v.performClick()
                    v.postDelayed({ repeat(v) }, ViewConfiguration.getKeyRepeatDelay().toLong())
                }
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        doRepeat = true
                        v.postDelayed({ repeat(v) }, ViewConfiguration.getKeyRepeatTimeout().toLong())
                    }

                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        doRepeat = false
                    }
                }
                return false
            }
        })
    }

    private var sendTextJob: Job? = null

    private fun handleTextBoxAction(textBox: EditText) {
        val text = textBox.text?.toString() ?: return
        if (text.isEmpty()) return
        sendTextToServer(text)
        textBox.setText("")
    }

    private fun requiresShift(codePoint: Int): Boolean {
        if (codePoint in 'A'.code..'Z'.code) return true
        return when (codePoint.toChar()) {
            '~', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
            '_', '+', '{', '}', '|', ':', '"', '<', '>', '?' -> true
            else -> false
        }
    }

    fun sendTextToServer(text: String) {
        if (text.isEmpty()) return
        val clampedText = if (text.length > 1000) text.substring(0, 1000) else text

        // Release Meta keys to avoid interference with these key events
        releaseMetaKeys()
        viewModel.messenger?.releaseAllModifiers()

        // 1. Immediately sync full text to remote clipboard for direct paste
        if (clampedText.length > 1) {
            viewModel.messenger?.sendClipboardText(clampedText)
        }

        // Cancel previous streaming job so multiple paste/send actions never interleave keystrokes
        sendTextJob?.cancel()

        // 2. Stream individual keysyms directly to remote VNC server using native RFB keysyms
        sendTextJob = activity.lifecycleScope.launch(Dispatchers.Default) {
            val messenger = viewModel.messenger ?: return@launch
            val pacingDelay = if (clampedText.length > 100) 8L else 12L

            // Strip trailing newlines and carriage returns so pasting text never automatically clicks Enter!
            val textToStream = clampedText.trimEnd('\r', '\n')

            var idx = 0
            while (idx < textToStream.length) {
                if (!isActive) break
                val codePoint = textToStream.codePointAt(idx)
                idx += Character.charCount(codePoint)

                // Skip standalone \r, return is handled on \n
                if (codePoint == '\r'.code) continue

                val withShift = requiresShift(codePoint)

                val keySym = when (codePoint) {
                    '\n'.code -> XKeySym.XK_Return
                    '\t'.code -> XKeySym.XK_Tab
                    '\b'.code -> XKeySym.XK_BackSpace
                    else -> {
                        val legacy = XKeySymUnicode.getLegacyKeySymForUnicodeChar(codePoint)
                        if (legacy != 0) legacy else XKeySymUnicode.getKeySymForUnicodeChar(codePoint)
                    }
                }

                messenger.sendKeyPress(keySym, 0, 6L, withShift = withShift)
                delay(pacingDelay)
            }
        }
    }

    private fun sendKey(keyCode: Int) {
        sendKey(keyCode, true)
        sendKey(keyCode, false)
    }

    private fun sendKey(keyCode: Int, isDown: Boolean) {
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
    /**
     * Direction of current horizontal scrolling.
     * See [canScrollHorizontally].
     */
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
