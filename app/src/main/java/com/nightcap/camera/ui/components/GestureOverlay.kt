package com.nightcap.camera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    currentZoomRatio: Float = 1.0f,
    minZoomRatio: Float = 1.0f,
    maxZoomRatio: Float = 10.0f,
    onTapToFocus: (Float, Float) -> Unit,
    onZoomChanged: (Float) -> Unit,
    content: @Composable () -> Unit
) {
    // Tap to focus coordinates and animation
    var focusTapPoint by remember { mutableStateOf<Offset?>(null) }
    val focusScale = remember { Animatable(1.5f) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Zoom indicator visibility and auto-fade timer
    var showZoomBadge by remember { mutableStateOf(false) }
    var hideBadgeJob by remember { mutableStateOf<Job?>(null) }

    // Reference states to avoid pointerInput restarts
    val currentZoomState by rememberUpdatedState(currentZoomRatio)
    val minZoomState by rememberUpdatedState(minZoomRatio)
    val maxZoomState by rememberUpdatedState(maxZoomRatio)
    val onZoomChangedState by rememberUpdatedState(onZoomChanged)
    val onTapToFocusState by rememberUpdatedState(onTapToFocus)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    var isPinching = false
                    var prevDistance = -1f
                    var hasMovedBeyondSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    val initialPos = firstDown.position
                    var accumulatedZoom = currentZoomState

                    do {
                        val event = awaitPointerEvent()
                        val activeChanges = event.changes.filter { it.pressed }

                        if (activeChanges.size >= 2) {
                            // 2-finger multi-touch: Pinch to zoom
                            isPinching = true
                            val p1 = activeChanges[0].position
                            val p2 = activeChanges[1].position
                            val currentDistance = (p1 - p2).getDistance()

                            if (prevDistance > 0f && currentDistance > 0f) {
                                val zoomDelta = currentDistance / prevDistance
                                accumulatedZoom = (accumulatedZoom * zoomDelta).coerceIn(minZoomState, maxZoomState)
                                onZoomChangedState(accumulatedZoom)

                                showZoomBadge = true
                                hideBadgeJob?.cancel()
                            }
                            prevDistance = currentDistance
                            activeChanges.forEach { it.consume() }
                        } else {
                            prevDistance = -1f
                            if (!isPinching && activeChanges.size == 1) {
                                val moveDist = (activeChanges[0].position - initialPos).getDistance()
                                if (moveDist > touchSlop) {
                                    hasMovedBeyondSlop = true
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (isPinching) {
                        // Pinch gesture finished, fade out zoom badge after 1.5s
                        hideBadgeJob = coroutineScope.launch {
                            delay(1500)
                            showZoomBadge = false
                        }
                    } else if (!hasMovedBeyondSlop) {
                        // Single tap without movement -> Tap to Focus
                        val normX = (initialPos.x / size.width).coerceIn(0f, 1f)
                        val normY = (initialPos.y / size.height).coerceIn(0f, 1f)
                        focusTapPoint = initialPos
                        onTapToFocusState(normX, normY)

                        coroutineScope.launch {
                            focusScale.snapTo(1.4f)
                            focusScale.animateTo(1.0f, tween(250))
                            delay(1000)
                            focusTapPoint = null
                        }
                    }
                }
            }
    ) {
        content()

        val primaryColor = MaterialTheme.colorScheme.primary

        // Animated Focus Ring on tapped position
        focusTapPoint?.let { point ->
            val ringSize = 64.dp
            val ringSizePx = with(density) { ringSize.toPx() }
            val xDp = with(density) { (point.x - ringSizePx / 2).toDp() }
            val yDp = with(density) { (point.y - ringSizePx / 2).toDp() }

            Box(
                modifier = Modifier
                    .offset(x = xDp, y = yDp)
                    .size(ringSize)
                    .scale(focusScale.value)
                    .border(2.dp, primaryColor, CircleShape)
            )
        }

        // Sleek floating Zoom HUD with quick presets
        AnimatedVisibility(
            visible = showZoomBadge || currentZoomRatio > 1.05f,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 1x preset
                    ZoomPresetChip(
                        text = "1x",
                        isSelected = Math.abs(currentZoomRatio - 1.0f) < 0.1f,
                        primaryColor = primaryColor,
                        onClick = {
                            onZoomChangedState(1.0f)
                            hideBadgeJob?.cancel()
                            hideBadgeJob = coroutineScope.launch {
                                delay(1500)
                                showZoomBadge = false
                            }
                        }
                    )

                    // Fractional zoom badge when not close to presets
                    val isNearPreset = Math.abs(currentZoomRatio - 1.0f) < 0.1f ||
                            Math.abs(currentZoomRatio - 2.0f) < 0.1f ||
                            (maxZoomState >= 5.0f && Math.abs(currentZoomRatio - 5.0f) < 0.1f)

                    if (!isNearPreset) {
                        Surface(
                            color = primaryColor.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, primaryColor)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1fx", currentZoomRatio),
                                color = primaryColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // 2x preset
                    if (maxZoomState >= 2.0f) {
                        ZoomPresetChip(
                            text = "2x",
                            isSelected = Math.abs(currentZoomRatio - 2.0f) < 0.1f,
                            primaryColor = primaryColor,
                            onClick = {
                                onZoomChangedState(2.0f)
                                hideBadgeJob?.cancel()
                                hideBadgeJob = coroutineScope.launch {
                                    delay(1500)
                                    showZoomBadge = false
                                }
                            }
                        )
                    }

                    // 5x preset if device supports
                    if (maxZoomState >= 5.0f) {
                        ZoomPresetChip(
                            text = "5x",
                            isSelected = Math.abs(currentZoomRatio - 5.0f) < 0.1f,
                            primaryColor = primaryColor,
                            onClick = {
                                onZoomChangedState(5.0f)
                                hideBadgeJob?.cancel()
                                hideBadgeJob = coroutineScope.launch {
                                    delay(1500)
                                    showZoomBadge = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomPresetChip(
    text: String,
    isSelected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
