package com.tgwgroup.multilayer

import android.graphics.BitmapFactory
import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tgwgroup.mujicanvas.MujicaRenderer
import com.tgwgroup.mujicanvas.layer.FilterLayer
import com.tgwgroup.mujicanvas.layer.ImageLayer
import com.tgwgroup.multilayer.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var renderer: MujicaRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initView() {
        binding.surfaceView.setEGLContextClientVersion(2)
        renderer = MujicaRenderer(this, binding.surfaceView)
        binding.surfaceView.setRenderer(renderer)
        binding.surfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        lifecycleScope.launch(Dispatchers.IO) {
            val imageLayer = ImageLayer(this@MainActivity)
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.img_demo)
            imageLayer.setZOrder(0)
            imageLayer.setImage(bitmap)
            renderer?.addLayer(imageLayer)

//            // 添加贴纸图层
//            val stickerLayer = StickerLayer(this@MainActivity)
//            val stickerBmp = BitmapFactory.decodeResource(resources, R.drawable.img_sticker_demo)
//            stickerLayer.setZOrder(2)
//            stickerLayer.setImage(stickerBmp)
//
//            // 设置贴纸位置
//            val width = binding.surfaceView.width
//            val height = binding.surfaceView.height
//            stickerLayer.setPosition(width / 2f, height / 2f)
//
//            // 设置贴纸缩放
//            stickerLayer.setScale(1f)
//
//            // 设置贴纸旋转
//            stickerLayer.setRotation(90f)
//
//            renderer?.addLayer(stickerLayer)

            // 添加滤镜图层
            val filterLayer = FilterLayer(this@MainActivity)
            filterLayer.setIntensity(1f)
            filterLayer.setZOrder(1)
            renderer?.addLayer(filterLayer)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer?.clear()
    }
}