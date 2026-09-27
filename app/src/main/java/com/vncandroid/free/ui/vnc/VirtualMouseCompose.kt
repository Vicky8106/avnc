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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
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
 * A single draggable floating action button that expands into one vertical
 * panel with Scroll Up, Scroll Down, Left Click, and Right Click.
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

    /**
     * Called when the app goes to background. Releases any held remote buttons
     * (so a drag can never get stuck) while preserving overlay visibility,
     * expanded state, and the gesture-style override. Backgrounding must never
     * toggle the mouse.
     */
    fun onBackground() {
        releaseAllButtons()
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

/**
 * Tracks a press that may turn into a drag, with a deliberately low drag-start
 * threshold (half the system touch slop) so floating controls feel responsive.
 * Every move is consumed, so touches starting on the control never leak through
 * to the frame underneath (that leak-through is what made dragging jitter).
 *
 * Returns the total distance travelled (so callers can tell taps from drags)
 * plus whatever drag was already applied (so taps can undo micro-moves).
 */
private data class PressDragResult(val distance: Float, val appliedX: Float, val appliedY: Float)

private suspend fun AwaitPointerEventScope.trackPressAndDrag(
    touchSlop: Float,
    onDragDelta: (dx: Float, dy: Float) -> Unit
): PressDragResult {
    val down = awaitFirstDown(requireUnconsumed = false)
    down.consume()
    var totalDx = 0f
    var totalDy = 0f
    var appliedX = 0f
    var appliedY = 0f
    val threshold = touchSlop * 0.5f
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (change.changedToUp()) {
            change.consume()
            break
        }
        // Framework-tracked delta: unlike a manually stored last position, the
        // framework keeps previousPosition consistent across the layout shifts
        // that applying the drag itself causes, so dragging tracks 1:1.
        val delta = change.position - change.previousPosition
        change.consume()
        totalDx += delta.x
        totalDy += delta.y
        if (hypot(totalDx, totalDy) > threshold) {
            onDragDelta(delta.x, delta.y)
            appliedX += delta.x
            appliedY += delta.y
        }
    }
    return PressDragResult(hypot(totalDx, totalDy), appliedX, appliedY)
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
    val touchSlop = LocalViewConfiguration.current.touchSlop

    // Return to the anchor corner whenever the mouse is re-shown, so free
    // dragging can never strand it off-screen with no way back.
    LaunchedEffect(isVisible) {
        if (isVisible) {
            offsetX = 0f
            offsetY = 0f
        }
    }

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
                        IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
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
                        Surface(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val result = trackPressAndDrag(touchSlop) { dx, dy ->
                                            offsetX += dx
                                            offsetY += dy
                                        }
                                        if (result.distance < touchSlop * 2f) {
                                            // Tap (or wobble): undo any micro-move so taps can
                                            // never drift the icon, then expand the panel.
                                            offsetX -= result.appliedX
                                            offsetY -= result.appliedY
                                            virtualMouse.expand()
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
                        // Single vertical mouse panel: Scroll Up/Down, then Left/Right clicks.
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            tonalElevation = 8.dp,
                            shadowElevation = 12.dp,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(26.dp))
                                .width(64.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 5.dp)
                            ) {
                                // Drag handle to reposition the panel anywhere on screen.
                                // Three clearly visible grip lines show users where to touch to move it.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(22.dp)
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                trackPressAndDrag(touchSlop) { dx, dy ->
                                                    offsetX += dx
                                                    offsetY += dy
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.5.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        repeat(3) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 22.dp, height = 3.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                        RoundedCornerShape(1.5.dp)
                                                    )
                                            )
                                        }
                                    }
                                }

                                // Scroll Up (supports hold-to-repeat)
                                var isScrollUpHolding by remember { mutableStateOf(false) }
                                LaunchedEffect(isScrollUpHolding) {
                                    if (isScrollUpHolding) {
                                        virtualMouse.onScrollUp()
                                        delay(200)
                                    }
                                    while (isScrollUpHolding) {
                                        virtualMouse.onScrollUp()
                                        delay(50)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .size(width = 54.dp, height = 52.dp)
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
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Scroll Up",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                // Scroll Down (supports hold-to-repeat)
                                var isScrollDownHolding by remember { mutableStateOf(false) }
                                LaunchedEffect(isScrollDownHolding) {
                                    if (isScrollDownHolding) {
                                        virtualMouse.onScrollDown()
                                        delay(200)
                                    }
                                    while (isScrollDownHolding) {
                                        virtualMouse.onScrollDown()
                                        delay(50)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .size(width = 54.dp, height = 52.dp)
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
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Scroll Down",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                // Left Click (supports hold & drag)
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
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .size(width = 54.dp, height = 44.dp)
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

                                // Right Click
                                FilledTonalButton(
                                    onClick = { virtualMouse.onRightClick() },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.size(width = 54.dp, height = 44.dp)
                                ) {
                                    Text("Right", fontSize = 12.sp)
                                }

                                // Collapse back to the floating button
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
