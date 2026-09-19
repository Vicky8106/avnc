/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.ui.vnc

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.vncandroid.free.ui.vnc.gl.Renderer
import com.vncandroid.free.viewmodel.VncViewModel
import com.vncandroid.free.vnc.VncClient

/**
 * This class renders the VNC framebuffer on screen.
 *
 * It derives from [GLSurfaceView], which creates an EGL Display, where we can
 * render the framebuffer using OpenGL ES. See [GLSurfaceView] for more details.
 *
 * Actual rendering is done by [Renderer], which is executed on a dedicated
 * thread by [GLSurfaceView].
 *
 *
 *-   +-------------------+          +--------------------+         +--------------------+
 *-   |   [FrameView]     |          |  [VncViewModel]    |         |   [VncClient]      |
 *-   +--------+----------+          +----------+---------+         +----------+---------+
 *-            |                                |                              |
 *-            |                                |                              |
 *-            | Render Request                 | [FrameState]                 | Framebuffer
 *-            |                                v                              |
 *-            |                     +----------+---------+                    |
 *-            +-------------------> |     [Renderer]     | <------------------+
 *-                                  +--------------------+

 */
class FrameView(context: Context?, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    /**
     * Should be called from [VncActivity.onCreate].
     */
    fun initialize(viewModel: VncViewModel) {
        setEGLContextClientVersion(2)
        setRenderer(Renderer(viewModel))
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}