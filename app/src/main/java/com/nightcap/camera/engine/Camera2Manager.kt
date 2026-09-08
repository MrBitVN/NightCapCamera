package com.nightcap.camera.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
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

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var currentSurfaceTexture: SurfaceTexture? = null

    // Thông số cảm biến của thiết bị
    var exposureTimeRange: Range<Long> = Range(1_000_000L, 1_000_000_000L) // Nanoseconds
        private set
    var isoRange: Range<Int> = Range(100, 3200)
        private set
    var minFocusDistance: Float = 0.0f
        private set

    // Giá trị hiện tại
    var isManualControl: Boolean = true
    var isInfinityFocus: Boolean = true
    var currentExposureTimeNs: Long = 250_000_000L // Mặc định 1/4s (250ms)
    var currentIso: Int = 1600

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

            // Đọc dải thông số cảm biến
            characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let {
                exposureTimeRange = it
                // Đặt mặc định gần cận trên để thu sáng tối đa cho chụp đêm
                currentExposureTimeNs = Math.min(it.upper, 500_000_000L) // Tối đa 0.5s mỗi frame
            }

            characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let {
                isoRange = it
                currentIso = Math.min(it.upper, 3200)
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
            previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL).apply {
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
            // Tắt tự động đo sáng (Auto Exposure) để toàn quyền chỉnh tay Shutter và ISO
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureTimeNs)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        if (isInfinityFocus) {
            // Khóa lấy nét ở vô cực (0.0 diopters = tiêu cự vô tận - chuẩn cho chụp sao)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        // Tắt giảm nhiễu mặc định của camera để engine GPU tự xử lý stacking sạch hơn
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
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
        currentExposureTimeNs = exposureNs.coerceIn(exposureTimeRange.lower, exposureTimeRange.upper)
        updatePreview()
    }

    fun setIso(iso: Int) {
        currentIso = iso.coerceIn(isoRange.lower, isoRange.upper)
        updatePreview()
    }

    fun toggleInfinityFocus(enable: Boolean) {
        isInfinityFocus = enable
        updatePreview()
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
