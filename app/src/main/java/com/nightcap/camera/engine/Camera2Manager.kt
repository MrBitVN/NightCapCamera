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

    // Dải thông số cảm biến của máy
    var exposureTimeRange: Range<Long> = Range(1_000_000L, 1_000_000_000L) // Nanoseconds
        private set
    var isoRange: Range<Int> = Range(100, 3200)
        private set
    var minFocusDistance: Float = 0.0f
        private set

    // Trạng thái vận hành
    // MẶC ĐỊNH LÀ FALSE (Auto Exposure & Auto Focus) để hình ảnh luôn nét và không bị chói lóa khi mở app
    var isManualControl: Boolean = false
    var isInfinityFocus: Boolean = false
    var currentExposureTimeNs: Long = 33_333_333L // Mặc định 1/30s (33ms)
    var currentIso: Int = 400

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
            val cameraId = getBackCameraId() ?: return
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
        }

        if (isInfinityFocus) {
            // Khóa lấy nét ở vô cực (Dành riêng cho chụp sao trời ban đêm)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
        } else {
            // Lấy nét tự động liên tục (Continuous AF): Đảm bảo các vật thể trong phòng luôn cực nét
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        // Bật chống rung quang học / số nếu thiết bị hỗ trợ
        val oisModes = cameraCharacteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        if (oisModes != null && oisModes.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
        }

        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
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

    fun toggleAutoExposure() {
        isManualControl = !isManualControl
        updatePreview()
    }

    fun toggleInfinityFocus(enable: Boolean) {
        isInfinityFocus = enable
        updatePreview()
    }

    /**
     * Chạm để lấy nét (Tap to Focus) vào toạ độ bất kỳ trên màn hình
     */
    fun tapToFocus(xNorm: Float, yNorm: Float) {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val sensorArray = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return

        try {
            // Chuyển toạ độ chuẩn hóa [0, 1] sang toạ độ cảm biến
            val halfArea = 150
            val centerX = (xNorm * sensorArray.width()).toInt()
            val centerY = (yNorm * sensorArray.height()).toInt()

            val left = (centerX - halfArea).coerceIn(0, sensorArray.width() - 1)
            val top = (centerY - halfArea).coerceIn(0, sensorArray.height() - 1)
            val right = (centerX + halfArea).coerceIn(0, sensorArray.width() - 1)
            val bottom = (centerY + halfArea).coerceIn(0, sensorArray.height() - 1)

            val focusRect = Rect(left, top, right, bottom)
            val meteringRectangle = MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX)

            isInfinityFocus = false

            // Đặt vùng lấy nét và đo sáng
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRectangle))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRectangle))
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)

            session.capture(builder.build(), null, backgroundHandler)

            // Tiếp tục preview với chế độ continuous sau khi lấy nét
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Tap to focus error", e)
        }
    }

    fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }

    private fun getBackCameraId(): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull()
    }
}
