/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.util

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Contract for openable documents.
 * This is needed because OpenDocument doesn't specify [Intent.CATEGORY_OPENABLE]
 */
class OpenableDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
    }
}