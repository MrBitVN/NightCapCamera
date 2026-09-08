package com.nightcap.camera.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightcap.camera.engine.StackingMode

@Composable
fun ManualControlBar(
    modifier: Modifier = Modifier,
    selectedMode: StackingMode,
    isStackingActive: Boolean,
    isoBoost: Float,
    onModeSelected: (StackingMode) -> Unit,
    onShutterClick: () -> Unit,
    onCycleIsoBoost: () -> Unit,
    onResetStacking: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    // Animation xoay vòng tròn khi đang phơi sáng
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Thanh cuộn chọn chế độ chụp (Mode Selector)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StackingMode.values().forEach { mode ->
                val isSelected = mode == selectedMode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) primaryColor else Color.White.copy(alpha = 0.08f)
                        )
                        .clickable(enabled = !isStackingActive) {
                            onModeSelected(mode)
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = mode.name.replace("_", " "),
                        color = if (isSelected) Color.Black else Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Hàng nút Shutter và các công cụ bổ trợ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nút ISO Boost (1x -> 2x -> 4x -> 8x)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onCycleIsoBoost() }
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (isoBoost > 1.0f) primaryColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                            CircleShape
                        )
                        .border(1.dp, if (isoBoost > 1.0f) primaryColor else Color.Gray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "ISO Boost",
                        tint = if (isoBoost > 1.0f) primaryColor else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${String.format("%.0f", isoBoost)}x BOOST",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isoBoost > 1.0f) primaryColor else Color.Gray
                )
            }

            // Nút Shutter chụp / dừng phơi sáng
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(
                        width = 4.dp,
                        color = if (isStackingActive) Color.Red else primaryColor,
                        shape = CircleShape
                    )
                    .clickable { onShutterClick() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isStackingActive) {
                    // Khi đang phơi sáng: Icon hình vuông màu đỏ (Dừng phơi sáng & Lưu)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.Red, RoundedCornerShape(6.dp))
                    )
                } else {
                    // Khi chờ: Vòng tròn đầy màu trắng/vàng
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(primaryColor, CircleShape)
                    )
                }
            }

            // Nút Hủy / Reset phiên chụp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(enabled = isStackingActive) { onResetStacking() }
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .border(1.dp, if (isStackingActive) Color.Gray else Color.DarkGray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Stacking",
                        tint = if (isStackingActive) Color.White else Color.DarkGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "RESET",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isStackingActive) Color.LightGray else Color.DarkGray
                )
            }
        }
    }
}
