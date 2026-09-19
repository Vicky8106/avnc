/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free

import android.app.Application
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import com.vncandroid.free.util.AppPreferences

class App : Application() {

    @Keep
    lateinit var prefs: AppPreferences

    override fun onCreate() {
        super.onCreate()
        configureLeakCanary()

        prefs = AppPreferences(this)
        prefs.ui.theme.observeForever { updateNightMode(it) }
    }

    private fun updateNightMode(theme: String) {
        val nightMode = when (theme) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun configureLeakCanary() {
        if (BuildConfig.DEBUG) {
            Class.forName("com.vncandroid.free.LeakCanaryInitializer")
                    .getMethod("initialize", Application::class.java)
                    .invoke(null, this)
        }
    }
}