package com.nightcap.camera.ui

import android.app.Activity
import android.opengl.GLSurfaceView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.nightcap.camera.engine.Camera2Manager
import com.nightcap.camera.engine.GyroStabilizer
import com.nightcap.camera.engine.StackingMode
import com.nightcap.camera.gl.GLTextureRenderer
import com.nightcap.camera.ui.components.ActiveParameter
import com.nightcap.camera.ui.components.GestureOverlay
import com.nightcap.camera.ui.components.ManualControlBar
import com.nightcap.camera.ui.components.ParameterSliderBar
import com.nightcap.camera.ui.components.StackingHUD
import com.nightcap.camera.util.ImageSaver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Hardware controllers
    val gyroStabilizer = remember { GyroStabilizer(context) }
    val cameraManager = remember { Camera2Manager(context) }

    var selectedMode by remember { mutableStateOf(StackingMode.PHOTO) }
    var isStackingActive by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var frameCount by remember { mutableIntStateOf(0) }
    var isGyroEnabled by remember { mutableStateOf(true) }
    var isRedVision by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }

    // Front / Back camera & Video recording
    var isFrontCamera by remember { mutableStateOf(false) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    // Zoom state
    var currentZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(10.0f) }

    // Camera parameters
    var activeParam by remember { mutableStateOf(ActiveParameter.NONE) }
    var isManualExposure by remember { mutableStateOf(false) }
    var currentIso by remember { mutableIntStateOf(400) }
    var isoBoost by remember { mutableFloatStateOf(1.0f) }
    var exposureTimeNs by remember { mutableLongStateOf(33_333_333L) }

    var isManualFocus by remember { mutableStateOf(false) }
    var isInfinityFocus by remember { mutableStateOf(false) }
    var focusDistance by remember { mutableFloatStateOf(0.0f) }
    var evCompensation by remember { mutableIntStateOf(0) }

    // OpenGL Renderer
    val renderer = remember {
        GLTextureRenderer(
            context = context,
            gyroStabilizer = gyroStabilizer,
            onSurfaceTextureReady = { surfaceTexture ->
                cameraManager.openCamera(surfaceTexture)
            },
            onFrameProcessed = { count ->
                frameCount = count
            }
        )
    }

    LaunchedEffect(selectedMode) {
        renderer.currentMode = selectedMode
    }
    LaunchedEffect(isoBoost) {
        renderer.isoBoost = isoBoost
    }
    LaunchedEffect(isRedVision) {
        renderer.redVisionMode = isRedVision
    }
    LaunchedEffect(isFrontCamera) {
        renderer.isFrontCamera = isFrontCamera
    }

    // Stacking exposure timer
    LaunchedEffect(isStackingActive) {
        if (isStackingActive) {
            elapsedSeconds = 0
            while (isStackingActive) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    // Video recording timer
    LaunchedEffect(isRecordingVideo) {
        if (isRecordingVideo) {
            recordingSeconds = 0
            while (isRecordingVideo) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    // Hardware lifecycle
    DisposableEffect(Unit) {
        cameraManager.startBackgroundThread()
        gyroStabilizer.start()
        cameraManager.onPreviewSizeConfigured = { w, h ->
            renderer.setPreviewSize(w, h)
        }
        cameraManager.onZoomChanged = { zoom ->
            currentZoomRatio = zoom
            minZoomRatio = cameraManager.minZoomRatio
            maxZoomRatio = cameraManager.maxZoomRatio
        }

        onDispose {
            cameraManager.onPreviewSizeConfigured = null
            cameraManager.onZoomChanged = null
            gyroStabilizer.stop()
            cameraManager.closeCamera()
            cameraManager.stopBackgroundThread()
        }
    }

    NightCapTheme(isRedVision = isRedVision) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Layer 1: Fullscreen OpenGL Camera Viewfinder
            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(3)
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Layer 2: Gesture Overlay (Pinch to Zoom & Tap to Focus)
            GestureOverlay(
                currentZoomRatio = currentZoomRatio,
                minZoomRatio = minZoomRatio,
                maxZoomRatio = maxZoomRatio,
                onTapToFocus = { normX, normY ->
                    cameraManager.tapToFocus(normX, normY)
                },
                onZoomChanged = { targetZoom ->
                    currentZoomRatio = targetZoom
                    cameraManager.setZoomRatio(targetZoom)
                }
            ) {
                // Layer 3: Top HUD (Status insets, Mode badge, Quick buttons) & Center timers
                StackingHUD(
                    isStackingActive = isStackingActive,
                    elapsedSeconds = elapsedSeconds,
                    frameCount = frameCount,
                    isRecordingVideo = isRecordingVideo,
                    recordingSeconds = recordingSeconds,
                    mode = selectedMode,
                    isGyroEnabled = isGyroEnabled,
                    isRedVision = isRedVision,
                    isTorchOn = isTorchOn,
                    isFrontCamera = isFrontCamera,
                    onToggleRedVision = {
                        isRedVision = !isRedVision
                    },
                    onToggleGyro = {
                        isGyroEnabled = !isGyroEnabled
                        gyroStabilizer.isEnabled = isGyroEnabled
                        Toast.makeText(
                            context,
                            if (isGyroEnabled) "Handheld stabilization enabled" else "Stabilization disabled (Tripod mode)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onToggleTorch = {
                        isTorchOn = !isTorchOn
                        cameraManager.toggleTorch(isTorchOn)
                        Toast.makeText(
                            context,
                            if (isTorchOn) "Torch enabled" else "Torch disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onSwitchCamera = {
                        if (isRecordingVideo) {
                            Toast.makeText(context, "Please stop recording before switching camera", Toast.LENGTH_SHORT).show()
                        } else if (isStackingActive) {
                            Toast.makeText(context, "Please stop exposure before switching camera", Toast.LENGTH_SHORT).show()
                        } else {
                            cameraManager.switchCamera()
                            isFrontCamera = cameraManager.isFrontCamera
                            Toast.makeText(
                                context,
                                if (isFrontCamera) "Switched to Front Camera" else "Switched to Back Camera",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            // Layer 4: Bottom Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Parameter Slider Bar
                ParameterSliderBar(
                    activeParam = activeParam,
                    exposureTimeNs = exposureTimeNs,
                    minExposureNs = cameraManager.exposureTimeRange.lower,
                    maxExposureNs = cameraManager.exposureTimeRange.upper,
                    isManualExposure = isManualExposure,
                    onExposureChanged = { newExposure ->
                        exposureTimeNs = newExposure
                        isManualExposure = true
                        cameraManager.setExposureTime(newExposure)
                    },
                    onAutoExposure = {
                        isManualExposure = false
                        cameraManager.setAutoExposure()
                    },
                    currentIso = currentIso,
                    minIso = cameraManager.isoRange.lower,
                    maxIso = cameraManager.isoRange.upper,
                    onIsoChanged = { newIso ->
                        currentIso = newIso
                        isManualExposure = true
                        cameraManager.setIso(newIso)
                    },
                    focusDistance = focusDistance,
                    minFocusDistance = cameraManager.minFocusDistance,
                    isManualFocus = isManualFocus,
                    isInfinityFocus = isInfinityFocus,
                    onFocusDistanceChanged = { newDist ->
                        focusDistance = newDist
                        isManualFocus = true
                        isInfinityFocus = false
                        cameraManager.setFocusDistance(newDist)
                    },
                    onAutoFocus = {
                        isManualFocus = false
                        isInfinityFocus = false
                        cameraManager.setAutoFocus()
                    },
                    onInfinityFocus = {
                        isInfinityFocus = true
                        isManualFocus = false
                        focusDistance = 0f
                        cameraManager.toggleInfinityFocus(true)
                    },
                    evCompensation = evCompensation,
                    minEv = cameraManager.evRange.lower,
                    maxEv = cameraManager.evRange.upper,
                    evStepRational = cameraManager.evStep,
                    onEvChanged = { newEv ->
                        evCompensation = newEv
                        cameraManager.setEvCompensation(newEv)
                    },
                    onDismiss = {
                        activeParam = ActiveParameter.NONE
                    }
                )

                // Manual Control Bar & Shutter
                ManualControlBar(
                    selectedMode = selectedMode,
                    activeParam = activeParam,
                    isStackingActive = isStackingActive,
                    isRecordingVideo = isRecordingVideo,
                    isManualExposure = isManualExposure,
                    exposureTimeNs = exposureTimeNs,
                    currentIso = currentIso,
                    isoBoost = isoBoost,
                    isManualFocus = isManualFocus,
                    isInfinityFocus = isInfinityFocus,
                    focusDistance = focusDistance,
                    evCompensation = evCompensation,
                    evStepRational = cameraManager.evStep,
                    onSelectParameter = { param ->
                        activeParam = param
                    },
                    onModeSelected = { mode ->
                        if (isRecordingVideo) {
                            Toast.makeText(context, "Please stop recording before changing mode", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedMode = mode
                            // Suggest optimal night parameters when switching to astrophotography modes
                            if (mode != StackingMode.PHOTO && mode != StackingMode.VIDEO) {
                                isManualExposure = true
                                cameraManager.setExposureTime(250_000_000L) // 1/4s
                                cameraManager.setIso(1600)
                                currentIso = 1600
                                exposureTimeNs = 250_000_000L
                            } else {
                                isManualExposure = false
                                cameraManager.setAutoExposure()
                            }
                        }
                    },
                    onShutterClick = {
                        when (selectedMode) {
                            StackingMode.VIDEO -> {
                                // Video Recording Mode
                                if (!isRecordingVideo) {
                                    val tempFile = java.io.File(context.cacheDir, "REC_${System.currentTimeMillis()}.mp4")
                                    cameraManager.startRecordingVideo(
                                        outputFile = tempFile,
                                        onStarted = {
                                            isRecordingVideo = true
                                            Toast.makeText(context, "Recording started...", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            isRecordingVideo = false
                                            Toast.makeText(context, "Recording error: $err", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                } else {
                                    isRecordingVideo = false
                                    Toast.makeText(context, "Processing and saving video...", Toast.LENGTH_SHORT).show()
                                    cameraManager.stopRecordingVideo { videoFile ->
                                        coroutineScope.launch {
                                            val uri = ImageSaver.saveVideoToGallery(context, videoFile)
                                            (context as? Activity)?.runOnUiThread {
                                                if (uri != null) {
                                                    Toast.makeText(context, "Saved video ($recordingSeconds s) to Gallery!", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Failed to save video", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            StackingMode.PHOTO -> {
                                // Single Photo Mode
                                Toast.makeText(context, "Capturing photo...", Toast.LENGTH_SHORT).show()
                                renderer.captureSinglePhoto { bitmap ->
                                    coroutineScope.launch {
                                        val uri = ImageSaver.saveBitmapToGallery(context, bitmap, "PHOTO")
                                        (context as? Activity)?.runOnUiThread {
                                            if (uri != null) {
                                                Toast.makeText(context, "Photo saved to Gallery!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                // Multi-frame Stacking Modes (STARS, TRAILS, METEORS)
                                if (!isStackingActive) {
                                    frameCount = 0
                                    isStackingActive = true
                                    renderer.startStacking()
                                    Toast.makeText(context, "Exposure started [${selectedMode.displayName}]", Toast.LENGTH_SHORT).show()
                                } else {
                                    isStackingActive = false
                                    renderer.stopStacking { bitmap ->
                                        coroutineScope.launch {
                                            val uri = ImageSaver.saveBitmapToGallery(context, bitmap, selectedMode.name)
                                            (context as? Activity)?.runOnUiThread {
                                                if (uri != null) {
                                                    Toast.makeText(context, "Saved astrophoto ($frameCount frames) to Gallery!", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onCycleIsoBoost = {
                        isoBoost = when (isoBoost) {
                            1.0f -> 2.0f
                            2.0f -> 4.0f
                            4.0f -> 8.0f
                            else -> 1.0f
                        }
                        Toast.makeText(context, "ISO Boost: ${String.format("%.0f", isoBoost)}x", Toast.LENGTH_SHORT).show()
                    },
                    onResetStacking = {
                        if (isStackingActive) {
                            isStackingActive = false
                            renderer.resetStacking()
                            frameCount = 0
                            elapsedSeconds = 0
                            Toast.makeText(context, "Exposure cancelled", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}
