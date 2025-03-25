package com.tgwgroup.multilayer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLSurfaceView.Renderer
import android.util.Log
import com.tgwgroup.multilayer.layer.FilterLayer
import com.tgwgroup.multilayer.layer.ILayer
import com.tgwgroup.multilayer.utils.TextureRenderer
import java.util.concurrent.CopyOnWriteArrayList
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MultiLayerRenderer(
    private val context: Context,
    private val surfaceView: GLSurfaceView
) : Renderer {
    companion object {
        const val TAG = "MultiLayerRenderer"
    }

    private val layers = CopyOnWriteArrayList<ILayer>()
    private var width = 0
    private var height = 0
    private var sharedFboId: Int = -1
    private var sharedFboTextureId: Int = -1

    private var textureRenderer: TextureRenderer? = null

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        for (layer in layers) {
            layer.onSurfaceCreated()
        }
    }

    override fun onSurfaceChanged(p0: GL10?, p1: Int, p2: Int) {
        GLES20.glViewport(0, 0, p1, p2)
        width = p1
        height = p2

        for (layer in layers) {
            layer.onSurfaceChanged(width, height)
        }

        createSharedFBO(width, height)
    }

    override fun onDrawFrame(p0: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 启用混合模式以支持透明度
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 按Z轴顺序绘制图层
        val sortedLayers = layers.sortedBy { it.getZOrder() }

        // 如果没有图层，直接清屏返回
        if (sortedLayers.isEmpty()) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }

        // 渲染图层
        renderLayersWithFBO(sortedLayers)
    }

    fun addLayer(layer: ILayer) {
        layers.add(layer)
        // 如果渲染器已经初始化，则初始化新添加的图层
        if (width > 0 && height > 0) {
            surfaceView.queueEvent {
                layer.onSurfaceCreated()
                layer.onSurfaceChanged(width, height)
                surfaceView.requestRender()
            }
        }
    }

    fun removeLayer(layer: ILayer) {
        layer.release()
        layers.remove(layer)
    }

    fun moveLayerUp(layer: ILayer) {
        val index = layers.indexOf(layer)
        if (index < layers.size - 1) {
            val upperLayer = layers[index + 1]
            val upperZOrder = upperLayer.getZOrder()
            val currentZOrder = layer.getZOrder()
            layer.setZOrder(upperZOrder)
            upperLayer.setZOrder(currentZOrder)
        }
    }

    fun moveLayerDown(layer: ILayer) {
        val index = layers.indexOf(layer)
        if (index > 0) {
            val lowerLayer = layers[index - 1]
            val lowerZOrder = lowerLayer.getZOrder()
            val currentZOrder = layer.getZOrder()
            layer.setZOrder(lowerZOrder)
            lowerLayer.setZOrder(currentZOrder)
        }
    }

    fun clear() {
        for (layer in layers) {
            layer.release()
        }
        layers.clear()
    }

    /**
     * 删除旧的FBO和纹理
     */
    private fun releaseFBO() {
        if (sharedFboId != -1) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(sharedFboId), 0)
            sharedFboId = -1
        }
        if (sharedFboTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(sharedFboTextureId), 0)
            sharedFboTextureId = -1
        }
    }

    private fun createSharedFBO(width: Int, height: Int) {
        releaseFBO()

        // 创建FBO纹理
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        sharedFboTextureId = textures[0]

        // 绑定并设置纹理参数
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sharedFboTextureId)
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
        sharedFboId = fboArray[0]

        // 绑定FBO并附加纹理
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sharedFboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            sharedFboTextureId,
            0
        )

        // 检查FBO完整性
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO创建失败, 状态: $status")
            return
        }

        // 恢复默认帧缓冲
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun renderLayersWithFBO(sortedLayers: List<ILayer>) {
        // 分析图层，找出所有FilterLayer的索引位置
        val filterLayerIndices = sortedLayers.indices.filter { sortedLayers[it] is FilterLayer }

        // 如果没有滤镜图层，直接按顺序渲染
        if (filterLayerIndices.isEmpty()) {
            // 绑定默认帧缓冲
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)

            // 清除颜色缓冲区
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // 依次渲染每个图层
            for (layer in sortedLayers) {
                layer.draw()
            }
            return
        }

        // 第一个滤镜之前的图层先渲染到FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sharedFboId)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val firstFilterIndex = filterLayerIndices.first()

        // 渲染第一个滤镜前的所有普通图层
        for (i in 0 until firstFilterIndex) {
            sortedLayers[i].draw()
        }

        // 临时保存FBO纹理ID，用于传递给滤镜
        var currentInputTextureId = sharedFboTextureId

        // 处理每个滤镜图层
        for (i in filterLayerIndices) {
            val filterLayer = sortedLayers[i] as FilterLayer

            // 设置输入纹理（上一步渲染的结果）
            filterLayer.setInputTexture(currentInputTextureId)

            // 如果是最后一个滤镜，则渲染到屏幕
            val isLastFilter = i == filterLayerIndices.last()

            if (isLastFilter) {
                // 最后一个滤镜，直接渲染到默认帧缓冲
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glViewport(0, 0, width, height)
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            } else {
                // 非最后滤镜，渲染到FBO
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, filterLayer.getFboId())
                GLES20.glViewport(0, 0, width, height)
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }

            // 渲染当前滤镜
            filterLayer.draw()

            // 更新输入纹理为当前滤镜的输出
            if (!isLastFilter) {
                currentInputTextureId = filterLayer.getOutputTextureId()
            }

            // 如果两个滤镜之间还有普通图层
            if (!isLastFilter) {
                val nextFilterIndex = filterLayerIndices.find { it > i } ?: sortedLayers.size

                // 如果有普通图层，需要将它们渲染到当前输出上
                if (i + 1 < nextFilterIndex) {
                    // 渲染到临时FBO
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sharedFboId)

                    // 先绘制当前滤镜的输出结果
                    drawTextureToFBO(currentInputTextureId)

                    // 再叠加绘制普通图层
                    for (j in i + 1 until nextFilterIndex) {
                        sortedLayers[j].draw()
                    }

                    // 更新当前输入纹理
                    currentInputTextureId = sharedFboTextureId
                }
            }
        }

        // 最后一个滤镜之后的普通图层
        val lastFilterIndex = filterLayerIndices.last()
        if (lastFilterIndex < sortedLayers.size - 1) {
            // 已经绑定到默认帧缓冲，直接渲染剩余图层
            for (i in lastFilterIndex + 1 until sortedLayers.size) {
                sortedLayers[i].draw()
            }
        }
    }

    /**
     * 将纹理绘制到当前绑定的FBO
     */
    private fun drawTextureToFBO(textureId: Int) {
        if (textureRenderer == null) {
            textureRenderer = TextureRenderer()
        }
        textureRenderer?.setTexture(textureId)
        textureRenderer?.draw()
    }
}