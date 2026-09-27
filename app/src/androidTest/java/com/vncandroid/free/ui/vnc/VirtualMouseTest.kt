/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.ui.vnc

import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vncandroid.free.CleanPrefsRule
import com.vncandroid.free.R
import com.vncandroid.free.VncSessionTest
import com.vncandroid.free.doClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualMouseTest : VncSessionTest() {

    @JvmField
    @Rule
    val prefsRule = CleanPrefsRule()

    @Test
    fun backgroundingPreservesMouseState() {
        // Use a non-touchpad gesture style so the mouse override is observable.
        vncSession.profile.gestureStyle = "touchscreen"
        vncSession.run {
            vncSession.onActivity { activity ->
                assertTrue(activity.virtualMouse.isVisible)
                activity.virtualMouse.expand()
            }

            // Simulate switching the app to background and back.
            vncSession.activityScenario?.moveToState(Lifecycle.State.CREATED)
            vncSession.onActivity { activity ->
                assertTrue(activity.virtualMouse.isVisible)
                assertTrue(activity.virtualMouse.isExpandedState.value)
                assertEquals("touchpad", activity.viewModel.activeGestureStyle.value)
            }

            vncSession.activityScenario?.moveToState(Lifecycle.State.RESUMED)
            vncSession.onActivity { activity ->
                assertTrue(activity.virtualMouse.isVisible)
                assertTrue(activity.virtualMouse.isExpandedState.value)
            }
        }
    }

    @Test
    fun keyboardOpenDoesNotForceMouseVisible() {
        vncSession.run {
            vncSession.onActivity { activity ->
                activity.virtualMouse.hide()
            }

            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.keyboard_btn)).doClick()

            vncSession.onActivity { activity ->
                assertFalse(activity.virtualMouse.isVisible)
            }
        }
    }
}
