package com.gkeepdroid

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Sync intervals below 15 minutes, which WorkManager cannot do. An alarm fires, starts a sync and
 * sets the next alarm. Android may delay alarms while the phone is idle (Doze), so this is "about
 * every N minutes", and it costs more battery than the longer intervals.
 */
object SyncAlarm {
    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, SyncAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(context: Context, minutes: Int) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + minutes * 60_000L,
            pending(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }

    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(NotesWidgetReceiver::class.java, SingleNoteWidgetReceiver::class.java).any {
            manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
    }
}

class SyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = SettingsStore.loadGlobal(context)
        val minutes = settings.backgroundIntervalMin
        // The chain stops when background sync was turned off, the interval changed or the last widget is gone.
        if (settings.backgroundSync && minutes in 1..14 && SyncAlarm.hasWidgets(context)) {
            SyncPolicy.request(context, SyncPolicy.Reason.TIMER)
            SyncAlarm.schedule(context, minutes)
        }
    }
}

/** Alarms are lost on reboot and app update, so the sync schedule is set up again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SyncWorker.schedule(context)
    }
}
