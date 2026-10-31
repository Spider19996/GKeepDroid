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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    /** Minutes that "Snooze" puts it off; null follows the setting. */
    val snooze: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("at", at).also { o ->
        sound?.let { o.put("sound", it) }
        vibrate?.let { o.put("vibrate", it) }
        override?.let { o.put("override", it) }
        snooze?.let { o.put("snooze", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = Reminder(
            id = o.getString("id"),
            at = o.getLong("at"),
            sound = if (o.has("sound")) o.getString("sound") else null,
            vibrate = if (o.has("vibrate")) o.getBoolean("vibrate") else null,
            override = if (o.has("override")) o.getBoolean("override") else null,
            snooze = if (o.has("snooze")) o.getInt("snooze") else null,
        )
    }
}

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
            (0 until array.length()).map { Reminder.fromJson(array.getJSONObject(it)) }.sortedBy { it.at }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, account: String, noteId: String, reminders: List<Reminder>) {
        val array = JSONArray()
        reminders.forEach { array.put(it.toJson()) }
        val editor = prefs(context).edit()
        if (reminders.isEmpty()) editor.remove(key(account, noteId)) else editor.putString(key(account, noteId), array.toString())
        editor.apply()
    }

    /** Adds a reminder, or replaces the one with the same id, and sets its alarm. */
    fun put(context: Context, account: String, noteId: String, reminder: Reminder) {
        write(context, account, noteId, list(context, account, noteId).filter { it.id != reminder.id } + reminder)
        schedule(context, account, noteId, reminder)
        refreshWidgets(context)
    }

    fun remove(context: Context, account: String, noteId: String, reminderId: String) {
        write(context, account, noteId, list(context, account, noteId).filter { it.id != reminderId })
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, account, noteId, reminderId))
        refreshWidgets(context)
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
        if (canScheduleExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, operation)
        }
    }

    /** True when alarms may fire on the minute; below Android 12 they always may. */
    fun canScheduleExact(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Android's screen for the "Alarms & reminders" permission, or null where there is none. */
    fun exactAlarmSettings(context: Context): Intent? =
        if (android.os.Build.VERSION.SDK_INT < 31) null
        else Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))

    /** Android's list of apps that are exempt from battery optimization. */
    fun batterySettings(): Intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** True when notifications may be shown; below Android 13 they always may. */
    fun canNotify(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Shows the notification and plays the sound and vibration of one reminder; [done] runs once they are over. */
    internal fun fire(context: Context, account: String, noteId: String, reminderId: String, finished: () -> Unit) {
        val reminder = list(context, account, noteId).firstOrNull { it.id == reminderId }
        remove(context, account, noteId, reminderId)
        // A note that was trashed or deleted in the meantime no longer reminds.
        val note = NotesCache.readAll(context, account).firstOrNull { it.id == noteId && it.state != "trashed" }
        if (reminder == null || note == null || !canNotify(context)) return finished()

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
        val notificationId = (key(account, noteId) + reminderId).hashCode()
        val done = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, DoneReceiver::class.java)
                .setData(Uri.parse("gkeepdroid://done/" + Uri.encode(key(account, noteId) + "|" + reminderId)))
                .putExtra(SnoozeReceiver.EXTRA_NOTIFICATION_ID, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snoozeMinutes = reminder.snooze ?: SettingsStore.loadGlobal(context).reminderSnoozeMin
        val snooze = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, SnoozeReceiver::class.java)
                .setData(Uri.parse("gkeepdroid://snooze/" + Uri.encode(key(account, noteId) + "|" + reminderId)))
                .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account)
                .putExtra(EditNoteActivity.EXTRA_NOTE_ID, noteId)
                .putExtra(SnoozeReceiver.EXTRA_REMINDER, reminder.toJson().toString())
                .putExtra(SnoozeReceiver.EXTRA_NOTIFICATION_ID, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            notificationId,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.reminder_snooze_action, snoozeMinutes), snooze)
                .addAction(0, context.getString(R.string.reminder_done), done)
                .build()
        )
        alert(context, reminder, finished)
    }

    /**
     * Forgets the reminders of notes that no longer exist, are in the trash, or belong to an account
     * that was removed. A note list that is empty (not loaded yet) is never taken as proof.
     */
    fun cleanup(context: Context) {
        if (DemoNotes.enabled(context)) return
        val accounts = CredentialStore.accounts(context).map { it.lowercase() }
        var changed = false
        prefs(context).all.keys.toList().forEach { k ->
            val (account, noteId) = k.split("|", limit = 2).takeIf { it.size == 2 } ?: return@forEach
            val notes = if (account.lowercase() in accounts) NotesCache.readAll(context, account) else emptyList()
            val gone = account.lowercase() !in accounts ||
                (notes.isNotEmpty() && notes.none { it.id == noteId && it.state != "trashed" })
            if (gone) {
                list(context, account, noteId).forEach { r ->
                    context.getSystemService(AlarmManager::class.java).cancel(pending(context, account, noteId, r.id))
                }
                write(context, account, noteId, emptyList())
                changed = true
            }
        }
        if (changed) refreshWidgets(context)
    }

    /** Every reminder as JSON for the settings backup: "account|note" to its list. */
    fun exportAll(context: Context): JSONObject = JSONObject().also { all ->
        prefs(context).all.keys.forEach { k ->
            val (account, noteId) = k.split("|", limit = 2).takeIf { it.size == 2 } ?: return@forEach
            all.put(k, JSONArray().also { array -> list(context, account, noteId).forEach { array.put(it.toJson()) } })
        }
    }

    /** Takes back the reminders of a backup; those that are already past are left out. */
    fun importAll(context: Context, all: JSONObject) {
        val now = System.currentTimeMillis()
        all.keys().forEach { k ->
            val (account, noteId) = k.split("|", limit = 2).takeIf { it.size == 2 } ?: return@forEach
            val array = all.optJSONArray(k) ?: return@forEach
            (0 until array.length()).map { Reminder.fromJson(array.getJSONObject(it)) }
                .filter { it.at > now }
                .forEach { put(context, account, noteId, it) }
        }
    }

    /** The widgets show the next reminder, so they are drawn again whenever a reminder changes or fires. */
    fun refreshWidgets(context: Context) {
        CoroutineScope(Dispatchers.Default).launch { WidgetRefresh.all(context) }
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

/** "Snooze" in the notification: the same reminder again, a few minutes later. */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val account = intent.getStringExtra(EditNoteActivity.EXTRA_ACCOUNT) ?: return
        val noteId = intent.getStringExtra(EditNoteActivity.EXTRA_NOTE_ID) ?: return
        val reminder = try {
            Reminder.fromJson(JSONObject(intent.getStringExtra(EXTRA_REMINDER) ?: return))
        } catch (e: Exception) {
            return
        }
        val minutes = reminder.snooze ?: SettingsStore.loadGlobal(context).reminderSnoozeMin
        Reminders.put(context, account, noteId, reminder.copy(at = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L))
        context.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
    }

    companion object {
        const val EXTRA_REMINDER = "reminder"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}

