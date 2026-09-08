package com.nightcap.camera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    currentIso: Int,
    isoBoost: Float,
    currentExposureNs: Long,
    onIsoChanged: (Int, Float) -> Unit,
    onExposureChanged: (Long) -> Unit,
    onTapToFocus: (Float, Float) -> Unit,
    content: @Composable () -> Unit
) {
    var hudText by remember { mutableStateOf("") }
    var isDragging by remember { mutableStateOf(false) }

    // Toạ độ điểm lấy nét khi người dùng chạm
    var focusTapPoint by remember { mutableStateOf<Offset?>(null) }
    val focusScale = remember { Animatable(1.5f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isDragging) {
        if (!isDragging) {
            delay(1200)
            hudText = ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normX = offset.x / size.width
                    val normY = offset.y / size.height
                    focusTapPoint = offset
                    onTapToFocus(normX, normY)

                    coroutineScope.launch {
                        focusScale.snapTo(1.4f)
                        focusScale.animateTo(1.0f, tween(200))
                        delay(900)
                        focusTapPoint = null
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x
                        val dy = dragAmount.y

                        if (Math.abs(dy) > Math.abs(dx)) {
                            // Vuốt dọc: Điều chỉnh ISO & ISO Boost
                            val isoDelta = (-dy * 6).toInt()
                            var newIso = (currentIso + isoDelta).coerceIn(50, 6400)
                            var newBoost = isoBoost

                            if (newIso >= 3200 && dy < 0) {
                                newBoost = (isoBoost + 0.05f).coerceIn(1.0f, 8.0f)
                            } else if (newBoost > 1.0f && dy > 0) {
                                newBoost = (isoBoost - 0.05f).coerceIn(1.0f, 8.0f)
                            }

                            onIsoChanged(newIso, newBoost)

                            val boostText = if (newBoost > 1.0f) " (Boost ${String.format("%.1fx", newBoost)})" else ""
                            hudText = "ISO: $newIso$boostText"
                        } else {
                            // Vuốt ngang: Điều chỉnh tốc độ màn trập (Exposure Time)
                            val exposureStep = (dx * 1_500_000L).toLong()
                            val newExposure = (currentExposureNs + exposureStep).coerceIn(1_000_000L, 1_000_000_000L)
                            onExposureChanged(newExposure)

                            val seconds = newExposure / 1_000_000_000.0
                            val displayTime = if (seconds >= 1.0) {
                                String.format("%.1fs", seconds)
                            } else {
                                val fraction = Math.round(1.0 / seconds)
                                "1/${fraction}s"
                            }
                            hudText = "Tốc độ: $displayTime"
                        }
                    }
                )
            }
    ) {
        content()

        // Hiển thị vòng tròn lấy nét (Focus Ring) khi chạm vào màn hình
        focusTapPoint?.let { point ->
            val primaryColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .offset(x = (point.x - 30).dp, y = (point.y - 30).dp)
                    .size(60.dp)
                    .scale(focusScale.value)
                    .border(2.dp, primaryColor, CircleShape)
            )
        }

        // Hiển thị thông số dạng HUD khi vuốt
        AnimatedVisibility(
            visible = hudText.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = hudText,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
