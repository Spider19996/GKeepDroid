package com.gkeepdroid

import android.app.Application

class GKeepDroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocaleHelper.applyToApp(this)
        NotificationChannels.create(this)
        // A crash goes into the app log (class names and code positions only) before the system handles it.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.crash(this, throwable)
            previous?.uncaughtException(thread, throwable)
        }
        AppLog.i(this, "app", "started")
        Thread { AppLog.logExitReasons(this) }.start()
        // Switching the screen off (locking the phone) locks the app at once.
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context, intent: android.content.Intent) = AppLock.lockNow()
            },
            android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Watch the Markdown folder for edits made by other apps while the process lives.
        if (MarkdownSync.isEnabled(this)) MarkdownSync.startObserving(this)
        ScreenSync.start(this)
        // Changes that could not be sent earlier (no connection, a crash) are tried again at every start.
        if (PendingEdits.all(this).isNotEmpty()) PendingEdits.sendNow(this)
        // Python takes a moment to start; do it in the background so the app opens quickly.
        Thread { PythonRuntime.get(this) }.start()
    }
}
