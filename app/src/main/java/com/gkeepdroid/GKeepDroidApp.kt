package com.gkeepdroid

import android.app.Application

class GKeepDroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Watch the Markdown folder for edits made by other apps while the process lives.
        if (MarkdownSync.isEnabled(this)) MarkdownSync.startObserving(this)
        ScreenSync.start(this)
        // Python takes a moment to start; do it in the background so the app opens quickly.
        Thread { PythonRuntime.get(this) }.start()
    }
}
