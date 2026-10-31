package com.gkeepdroid

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Reminders that live on this phone only: Keep's own reminders are not reachable through gkeepapi.
 * Each one is an alarm that shows a notification; the alarms are set again after a reboot.
 */
object Reminders {
    private const val CHANNEL = "reminders"

    private fun prefs(context: Context) = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    private fun key(account: String, noteId: String) = "$account|$noteId"

    /** A short date and time, such as "Nov 5, 9:00 AM". */
    fun label(context: Context, at: Long): String = android.text.format.DateUtils.formatDateTime(
        context, at,
        android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME or
            android.text.format.DateUtils.FORMAT_ABBREV_ALL,
    )

    /** The due time in milliseconds, or null when the note has no reminder. */
    fun get(context: Context, account: String, noteId: String): Long? =
        prefs(context).getLong(key(account, noteId), 0L).takeIf { it > 0 }

    fun set(context: Context, account: String, noteId: String, at: Long) {
        prefs(context).edit().putLong(key(account, noteId), at).apply()
        schedule(context, account, noteId, at)
    }

    fun clear(context: Context, account: String, noteId: String) {
        prefs(context).edit().remove(key(account, noteId)).apply()
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, account, noteId))
    }

    /** Sets every stored alarm again; one that fell due while the phone was off fires at once. */
    fun restoreAll(context: Context) {
        prefs(context).all.forEach { (k, v) ->
            val at = v as? Long ?: return@forEach
            val (account, noteId) = k.split("|", limit = 2).takeIf { it.size == 2 } ?: return@forEach
            schedule(context, account, noteId, at)
        }
    }

    private fun pending(context: Context, account: String, noteId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("gkeepdroid://reminder/" + Uri.encode(key(account, noteId))))
            .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account)
            .putExtra(EditNoteActivity.EXTRA_NOTE_ID, noteId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun schedule(context: Context, account: String, noteId: String, at: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val operation = pending(context, account, noteId)
        // Exact alarms need a permission the user can withdraw; without it Android may delay the reminder a little.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    /** True when notifications may be shown; below Android 13 they always may. */
    fun canNotify(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun notify(context: Context, account: String, noteId: String) {
        clear(context, account, noteId)
        if (!canNotify(context)) return
        // A note that was trashed or deleted in the meantime no longer reminds.
        val note = NotesCache.readAll(context, account).firstOrNull { it.id == noteId } ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH)
        )
        // A private note keeps its text out of the notification.
        val hidden = PrivateNotes.key(account, noteId) in PrivateNotes.load(context)
        val title = note?.title.orEmpty().ifBlank { context.getString(R.string.reminder) }
        val body = if (hidden) "" else note.text.ifBlank { note.items.joinToString("\n") { it.text } }.take(200)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, EditNoteActivity::class.java)
                .setData(Uri.parse("gkeepdroid://note/" + Uri.encode(key(account, noteId))))
                .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account)
                .putExtra(EditNoteActivity.EXTRA_NOTE_ID, noteId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(key(account, noteId).hashCode(), notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val account = intent.getStringExtra(EditNoteActivity.EXTRA_ACCOUNT) ?: return
        val noteId = intent.getStringExtra(EditNoteActivity.EXTRA_NOTE_ID) ?: return
        Reminders.notify(context, account, noteId)
    }
}
