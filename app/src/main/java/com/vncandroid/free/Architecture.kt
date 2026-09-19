/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free

import com.vncandroid.free.model.ServerProfile
import com.vncandroid.free.model.db.MainDb
import com.vncandroid.free.ui.about.AboutActivity
import com.vncandroid.free.ui.home.HomeActivity
import com.vncandroid.free.ui.home.ServerTabs
import com.vncandroid.free.ui.home.UrlBarActivity
import com.vncandroid.free.ui.prefs.PrefsActivity
import com.vncandroid.free.ui.vnc.input.Dispatcher
import com.vncandroid.free.ui.vnc.FrameState
import com.vncandroid.free.ui.vnc.FrameView
import com.vncandroid.free.ui.vnc.ConfirmationDialog
import com.vncandroid.free.ui.vnc.LoginFragment
import com.vncandroid.free.ui.vnc.VirtualKeys
import com.vncandroid.free.ui.vnc.VncActivity
import com.vncandroid.free.viewmodel.HomeViewModel
import com.vncandroid.free.viewmodel.PrefsViewModel
import com.vncandroid.free.viewmodel.VncViewModel
import com.vncandroid.free.viewmodel.service.Discovery
import com.vncandroid.free.viewmodel.service.SshClient
import com.vncandroid.free.session.Messenger
import com.vncandroid.free.vnc.VncClient

/**
 * This is a brain dump of the design & architecture of this app.
 *
 *
 * Overview
 * ========
 *
 * There are three main layers:
 *
 *- +---------------------------------------------------------------------------------------------------------------+
 *-  UI
 *-
 *-                 +------------------+      +------------------+      +------------------+     +------------------+
 *-                 |  [HomeActivity]  |      |  [VncActivity]   |      |  [PrefsActivity] |     |  [AboutActivity] |
 *-                 +--------+---------+      +--------+---------+      +--------+---------+     +------------------+
 *-                          |                         |                         |
 *-                          |                         |                         |
 *- +------------------------|-------------------------|-------------------------|----------------------------------+
 *-  ViewModel               |                         |                         |
 *-                          v                         v                         v
 *-                 +------------------+      +------------------+      +------------------+
 *-                 |  [HomeViewModel] |      |  [VncViewModel]  |      | [PrefsViewModel] |
 *-                 +------------------+      +------------------+      +------------------+
 *-                          A                         A                                         +--------------+
 *-                          |                         |                                         |   Services   |
 *-                          |                         |                                         +--------------+
 *- +------------------------|-------------------------|------------------------------------------------------------+
 *-  Model & Client          |                         |
 *-                          V                         V
 *-                 +------------------+      +------------------+
 *-                 |  [ServerProfile] |      |   [VncClient]    |
 *-                 |                  |      |                  |
 *-                 |     Database     |      |   LibVNCClient   |
 *-                 +------------------+      +------------------+
 *-
 *- +---------------------------------------------------------------------------------------------------------------+
 *
 *
 * Home
 * ====
 *
 * [HomeActivity] is the main activity of the app. Components:
 *
 * - A urlbar, which launches [UrlBarActivity], allowing user to quickly connect
 *   to a server without creating a profile for it.
 *
 * - Lists of saved & discovered servers, in [ServerTabs].
 *
 * - Profile editors used for creating/editing [ServerProfile].
 *
 *
 * VNC UI
 * ======
 *
 * [VncActivity] is responsible for driving the connection to VNC server.
 *
 * - [FrameView] renders the VNC framebuffer on screen.
 * - [FrameState] maintains information related to framebuffer rendering,
 *   like zoom, pan etc.
 *
 * - See [Dispatcher] for overview of input handling.
 * - [VirtualKeys] are used for keys not normally found on Android Keyboards.
 *
 *
 * VNC Connection
 * ==============
 *
 * - Connection to VNC server is managed by [VncViewModel], using [VncClient].
 * - [VncClient] is a wrapper around native `rfbClient` from LibVNCClient.
 *
 * - [LoginFragment] is used to ask username & password from user.
 * - [SshClient] is used to create a SSH tunnel, which can be used for connection.
 * - [ConfirmationDialog] is used to verify unknown SSH hosts and X509 certs with user.
 * -
 * - [Messenger] is used to send events to VNC server.
 *
 *
 * Database
 * ========
 *
 * We use a Room Database, [MainDb], to save list of servers.
 * Servers are modeled by the [ServerProfile] entity.
 *
 *
 * Services
 * ========
 *
 * These are sort-of standalone components which perform a particular task:
 *
 * - Server discovery ([Discovery])
 * - SSH Tunnel ([SshClient])
 * - Import/Export (in [PrefsViewModel])
 *
 * Note: These are NOT Android Services (these are called services for lack of a better word).
 */
private fun vnc() {
}