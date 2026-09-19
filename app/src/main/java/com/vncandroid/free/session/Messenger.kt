/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.session

import android.graphics.PointF
import android.util.Log
import com.vncandroid.free.vnc.PointerButton
import com.vncandroid.free.vnc.VncClient
import com.vncandroid.free.vnc.XKeySym
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Allows sending different types of messages to remote server.
 */
class Messenger(private val client: VncClient) {

    /**************************************************************************
     * Sender thread
     **************************************************************************/
    private val sender = Executors.newSingleThreadExecutor()

    private fun execute(action: Runnable): Boolean {
        try {
            if (client.connected && !sender.isShutdown) {
                sender.execute(action)
                return true
            }
        } catch (e: Exception) {
            Log.w("Messenger", "Failed to enqueue action [isShutdown: ${sender.isShutdown}]: ${e.message}")
        }
        return false
    }

    fun shutdown() {
        runCatching {
            sender.shutdown()
            sender.awaitTermination(60, TimeUnit.SECONDS)
        }
        if (!sender.isTerminated)
            Log.w("Messenger", "Unable to shutdown messenger thread")
    }


    /**************************************************************************
     * Input events
     **************************************************************************/

    /**
     * Keeps track of current pointer button state.
     */
    private var pointerButtonMask: Int = 0

    private fun sendPointerEvent(mask: Int, p: PointF) {
        val x = p.x.toInt()
        val y = p.y.toInt()
        client.moveClientPointer(x, y)
        execute { client.sendPointerEvent(x, y, mask) }
    }

    fun sendPointerButtonDown(button: PointerButton, p: PointF) {
        pointerButtonMask = pointerButtonMask or button.bitMask
        sendPointerEvent(pointerButtonMask, p)
    }

    fun sendPointerButtonUp(button: PointerButton, p: PointF) {
        pointerButtonMask = pointerButtonMask and button.bitMask.inv()
        sendPointerEvent(pointerButtonMask, p)
    }

    fun sendPointerButtonRelease(p: PointF) {
        if (pointerButtonMask != 0) {
            pointerButtonMask = 0
            sendPointerEvent(pointerButtonMask, p)
        }
    }

    fun sendKey(keySym: Int, xtCode: Int, isDown: Boolean): Boolean {
        return execute { client.sendKeyEvent(keySym, xtCode, isDown) }
    }

    fun sendKeyPress(keySym: Int, xtCode: Int = 0, pressDurationMs: Long = 18L): Boolean {
        return execute {
            client.sendKeyEvent(keySym, xtCode, true)
            if (pressDurationMs > 0) {
                try {
                    Thread.sleep(pressDurationMs)
                } catch (_: InterruptedException) {
                }
            }
            client.sendKeyEvent(keySym, xtCode, false)
        }
    }

    fun releaseAllModifiers() {
        execute {
            val modifiers = intArrayOf(
                XKeySym.XK_Shift_L,
                XKeySym.XK_Shift_R,
                XKeySym.XK_Control_L,
                XKeySym.XK_Control_R,
                XKeySym.XK_Alt_L,
                XKeySym.XK_Alt_R,
                XKeySym.XK_Meta_L,
                XKeySym.XK_Meta_R,
                XKeySym.XK_Caps_Lock,
                XKeySym.XK_space
            )
            for (sym in modifiers) {
                client.sendKeyEvent(sym, 0, false)
                try {
                    Thread.sleep(3)
                } catch (_: InterruptedException) {
                }
            }
        }
    }

    fun insertButtonUpDelay() {
        execute { runCatching { Thread.sleep(200) } }
    }

    /**************************************************************************
     * Misc
     **************************************************************************/

    fun sendClipboardText(text: String) {
        execute { client.sendCutText(text) }
    }

    fun setDesktopSize(width: Int, height: Int) {
        execute { client.setDesktopSize(width, height) }
    }

    fun refreshFrameBuffer() {
        execute { client.refreshFrameBuffer() }
    }

    fun setFrameBufferUpdatesPaused(pause: Boolean) {
        execute { client.setFrameBufferUpdatesPaused(pause) }
    }
}