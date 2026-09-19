/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.ui.vnc

import android.graphics.PointF
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vncandroid.free.R
import com.vncandroid.free.viewmodel.VncViewModel
import com.vncandroid.free.vnc.PointerButton
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

    fun minimize() {
        isExpandedState.value = false
    }

    fun expand() {
        isExpandedState.value = true
    }

    fun toggle() {
        if (isVisibleState.value) {
            hide()
        } else {
            show(expand = false)
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
                    .offset {
                        val clampedX = if (isExpanded) offsetX.coerceAtMost(0f) else offsetX
                        IntOffset(clampedX.roundToInt(), offsetY.roundToInt())
                    }
                    .alpha(0.80f)
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
                        val touchSlop = LocalViewConfiguration.current.touchSlop
                        Surface(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var hasMoved = false
                                        var totalDx = 0f
                                        var totalDy = 0f
                                        var lastPos = down.position
                                        val downTime = System.currentTimeMillis()
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (change.changedToUp()) {
                                                val duration = System.currentTimeMillis() - downTime
                                                val dist = hypot(totalDx, totalDy)
                                                if (!hasMoved || (duration < 350 && dist < touchSlop * 2f)) {
                                                    virtualMouse.expand()
                                                }
                                                break
                                            }
                                            val currentPos = change.position
                                            val dx = currentPos.x - lastPos.x
                                            val dy = currentPos.y - lastPos.y
                                            totalDx += dx
                                            totalDy += dy
                                            if (!hasMoved && hypot(totalDx, totalDy) > touchSlop) {
                                                hasMoved = true
                                            }
                                            if (hasMoved) {
                                                change.consume()
                                                offsetX += dx
                                                offsetY += dy
                                            }
                                            lastPos = currentPos
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
                                    contentDescription = "Virtual Mouse - Tap to expand",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    } else {
                        // Expanded Floating Mouse Control Bar (20% transparent)
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            tonalElevation = 8.dp,
                            shadowElevation = 12.dp,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                            modifier = Modifier.clip(RoundedCornerShape(24.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .height(46.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                // Drag handle to reposition mouse bar anywhere on screen
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(18.dp)
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // 3. Left Click Button (Supports Hold & Drag)
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
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .defaultMinSize(minWidth = 38.dp, minHeight = 36.dp)
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
                                    Text("Left", fontSize = 12.sp)
                                }

                                // 4. Middle Click Button
                                FilledTonalButton(
                                    onClick = { virtualMouse.onMiddleClick() },
                                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .defaultMinSize(minWidth = 34.dp, minHeight = 36.dp)
                                ) {
                                    Text("Mid", fontSize = 12.sp)
                                }

                                // 5. Scroll Up Button (Supports hold-to-repeat)
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
                                        .size(34.dp)
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

                                // 6. Scroll Down Button (Supports hold-to-repeat)
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
                                        .size(34.dp)
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

                                // 7. Right Click Button
                                FilledTonalButton(
                                    onClick = { virtualMouse.onRightClick() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .defaultMinSize(minWidth = 38.dp, minHeight = 36.dp)
                                ) {
                                    Text("Right", fontSize = 12.sp)
                                }

                                // 8. Keyboard Button: opens keyboard directly from mouse bar
                                IconButton(
                                    onClick = { virtualMouse.onOpenKeyboard() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_keyboard),
                                        contentDescription = "Keyboard",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 9. Second Minimize Button at the END: so whether looking at start or end, a Cross ("✕") is right there!
                                IconButton(
                                    onClick = { virtualMouse.minimize() },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Collapse to floating button",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(17.dp)
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
