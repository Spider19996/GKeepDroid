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
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One reminder of a note. [sound], [vibrate] and [override] left null follow the defaults in the
 * settings. A sound is "" (the system's notification sound), [SOUND_NONE] or the URI of a ringtone.
 */
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val at: Long,
    val sound: String? = null,
    val vibrate: Boolean? = null,
    val override: Boolean? = null,
)

const val SOUND_NONE = "none"

/**
 * Reminders that live on this phone only: Keep's own reminders are not reachable through gkeepapi.
 * Each one is an alarm that shows a notification; the alarms are set again after a reboot.
 */
object Reminders {
    private const val CHANNEL = "reminders"

    /** How long the sound plays at most; the receiver has to finish within a few seconds. */
    private const val PLAY_MS = 8_000L

    private fun prefs(context: Context) = context.getSharedPreferences("note_reminders", Context.MODE_PRIVATE)

    private fun key(account: String, noteId: String) = "$account|$noteId"

    /** The reminders of a note, the soonest first. */
    fun list(context: Context, account: String, noteId: String): List<Reminder> {
        val raw = prefs(context).getString(key(account, noteId), null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getJSONObject(it) }.map { o ->
                Reminder(
                    id = o.getString("id"),
                    at = o.getLong("at"),
                    sound = if (o.has("sound")) o.getString("sound") else null,
                    vibrate = if (o.has("vibrate")) o.getBoolean("vibrate") else null,
                    override = if (o.has("override")) o.getBoolean("override") else null,
                )
            }.sortedBy { it.at }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, account: String, noteId: String, reminders: List<Reminder>) {
        val array = JSONArray()
        reminders.forEach { r ->
            val o = JSONObject().put("id", r.id).put("at", r.at)
            r.sound?.let { o.put("sound", it) }
            r.vibrate?.let { o.put("vibrate", it) }
            r.override?.let { o.put("override", it) }
            array.put(o)
        }
        val editor = prefs(context).edit()
        if (reminders.isEmpty()) editor.remove(key(account, noteId)) else editor.putString(key(account, noteId), array.toString())
        editor.apply()
    }

    /** Adds a reminder, or replaces the one with the same id, and sets its alarm. */
    fun put(context: Context, account: String, noteId: String, reminder: Reminder) {
        write(context, account, noteId, list(context, account, noteId).filter { it.id != reminder.id } + reminder)
        schedule(context, account, noteId, reminder)
    }

    fun remove(context: Context, account: String, noteId: String, reminderId: String) {
        write(context, account, noteId, list(context, account, noteId).filter { it.id != reminderId })
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, account, noteId, reminderId))
    }

    /** Sets every stored alarm again; one that fell due while the phone was off fires at once. */
    fun restoreAll(context: Context) {
        prefs(context).all.keys.forEach { k ->
            val (account, noteId) = k.split("|", limit = 2).takeIf { it.size == 2 } ?: return@forEach
            list(context, account, noteId).forEach { schedule(context, account, noteId, it) }
        }
    }

    /** A short date and time, such as "Nov 5, 9:00 AM". */
    fun label(context: Context, at: Long): String = android.text.format.DateUtils.formatDateTime(
        context, at,
        android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME or
            android.text.format.DateUtils.FORMAT_ABBREV_ALL,
    )

    /** What a sound setting is called: "Default", "No sound" or the ringtone's title. */
    fun soundLabel(context: Context, sound: String): String = when (sound) {
        "" -> context.getString(R.string.reminder_default)
        SOUND_NONE -> context.getString(R.string.reminder_sound_none)
        else -> try {
            RingtoneManager.getRingtone(context, Uri.parse(sound))?.getTitle(context)
        } catch (e: Exception) {
            null
        } ?: context.getString(R.string.reminder_default)
    }

    private fun pending(context: Context, account: String, noteId: String, reminderId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("gkeepdroid://reminder/" + Uri.encode(key(account, noteId) + "|" + reminderId)))
            .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account)
            .putExtra(EditNoteActivity.EXTRA_NOTE_ID, noteId)
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun schedule(context: Context, account: String, noteId: String, reminder: Reminder) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val operation = pending(context, account, noteId, reminder.id)
        // Exact alarms need a permission the user can withdraw; without it Android may delay the reminder a little.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, operation)
        }
    }

    /** True when notifications may be shown; below Android 13 they always may. */
    fun canNotify(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Shows the notification and plays the sound and vibration of one reminder; [done] runs once they are over. */
    internal fun fire(context: Context, account: String, noteId: String, reminderId: String, done: () -> Unit) {
        val reminder = list(context, account, noteId).firstOrNull { it.id == reminderId }
        remove(context, account, noteId, reminderId)
        // A note that was trashed or deleted in the meantime no longer reminds.
        val note = NotesCache.readAll(context, account).firstOrNull { it.id == noteId }
        if (reminder == null || note == null || !canNotify(context)) return done()

        val manager = context.getSystemService(NotificationManager::class.java)
        // The channel is silent: sound and vibration are played below, so each reminder can have its own.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminders_title), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        // A private note keeps its text out of the notification.
        val hidden = PrivateNotes.key(account, noteId) in PrivateNotes.load(context)
        val title = note.title.ifBlank { context.getString(R.string.reminder) }
        val body = if (hidden) "" else note.text.ifBlank { note.items.joinToString("\n") { it.text } }.take(200)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, EditNoteActivity::class.java)
                .setData(Uri.parse("gkeepdroid://note/" + Uri.encode(key(account, noteId))))
                .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account)
                .putExtra(EditNoteActivity.EXTRA_NOTE_ID, noteId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            (key(account, noteId) + reminderId).hashCode(),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
        alert(context, reminder, done)
    }

    /**
     * Sound only while the phone rings, vibration while it is silent or on vibrate (if wanted).
     * "Ignore silent mode" plays the sound at alarm volume in every mode.
     */
    private fun alert(context: Context, reminder: Reminder, done: () -> Unit) {
        val global = SettingsStore.loadGlobal(context)
        val sound = reminder.sound ?: global.reminderSound
        val vibrate = reminder.vibrate ?: global.reminderVibrate
        val override = reminder.override ?: global.reminderOverride
        val ringing = context.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL

        if (vibrate && !ringing) {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
            }
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
        }

        val uri = when (sound) {
            SOUND_NONE -> null
            "" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> Uri.parse(sound)
        }
        val ringtone = if (uri != null && (ringing || override)) RingtoneManager.getRingtone(context, uri) else null
        if (ringtone == null) return done()
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(if (override) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone.play()
        Handler(Looper.getMainLooper()).postDelayed({ ringtone.stop(); done() }, PLAY_MS)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val account = intent.getStringExtra(EditNoteActivity.EXTRA_ACCOUNT) ?: return
        val noteId = intent.getStringExtra(EditNoteActivity.EXTRA_NOTE_ID) ?: return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        // The receiver stays alive until the sound is over.
        val pending = goAsync()
        Reminders.fire(context, account, noteId, reminderId) { pending.finish() }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
