/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.PointF
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaurav.avnc.R
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.PointerButton
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Jetpack Compose On-Screen Real-Time Virtual Mouse Controls (RealVNC style).
 * Features a draggable floating action button that expands into Left Click, Right Click,
 * Middle Click, Scroll Up/Down, and Keyboard triggers.
 */
class VirtualMouse(private val activity: VncActivity) {

    private val viewModel: VncViewModel = activity.viewModel
    val isVisibleState = mutableStateOf(false)
    val isExpandedState = mutableStateOf(false)
    private var previousGestureStyle: String? = null

    val isVisible: Boolean
        get() = isVisibleState.value

    fun show(expand: Boolean = false) {
        if (!isVisibleState.value) {
            previousGestureStyle = viewModel.activeGestureStyle.value
            viewModel.activeGestureStyle.value = "touchpad"
            isVisibleState.value = true
        }
        if (expand) {
            isExpandedState.value = true
        }
    }

    fun hide() {
        if (isVisibleState.value) {
            releaseAllButtons()
            isExpandedState.value = false
            isVisibleState.value = false
            previousGestureStyle?.let {
                viewModel.activeGestureStyle.value = it
            }
        }
    }

    fun toggle() {
        if (isVisibleState.value) {
            if (!isExpandedState.value) {
                isExpandedState.value = true
            } else {
                hide()
            }
        } else {
            show(expand = true)
        }
    }

    fun onOpenKeyboard() {
        activity.showKeyboard()
    }

    private val currentPointerPos: PointF
        get() = PointF(
            viewModel.client?.pointerX?.toFloat() ?: 0f,
            viewModel.client?.pointerY?.toFloat() ?: 0f
        )

    fun onLeftDown() {
        viewModel.messenger?.sendPointerButtonDown(PointerButton.Left, currentPointerPos)
    }

    fun onLeftUp() {
        viewModel.messenger?.sendPointerButtonUp(PointerButton.Left, currentPointerPos)
    }

    fun onRightClick() {
        val pos = currentPointerPos
        viewModel.messenger?.sendPointerButtonDown(PointerButton.Right, pos)
        if (viewModel.profile.fButtonUpDelay) viewModel.messenger?.insertButtonUpDelay()
        viewModel.messenger?.sendPointerButtonUp(PointerButton.Right, pos)
    }

    fun onMiddleClick() {
        val pos = currentPointerPos
        viewModel.messenger?.sendPointerButtonDown(PointerButton.Middle, pos)
        if (viewModel.profile.fButtonUpDelay) viewModel.messenger?.insertButtonUpDelay()
        viewModel.messenger?.sendPointerButtonUp(PointerButton.Middle, pos)
    }

    fun onScrollUp() {
        val pos = currentPointerPos
        viewModel.messenger?.sendPointerButtonDown(PointerButton.WheelUp, pos)
        viewModel.messenger?.sendPointerButtonUp(PointerButton.WheelUp, pos)
    }

    fun onScrollDown() {
        val pos = currentPointerPos
        viewModel.messenger?.sendPointerButtonDown(PointerButton.WheelDown, pos)
        viewModel.messenger?.sendPointerButtonUp(PointerButton.WheelDown, pos)
    }

    fun releaseAllButtons() {
        viewModel.messenger?.sendPointerButtonRelease(currentPointerPos)
    }
}

