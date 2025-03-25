package com.tgwgroup.multilayer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLSurfaceView.Renderer
import com.tgwgroup.multilayer.layer.ILayer
import java.util.concurrent.CopyOnWriteArrayList
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MultiLayerRenderer(
    private val context: Context,
    private val surfaceView: GLSurfaceView
) : Renderer {
    private val layers = CopyOnWriteArrayList<ILayer>()
    private var width = 0
    private var height = 0

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
    }

    override fun onDrawFrame(p0: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 按Z轴顺序绘制图层
        val sortedLayers = layers.sortedBy { it.getZOrder() }
        for (layer in sortedLayers) {
            layer.draw()
        }
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
}