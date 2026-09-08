package com.nightcap.camera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightcap.camera.engine.StackingMode

@Composable
fun StackingHUD(
    modifier: Modifier = Modifier,
    isStackingActive: Boolean,
    elapsedSeconds: Int,
    frameCount: Int,
    mode: StackingMode,
    isGyroEnabled: Boolean,
    isInfinityFocus: Boolean,
    isRedVision: Boolean,
    currentIso: Int,
    isoBoost: Float,
    exposureTimeNs: Long,
    onToggleRedVision: () -> Unit,
    onToggleGyro: () -> Unit,
    onToggleInfinityFocus: () -> Unit
) {
    val seconds = exposureTimeNs / 1_000_000_000.0
    val displayShutter = if (seconds >= 1.0) {
        String.format("%.1fs", seconds)
    } else {
        val fraction = Math.round(1.0 / seconds)
        "1/${fraction}s"
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    // Hiệu ứng nhấp nháy cho đèn báo Rec khi đang phơi sáng
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Thanh tiêu đề trên cùng
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chế độ hiện tại
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isStackingActive) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = mode.displayName,
                        color = primaryColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Các nút tiện ích nhanh trên cùng
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Nút Chống rung con quay hồi chuyển (Handheld Gyro)
                IconButton(
                    onClick = onToggleGyro,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isGyroEnabled) primaryColor.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .border(1.dp, if (isGyroEnabled) primaryColor else Color.Gray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Gyroscope",
                        tint = if (isGyroEnabled) primaryColor else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Nút Khóa nét vô cực (Infinity Focus)
                IconButton(
                    onClick = onToggleInfinityFocus,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isInfinityFocus) primaryColor.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .border(1.dp, if (isInfinityFocus) primaryColor else Color.Gray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.AllInclusive,
                        contentDescription = "Infinity Focus",
                        tint = if (isInfinityFocus) primaryColor else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Nút Chế độ Red Vision đèn đỏ ngắm sao
                IconButton(
                    onClick = onToggleRedVision,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isRedVision) Color.Red.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .border(1.dp, if (isRedVision) Color.Red else Color.Gray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Red Vision",
                        tint = if (isRedVision) Color.Red else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bảng đếm thời gian và số frame đã xếp chồng (Xuất hiện khi đang chụp)
        AnimatedVisibility(
            visible = isStackingActive,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val minutes = elapsedSeconds / 60
                    val remSeconds = elapsedSeconds % 60
                    val timeFormatted = String.format("%02d:%02d", minutes, remSeconds)

                    Text(
                        text = timeFormatted,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Đã xếp chồng $frameCount khung hình",
                        color = primaryColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Thanh thông số phơi sáng dưới cùng (Shutter, ISO, Focus)
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 120.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ParamItem(label = "TỐC ĐỘ", value = displayShutter, color = primaryColor)
                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.DarkGray))
                val boostLabel = if (isoBoost > 1.0f) " x${String.format("%.1f", isoBoost)}" else ""
                ParamItem(label = "ISO", value = "$currentIso$boostLabel", color = primaryColor)
                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.DarkGray))
                ParamItem(
                    label = "TIÊU CỰ",
                    value = if (isInfinityFocus) "∞ VÔ CỰC" else "AUTO",
                    color = primaryColor
                )
            }
        }
    }
}

@Composable
private fun ParamItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
