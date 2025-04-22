package com.tgwgroup.mujicanvas.utils

import android.util.Log

object MujicaLog {
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}