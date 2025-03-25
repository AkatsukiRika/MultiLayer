package com.tgwgroup.multilayer.utils

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class TextureRenderer {
    companion object {
        private const val TAG = "TextureRenderer"
    }

    private var program: Int = -1
    private var positionHandle: Int = -1
    private var texCoordHandle: Int = -1
    private var mvpMatrixHandle: Int = -1
    private var textureHandle: Int = -1

    private var vertexBuffer: FloatBuffer
    private var texCoordBuffer: FloatBuffer

    private var textureId: Int = -1

    // 顶点坐标
    private val vertexData = floatArrayOf(
        -1.0f, -1.0f, 0.0f,  // 左下
        1.0f, -1.0f, 0.0f,   // 右下
        -1.0f, 1.0f, 0.0f,   // 左上
        1.0f, 1.0f, 0.0f     // 右上
    )

    // 纹理坐标
    private val texCoordData = floatArrayOf(
        0.0f, 1.0f,  // 左下
        1.0f, 1.0f,  // 右下
        0.0f, 0.0f,  // 左上
        1.0f, 0.0f   // 右上
    )

    init {
        // 初始化顶点缓冲区
        val bb = ByteBuffer.allocateDirect(vertexData.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(vertexData)
        vertexBuffer.position(0)

        // 初始化纹理坐标缓冲区
        val tb = ByteBuffer.allocateDirect(texCoordData.size * 4)
        tb.order(ByteOrder.nativeOrder())
        texCoordBuffer = tb.asFloatBuffer()
        texCoordBuffer.put(texCoordData)
        texCoordBuffer.position(0)

        createProgram()
    }

    private fun createProgram() {
        // 编译着色器
        val vertexShader = compileShader(TAG, GLES20.GL_VERTEX_SHADER, basicVertexShader)
        val fragmentShader = compileShader(TAG, GLES20.GL_FRAGMENT_SHADER, basicFragmentShader)

        // 创建程序
        program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "无法创建程序对象")
            return
        }

        // 链接着色器
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        // 检查链接状态
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "程序链接失败: $log")
            GLES20.glDeleteProgram(program)
            return
        }

        // 获取attribute和uniform变量位置
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")
        textureHandle = GLES20.glGetUniformLocation(program, "u_Texture")

        // 清理着色器
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
    }

    fun setTexture(textureId: Int) {
        this.textureId = textureId
    }

    fun draw() {
        if (textureId == -1) {
            return
        }

        GLES20.glUseProgram(program)

        // 设置顶点位置
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        // 设置纹理坐标
        texCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        // 设置MVP矩阵 (单位矩阵)
        val mvpMatrix = FloatArray(16)
        Matrix.setIdentityM(mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // 设置纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        // 启用混合模式以支持透明度
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // 清理
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    fun release() {
        if (program != -1) {
            GLES20.glDeleteProgram(program)
            program = -1
        }
    }
}