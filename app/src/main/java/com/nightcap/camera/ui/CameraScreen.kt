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

    // Khởi tạo các thành phần phần cứng
    val gyroStabilizer = remember { GyroStabilizer(context) }
    val cameraManager = remember { Camera2Manager(context) }

    var selectedMode by remember { mutableStateOf(StackingMode.STARS) }
    var isStackingActive by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var frameCount by remember { mutableIntStateOf(0) }
    var isGyroEnabled by remember { mutableStateOf(true) }
    var isInfinityFocus by remember { mutableStateOf(true) }
    var isRedVision by remember { mutableStateOf(false) }
    var currentIso by remember { mutableIntStateOf(1600) }
    var isoBoost by remember { mutableFloatStateOf(1.0f) }
    var exposureTimeNs by remember { mutableLongStateOf(250_000_000L) }

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

    // Cập nhật trạng thái vào Renderer khi thay đổi
    LaunchedEffect(selectedMode) {
        renderer.currentMode = selectedMode
    }
    LaunchedEffect(isoBoost) {
        renderer.isoBoost = isoBoost
    }
    LaunchedEffect(isRedVision) {
        renderer.redVisionMode = isRedVision
    }

    // Vòng lặp đếm thời gian khi đang phơi sáng
    LaunchedEffect(isStackingActive) {
        if (isStackingActive) {
            elapsedSeconds = 0
            while (isStackingActive) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    // Quản lý vòng đời cảm biến Gyro
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
            // Lớp 1: Khung ngắm Camera & OpenGL Stacking Preview
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

            // Lớp 2: Lớp lắng nghe cử chỉ vuốt để chỉnh ISO và Tốc độ màn trập
            GestureOverlay(
                currentIso = currentIso,
                isoBoost = isoBoost,
                currentExposureNs = exposureTimeNs,
                onIsoChanged = { newIso, newBoost ->
                    currentIso = newIso
                    isoBoost = newBoost
                    cameraManager.setIso(newIso)
                },
                onExposureChanged = { newExposure ->
                    exposureTimeNs = newExposure
                    cameraManager.setExposureTime(newExposure)
                }
            ) {
                // Lớp 3: HUD hiển thị thông số và các nút tiện ích trên đỉnh
                StackingHUD(
                    isStackingActive = isStackingActive,
                    elapsedSeconds = elapsedSeconds,
                    frameCount = frameCount,
                    mode = selectedMode,
                    isGyroEnabled = isGyroEnabled,
                    isInfinityFocus = isInfinityFocus,
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
                            if (isGyroEnabled) "Đã bật chống rung tay cầm" else "Đã tắt chống rung (Chế độ chân máy/Tripod)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onToggleInfinityFocus = {
                        isInfinityFocus = !isInfinityFocus
                        cameraManager.toggleInfinityFocus(isInfinityFocus)
                        Toast.makeText(
                            context,
                            if (isInfinityFocus) "Đã khóa nét vô cực (Infinity Focus ∞)" else "Lấy nét tự động (AF)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            // Lớp 4: Thanh điều khiển dưới cùng (Nút Shutter, chọn chế độ, ISO Boost)
            ManualControlBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedMode = selectedMode,
                isStackingActive = isStackingActive,
                isoBoost = isoBoost,
                onModeSelected = { mode ->
                    selectedMode = mode
                },
                onShutterClick = {
                    if (!isStackingActive) {
                        // Bắt đầu phiên phơi sáng / xếp chồng
                        frameCount = 0
                        isStackingActive = true
                        renderer.startStacking()
                        Toast.makeText(context, "Bắt đầu phơi sáng [${selectedMode.displayName}]", Toast.LENGTH_SHORT).show()
                    } else {
                        // Dừng phơi sáng và xuất ảnh độ phân giải cao
                        isStackingActive = false
                        renderer.stopStacking { bitmap ->
                            coroutineScope.launch {
                                val uri = ImageSaver.saveBitmapToGallery(context, bitmap, selectedMode.name)
                                (context as? Activity)?.runOnUiThread {
                                    if (uri != null) {
                                        Toast.makeText(context, "Đã lưu ảnh thiên văn vào Bộ sưu tập!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Lỗi khi lưu ảnh", Toast.LENGTH_SHORT).show()
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
