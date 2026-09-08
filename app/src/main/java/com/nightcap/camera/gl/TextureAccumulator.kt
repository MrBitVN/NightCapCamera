package com.nightcap.camera.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TextureAccumulator(val width: Int, val height: Int) {
    companion object {
        private const val TAG = "TextureAccumulator"
    }

    private val fbos = IntArray(4) // 0: Ping, 1: Pong, 2: Current Frame, 3: Prev Frame
    private val textures = IntArray(4)

    private var pingPongIndex = 0
    var frameCount = 0
        private set

    init {
        GLES30.glGenFramebuffers(4, fbos, 0)
        GLES30.glGenTextures(4, textures, 0)

        for (i in 0 until 4) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[i])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

            // Dùng GL_RGBA16F (16-bit float) để chống bão hòa màu và mất dải nhạy sáng động khi tích lũy hàng trăm frame
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, width, height, 0,
                GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null
            )

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbos[i])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, textures[i], 0
            )

            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "Framebuffer $i incomplete, status: 0x${Integer.toHexString(status)}")
            }
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        reset()
    }

    fun reset() {
        frameCount = 0
        pingPongIndex = 0

        // Xóa FBO về đen
        for (i in 0 until 4) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbos[i])
            GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    val currentFrameFbo: Int
        get() = fbos[2]

    val currentFrameTexture: Int
        get() = textures[2]

    val prevFrameFbo: Int
        get() = fbos[3]

    val prevFrameTexture: Int
        get() = textures[3]

    /**
     * FBO nguồn chứa kết quả tích lũy trước đó
     */
    val sourceTexture: Int
        get() = textures[pingPongIndex]

    /**
     * FBO đích sẽ được vẽ kết quả tích lũy mới vào
     */
    val targetFbo: Int
        get() = fbos[1 - pingPongIndex]

    /**
     * Đổi FBO ping-pong sau mỗi frame đã xử lý xong
     */
    fun swapPingPong() {
        pingPongIndex = 1 - pingPongIndex
        frameCount++
    }

    /**
     * Lưu kết quả khung hình hiện tại sang prevTexture để so sánh frame differencing (Meteor mode)
     */
    fun copyCurrentToPrev() {
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, fbos[2])
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, fbos[3])
        GLES30.glBlitFramebuffer(
            0, 0, width, height,
            0, 0, width, height,
            GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST
        )
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /**
     * Đọc pixel từ FBO hiện tại ra Bitmap chất lượng cao
     */
    fun readPixelsToBitmap(): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        val sourceFbo = fbos[pingPongIndex]

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sourceFbo)
        GLES30.glReadPixels(
            0, 0, width, height,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer
        )
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        buffer.rewind()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // Lật ảnh theo trục Y vì toạ độ OpenGL đi từ dưới lên
        val matrix = android.graphics.Matrix().apply {
            preScale(1.0f, -1.0f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    fun release() {
        GLES30.glDeleteFramebuffers(4, fbos, 0)
        GLES30.glDeleteTextures(4, textures, 0)
    }
}
