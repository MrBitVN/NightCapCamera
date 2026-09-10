package com.nightcap.camera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ActiveParameter {
    NONE,
    SHUTTER,
    ISO,
    FOCUS,
    EV
}

@Composable
fun ParameterSliderBar(
    modifier: Modifier = Modifier,
    activeParam: ActiveParameter,
    // Shutter speed (Exposure Time)
    exposureTimeNs: Long,
    minExposureNs: Long,
    maxExposureNs: Long,
    isManualExposure: Boolean,
    onExposureChanged: (Long) -> Unit,
    onAutoExposure: () -> Unit,
    // ISO
    currentIso: Int,
    minIso: Int,
    maxIso: Int,
    onIsoChanged: (Int) -> Unit,
    // Focus
    focusDistance: Float,
    minFocusDistance: Float,
    isManualFocus: Boolean,
    isInfinityFocus: Boolean,
    onFocusDistanceChanged: (Float) -> Unit,
    onAutoFocus: () -> Unit,
    onInfinityFocus: () -> Unit,
    // EV (Exposure Compensation)
    evCompensation: Int,
    minEv: Int,
    maxEv: Int,
    evStepRational: android.util.Rational,
    onEvChanged: (Int) -> Unit,
    // Close slider
    onDismiss: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    AnimatedVisibility(
        visible = activeParam != ActiveParameter.NONE,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .background(Color(0xF0141414), RoundedCornerShape(22.dp))
                .border(1.dp, primaryColor.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (activeParam) {
                ActiveParameter.SHUTTER -> {
                    // Shutter display calculation
                    val seconds = exposureTimeNs / 1_000_000_000.0
                    val displayValue = if (!isManualExposure) {
                        "AUTO"
                    } else if (seconds >= 1.0) {
                        String.format("%.2fs", seconds)
                    } else {
                        val frac = Math.round(1.0 / seconds)
                        "1/${frac}s"
                    }

                    SliderHeader(
                        icon = Icons.Default.Speed,
                        title = "SHUTTER SPEED",
                        valueText = displayValue,
                        isAuto = !isManualExposure,
                        onAutoClick = onAutoExposure,
                        onCloseClick = onDismiss,
                        primaryColor = primaryColor
                    )

                    // Logarithmic volume slider for Shutter speed
                    val minLog = Math.log(minExposureNs.toDouble().coerceAtLeast(100_000.0))
                    val maxLog = Math.log(maxExposureNs.toDouble().coerceAtLeast(minExposureNs.toDouble() + 1.0))
                    val currentLog = Math.log(exposureTimeNs.toDouble().coerceIn(minExposureNs.toDouble(), maxExposureNs.toDouble()))
                    val sliderFraction = ((currentLog - minLog) / (maxLog - minLog)).toFloat().coerceIn(0f, 1f)

                    VolumeSliderRow(
                        sliderValue = sliderFraction,
                        onValueChange = { frac ->
                            val targetLog = minLog + frac * (maxLog - minLog)
                            val targetNs = Math.exp(targetLog).toLong().coerceIn(minExposureNs, maxExposureNs)
                            onExposureChanged(targetNs)
                        },
                        onStepDown = {
                            val newNs = (exposureTimeNs / 1.5).toLong().coerceIn(minExposureNs, maxExposureNs)
                            onExposureChanged(newNs)
                        },
                        onStepUp = {
                            val newNs = (exposureTimeNs * 1.5).toLong().coerceIn(minExposureNs, maxExposureNs)
                            onExposureChanged(newNs)
                        },
                        leftLabel = "1/8000s",
                        rightLabel = "${maxExposureNs / 1_000_000_000L}s",
                        primaryColor = primaryColor
                    )
                }

                ActiveParameter.ISO -> {
                    val displayValue = if (!isManualExposure) "AUTO" else "ISO $currentIso"

                    SliderHeader(
                        icon = Icons.Default.WbSunny,
                        title = "ISO SENSITIVITY",
                        valueText = displayValue,
                        isAuto = !isManualExposure,
                        onAutoClick = onAutoExposure,
                        onCloseClick = onDismiss,
                        primaryColor = primaryColor
                    )

                    // Volume-like slider for ISO
                    val minLog = Math.log(minIso.toDouble().coerceAtLeast(50.0))
                    val maxLog = Math.log(maxIso.toDouble().coerceAtLeast(100.0))
                    val currentLog = Math.log(currentIso.toDouble().coerceIn(minIso.toDouble(), maxIso.toDouble()))
                    val sliderFraction = ((currentLog - minLog) / (maxLog - minLog)).toFloat().coerceIn(0f, 1f)

                    VolumeSliderRow(
                        sliderValue = sliderFraction,
                        onValueChange = { frac ->
                            val targetLog = minLog + frac * (maxLog - minLog)
                            val targetIso = Math.round(Math.exp(targetLog) / 50.0).toInt() * 50
                            onIsoChanged(targetIso.coerceIn(minIso, maxIso))
                        },
                        onStepDown = {
                            onIsoChanged((currentIso - 100).coerceIn(minIso, maxIso))
                        },
                        onStepUp = {
                            onIsoChanged((currentIso + 100).coerceIn(minIso, maxIso))
                        },
                        leftLabel = "$minIso",
                        rightLabel = "$maxIso",
                        primaryColor = primaryColor
                    )
                }

                ActiveParameter.FOCUS -> {
                    val displayValue = when {
                        !isManualFocus && !isInfinityFocus -> "AUTO FOCUS (AF)"
                        isInfinityFocus -> "∞ INFINITY (STARS)"
                        else -> {
                            val approxDistance = if (focusDistance > 0.05f) {
                                String.format("%.2fm (Macro)", 1.0f / focusDistance)
                            } else {
                                "∞ Infinity"
                            }
                            "MANUAL: $approxDistance"
                        }
                    }

                    SliderHeader(
                        icon = Icons.Default.CenterFocusStrong,
                        title = "FOCUS DISTANCE",
                        valueText = displayValue,
                        isAuto = !isManualFocus && !isInfinityFocus,
                        onAutoClick = onAutoFocus,
                        onCloseClick = onDismiss,
                        primaryColor = primaryColor,
                        extraAction = {
                            // Quick button to lock Infinity focus
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isInfinityFocus) primaryColor else Color.White.copy(alpha = 0.12f))
                                    .clickable { onInfinityFocus() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "∞ INFINITY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isInfinityFocus) Color.Black else Color.White
                                )
                            }
                        }
                    )

                    val maxFocus = if (minFocusDistance > 0f) minFocusDistance else 10f
                    val currentVal = if (isInfinityFocus) 0f else focusDistance.coerceIn(0f, maxFocus)
                    val sliderFraction = (currentVal / maxFocus).coerceIn(0f, 1f)

                    VolumeSliderRow(
                        sliderValue = sliderFraction,
                        onValueChange = { frac ->
                            val dist = frac * maxFocus
                            onFocusDistanceChanged(dist)
                        },
                        onStepDown = {
                            val step = maxFocus * 0.05f
                            onFocusDistanceChanged((focusDistance - step).coerceIn(0f, maxFocus))
                        },
                        onStepUp = {
                            val step = maxFocus * 0.05f
                            onFocusDistanceChanged((focusDistance + step).coerceIn(0f, maxFocus))
                        },
                        leftLabel = "∞ Infinity",
                        rightLabel = "Macro",
                        primaryColor = primaryColor
                    )
                }

                ActiveParameter.EV -> {
                    val stepVal = if (evStepRational.denominator != 0) {
                        evStepRational.numerator.toDouble() / evStepRational.denominator.toDouble()
                    } else {
                        0.5
                    }
                    val evReal = evCompensation * stepVal
                    val displayValue = if (evCompensation == 0) {
                        "0.0 EV (STANDARD)"
                    } else {
                        String.format("%+.1f EV", evReal)
                    }

                    SliderHeader(
                        icon = Icons.Default.Exposure,
                        title = "EXPOSURE COMPENSATION (EV)",
                        valueText = displayValue,
                        isAuto = evCompensation == 0,
                        onAutoClick = { onEvChanged(0) },
                        onCloseClick = onDismiss,
                        primaryColor = primaryColor
                    )

                    val totalRange = (maxEv - minEv).coerceAtLeast(1)
                    val sliderFraction = ((evCompensation - minEv).toFloat() / totalRange.toFloat()).coerceIn(0f, 1f)

                    VolumeSliderRow(
                        sliderValue = sliderFraction,
                        onValueChange = { frac ->
                            val newEv = Math.round(minEv + frac * totalRange).toInt()
                            onEvChanged(newEv.coerceIn(minEv, maxEv))
                        },
                        onStepDown = {
                            onEvChanged((evCompensation - 1).coerceIn(minEv, maxEv))
                        },
                        onStepUp = {
                            onEvChanged((evCompensation + 1).coerceIn(minEv, maxEv))
                        },
                        leftLabel = String.format("%.1f", minEv * stepVal),
                        rightLabel = String.format("+%.1f", maxEv * stepVal),
                        primaryColor = primaryColor
                    )
                }

                ActiveParameter.NONE -> {}
            }
        }
    }
}

