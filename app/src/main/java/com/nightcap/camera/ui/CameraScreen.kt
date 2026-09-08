package com.nightcap.camera.ui

import android.app.Activity
import android.content.Context
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
import com.nightcap.camera.ui.components.GestureOverlay
import com.nightcap.camera.ui.components.ManualControlBar
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

    // Khởi tạo phần cứng
    val gyroStabilizer = remember { GyroStabilizer(context) }
    val cameraManager = remember { Camera2Manager(context) }

    var selectedMode by remember { mutableStateOf(StackingMode.PHOTO) }
    var isStackingActive by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var frameCount by remember { mutableIntStateOf(0) }
    var isGyroEnabled by remember { mutableStateOf(true) }
    var isInfinityFocus by remember { mutableStateOf(false) }
    var isManualExposure by remember { mutableStateOf(false) }
    var isRedVision by remember { mutableStateOf(false) }
    var currentIso by remember { mutableIntStateOf(400) }
    var isoBoost by remember { mutableFloatStateOf(1.0f) }
    var exposureTimeNs by remember { mutableLongStateOf(33_333_333L) }

    // Khởi tạo OpenGL Renderer
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

    // Đếm thời gian khi phơi sáng
    LaunchedEffect(isStackingActive) {
        if (isStackingActive) {
            elapsedSeconds = 0
            while (isStackingActive) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    // Vòng đời cảm biến
    DisposableEffect(Unit) {
        cameraManager.startBackgroundThread()
        gyroStabilizer.start()

        onDispose {
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
            // Khung ngắm Camera OpenGL
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

            // Lớp cử chỉ vuốt & Chạm lấy nét (Tap to Focus)
            GestureOverlay(
                currentIso = currentIso,
                isoBoost = isoBoost,
                currentExposureNs = exposureTimeNs,
                onIsoChanged = { newIso, newBoost ->
                    currentIso = newIso
                    isoBoost = newBoost
                    isManualExposure = true
                    cameraManager.setIso(newIso)
                },
                onExposureChanged = { newExposure ->
                    exposureTimeNs = newExposure
                    isManualExposure = true
                    cameraManager.setExposureTime(newExposure)
                },
                onTapToFocus = { normX, normY ->
                    cameraManager.tapToFocus(normX, normY)
                }
            ) {
                // HUD thông số và phím chức năng
                StackingHUD(
                    isStackingActive = isStackingActive,
                    elapsedSeconds = elapsedSeconds,
                    frameCount = frameCount,
                    mode = selectedMode,
                    isGyroEnabled = isGyroEnabled,
                    isInfinityFocus = isInfinityFocus,
                    isManualExposure = isManualExposure,
                    isRedVision = isRedVision,
                    currentIso = currentIso,
                    isoBoost = isoBoost,
                    exposureTimeNs = exposureTimeNs,
                    onToggleRedVision = {
                        isRedVision = !isRedVision
                    },
                    onToggleGyro = {
                        isGyroEnabled = !isGyroEnabled
                        gyroStabilizer.isEnabled = isGyroEnabled
                        Toast.makeText(
                            context,
                            if (isGyroEnabled) "Đã bật chống rung tay cầm" else "Đã tắt chống rung (Chế độ chân máy)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onToggleInfinityFocus = {
                        isInfinityFocus = !isInfinityFocus
                        cameraManager.toggleInfinityFocus(isInfinityFocus)
                        Toast.makeText(
                            context,
                            if (isInfinityFocus) "Đã khóa nét vô cực (∞)" else "Lấy nét tự động liên tục (AF)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onToggleExposureMode = {
                        cameraManager.toggleAutoExposure()
                        isManualExposure = cameraManager.isManualControl
                        Toast.makeText(
                            context,
                            if (isManualExposure) "Đã chuyển sang chỉnh tay (Manual)" else "Đã bật Tự động đo sáng (Auto Exposure)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            // Thanh điều khiển dưới cùng
            ManualControlBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedMode = selectedMode,
                isStackingActive = isStackingActive,
                isoBoost = isoBoost,
                onModeSelected = { mode ->
                    selectedMode = mode
                    // Nếu người dùng chọn mode chụp thiên văn thì gợi ý thông số tối ưu ban đêm
                    if (mode != StackingMode.PHOTO) {
                        isManualExposure = true
                        cameraManager.setExposureTime(250_000_000L) // 1/4s
                        cameraManager.setIso(1600)
                        currentIso = 1600
                        exposureTimeNs = 250_000_000L
                    } else {
                        isManualExposure = false
                        cameraManager.isManualControl = false
                        cameraManager.updatePreview()
                    }
                },
                onShutterClick = {
                    if (selectedMode == StackingMode.PHOTO) {
                        // CHỤP ĐƠN (PHOTO MODE): Chụp ngay 1 ảnh duy nhất không cần bấm Stop
                        Toast.makeText(context, "Đang chụp ảnh...", Toast.LENGTH_SHORT).show()
                        renderer.captureSinglePhoto { bitmap ->
                            coroutineScope.launch {
                                val uri = ImageSaver.saveBitmapToGallery(context, bitmap, "PHOTO")
                                (context as? Activity)?.runOnUiThread {
                                    if (uri != null) {
                                        Toast.makeText(context, "Đã lưu ảnh vào Bộ sưu tập!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Lỗi khi lưu ảnh", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    } else {
                        // CHẾ ĐỘ XẾP CHỒNG (STARS, TRAILS, METEORS): Bấm lần 1 bắt đầu, Bấm lần 2 dừng
                        if (!isStackingActive) {
                            frameCount = 0
                            isStackingActive = true
                            renderer.startStacking()
                            Toast.makeText(context, "Bắt đầu phơi sáng [${selectedMode.displayName}]", Toast.LENGTH_SHORT).show()
                        } else {
                            isStackingActive = false
                            renderer.stopStacking { bitmap ->
                                coroutineScope.launch {
                                    val uri = ImageSaver.saveBitmapToGallery(context, bitmap, selectedMode.name)
                                    (context as? Activity)?.runOnUiThread {
                                        if (uri != null) {
                                            Toast.makeText(context, "Đã lưu ảnh thiên văn ($frameCount frames) vào Bộ sưu tập!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Lỗi khi lưu ảnh", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "Đã hủy phiên phơi sáng", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
