package com.nightcap.camera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    currentIso: Int,
    isoBoost: Float,
    currentExposureNs: Long,
    onIsoChanged: (Int, Float) -> Unit,
    onExposureChanged: (Long) -> Unit,
    content: @Composable () -> Unit
) {
    var hudText by remember { mutableStateOf("") }
    var isDragging by remember { mutableStateOf(false) }

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
                            // Vuốt lên: tăng, Vuốt xuống: giảm
                            val isoDelta = (-dy * 8).toInt()
                            var newIso = (currentIso + isoDelta).coerceIn(50, 6400)
                            var newBoost = isoBoost

                            if (newIso >= 3200 && dy < 0) {
                                // Nếu đã đạt trần ISO phần cứng, kích hoạt ISO Boost kỹ thuật số
                                newBoost = (isoBoost + 0.05f).coerceIn(1.0f, 8.0f)
                            } else if (newBoost > 1.0f && dy > 0) {
                                newBoost = (isoBoost - 0.05f).coerceIn(1.0f, 8.0f)
                            }

                            onIsoChanged(newIso, newBoost)

                            val boostText = if (newBoost > 1.0f) " (Boost ${String.format("%.1fx", newBoost)})" else ""
                            hudText = "ISO $newIso$boostText"
                        } else {
                            // Vuốt ngang: Điều chỉnh tốc độ màn trập (Exposure Time)
                            // Vuốt phải: phơi sáng dài hơn, Vuốt trái: ngắn hơn
                            val exposureStep = (dx * 1_000_000L).toLong() // Bước dịch
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

        // Hiển thị bong bóng thông số khi người dùng vuốt trên màn hình
        AnimatedVisibility(
            visible = hudText.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
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
