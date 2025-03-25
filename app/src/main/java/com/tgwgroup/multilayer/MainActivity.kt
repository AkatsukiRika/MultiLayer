package com.tgwgroup.multilayer

import android.graphics.BitmapFactory
import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tgwgroup.multilayer.databinding.ActivityMainBinding
import com.tgwgroup.multilayer.layer.ImageLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var renderer: MultiLayerRenderer? = null

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
        renderer = MultiLayerRenderer(this, binding.surfaceView)
        binding.surfaceView.setRenderer(renderer)
        binding.surfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        lifecycleScope.launch(Dispatchers.IO) {
            val imageLayer = ImageLayer(this@MainActivity)
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.img_demo)
            imageLayer.setZOrder(0)
            imageLayer.setImage(bitmap)
            renderer?.addLayer(imageLayer)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer?.clear()
    }
}