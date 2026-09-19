/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.ui.vnc

import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vncandroid.free.R
import kotlinx.coroutines.delay

/**
 * Modern Jetpack Compose Material 3 Virtual Keys Overlay.
 * Designed with a RealVNC desktop-first ergonomic workflow:
 * - Direct Fn toggle with expandable full Function Keys strip (F1 to F12)
 * - Dedicated Windows button (Win) with start menu trigger & modifier latching
 * - Dedicated forward Delete key (Del)
 * - RealVNC Inverted-T Aligned Arrow Cluster with generous touch spacing
 * - Enlarged Scroll Up & Scroll Down controls with hold-to-scroll acceleration
 * - One-tap Virtual Mouse toggle mode
 * - 20% transparent surface (alpha = 0.80f)
 */
@Composable
fun VirtualKeysOverlay(
    virtualKeys: VirtualKeys,
    modifier: Modifier = Modifier
) {
    val isVisible by virtualKeys.isVisibleState
    val isFnMode by virtualKeys.isFnModeState

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // 1. Expandable Function Keys Strip (F1 - F12)
                AnimatedVisibility(
                    visible = isFnMode,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    FunctionKeysStrip(virtualKeys = virtualKeys)
                }

                // 2. Primary Control & RealVNC Navigation Bar
                MainControlsBar(virtualKeys = virtualKeys)
            }
        }
    }
}

@Composable
private fun FunctionKeysStrip(
    virtualKeys: VirtualKeys,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tag badge: "FN"
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(end = 2.dp)
        ) {
            Text(
                text = "FN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        // Group 1: F1 - F4
        FunctionKeyButton("F1", KeyEvent.KEYCODE_F1, virtualKeys)
        FunctionKeyButton("F2", KeyEvent.KEYCODE_F2, virtualKeys)
        FunctionKeyButton("F3", KeyEvent.KEYCODE_F3, virtualKeys)
        FunctionKeyButton("F4", KeyEvent.KEYCODE_F4, virtualKeys)

        // Visual Separator between clusters
        Spacer(modifier = Modifier.width(6.dp))

        // Group 2: F5 - F8
        FunctionKeyButton("F5", KeyEvent.KEYCODE_F5, virtualKeys)
        FunctionKeyButton("F6", KeyEvent.KEYCODE_F6, virtualKeys)
        FunctionKeyButton("F7", KeyEvent.KEYCODE_F7, virtualKeys)
        FunctionKeyButton("F8", KeyEvent.KEYCODE_F8, virtualKeys)

        // Visual Separator between clusters
        Spacer(modifier = Modifier.width(6.dp))

        // Group 3: F9 - F12
        FunctionKeyButton("F9", KeyEvent.KEYCODE_F9, virtualKeys)
        FunctionKeyButton("F10", KeyEvent.KEYCODE_F10, virtualKeys)
        FunctionKeyButton("F11", KeyEvent.KEYCODE_F11, virtualKeys)
        FunctionKeyButton("F12", KeyEvent.KEYCODE_F12, virtualKeys)
    }
}

