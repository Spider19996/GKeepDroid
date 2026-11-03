package com.gkeepdroid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * The notification categories of the app, so each can be switched off on its own in Android's settings.
 * They are created at every start, so both are listed there from the beginning.
 */
object NotificationChannels {
    const val REMINDERS = "reminders"
    const val SYNC = "save_failures"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // The reminder channel is silent: sound and vibration are played by the app, so each reminder can have its own.
        manager.createNotificationChannel(
            NotificationChannel(REMINDERS, context.getString(R.string.reminders_title), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(SYNC, context.getString(R.string.sync_section), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
