/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.vnc

/**
 * This class is used for returning user credentials from callbacks.
 */
data class UserCredential(
        @JvmField val username: String = "",
        @JvmField val password: String = ""
)