@Composable
private fun FunctionKeyButton(
    label: String,
    keyCode: Int,
    virtualKeys: VirtualKeys,
    modifier: Modifier = Modifier
) {
    var isPressedVisual by remember { mutableStateOf(false) }
    var isHolding by remember { mutableStateOf(false) }

    val repeatTimeout = remember { ViewConfiguration.getKeyRepeatTimeout().toLong().coerceAtLeast(280L) }
    val repeatDelay = remember { ViewConfiguration.getKeyRepeatDelay().toLong().coerceAtLeast(40L) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            virtualKeys.sendKey(keyCode)
            delay(repeatTimeout)
            while (isHolding) {
                virtualKeys.sendKey(keyCode)
                delay(repeatDelay)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPressedVisual) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
        },
        contentColor = if (isPressedVisual) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
            .height(32.dp)
            .defaultMinSize(minWidth = 42.dp)
            .pointerInput(keyCode) {
                detectTapGestures(
                    onPress = {
                        isPressedVisual = true
                        isHolding = true
                        tryAwaitRelease()
                        isHolding = false
                        isPressedVisual = false
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MainControlsBar(
    virtualKeys: VirtualKeys,
    modifier: Modifier = Modifier
) {
    val isFnMode by virtualKeys.isFnModeState
    val isMouseVisible by virtualKeys.virtualMouse.isVisibleState

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- ZONE 1: Quick Action / Mode Switchers (Keyboard, Mouse, Fn) ---
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Keyboard toggle
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                modifier = Modifier
                    .size(width = 38.dp, height = 34.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { virtualKeys.toggleKeyboard() })
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_keyboard),
                        contentDescription = "Toggle Keyboard",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Mouse toggle (RealVNC style)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isMouseVisible) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
                },
                border = if (isMouseVisible) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .size(width = 38.dp, height = 34.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { virtualKeys.toggleMouse() })
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mouse),
                        contentDescription = "Toggle Mouse Controls",
                        tint = if (isMouseVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Fn Toggle Button (Takes the place of old Send Text button!)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isFnMode) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
            },
            modifier = Modifier
                .size(width = 36.dp, height = 72.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { virtualKeys.toggleFnMode() })
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Fn",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFnMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )
                if (isFnMode) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                    )
                }
            }
        }

        // Section Divider
        BarDivider()

        // --- ZONE 2: Essential Desktop Controls (2 Rows) ---
        // Row 1: Esc, Tab, Win, Del
        // Row 2: Ctrl, Alt, Shift, Caps
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Esc, Tab, Win, Del
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StandardKeyButton("Esc", KeyEvent.KEYCODE_ESCAPE, virtualKeys, minWidth = 44.dp)
                StandardKeyButton("Tab", KeyEvent.KEYCODE_TAB, virtualKeys, minWidth = 44.dp)
                WindowsKeyButton(virtualKeys = virtualKeys, minWidth = 52.dp)
                DeleteKeyButton(virtualKeys = virtualKeys, minWidth = 46.dp)
            }

            // Row 2: Ctrl, Alt, Shift, Caps
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ModifierKeyButton(VirtualKey.LeftCtrl, virtualKeys, minWidth = 44.dp)
                ModifierKeyButton(VirtualKey.LeftAlt, virtualKeys, minWidth = 44.dp)
                ModifierKeyButton(VirtualKey.LeftShift, virtualKeys, minWidth = 52.dp)
                ModifierKeyButton(VirtualKey.CapsLock, virtualKeys, minWidth = 46.dp)
            }
        }

        // Section Divider
        BarDivider()

        // --- ZONE 3: RealVNC Aligned Navigation Cluster (Generous Spacing) ---
        // Left Column: Home & End
        // Center: Inverted-T Arrow Cluster (Up centered directly above Down, flanked by Left & Right)
        // Right Column: PgUp & PgDn
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Left Column: Home (top) & End (bottom)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StandardKeyButton("Home", KeyEvent.KEYCODE_MOVE_HOME, virtualKeys, minWidth = 46.dp)
                StandardKeyButton("End", KeyEvent.KEYCODE_MOVE_END, virtualKeys, minWidth = 46.dp)
            }

            // Center Column: Inverted-T Arrow Pad
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row: Up Arrow centered!
                ArrowKeyButton(
                    key = VirtualKey.Up,
                    icon = R.drawable.ic_keyboard_arrow_up,
                    description = "Up Arrow",
                    virtualKeys = virtualKeys,
                    width = 54.dp,
                    height = 34.dp
                )

                // Bottom Row: Left, Down, Right
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ArrowKeyButton(
                        key = VirtualKey.Left,
                        icon = R.drawable.ic_keyboard_arrow_left,
                        description = "Left Arrow",
                        virtualKeys = virtualKeys,
                        width = 48.dp,
                        height = 34.dp
                    )
                    ArrowKeyButton(
                        key = VirtualKey.Down,
                        icon = R.drawable.ic_keyboard_arrow_down,
                        description = "Down Arrow",
                        virtualKeys = virtualKeys,
                        width = 54.dp,
                        height = 34.dp
                    )
                    ArrowKeyButton(
                        key = VirtualKey.Right,
                        icon = R.drawable.ic_keyboard_arrow_right,
                        description = "Right Arrow",
                        virtualKeys = virtualKeys,
                        width = 48.dp,
                        height = 34.dp
                    )
                }
            }

            // Right Column: PgUp (top) & PgDn (bottom)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StandardKeyButton("PgUp", KeyEvent.KEYCODE_PAGE_UP, virtualKeys, minWidth = 46.dp)
                StandardKeyButton("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, virtualKeys, minWidth = 46.dp)
            }
        }

        // Section Divider
        BarDivider()

        // --- ZONE 4: Big Scroll Up & Scroll Down Controls ---
        // The user specifically requested:
        // "THE SCROLL UP, SCROLL DOWN OPTIONS NEED TO BE BIGGER IN SIZE. THERE IS ENOUGH SPACE IN THE SCREEN FOR THEM TO FIT. LOOK AT THAT."
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScrollPadButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Scroll Up",
                onScroll = { virtualKeys.onScrollUp() },
                width = 54.dp,
                height = 34.dp
            )
            ScrollPadButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll Down",
                onScroll = { virtualKeys.onScrollDown() },
                width = 54.dp,
                height = 34.dp
            )
        }

        // Section Divider
        BarDivider()

        // --- ZONE 5: Dismiss / Close Button ---
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
            modifier = Modifier
                .size(width = 34.dp, height = 72.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { virtualKeys.hide(saveVisibility = true) })
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_clear),
                    contentDescription = "Close Virtual Keys",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun BarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(64.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    )
}

