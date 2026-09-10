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
    activeParam: ActiveParameter,
    isStackingActive: Boolean,
    isRecordingVideo: Boolean = false,
    // Display parameters
    isManualExposure: Boolean,
    exposureTimeNs: Long,
    currentIso: Int,
    isoBoost: Float,
    isManualFocus: Boolean,
    isInfinityFocus: Boolean,
    focusDistance: Float,
    evCompensation: Int,
    evStepRational: android.util.Rational,
    // Callbacks
    onSelectParameter: (ActiveParameter) -> Unit,
    onModeSelected: (StackingMode) -> Unit,
    onShutterClick: () -> Unit,
    onCycleIsoBoost: () -> Unit,
    onResetStacking: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    val seconds = exposureTimeNs / 1_000_000_000.0
    val shutterLabel = if (!isManualExposure) {
        "AUTO"
    } else if (seconds >= 1.0) {
        String.format("%.1fs", seconds)
    } else {
        val fraction = Math.round(1.0 / seconds)
        "1/${fraction}s"
    }

    val isoLabel = if (!isManualExposure) "AUTO" else "$currentIso"

    val focusLabel = when {
        isInfinityFocus -> "∞ INF"
        isManualFocus -> "MANUAL"
        else -> "AUTO"
    }

    val evStepVal = if (evStepRational.denominator != 0) {
        evStepRational.numerator.toFloat() / evStepRational.denominator.toFloat()
    } else {
        0.5f
    }
    val evLabel = if (evCompensation == 0) "0.0" else String.format("%+.1f", evCompensation * evStepVal)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.92f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 10.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Parameter Selector Chips: SHUTTER, ISO, FOCUS, EV, BOOST
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ParamChip(
                label = "SHUTTER",
                value = shutterLabel,
                isActive = activeParam == ActiveParameter.SHUTTER,
                isModified = isManualExposure,
                primaryColor = primaryColor,
                onClick = {
                    onSelectParameter(
                        if (activeParam == ActiveParameter.SHUTTER) ActiveParameter.NONE else ActiveParameter.SHUTTER
                    )
                }
            )

            ParamChip(
                label = "ISO",
                value = isoLabel,
                isActive = activeParam == ActiveParameter.ISO,
                isModified = isManualExposure,
                primaryColor = primaryColor,
                onClick = {
                    onSelectParameter(
                        if (activeParam == ActiveParameter.ISO) ActiveParameter.NONE else ActiveParameter.ISO
                    )
                }
            )

            ParamChip(
                label = "FOCUS",
                value = focusLabel,
                isActive = activeParam == ActiveParameter.FOCUS,
                isModified = isManualFocus || isInfinityFocus,
                primaryColor = primaryColor,
                onClick = {
                    onSelectParameter(
                        if (activeParam == ActiveParameter.FOCUS) ActiveParameter.NONE else ActiveParameter.FOCUS
                    )
                }
            )

            ParamChip(
                label = "EV",
                value = evLabel,
                isActive = activeParam == ActiveParameter.EV,
                isModified = evCompensation != 0,
                primaryColor = primaryColor,
                onClick = {
                    onSelectParameter(
                        if (activeParam == ActiveParameter.EV) ActiveParameter.NONE else ActiveParameter.EV
                    )
                }
            )

            // Quick ISO BOOST button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isoBoost > 1.0f) primaryColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                    .border(1.dp, if (isoBoost > 1.0f) primaryColor else Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    .clickable { onCycleIsoBoost() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Boost",
                        tint = if (isoBoost > 1.0f) primaryColor else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${String.format("%.0f", isoBoost)}x BOOST",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isoBoost > 1.0f) primaryColor else Color.LightGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mode Selector Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
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
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Shutter Button & Tools Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ISO Boost cycle button
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

            val isActionActive = isStackingActive || isRecordingVideo
            val isVideoMode = selectedMode == StackingMode.VIDEO

            // Shutter Button (Capture / Record)
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(
                        width = 4.dp,
                        color = if (isActionActive || isVideoMode) Color.Red else primaryColor,
                        shape = CircleShape
                    )
                    .clickable { onShutterClick() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isActionActive) {
                    // Active Exposure or Recording: Red Square Stop Icon
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(Color.Red, RoundedCornerShape(6.dp))
                    )
                } else if (isVideoMode) {
                    // Video Mode Idle: Solid Red Circle
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Red, CircleShape)
                    )
                } else {
                    // Photo Mode Idle: Solid Theme Color Circle
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(primaryColor, CircleShape)
                    )
                }
            }

            // Reset Stacking Session Button
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

@Composable
private fun ParamChip(
    label: String,
    value: String,
    isActive: Boolean,
    isModified: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isActive) primaryColor.copy(alpha = 0.35f)
                else if (isModified) Color.White.copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.06f)
            )
            .border(
                width = 1.dp,
                color = if (isActive) primaryColor else if (isModified) primaryColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) primaryColor else Color.Gray,
                letterSpacing = 0.5.sp
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) primaryColor else Color.White
            )
        }
    }
}
