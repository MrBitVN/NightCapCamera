package com.nightcap.camera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    isRecordingVideo: Boolean,
    recordingSeconds: Int,
    mode: StackingMode,
    isGyroEnabled: Boolean,
    isRedVision: Boolean,
    isTorchOn: Boolean,
    isFrontCamera: Boolean,
    onToggleRedVision: () -> Unit,
    onToggleGyro: () -> Unit,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

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
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current Mode Badge
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isRecordingVideo) Color.Red else primaryColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isStackingActive || isRecordingVideo) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isRecordingVideo) "REC" else mode.displayName.uppercase(),
                        color = if (isRecordingVideo) Color.Red else primaryColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick utility buttons on top
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Switch Front / Back Camera (Selfie Flip)
                IconButton(
                    onClick = onSwitchCamera,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (isFrontCamera) primaryColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .border(1.dp, if (isFrontCamera) primaryColor else Color.Gray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch Camera",
                        tint = if (isFrontCamera) primaryColor else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Torch / Flashlight
                IconButton(
                    onClick = onToggleTorch,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (isTorchOn) primaryColor.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .border(1.dp, if (isTorchOn) primaryColor else Color.Gray, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash/Torch",
                        tint = if (isTorchOn) primaryColor else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Handheld Gyroscope Stabilization
                IconButton(
                    onClick = onToggleGyro,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (isGyroEnabled) primaryColor.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .border(1.dp, if (isGyroEnabled) primaryColor else Color.Gray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Gyroscope",
                        tint = if (isGyroEnabled) primaryColor else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Astronomy Red Vision Mode
                IconButton(
                    onClick = onToggleRedVision,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (isRedVision) Color.Red.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .border(1.dp, if (isRedVision) Color.Red else Color.Gray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Red Vision",
                        tint = if (isRedVision) Color.Red else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Video Recording Timer Display
        AnimatedVisibility(
            visible = isRecordingVideo,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.Red)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = pulseAlpha))
                    )
                    val recMin = recordingSeconds / 60
                    val recSec = recordingSeconds % 60
                    Text(
                        text = String.format("REC %02d:%02d", recMin, recSec),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Stacking Exposure Timer & Frame Counter Display
        AnimatedVisibility(
            visible = isStackingActive,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, primaryColor)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val minutes = elapsedSeconds / 60
                    val remSeconds = elapsedSeconds % 60
                    val timeFormatted = String.format("%02d:%02d", minutes, remSeconds)

                    Text(
                        text = timeFormatted,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Stacked $frameCount frames",
                        color = primaryColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Spacer to balance SpaceBetween vertical alignment
        Spacer(modifier = Modifier.height(1.dp))
    }
}
