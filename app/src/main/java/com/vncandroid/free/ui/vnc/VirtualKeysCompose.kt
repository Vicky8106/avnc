/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.ui.vnc

import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vncandroid.free.R
import kotlinx.coroutines.delay

/**
 * Modern Jetpack Compose Material 3 Virtual Keys Bar.
 * Features 20% transparency (matching the floating Virtual Mouse),
 * responsive modifier toggle latching with visual lock indicator,
 * hold-to-repeat hardware scan-code dispatching (18ms BMC USB HID timing),
 * and a sleek integrated Text Send console.
 */
@Composable
fun VirtualKeysOverlay(
    virtualKeys: VirtualKeys,
    modifier: Modifier = Modifier
) {
    val isVisible by virtualKeys.isVisibleState
    val isTextMode by virtualKeys.isTextModeState

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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            AnimatedContent(
                targetState = isTextMode,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "VirtualKeysModeTransition"
            ) { textMode ->
                if (!textMode) {
                    KeysModeContent(virtualKeys = virtualKeys)
                } else {
                    TextModeContent(virtualKeys = virtualKeys)
                }
            }
        }
    }
}

@Composable
private fun KeysModeContent(
    virtualKeys: VirtualKeys,
    modifier: Modifier = Modifier
) {
    val pref = virtualKeys.pref
    val rowCount = remember(pref) { pref.input.vkRowCount.coerceIn(1, 4) }
    val keys = remember(pref) { VirtualKeyLayoutConfig.getLayout(pref) }
    val columns = remember(keys, rowCount) { keys.chunked(rowCount) }

    Row(
        modifier = modifier
            .padding(horizontal = 5.dp, vertical = 4.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quick Action: Switch to Text Send Mode
        IconButton(
            onClick = { virtualKeys.setTextMode(true) },
            modifier = Modifier
                .size(34.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_send),
                contentDescription = "Send Text to Server",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(32.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        )
        Spacer(modifier = Modifier.width(4.dp))

        // Horizontally Scrollable Keys Grid
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            columns.forEach { columnKeys ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    columnKeys.forEach { key ->
                        VirtualKeyItem(key = key, virtualKeys = virtualKeys)
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualKeyItem(
    key: VirtualKey,
    virtualKeys: VirtualKeys,
    modifier: Modifier = Modifier
) {
    when {
        key == VirtualKey.ToggleKeyboard -> {
            IconButton(
                onClick = { virtualKeys.toggleKeyboard() },
                modifier = modifier
                    .size(width = 36.dp, height = 32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard),
                    contentDescription = "Toggle Keyboard",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        key == VirtualKey.CloseKeys -> {
            IconButton(
                onClick = { virtualKeys.hide(saveVisibility = true) },
                modifier = modifier
                    .size(width = 36.dp, height = 32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_clear),
                    contentDescription = "Close Virtual Keys",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        key.isToggle -> {
            ToggleKeyItem(key = key, virtualKeys = virtualKeys, modifier = modifier)
        }

        else -> {
            RepeatableKeyItem(key = key, virtualKeys = virtualKeys, modifier = modifier)
        }
    }
}

@Composable
private fun ToggleKeyItem(
    key: VirtualKey,
    virtualKeys: VirtualKeys,
    modifier: Modifier = Modifier
) {
    val isToggled = virtualKeys.activeToggleKeys[key] == true
    val isLocked = virtualKeys.lockedToggleKeys[key] == true

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            isLocked -> MaterialTheme.colorScheme.primary
            isToggled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        contentColor = when {
            isLocked -> MaterialTheme.colorScheme.onPrimary
            isToggled -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (isToggled && !isLocked) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else null,
        modifier = modifier
            .height(32.dp)
            .defaultMinSize(minWidth = 36.dp)
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
            if (key.icon != null) {
                Icon(
                    painter = painterResource(id = key.icon),
                    contentDescription = key.description ?: key.label ?: key.name,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = key.label ?: key.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
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
private fun RepeatableKeyItem(
    key: VirtualKey,
    virtualKeys: VirtualKeys,
    modifier: Modifier = Modifier
) {
    var isHolding by remember { mutableStateOf(false) }
    var isPressedVisual by remember { mutableStateOf(false) }

    val repeatTimeout = remember { ViewConfiguration.getKeyRepeatTimeout().toLong().coerceAtLeast(300L) }
    val repeatDelay = remember { ViewConfiguration.getKeyRepeatDelay().toLong().coerceAtLeast(40L) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            key.keyCode?.let { virtualKeys.sendKey(it) }
            delay(repeatTimeout)
            while (isHolding) {
                key.keyCode?.let { virtualKeys.sendKey(it) }
                delay(repeatDelay)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPressedVisual) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .height(32.dp)
            .defaultMinSize(minWidth = if (key.icon != null) 32.dp else 36.dp)
            .pointerInput(key) {
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
            if (key.icon != null) {
                Icon(
                    painter = painterResource(id = key.icon),
                    contentDescription = key.description ?: key.label ?: key.name,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = key.label ?: key.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TextModeContent(
    virtualKeys: VirtualKeys,
    modifier: Modifier = Modifier
) {
    var text by virtualKeys.textInputState
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Back Button: Return to Keys Mode
        IconButton(
            onClick = { virtualKeys.setTextMode(false) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_back),
                contentDescription = "Back to keys",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Text Input Box
        BasicTextField(
            value = text,
            onValueChange = { if (it.length <= 1000) text = it },
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .focusRequester(focusRequester)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 10.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send,
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    virtualKeys.handleTextBoxAction(text)
                }
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.hint_send_text_to_server),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                                fontSize = 12.sp
                            )
                        }
                        innerTextField()
                    }
                    if (text.isNotEmpty()) {
                        IconButton(
                            onClick = { text = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear text",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )

        // Paste Button
        IconButton(
            onClick = { virtualKeys.onPasteClick() },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_content_paste),
                contentDescription = "Paste from clipboard",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Send Button
        FilledTonalButton(
            onClick = { virtualKeys.handleTextBoxAction(text) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_send),
                contentDescription = "Send text",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
