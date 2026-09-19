/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.ui.about

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vncandroid.free.R
import com.vncandroid.free.util.EdgeToEdgeHelper

/**
 * Activity for app details.
 */
class AboutActivity : AppCompatActivity() {

    companion object {
        const val GIT_REPO_URL = "https://github.com/Vicky8106/vnc-android-free"
        const val BUG_REPORT_URL = "https://github.com/Vicky8106/vnc-android-free/issues/new"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EdgeToEdgeHelper.setContentView(this, R.layout.activity_about)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_host, AboutFragment())
                    .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}