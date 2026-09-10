package com.nightcap.camera.gl

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object GLShaderUtils {
    private const val TAG = "GLShaderUtils"

    // Fullscreen quad vertex coordinates (2 triangles)
    private val QUAD_COORDS = floatArrayOf(
        -1.0f, -1.0f, 0.0f,
         1.0f, -1.0f, 0.0f,
        -1.0f,  1.0f, 0.0f,
         1.0f,  1.0f, 0.0f
    )

    // Texture coordinates [0, 1]
    private val TEX_COORDS = floatArrayOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    )

    fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(coords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(coords)
                position(0)
            }
        }
    }

    fun getQuadBuffer(): FloatBuffer = createFloatBuffer(QUAD_COORDS)
    fun getTexCoordBuffer(): FloatBuffer = createFloatBuffer(TEX_COORDS)

    fun loadShaderFromAsset(context: Context, shaderType: Int, assetPath: String): Int {
        val code = readAssetFile(context, assetPath)
        return compileShader(shaderType, code)
    }

    fun compileShader(shaderType: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(shaderType)
        if (shader == 0) {
            Log.e(TAG, "Could not create shader of type: $shaderType")
            return 0
        }

        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compilation error: $error\nSource:\n$shaderCode")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun createProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES30.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Could not create GL program")
            return 0
        }

        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES30.GL_TRUE) {
            val error = GLES30.glGetProgramInfoLog(program)
            Log.e(TAG, "Program link error: $error")
            GLES30.glDeleteProgram(program)
            return 0
        }
        return program
    }

    fun createTexture2D(width: Int, height: Int): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        val textureId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return textureId
    }

    fun checkGlError(op: String) {
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "GL Error after $op: 0x${Integer.toHexString(error)}")
        }
    }

    private fun readAssetFile(context: Context, path: String): String {
        return context.assets.open(path).use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        }
    }
}
