package com.tgwgroup.mujicanvas.layer

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.tgwgroup.mujicanvas.utils.basicVertexShader
import com.tgwgroup.mujicanvas.utils.compileShader
import com.tgwgroup.mujicanvas.utils.grayFilterFragmentShader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class FilterLayer(private val context: Context) : ILayer {
    companion object {
        const val TAG = "FilterLayer"
    }

    private var program: Int = -1
    private var positionHandle: Int = -1
    private var texCoordHandle: Int = -1
    private var mvpMatrixHandle: Int = -1
    private var textureHandle: Int = -1
    private var intensityHandle: Int = -1

    private var vertexBuffer: FloatBuffer
    private var texCoordBuffer: FloatBuffer
    private var zOrder: Int = 0

    // 滤镜强度 (0.0-1.0)
    private var intensity: Float = 1.0f

    // 自定义着色器代码
    private var vertexShaderCode: String = basicVertexShader
    private var fragmentShaderCode: String = grayFilterFragmentShader

    // 视口尺寸
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0

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

    // FBO 相关变量
    private var fboId: Int = -1
    private var fboTextureId: Int = -1
    private var inputTextureId: Int = -1  // 从前面图层渲染结果获取的纹理ID

    private var filterEnabled: Boolean = true

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

    // 设置滤镜强度 (0.0-1.0)
    fun setIntensity(intensity: Float) {
        this.intensity = intensity.coerceIn(0.0f, 1.0f)
    }

    // 设置自定义着色器代码
    fun setShaders(vertexShader: String? = null, fragmentShader: String? = null) {
        if (vertexShader != null) {
            this.vertexShaderCode = vertexShader
        }

        if (fragmentShader != null) {
            this.fragmentShaderCode = fragmentShader
        }

        // 如果程序已创建，则重新创建
        if (program != -1) {
            GLES20.glDeleteProgram(program)
            program = -1
            createShaderProgram()
        }
    }

    // 设置输入纹理
    fun setInputTexture(textureId: Int) {
        inputTextureId = textureId
    }

    // 获取自己的FBO ID
    fun getFboId(): Int {
        return fboId
    }

    // 获取FBO纹理ID，用于后续渲染
    fun getOutputTextureId(): Int {
        return fboTextureId
    }

    fun setFilterEnabled(enabled: Boolean) {
        filterEnabled = enabled
    }

    override fun onSurfaceCreated() {
        createShaderProgram()
    }

    private fun createShaderProgram() {
        // 编译着色器
        val vertexShader = compileShader(TAG, GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = compileShader(TAG, GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

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

        intensityHandle = GLES20.glGetUniformLocation(program, "u_Intensity")
        if (intensityHandle == -1) {
            Log.e(TAG, "无法获取uniform变量u_Intensity")
            return
        }

        // 清理着色器
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height

        // 创建FBO和纹理
        createFBO(width, height)
    }

    private fun createFBO(width: Int, height: Int) {
        // 删除旧的FBO和纹理（如果存在）
        if (fboId != -1) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            fboId = -1
        }
        if (fboTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
            fboTextureId = -1
        }

        // 创建FBO纹理
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        fboTextureId = textures[0]

        // 绑定并设置纹理参数
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // 分配纹理内存
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )

        // 创建FBO
        val fboArray = IntArray(1)
        GLES20.glGenFramebuffers(1, fboArray, 0)
        fboId = fboArray[0]

        // 绑定FBO并附加纹理
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            fboTextureId,
            0
        )

        // 检查FBO完整性
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO创建失败, 状态: $status")
        }

        // 恢复默认帧缓冲
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    override fun draw() {
        // 如果没有输入纹理，则不进行渲染
        if (inputTextureId == -1) {
            return
        }

        if (filterEnabled) {
            // 渲染到FBO（应用滤镜）
            renderToFBO()
        }

        // 渲染FBO纹理到屏幕
        renderToScreen()
    }

    private fun renderToFBO() {
        // 绑定FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)

        // 设置视口
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)

        // 清除缓冲区
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

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

        // 设置输入纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureId)
        GLES20.glUniform1i(textureHandle, 0)

        // 设置滤镜强度
        GLES20.glUniform1f(intensityHandle, intensity)

        // 启用混合模式以支持透明度
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // 清理
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        // 解绑FBO，返回到默认帧缓冲
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun renderToScreen() {
        // 设置视口
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)

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

        // 设置FBO纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
        GLES20.glUniform1i(textureHandle, 0)

        // 设置滤镜强度为0，因为已经在FBO中应用了滤镜效果
        GLES20.glUniform1f(intensityHandle, 0.0f)

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
        if (fboTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
            fboTextureId = -1
        }
        if (fboId != -1) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            fboId = -1
        }
        if (program != -1) {
            GLES20.glDeleteProgram(program)
            program = -1
        }
    }

    override fun setZOrder(zOrder: Int) {
        this.zOrder = zOrder
    }

    override fun getZOrder(): Int {
        return zOrder
    }
}