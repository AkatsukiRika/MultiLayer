package com.tgwgroup.multilayer.utils

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log

val basicVertexShader = """
    attribute vec4 a_Position;
    attribute vec2 a_TexCoord;
    varying vec2 v_TexCoord;
    uniform mat4 u_MVPMatrix;
    void main() {
        gl_Position = u_MVPMatrix * a_Position;
        v_TexCoord = a_TexCoord;
    }
""".trimIndent()

val basicFragmentShader = """
    precision mediump float;
    varying vec2 v_TexCoord;
    uniform sampler2D u_Texture;
    void main() {
        gl_FragColor = texture2D(u_Texture, v_TexCoord);
    }
""".trimIndent()

val grayFilterFragmentShader = """
    precision mediump float;
    varying vec2 v_TexCoord;
    uniform sampler2D u_Texture;
    uniform float u_Intensity; // 滤镜强度 (0.0-1.0)
    
    void main() {
        vec4 color = texture2D(u_Texture, v_TexCoord);
        float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        vec4 grayColor = vec4(vec3(gray), color.a);
        gl_FragColor = mix(color, grayColor, u_Intensity);
    }
""".trimIndent()

fun compileShader(tag: String, type: Int, shaderCode: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, shaderCode)
    GLES20.glCompileShader(shader)

    // 检查编译状态
    val compileStatus = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

    if (compileStatus[0] == 0) {
        // 如果编译失败，获取错误信息
        val log = GLES20.glGetShaderInfoLog(shader)
        val shaderTypeString = if (type == GLES20.GL_VERTEX_SHADER) "顶点着色器" else "片段着色器"
        Log.e(tag, "$shaderTypeString 编译失败: $log")

        // 删除失败的着色器
        GLES20.glDeleteShader(shader)
    }

    return shader
}

fun loadTexture(bitmap: Bitmap?, textureId: Int) {
    bitmap?.let {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // 设置纹理参数
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // 加载图片到纹理
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, it, 0)
    }
}