@Composable
private fun StandardKeyButton(
    label: String,
    keyCode: Int,
    virtualKeys: VirtualKeys,
    minWidth: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    var isPressedVisual by remember { mutableStateOf(false) }
    var isHolding by remember { mutableStateOf(false) }

    val repeatTimeout = remember { ViewConfiguration.getKeyRepeatTimeout().toLong().coerceAtLeast(280L) }
    val repeatDelay = remember { ViewConfiguration.getKeyRepeatDelay().toLong().coerceAtLeast(40L) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            virtualKeys.sendKey(keyCode)
            delay(repeatTimeout)
            while (isHolding) {
                virtualKeys.sendKey(keyCode)
                delay(repeatDelay)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPressedVisual) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
        },
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .height(34.dp)
            .defaultMinSize(minWidth = minWidth)
            .pointerInput(keyCode) {
                detectTapGestures(
                    onPress = {
                        isPressedVisual = true
                        isHolding = true
                        tryAwaitRelease()
                        isHolding = false
                        isPressedVisual = false
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WindowsKeyButton(
    virtualKeys: VirtualKeys,
    minWidth: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val key = VirtualKey.LeftSuper
    val isToggled = virtualKeys.activeToggleKeys[key] == true
    val isLocked = virtualKeys.lockedToggleKeys[key] == true

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            isLocked -> MaterialTheme.colorScheme.primary
            isToggled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
        },
        contentColor = when {
            isLocked -> MaterialTheme.colorScheme.onPrimary
            isToggled -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (isToggled && !isLocked) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .height(34.dp)
            .defaultMinSize(minWidth = minWidth)
            .pointerInput(key) {
                detectTapGestures(
                    onTap = {
                        virtualKeys.onToggleKeyClick(key)
                    },
                    onLongPress = {
                        virtualKeys.onToggleKeyLongClick(key)
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_super_key),
                contentDescription = "Windows Key",
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "Win",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            if (isLocked) {
                Spacer(modifier = Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun DeleteKeyButton(
    virtualKeys: VirtualKeys,
    minWidth: Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    var isPressedVisual by remember { mutableStateOf(false) }
    var isHolding by remember { mutableStateOf(false) }

    val repeatTimeout = remember { ViewConfiguration.getKeyRepeatTimeout().toLong().coerceAtLeast(280L) }
    val repeatDelay = remember { ViewConfiguration.getKeyRepeatDelay().toLong().coerceAtLeast(40L) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            virtualKeys.sendKey(KeyEvent.KEYCODE_FORWARD_DEL)
            delay(repeatTimeout)
            while (isHolding) {
                virtualKeys.sendKey(KeyEvent.KEYCODE_FORWARD_DEL)
                delay(repeatDelay)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPressedVisual) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        },
        contentColor = if (isPressedVisual) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.error
        },
        modifier = modifier
            .height(34.dp)
            .defaultMinSize(minWidth = minWidth)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressedVisual = true
                        isHolding = true
                        tryAwaitRelease()
                        isHolding = false
                        isPressedVisual = false
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Del",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ModifierKeyButton(
    key: VirtualKey,
    virtualKeys: VirtualKeys,
    minWidth: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val isToggled = virtualKeys.activeToggleKeys[key] == true
    val isLocked = virtualKeys.lockedToggleKeys[key] == true

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            isLocked -> MaterialTheme.colorScheme.primary
            isToggled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
        },
        contentColor = when {
            isLocked -> MaterialTheme.colorScheme.onPrimary
            isToggled -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (isToggled && !isLocked) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .height(34.dp)
            .defaultMinSize(minWidth = minWidth)
            .pointerInput(key) {
                detectTapGestures(
                    onTap = {
                        virtualKeys.onToggleKeyClick(key)
                    },
                    onLongPress = {
                        virtualKeys.onToggleKeyLongClick(key)
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.label ?: key.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (isLocked) {
                Spacer(modifier = Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ArrowKeyButton(
    key: VirtualKey,
    icon: Int,
    description: String,
    virtualKeys: VirtualKeys,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val keyCode = key.keyCode ?: return
    var isPressedVisual by remember { mutableStateOf(false) }
    var isHolding by remember { mutableStateOf(false) }

    val repeatTimeout = remember { ViewConfiguration.getKeyRepeatTimeout().toLong().coerceAtLeast(280L) }
    val repeatDelay = remember { ViewConfiguration.getKeyRepeatDelay().toLong().coerceAtLeast(40L) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            virtualKeys.sendKey(keyCode)
            delay(repeatTimeout)
            while (isHolding) {
                virtualKeys.sendKey(keyCode)
                delay(repeatDelay)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPressedVisual) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
        },
        contentColor = if (isPressedVisual) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
            .size(width = width, height = height)
            .pointerInput(keyCode) {
                detectTapGestures(
                    onPress = {
                        isPressedVisual = true
                        isHolding = true
                        tryAwaitRelease()
                        isHolding = false
                        isPressedVisual = false
                    }
                )
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = description,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ScrollPadButton(
    icon: ImageVector,
    contentDescription: String,
    onScroll: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    var isHolding by remember { mutableStateOf(false) }
    var isPressedVisual by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            onScroll()
            delay(200)
            while (isHolding) {
                onScroll()
                delay(50)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPressedVisual) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        },
        contentColor = if (isPressedVisual) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        modifier = modifier
            .size(width = width, height = height)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressedVisual = true
                        isHolding = true
                        tryAwaitRelease()
                        isHolding = false
                        isPressedVisual = false
                    }
                )
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
