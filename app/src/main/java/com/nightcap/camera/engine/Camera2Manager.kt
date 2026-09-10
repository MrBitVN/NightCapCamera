package com.nightcap.camera.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Surface

class Camera2Manager(private val context: Context) {

    companion object {
        private const val TAG = "Camera2Manager"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var cameraCharacteristics: CameraCharacteristics? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var currentSurfaceTexture: SurfaceTexture? = null

    // Camera sensor capability ranges
    var exposureTimeRange: Range<Long> = Range(1_000_000L, 1_000_000_000L) // Nanoseconds
        private set
    var isoRange: Range<Int> = Range(100, 3200)
        private set
    var minFocusDistance: Float = 0.0f
        private set
    var evRange: Range<Int> = Range(-4, 4)
        private set
    var evStep: android.util.Rational = android.util.Rational(1, 2)
        private set

    // Zoom capabilities and state
    var minZoomRatio: Float = 1.0f
        private set
    var maxZoomRatio: Float = 10.0f
        private set
    var currentZoomRatio: Float = 1.0f
        private set
    var supportsZoomRatio: Boolean = false
        private set
    var onZoomChanged: ((Float) -> Unit)? = null

    // Operating state
    var isManualControl: Boolean = false
    var isInfinityFocus: Boolean = false
    var isManualFocus: Boolean = false
    var manualFocusDistance: Float = 0.0f // 0.0f = infinity (astrophotography), minFocusDistance = macro
    var currentExposureTimeNs: Long = 33_333_333L // Default 1/30s (33ms)
    var currentIso: Int = 400
    var evCompensation: Int = 0
        private set
    var isTorchOn: Boolean = false

    var previewWidth: Int = 1920
        private set
    var previewHeight: Int = 1080
        private set
    var onPreviewSizeConfigured: ((Int, Int) -> Unit)? = null

    // Front / Back Camera management
    var isFrontCamera: Boolean = false
        private set

    // Quản lý Quay Video
    private var mediaRecorder: android.media.MediaRecorder? = null
    var isRecordingVideo: Boolean = false
        private set
    var currentVideoFile: java.io.File? = null
        private set

    fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(surfaceTexture: SurfaceTexture) {
        currentSurfaceTexture = surfaceTexture
        try {
            val cameraId = getCameraId(isFrontCamera) ?: return
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            cameraCharacteristics = characteristics

            // Đọc thông số cảm biến
            characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let {
                exposureTimeRange = it
            }

            characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let {
                isoRange = it
            }

            characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?.let {
                minFocusDistance = it
            }

            characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let {
                evRange = it
            }

            characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.let {
                evStep = it
            }

            // Sensor Zoom capabilities
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                if (zoomRange != null) {
                    supportsZoomRatio = true
                    minZoomRatio = zoomRange.lower
                    maxZoomRatio = zoomRange.upper.coerceAtMost(10.0f)
                } else {
                    supportsZoomRatio = false
                    val maxDigital = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                    minZoomRatio = 1.0f
                    maxZoomRatio = maxDigital.coerceIn(1.0f, 10.0f)
                }
            } else {
                supportsZoomRatio = false
                val maxDigital = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                minZoomRatio = 1.0f
                maxZoomRatio = maxDigital.coerceIn(1.0f, 10.0f)
            }
            currentZoomRatio = 1.0f.coerceIn(minZoomRatio, maxZoomRatio)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onZoomChanged?.invoke(currentZoomRatio)
            }

            // Maximize clarity: Pick highest optimal preview resolution (1080p or closest)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(SurfaceTexture::class.java)
            val optimalSize = chooseOptimalPreviewSize(outputSizes)
            previewWidth = optimalSize.width
            previewHeight = optimalSize.height
            Log.i(TAG, "Configured SurfaceTexture defaultBufferSize: ${optimalSize.width}x${optimalSize.height}")
            surfaceTexture.setDefaultBufferSize(optimalSize.width, optimalSize.height)
            onPreviewSizeConfigured?.invoke(optimalSize.width, optimalSize.height)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e(TAG, "Camera open error: $error")
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
        }
    }

    private fun chooseOptimalPreviewSize(sizes: Array<android.util.Size>?): android.util.Size {
        if (sizes.isNullOrEmpty()) return android.util.Size(1920, 1080)

        // Tìm độ phân giải 16:9
        val targetAspect = 16.0 / 9.0
        val sixteenByNineSizes = sizes.filter { size ->
            val aspect = size.width.toDouble() / size.height.toDouble()
            Math.abs(aspect - targetAspect) < 0.05
        }

        // Ưu tiên 1080p sắc nét
        val perfect1080p = sixteenByNineSizes.firstOrNull { it.width == 1920 && it.height == 1080 }
        if (perfect1080p != null) return perfect1080p

        // Chọn size 16:9 có diện tích lớn nhất trong giới hạn FHD (để đảm bảo 30-60 fps mượt)
        val best16x9 = sixteenByNineSizes
            .filter { it.width <= 1920 && it.height <= 1080 }
            .maxByOrNull { it.width * it.height }
        if (best16x9 != null) return best16x9

        // Nếu không có 16:9 thì chọn size lớn nhất <= 1920x1080
        return sizes
            .filter { it.width <= 1920 && it.height <= 1080 }
            .maxByOrNull { it.width * it.height }
            ?: sizes.first()
    }

    private fun startCaptureSession() {
        val camera = cameraDevice ?: return
        val st = currentSurfaceTexture ?: return

        try {
            val surface = Surface(st)
            // Dùng TEMPLATE_PREVIEW để cân bằng sáng tự nhiên nhất
            previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                applySettings(this)
            }

            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        updatePreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting capture session", e)
        }
    }

    fun applySettings(builder: CaptureRequest.Builder) {
        if (isManualControl) {
            // Chế độ chỉnh tay: Tắt AE để cố định Shutter và ISO
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureTimeNs)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
        } else {
            // Chế độ Tự động (Auto Exposure): Camera tự cân bằng sáng chống chói
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            if (evRange.upper > evRange.lower) {
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evCompensation)
            }
        }

        // Cấu hình Lấy nét (Focus)
        if (isManualFocus) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, manualFocusDistance)
        } else if (isInfinityFocus) {
            // Khóa lấy nét ở vô cực (Dành riêng cho chụp sao trời ban đêm)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
        } else {
            // Lấy nét tự động liên tục (Continuous AF): Đảm bảo các vật thể trong phòng luôn cực nét
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        // Đèn pin trợ sáng (Flash / Torch)
        if (isTorchOn) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }

        // Bật chống rung quang học / số nếu thiết bị hỗ trợ
        val oisModes = cameraCharacteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        if (oisModes != null && oisModes.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
        }

        // Maximize image sharpness and detail
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)

        // Apply hardware optical or digital zoom
        applyZoom(builder)
    }

    private fun applyZoom(builder: CaptureRequest.Builder) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && supportsZoomRatio) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoomRatio)
        } else {
            val sensorRect = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (sensorRect != null && currentZoomRatio > 1.0f) {
                val cropWidth = (sensorRect.width() / currentZoomRatio).toInt()
                val cropHeight = (sensorRect.height() / currentZoomRatio).toInt()
                val cropLeft = sensorRect.left + (sensorRect.width() - cropWidth) / 2
                val cropTop = sensorRect.top + (sensorRect.height() - cropHeight) / 2
                val cropRect = Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)
                builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
            } else if (sensorRect != null) {
                builder.set(CaptureRequest.SCALER_CROP_REGION, sensorRect)
            }
        }
    }

    fun setZoomRatio(ratio: Float) {
        val clamped = ratio.coerceIn(minZoomRatio, maxZoomRatio)
        if (Math.abs(clamped - currentZoomRatio) > 0.005f) {
            currentZoomRatio = clamped
            onZoomChanged?.invoke(currentZoomRatio)
            updatePreview()
        }
    }

    fun updatePreview() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return

        try {
            applySettings(builder)
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update repeating request", e)
        }
    }

    fun setExposureTime(exposureNs: Long) {
        isManualControl = true
        currentExposureTimeNs = exposureNs.coerceIn(exposureTimeRange.lower, exposureTimeRange.upper)
        updatePreview()
    }

    fun setIso(iso: Int) {
        isManualControl = true
        currentIso = iso.coerceIn(isoRange.lower, isoRange.upper)
        updatePreview()
    }

    fun setAutoExposure() {
        isManualControl = false
        updatePreview()
    }

    fun toggleAutoExposure() {
        isManualControl = !isManualControl
        updatePreview()
    }

    fun setFocusDistance(distance: Float) {
        isManualFocus = true
        isInfinityFocus = false
        val maxDist = if (minFocusDistance > 0f) minFocusDistance else 10.0f
        manualFocusDistance = distance.coerceIn(0.0f, maxDist)
        updatePreview()
    }

    fun setAutoFocus() {
        isManualFocus = false
        isInfinityFocus = false
        updatePreview()
    }

    fun toggleInfinityFocus(enable: Boolean) {
        isInfinityFocus = enable
        if (enable) {
            isManualFocus = false
            manualFocusDistance = 0.0f
        }
        updatePreview()
    }

    fun setEvCompensation(ev: Int) {
        evCompensation = ev.coerceIn(evRange.lower, evRange.upper)
        updatePreview()
    }

    fun toggleTorch(enable: Boolean) {
        isTorchOn = enable
        updatePreview()
    }

    /**
     * Tap to focus and meter on specific normalized coordinates [0.0, 1.0]
     */
    fun tapToFocus(xNorm: Float, yNorm: Float) {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val sensorArray = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return

        try {
            // Adjust coordinates based on current zoom crop
            val cropWidth = (sensorArray.width() / currentZoomRatio).toInt()
            val cropHeight = (sensorArray.height() / currentZoomRatio).toInt()
            val cropLeft = (sensorArray.width() - cropWidth) / 2
            val cropTop = (sensorArray.height() - cropHeight) / 2

            val centerX = cropLeft + (xNorm * cropWidth).toInt()
            val centerY = cropTop + (yNorm * cropHeight).toInt()
            val halfArea = 150

            val left = (centerX - halfArea).coerceIn(0, sensorArray.width() - 1)
            val top = (centerY - halfArea).coerceIn(0, sensorArray.height() - 1)
            val right = (centerX + halfArea).coerceIn(0, sensorArray.width() - 1)
            val bottom = (centerY + halfArea).coerceIn(0, sensorArray.height() - 1)

            val focusRect = Rect(left, top, right, bottom)
            val meteringRectangle = MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX)

            isInfinityFocus = false
            isManualFocus = false

            // Set AF/AE regions
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRectangle))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRectangle))
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)

            // Maintain focus & metering on tapped region
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Tap to focus error", e)
        }
    }

    fun switchCamera() {
        if (isRecordingVideo) return
        val st = currentSurfaceTexture ?: return
        isFrontCamera = !isFrontCamera
        currentZoomRatio = 1.0f
        onZoomChanged?.invoke(1.0f)
        closeCamera()
        openCamera(st)
    }

    fun startRecordingVideo(
        outputFile: java.io.File,
        onStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        val camera = cameraDevice ?: run {
            onError("Camera not ready")
            return
        }
        val st = currentSurfaceTexture ?: run {
            onError("SurfaceTexture not ready")
            return
        }

        try {
            captureSession?.close()
            captureSession = null

            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                android.media.MediaRecorder()
            }
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            recorder.setVideoSource(android.media.MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.setVideoEncodingBitRate(12_000_000)
            recorder.setVideoFrameRate(30)
            recorder.setVideoSize(previewWidth, previewHeight)
            recorder.setVideoEncoder(android.media.MediaRecorder.VideoEncoder.H264)
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)

            val sensorOrientation = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            recorder.setOrientationHint(sensorOrientation)
            recorder.prepare()
            mediaRecorder = recorder
            currentVideoFile = outputFile

            val previewSurface = Surface(st)
            val recorderSurface = recorder.surface

            previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(previewSurface)
                addTarget(recorderSurface)
                applySettings(this)
            }

            camera.createCaptureSession(
                listOf(previewSurface, recorderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
                            recorder.start()
                            isRecordingVideo = true
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onStarted()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting recorder", e)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onError(e.message ?: "Error starting video recording")
                            }
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onError("Failed to configure video recording session")
                        }
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up video recording", e)
            onError(e.message ?: "Failed to initialize MediaRecorder")
        }
    }

    fun stopRecordingVideo(onFinished: (java.io.File) -> Unit) {
        if (!isRecordingVideo) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        }
        isRecordingVideo = false

        startCaptureSession()

        currentVideoFile?.let {
            onFinished(it)
        }
    }

    fun closeCamera() {
        try {
            if (isRecordingVideo) {
                try {
                    mediaRecorder?.stop()
                } catch (e: Exception) {}
            }
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecordingVideo = false

            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }

    private fun getCameraId(front: Boolean): String? {
        val targetFacing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == targetFacing) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull()
    }
}
