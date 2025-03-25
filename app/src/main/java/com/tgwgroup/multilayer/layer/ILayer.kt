package com.tgwgroup.multilayer.layer

interface ILayer {
    fun onSurfaceCreated()
    fun onSurfaceChanged(width: Int, height: Int)
    fun draw()
    fun release()
    fun setZOrder(zOrder: Int)
    fun getZOrder(): Int
}