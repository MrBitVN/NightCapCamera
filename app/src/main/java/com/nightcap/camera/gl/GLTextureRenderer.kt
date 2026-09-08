package com.nightcap.camera.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.nightcap.camera.engine.GyroStabilizer
import com.nightcap.camera.engine.StackingMode
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLTextureRenderer(
    private val context: Context,
    private val gyroStabilizer: GyroStabilizer,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
    private val onFrameProcessed: (frameCount: Int) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "GLTextureRenderer"
    }

    private var surfaceWidth = 1080
    private var surfaceHeight = 1920

    private var cameraTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var updateSurface = false

    private val cameraTransformMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16)
    private val finalStabilizedMatrix = FloatArray(16)

    private var accumulator: TextureAccumulator? = null

    // GL Programs
    private var programCameraInput = 0
    private var programStackMax = 0
    private var programStackAverage = 0
    private var programStackMeteor = 0
    private var programDisplay = 0

    // Mesh Buffers
    private lateinit var quadBuffer: FloatBuffer
    private lateinit var texBuffer: FloatBuffer

    // Trạng thái vận hành
    var currentMode: StackingMode = StackingMode.STARS
    var isStackingActive: Boolean = false
    var isoBoost: Float = 1.0f
    var noiseThreshold: Float = 0.0f // Mặc định không cắt sàn nhiễu để giữ nguyên chi tiết bóng tối
    var redVisionMode: Boolean = false
    var brightness: Float = 0.0f
    var contrast: Float = 1.0f

    // Callback chụp ảnh thành phẩm
    @Volatile
    private var pendingCaptureCallback: ((Bitmap) -> Unit)? = null

    init {
        Matrix.setIdentityM(identityMatrix, 0)
        Matrix.setIdentityM(cameraTransformMatrix, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        quadBuffer = GLShaderUtils.getQuadBuffer()
        texBuffer = GLShaderUtils.getTexCoordBuffer()

        // Tạo OES Texture cho Camera2
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(cameraTextureId)
        st.setOnFrameAvailableListener(this)
        surfaceTexture = st
        onSurfaceTextureReady(st)

        // Compile các Shader chương trình
        val vertShader = GLShaderUtils.loadShaderFromAsset(context, GLES30.GL_VERTEX_SHADER, "shaders/passthrough.vert")
        val fragCameraInput = GLShaderUtils.loadShaderFromAsset(context, GLES30.GL_FRAGMENT_SHADER, "shaders/camera_input.frag")
        val fragStackMax = GLShaderUtils.loadShaderFromAsset(context, GLES30.GL_FRAGMENT_SHADER, "shaders/stack_max.frag")
        val fragStackAverage = GLShaderUtils.loadShaderFromAsset(context, GLES30.GL_FRAGMENT_SHADER, "shaders/stack_average.frag")
        val fragStackMeteor = GLShaderUtils.loadShaderFromAsset(context, GLES30.GL_FRAGMENT_SHADER, "shaders/stack_meteor.frag")
        val fragDisplay = GLShaderUtils.loadShaderFromAsset(context, GLES30.GL_FRAGMENT_SHADER, "shaders/display.frag")

        programCameraInput = GLShaderUtils.createProgram(vertShader, fragCameraInput)
        programStackMax = GLShaderUtils.createProgram(vertShader, fragStackMax)
        programStackAverage = GLShaderUtils.createProgram(vertShader, fragStackAverage)
        programStackMeteor = GLShaderUtils.createProgram(vertShader, fragStackMeteor)
        programDisplay = GLShaderUtils.createProgram(vertShader, fragDisplay)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES30.glViewport(0, 0, width, height)

        accumulator?.release()
        accumulator = TextureAccumulator(width, height)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) {
            updateSurface = true
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val accum = accumulator ?: return

        synchronized(this) {
            if (updateSurface) {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(cameraTransformMatrix)
                updateSurface = false
            }
        }

        // -------------------------------------------------------------
        // Bước 1: Render OES Camera Texture vào FBO Current Frame
        // -------------------------------------------------------------
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, accum.currentFrameFbo)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(programCameraInput)

        // Kết hợp ma trận Camera Transform và Ma trận ổn định Gyro
        if (isStackingActive && gyroStabilizer.isEnabled) {
            Matrix.multiplyMM(finalStabilizedMatrix, 0, cameraTransformMatrix, 0, gyroStabilizer.stabilizationMatrix, 0)
        } else {
            System.arraycopy(cameraTransformMatrix, 0, finalStabilizedMatrix, 0, 16)
        }

        setMatrixUniform(programCameraInput, "uTransformMatrix", finalStabilizedMatrix)
        setFloatUniform(programCameraInput, "uIsoBoost", isoBoost)
        setFloatUniform(programCameraInput, "uNoiseThreshold", noiseThreshold)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        setIntUniform(programCameraInput, "uCameraTexture", 0)

        renderQuad()

        // -------------------------------------------------------------
        // Bước 2: Thực hiện Xếp chồng khung hình (Image Stacking) nếu đang bật
        // -------------------------------------------------------------
        if (isStackingActive) {
            val frameIndex = (accum.frameCount + 1).toFloat()

            when (currentMode) {
                StackingMode.PHOTO -> {
                    // Chế độ Photo: copy frame hiện tại vào accumulator
                    copyTextureToFbo(accum.currentFrameTexture, accum.targetFbo)
                    accum.swapPingPong()
                }

                StackingMode.STAR_TRAILS, StackingMode.LIGHT_TRAILS -> {
                    // Maximum Blending (Lighten)
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, accum.targetFbo)
                    GLES30.glUseProgram(programStackMax)
                    setMatrixUniform(programStackMax, "uTransformMatrix", identityMatrix)

                    val decay = if (currentMode == StackingMode.LIGHT_TRAILS) 0.998f else 1.0f
                    setFloatUniform(programStackMax, "uDecayFactor", decay)

                    bindTexture2D(GLES30.GL_TEXTURE0, accum.sourceTexture, programStackMax, "uAccumTexture", 0)
                    bindTexture2D(GLES30.GL_TEXTURE1, accum.currentFrameTexture, programStackMax, "uCurrentTexture", 1)

                    renderQuad()
                    accum.swapPingPong()
                }

                StackingMode.STARS -> {
                    // Cumulative Running Mean
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, accum.targetFbo)
                    GLES30.glUseProgram(programStackAverage)
                    setMatrixUniform(programStackAverage, "uTransformMatrix", identityMatrix)
                    setFloatUniform(programStackAverage, "uFrameIndex", frameIndex)
                    setIntUniform(programStackAverage, "uIsExponential", 0)
                    setFloatUniform(programStackAverage, "uWeight", 0.05f)

                    bindTexture2D(GLES30.GL_TEXTURE0, accum.sourceTexture, programStackAverage, "uAccumTexture", 0)
                    bindTexture2D(GLES30.GL_TEXTURE1, accum.currentFrameTexture, programStackAverage, "uCurrentTexture", 1)

                    renderQuad()
                    accum.swapPingPong()
                }

                StackingMode.METEORS -> {
                    // Differential detection
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, accum.targetFbo)
                    GLES30.glUseProgram(programStackMeteor)
                    setMatrixUniform(programStackMeteor, "uTransformMatrix", identityMatrix)
                    setFloatUniform(programStackMeteor, "uSensitivity", 0.12f)

                    bindTexture2D(GLES30.GL_TEXTURE0, accum.sourceTexture, programStackMeteor, "uAccumTexture", 0)
                    bindTexture2D(GLES30.GL_TEXTURE1, accum.currentFrameTexture, programStackMeteor, "uCurrentTexture", 1)
                    bindTexture2D(GLES30.GL_TEXTURE2, accum.prevFrameTexture, programStackMeteor, "uPrevTexture", 2)

                    renderQuad()
                    accum.copyCurrentToPrev()
                    accum.swapPingPong()
                }
            }

            onFrameProcessed(accum.frameCount)
        }

        // -------------------------------------------------------------
        // Bước 3: Hiển thị lên màn hình (Default Framebuffer 0)
        // -------------------------------------------------------------
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(programDisplay)
        setMatrixUniform(programDisplay, "uTransformMatrix", identityMatrix)
        setIntUniform(programDisplay, "uRedVisionMode", if (redVisionMode) 1 else 0)
        setFloatUniform(programDisplay, "uBrightness", brightness)
        setFloatUniform(programDisplay, "uContrast", contrast)

        val displayTexture = if (isStackingActive && accum.frameCount > 0) {
            accum.sourceTexture
        } else {
            accum.currentFrameTexture
        }

        bindTexture2D(GLES30.GL_TEXTURE0, displayTexture, programDisplay, "uScreenTexture", 0)
        renderQuad()

        // -------------------------------------------------------------
        // Bước 4: Kiểm tra và xuất ảnh thành phẩm (Snapshot)
        // -------------------------------------------------------------
        pendingCaptureCallback?.let { callback ->
            val captureTexture = if (accum.frameCount > 0) {
                accum.sourceTexture
            } else {
                accum.currentFrameTexture
            }

            val bitmap = accum.exportTextureToBitmap(
                textureId = captureTexture,
                programDisplay = programDisplay,
                identityMatrix = identityMatrix,
                quadBuffer = quadBuffer,
                texBuffer = texBuffer,
                brightness = brightness,
                contrast = contrast
            )
            callback(bitmap)
            pendingCaptureCallback = null
        }
    }

    fun startStacking() {
        accumulator?.reset()
        gyroStabilizer.lockReferenceOrientation()
        isStackingActive = true
    }

    fun stopStacking(callback: (Bitmap) -> Unit) {
        isStackingActive = false
        gyroStabilizer.resetReference()
        pendingCaptureCallback = callback
    }

    fun captureSinglePhoto(callback: (Bitmap) -> Unit) {
        pendingCaptureCallback = callback
    }

    fun resetStacking() {
        isStackingActive = false
        accumulator?.reset()
        gyroStabilizer.resetReference()
    }

    private fun renderQuad() {
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, quadBuffer)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, texBuffer)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun copyTextureToFbo(textureId: Int, targetFbo: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFbo)
        GLES30.glUseProgram(programDisplay)
        setMatrixUniform(programDisplay, "uTransformMatrix", identityMatrix)
        setIntUniform(programDisplay, "uRedVisionMode", 0)
        setFloatUniform(programDisplay, "uBrightness", 0.0f)
        setFloatUniform(programDisplay, "uContrast", 1.0f)
        bindTexture2D(GLES30.GL_TEXTURE0, textureId, programDisplay, "uScreenTexture", 0)
        renderQuad()
    }

    private fun bindTexture2D(textureUnit: Int, textureId: Int, program: Int, uniformName: String, unitIndex: Int) {
        GLES30.glActiveTexture(textureUnit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        setIntUniform(program, uniformName, unitIndex)
    }

    private fun setMatrixUniform(program: Int, name: String, matrix: FloatArray) {
        val location = GLES30.glGetUniformLocation(program, name)
        if (location >= 0) {
            GLES30.glUniformMatrix4fv(location, 1, false, matrix, 0)
        }
    }

    private fun setFloatUniform(program: Int, name: String, value: Float) {
        val location = GLES30.glGetUniformLocation(program, name)
        if (location >= 0) {
            GLES30.glUniform1f(location, value)
        }
    }

    private fun setIntUniform(program: Int, name: String, value: Int) {
        val location = GLES30.glGetUniformLocation(program, name)
        if (location >= 0) {
            GLES30.glUniform1i(location, value)
        }
    }
}
