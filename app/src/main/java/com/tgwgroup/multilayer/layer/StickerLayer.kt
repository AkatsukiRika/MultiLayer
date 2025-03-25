package com.tgwgroup.multilayer.layer

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.tgwgroup.multilayer.utils.basicFragmentShader
import com.tgwgroup.multilayer.utils.basicVertexShader
import com.tgwgroup.multilayer.utils.compileShader
import com.tgwgroup.multilayer.utils.loadTexture
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class StickerLayer(private val context: Context) : ILayer {
    companion object {
        const val TAG = "StickerLayer"
    }

    private var textureId: Int = -1
    private var program: Int = -1
    private var positionHandle: Int = -1
    private var texCoordHandle: Int = -1
    private var mvpMatrixHandle: Int = -1
    private var textureHandle: Int = -1

    private var vertexBuffer: FloatBuffer
    private var texCoordBuffer: FloatBuffer
    private var zOrder: Int = 0
    private var bitmap: Bitmap? = null

    // 贴纸位置、缩放和旋转参数
    private var positionX: Float = 0f
    private var positionY: Float = 0f
    private var scale: Float = 1.0f
    private var rotation: Float = 0f

    // 视口尺寸
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0

    // 贴纸尺寸
    private var stickerWidth: Int = 0
    private var stickerHeight: Int = 0

    // MVP矩阵
    private val mvpMatrix = FloatArray(16)

    // 顶点坐标
    private val vertexData = floatArrayOf(
        -0.5f, -0.5f, 0.0f,  // 左下
        0.5f, -0.5f, 0.0f,   // 右下
        -0.5f, 0.5f, 0.0f,   // 左上
        0.5f, 0.5f, 0.0f     // 右上
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
    }

    fun setImage(bmp: Bitmap) {
        bitmap = bmp
        stickerWidth = bmp.width
        stickerHeight = bmp.height
        updateModelMatrix()
    }

    // 设置贴纸位置（以屏幕像素为单位）
    fun setPosition(x: Float, y: Float) {
        positionX = x
        positionY = y
        updateModelMatrix()
    }

    // 设置贴纸缩放比例
    fun setScale(scale: Float) {
        this.scale = scale
        updateModelMatrix()
    }

    // 设置贴纸旋转角度（以度为单位）
    fun setRotation(degrees: Float) {
        this.rotation = degrees
        updateModelMatrix()
    }

    override fun onSurfaceCreated() {
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
        if (positionHandle == -1) {
            Log.e(TAG, "无法获取属性a_Position")
            return
        }

        texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        if (texCoordHandle == -1) {
            Log.e(TAG, "无法获取属性a_TexCoord")
            return
        }

        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")
        if (mvpMatrixHandle == -1) {
            Log.e(TAG, "无法获取uniform变量u_MVPMatrix")
            return
        }

        textureHandle = GLES20.glGetUniformLocation(program, "u_Texture")
        if (textureHandle == -1) {
            Log.e(TAG, "无法获取uniform变量u_Texture")
            return
        }

        // 创建纹理
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        if (textureId == 0) {
            Log.e(TAG, "无法生成纹理ID")
            return
        }

        // 加载图片到纹理
        loadTexture(bitmap, textureId)

        // 清理着色器
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        updateModelMatrix()
    }

    override fun draw() {
        if (bitmap == null) {
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

        // 设置MVP矩阵
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // 设置纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        // 启用混合模式以支持透明度
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // 清理
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    override fun release() {
        if (textureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = -1
        }
        bitmap?.recycle()
        bitmap = null
    }

    override fun setZOrder(zOrder: Int) {
        this.zOrder = zOrder
    }

    override fun getZOrder(): Int {
        return zOrder
    }

    private fun updateModelMatrix() {
        if (viewportWidth == 0 || viewportHeight == 0) {
            return
        }

        // 重置为单位矩阵
        Matrix.setIdentityM(mvpMatrix, 0)

        // 将原点移到贴纸位置
        val normalizedX = 2.0f * positionX / viewportWidth - 1.0f
        val normalizedY = 1.0f - 2.0f * positionY / viewportHeight
        Matrix.translateM(mvpMatrix, 0, normalizedX, normalizedY, 0f)

        // 应用旋转
        Matrix.rotateM(mvpMatrix, 0, rotation, 0f, 0f, 1f)

        // 应用缩放
        val widthScale = scale * stickerWidth.toFloat() / viewportWidth
        val heightScale = scale * stickerHeight.toFloat() / viewportHeight
        Matrix.scaleM(mvpMatrix, 0, widthScale * 2, heightScale * 2, 1f)
    }
}