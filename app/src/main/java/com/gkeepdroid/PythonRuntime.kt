package com.gkeepdroid

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/** Starts the embedded Python once, on whichever thread needs it first. */
object PythonRuntime {
    @Synchronized
    fun get(context: Context): Python {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
        return Python.getInstance()
    }
}
