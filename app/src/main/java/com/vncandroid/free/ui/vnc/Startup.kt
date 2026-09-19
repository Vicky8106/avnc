/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.ui.vnc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.os.BundleCompat
import com.vncandroid.free.model.ServerProfile
import com.vncandroid.free.model.db.MainDb
import com.vncandroid.free.model.db.ServerProfileDao
import com.vncandroid.free.viewmodel.VncViewModel
import com.vncandroid.free.vnc.VncUri


/**********************************************************************************************
 * [VncActivity] startup utilities
 *********************************************************************************************/
private const val PROFILE_KEY = "com.vncandroid.free.server_profile"
private const val PROFILE_ID_KEY = "com.vncandroid.free.server_profile_id"

fun Bundle.putProfile(profile: ServerProfile) {
    putParcelable(PROFILE_KEY, profile)
}

fun Bundle.getProfile(): ServerProfile? {
    return BundleCompat.getParcelable(this, PROFILE_KEY, ServerProfile::class.java)
}

fun createVncIntent(context: Context, profile: ServerProfile): Intent {
    return Intent(context, VncActivity::class.java).apply {
        if (profile.isSaved())
            putExtra(PROFILE_ID_KEY, profile.ID)
        else
            putExtra(PROFILE_KEY, profile)
    }
}

fun startVncActivity(source: Activity, profile: ServerProfile) {
    source.startActivity(createVncIntent(source, profile))
}

fun startVncActivity(source: Activity, uri: VncUri) {
    startVncActivity(source, uri.toServerProfile())
}


/**********************************************************************************************
 * Argument parser
 *********************************************************************************************/
sealed class StartupArg {
    data class Profile(val profile: ServerProfile) : StartupArg()
    data class ProfileId(val id: Long) : StartupArg()
}

class MissingStartupArgException : Exception()

fun parseStartupArg(intent: Intent, savedState: Bundle?): StartupArg {
    // Prefer to use profile if available to keep changes across activity restarts.
    val profile = savedState?.getProfile() ?: intent.extras?.getProfile()
    if (profile != null)
        return StartupArg.Profile(profile.copy())  //Create a copy to avoid modification to source profile


    val id = intent.getLongExtra(PROFILE_ID_KEY, 0)
    if (id != 0L)
        return StartupArg.ProfileId(id)

    throw MissingStartupArgException()
}

/**********************************************************************************************
 * Session start
 *********************************************************************************************/

class InvalidProfileIdException(val id: Long) : Exception("Error: Invalid Server ID")

suspend fun startSession(startupArg: StartupArg, viewModel: VncViewModel) {
    val dao = MainDb.getInstance(viewModel.app).serverProfileDao
    val profile = loadProfile(startupArg, dao)
    viewModel.initConnection(profile)
}

private suspend fun loadProfile(startupArg: StartupArg, dao: ServerProfileDao): ServerProfile {
    when (startupArg) {
        is StartupArg.Profile -> return startupArg.profile
        is StartupArg.ProfileId -> return dao.getByID(startupArg.id)
                                          ?: throw InvalidProfileIdException(startupArg.id)
    }
}