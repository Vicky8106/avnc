/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.ui.home

import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.vncandroid.free.R
import com.vncandroid.free.databinding.ActivityUrlBinding
import com.vncandroid.free.ui.vnc.startVncActivity
import com.vncandroid.free.util.EdgeToEdgeHelper
import com.vncandroid.free.util.setClipboardTextWithNotification
import com.vncandroid.free.viewmodel.HomeViewModel
import com.vncandroid.free.viewmodel.UrlBarViewModel
import com.vncandroid.free.vnc.VncUri

/**
 * Activity allowing user to directly connect to a server.
 *
 * Possible future improvements:
 * - Keep history of recent entries
 * - Show suggestions from discovered servers.
 */
class UrlBarActivity : AppCompatActivity() {

    private val viewModel by viewModels<UrlBarViewModel>()
    private val homeViewModel by viewModels<HomeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)

        val binding = EdgeToEdgeHelper.setDataBindingContentView<ActivityUrlBinding>(this, R.layout.activity_url)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        binding.url.setOnEditorActionListener { _, _, _ -> go(binding.url.text.toString()) }
        binding.backBtn.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.clearBtn.setOnClickListener {
            if (binding.url.text.isEmpty())
                finish()
            else
                binding.url.setText("")
        }

        binding.servers.setSource(this, viewModel.filteredServers, homeViewModel.rediscoveredProfiles)
        binding.servers.itemAnimator?.addDuration = 0 // Disable "flashing" of added items
        binding.servers.onCopyServerName = { setClipboardTextWithNotification(it.name) }
        binding.servers.onCopyServerHost = { setClipboardTextWithNotification(it.host) }
        binding.servers.onServerClick = {
            startVncActivity(this, it)
            finish()
        }

        binding.url.requestFocus()
    }

    private fun go(url: String): Boolean {
        if (url.isBlank())
            return false

        val processed = processIPv6(url)
        val uri = VncUri(processed)

        if (uri.host.isNullOrBlank()) {
            Toast.makeText(this, R.string.msg_invalid_vnc_uri, Toast.LENGTH_SHORT).show()
            return false
        }

        startVncActivity(this, uri)
        finish()
        return true
    }

    /**
     * For IPv6, [VncUri] expects host address to be wrapped in square brackets.
     * We apply some heuristics to detect IPv6 address and add brackets if they
     * are missing.
     */
    private fun processIPv6(url: String): String {
        //we only want to process IP address literals without path/query
        if (url.contains('/') || url.contains('?') || url.contains('#'))
            return url

        //might already contain brackets
        if (url.contains('[') || url.contains(']'))
            return url

        //handle most common cases
        if (url.contains("::") || url.count { it == ':' } > 2)
            return "[$url]"

        return url
    }
}