@Composable
private fun SliderHeader(
    icon: ImageVector,
    title: String,
    valueText: String,
    isAuto: Boolean,
    onAutoClick: () -> Unit,
    onCloseClick: () -> Unit,
    primaryColor: Color,
    extraAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = primaryColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
                letterSpacing = 0.5.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            extraAction?.invoke()

            // Nút AUTO
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isAuto) primaryColor else Color.White.copy(alpha = 0.12f))
                    .clickable { onAutoClick() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "AUTO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAuto) Color.Black else Color.White
                )
            }

            // Close button (X)
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    // Centered digital parameter readout
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = valueText,
            color = primaryColor,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun VolumeSliderRow(
    sliderValue: Float,
    onValueChange: (Float) -> Unit,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    leftLabel: String,
    rightLabel: String,
    primaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Decrease button (-)
        IconButton(
            onClick = onStepDown,
            modifier = Modifier
                .size(34.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            text = leftLabel,
            fontSize = 10.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )

        // Volume-style Slider
        Slider(
            value = sliderValue,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = primaryColor,
                activeTrackColor = primaryColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            )
        )

        Text(
            text = rightLabel,
            fontSize = 10.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )

        // Increase button (+)
        IconButton(
            onClick = onStepUp,
            modifier = Modifier
                .size(34.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
