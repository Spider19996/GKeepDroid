package com.gkeepdroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Syncs while the display is on: once when the phone is unlocked, and then every few minutes (the
 * display-on interval) until the display goes off. It lives in the app process, which Android starts
 * again for the background sync, a widget update or a reboot.
 */
object ScreenSync {
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var registered = false

    private val tick = Runnable {
        appContext?.let {
            SyncPolicy.request(it, SyncPolicy.Reason.TIMER)
            reschedule(it)
        }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!registered) {
            registered = true
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        reschedule(app)
    }

    /** Sets the next timer tick from the saved interval; call it after the interval changed. */
    fun reschedule(context: Context) {
        handler.removeCallbacks(tick)
        val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive == true
        val minutes = SettingsStore.loadGlobal(context).syncIntervalMin
        if (interactive && minutes > 0) handler.postDelayed(tick, minutes * 60_000L)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> handler.removeCallbacks(tick)
                Intent.ACTION_SCREEN_ON -> reschedule(context)
                Intent.ACTION_USER_PRESENT -> {
                    SyncPolicy.request(context, SyncPolicy.Reason.UNLOCK)
                    reschedule(context)
                }
            }
        }
    }
}
