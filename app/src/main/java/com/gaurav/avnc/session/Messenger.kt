/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.session

import android.graphics.PointF
import android.util.Log
import com.gaurav.avnc.vnc.PointerButton
import com.gaurav.avnc.vnc.VncClient
import com.gaurav.avnc.vnc.XKeySym
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

    fun sendKeyPress(keySym: Int, xtCode: Int = 0, pressDurationMs: Long = 6L, withShift: Boolean = false): Boolean {
        return execute {
            if (withShift) {
                client.sendKeyEvent(XKeySym.XK_Shift_L, 0, true)
                try { Thread.sleep(2) } catch (_: InterruptedException) {}
            }
            client.sendKeyEvent(keySym, xtCode, true)
            if (pressDurationMs > 0) {
                try {
                    Thread.sleep(pressDurationMs)
                } catch (_: InterruptedException) {
                }
            }
            client.sendKeyEvent(keySym, xtCode, false)
            if (withShift) {
                try { Thread.sleep(2) } catch (_: InterruptedException) {}
                client.sendKeyEvent(XKeySym.XK_Shift_L, 0, false)
            }
        }
    }

    fun releaseAllModifiers() {
        execute {
            client.sendKeyEvent(XKeySym.XK_Shift_L, 0, false)
            client.sendKeyEvent(XKeySym.XK_Shift_R, 0, false)
            client.sendKeyEvent(XKeySym.XK_Control_L, 0, false)
            client.sendKeyEvent(XKeySym.XK_Control_R, 0, false)
            client.sendKeyEvent(XKeySym.XK_Alt_L, 0, false)
            client.sendKeyEvent(XKeySym.XK_Alt_R, 0, false)
            client.sendKeyEvent(XKeySym.XK_Meta_L, 0, false)
            client.sendKeyEvent(XKeySym.XK_Meta_R, 0, false)
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