/** The quick times offered for a reminder, as set in the settings: "r:60" is in 60 minutes, "t:18:00" the next 18:00. */
object ReminderPresets {
    fun parse(spec: String): List<String> = spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** The time a preset stands for, counted from [now]. */
    fun at(preset: String, now: Long = System.currentTimeMillis()): Long? {
        val value = preset.substringAfter(':', "")
        return when (preset.substringBefore(':')) {
            "r" -> value.toIntOrNull()?.let { now + it * 60_000L }
            "t" -> {
                val hour = value.substringBefore(':').toIntOrNull() ?: return null
                val minute = value.substringAfter(':', "0").toIntOrNull() ?: return null
                java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    if (timeInMillis <= now) add(java.util.Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
            }
            else -> null
        }
    }

    /** "In 1 h", "In 30 min" or "At 18:00". */
    fun label(context: Context, preset: String): String {
        val value = preset.substringAfter(':', "")
        return when (preset.substringBefore(':')) {
            "r" -> {
                val minutes = value.toIntOrNull() ?: 0
                if (minutes >= 60 && minutes % 60 == 0) context.getString(R.string.reminder_preset_in_h, minutes / 60)
                else context.getString(R.string.reminder_preset_in, minutes)
            }
            else -> context.getString(
                R.string.reminder_preset_at,
                android.text.format.DateUtils.formatDateTime(context, at(preset) ?: 0L, android.text.format.DateUtils.FORMAT_SHOW_TIME),
            )
        }
    }
}

/** "Done" in the notification: it only goes away, the reminder already ended when it fired. */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra(SnoozeReceiver.EXTRA_NOTIFICATION_ID, 0))
    }
}