@Composable
fun VirtualMouseOverlay(
    virtualMouse: VirtualMouse,
    modifier: Modifier = Modifier
) {
    val isVisible by virtualMouse.isVisibleState
    var isExpanded by virtualMouse.isExpandedState

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 80.dp)
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            ) {
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "VirtualMouseExpandTransition"
                ) { expanded ->
                    if (!expanded) {
                        // Floating Action Button: touching/tapping expands the mouse options
                        Surface(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var hasMoved = false
                                        var lastPos = down.position
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (change.changedToUp()) {
                                                if (!hasMoved) {
                                                    isExpanded = true
                                                }
                                                break
                                            }
                                            val currentPos = change.position
                                            val dx = currentPos.x - lastPos.x
                                            val dy = currentPos.y - lastPos.y
                                            if (hypot(dx, dy) > 4f) {
                                                hasMoved = true
                                                change.consume()
                                                offsetX += dx
                                                offsetY += dy
                                                lastPos = currentPos
                                            }
                                        }
                                    }
                                },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            tonalElevation = 8.dp,
                            shadowElevation = 10.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_mouse),
                                    contentDescription = "Virtual Mouse",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    } else {
                        // Expanded Floating Mouse Control Bar
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            tonalElevation = 8.dp,
                            shadowElevation = 12.dp,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            modifier = Modifier.clip(RoundedCornerShape(24.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .height(48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Drag handle to reposition mouse bar anywhere on screen
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(22.dp)
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                offsetX += dragAmount.x
                                                offsetY += dragAmount.y
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_drag_indicator),
                                        contentDescription = "Reposition",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Left Click Button (Supports Hold & Drag)
                                var isLeftPressed by remember { mutableStateOf(false) }
                                FilledTonalButton(
                                    onClick = { /* Handled by pointerInput */ },
                                    colors = if (isLeftPressed) {
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        ButtonDefaults.filledTonalButtonColors()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    isLeftPressed = true
                                                    virtualMouse.onLeftDown()
                                                    tryAwaitRelease()
                                                    isLeftPressed = false
                                                    virtualMouse.onLeftUp()
                                                }
                                            )
                                        }
                                ) {
                                    Text("Left", fontSize = 13.sp)
                                }

                                // Middle Click Button
                                FilledTonalButton(
                                    onClick = { virtualMouse.onMiddleClick() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text("Mid", fontSize = 12.sp)
                                }

                                // Scroll Up Button (Supports hold-to-repeat)
                                var isScrollUpHolding by remember { mutableStateOf(false) }
                                LaunchedEffect(isScrollUpHolding) {
                                    if (isScrollUpHolding) {
                                        virtualMouse.onScrollUp()
                                        delay(250)
                                    }
                                    while (isScrollUpHolding) {
                                        virtualMouse.onScrollUp()
                                        delay(60)
                                    }
                                }
                                IconButton(
                                    onClick = { virtualMouse.onScrollUp() },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    isScrollUpHolding = true
                                                    tryAwaitRelease()
                                                    isScrollUpHolding = false
                                                }
                                            )
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Scroll Up",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Scroll Down Button (Supports hold-to-repeat)
                                var isScrollDownHolding by remember { mutableStateOf(false) }
                                LaunchedEffect(isScrollDownHolding) {
                                    if (isScrollDownHolding) {
                                        virtualMouse.onScrollDown()
                                        delay(250)
                                    }
                                    while (isScrollDownHolding) {
                                        virtualMouse.onScrollDown()
                                        delay(60)
                                    }
                                }
                                IconButton(
                                    onClick = { virtualMouse.onScrollDown() },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    isScrollDownHolding = true
                                                    tryAwaitRelease()
                                                    isScrollDownHolding = false
                                                }
                                            )
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Scroll Down",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Right Click Button
                                FilledTonalButton(
                                    onClick = { virtualMouse.onRightClick() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text("Right", fontSize = 13.sp)
                                }

                                // Keyboard Button: opens keyboard directly from mouse bar
                                IconButton(
                                    onClick = { virtualMouse.onOpenKeyboard() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_keyboard),
                                        contentDescription = "Keyboard",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Collapse Button: collapses back to floating bubble
                                IconButton(
                                    onClick = { isExpanded = false },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_mouse),
                                        contentDescription = "Collapse",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Close Button: dismisses overlay completely
                                IconButton(
                                    onClick = {
                                        isExpanded = false
                                        virtualMouse.hide()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
