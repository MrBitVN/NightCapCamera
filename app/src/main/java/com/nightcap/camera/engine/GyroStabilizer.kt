package com.nightcap.camera.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import android.util.Log

class GyroStabilizer(context: Context) : SensorEventListener {
    companion object {
        private const val TAG = "GyroStabilizer"
        // Tiêu cự giả lập cho camera góc rộng thông thường (tỉ lệ chuẩn)
        private const val FOCAL_LENGTH_SCALE = 1.8f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    var isEnabled: Boolean = true

    // Ma trận góc chuẩn tại thời điểm bấm chụp frame đầu tiên
    private val referenceRotationMatrix = FloatArray(16)
    private val currentRotationMatrix = FloatArray(16)
    private val relativeRotationMatrix = FloatArray(16)

    // Ma trận ổn định 4x4 truyền vào OpenGL Shader
    val stabilizationMatrix = FloatArray(16)

    private var isCalibrated = false

    init {
        Matrix.setIdentityM(stabilizationMatrix, 0)
        Matrix.setIdentityM(referenceRotationMatrix, 0)
        Matrix.setIdentityM(currentRotationMatrix, 0)
    }

    fun start() {
        isCalibrated = false
        Matrix.setIdentityM(stabilizationMatrix, 0)

        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        } ?: gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        isCalibrated = false
        Matrix.setIdentityM(stabilizationMatrix, 0)
    }

    /**
     * Khóa góc tham chiếu tại thời điểm bắt đầu phiên chụp (Capture Start)
     */
    fun lockReferenceOrientation() {
        synchronized(this) {
            System.arraycopy(currentRotationMatrix, 0, referenceRotationMatrix, 0, 16)
            isCalibrated = true
            Matrix.setIdentityM(stabilizationMatrix, 0)
        }
    }

    /**
     * Reset góc tham chiếu
     */
    fun resetReference() {
        synchronized(this) {
            isCalibrated = false
            Matrix.setIdentityM(stabilizationMatrix, 0)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isEnabled) {
            Matrix.setIdentityM(stabilizationMatrix, 0)
            return
        }

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotMatrix = FloatArray(16)
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)

            synchronized(this) {
                System.arraycopy(rotMatrix, 0, currentRotationMatrix, 0, 16)

                if (!isCalibrated) {
                    System.arraycopy(currentRotationMatrix, 0, referenceRotationMatrix, 0, 16)
                    isCalibrated = true
                }

                // Tính toán ma trận lệch: R_rel = R_ref^T * R_current
                // (Nghịch đảo của ma trận trực giao là chuyển vị của nó)
                val refTranspose = FloatArray(16)
                Matrix.transposeM(refTranspose, 0, referenceRotationMatrix, 0)
                Matrix.multiplyMM(relativeRotationMatrix, 0, refTranspose, 0, currentRotationMatrix, 0)

                // Rút trích góc Euler (Yaw, Pitch, Roll)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(relativeRotationMatrix, orientation)
                val deltaYaw = orientation[0]   // Xoay quanh Z
                val deltaPitch = orientation[1] // Xoay quanh X
                val deltaRoll = orientation[2]  // Xoay quanh Y

                // Bù trừ rung tay bằng dịch chuyển texture offset (Homography approximation)
                val shiftX = -deltaRoll * FOCAL_LENGTH_SCALE
                val shiftY = deltaPitch * FOCAL_LENGTH_SCALE
                val angleDeg = Math.toDegrees(-deltaYaw.toDouble()).toFloat()

                // Tạo ma trận biến đổi OpenGL để dịch và xoay khung hình về đúng vị trí chuẩn
                Matrix.setIdentityM(stabilizationMatrix, 0)
                // Dịch chuyển tâm về (0.5, 0.5)
                Matrix.translateM(stabilizationMatrix, 0, 0.5f, 0.5f, 0.0f)
                // Xoay khử rung quanh trục Z
                Matrix.rotateM(stabilizationMatrix, 0, angleDeg, 0.0f, 0.0f, 1.0f)
                // Dịch chuyển bù rung tay X và Y
                Matrix.translateM(stabilizationMatrix, 0, shiftX, shiftY, 0.0f)
                // Đưa tâm trở lại (0, 0)
                Matrix.translateM(stabilizationMatrix, 0, -0.5f, -0.5f, 0.0f)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
