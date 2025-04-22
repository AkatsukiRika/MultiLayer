package com.tgwgroup.mujicanvas.utils

import android.util.Log

object MujicaLog {
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}