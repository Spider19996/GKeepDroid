@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.gkeepdroid

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri

/**
 * The list of a note's reminders, with a dialog to add and change one. Every change is saved at once
 * and reported through [onChange].
 */
@Composable
internal fun RemindersDialog(
    activity: Activity, account: String, noteId: String, reminders: List<Reminder>,
    onChange: () -> Unit, onDismiss: () -> Unit, askNotifications: () -> Unit,
    /** For a note that does not exist yet: where the reminders are kept instead of being set at once. */
    onPut: ((Reminder) -> Unit)? = null, onRemove: ((String) -> Unit)? = null,
    /** Shows the list as a sheet instead of a dialog: gets what to do on dismissing and the content. */
    host: (@Composable (onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) -> Unit)? = null,
) {
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var exactHint by remember { mutableStateOf(false) }
    fun newReminder() {
        editing = Reminder(at = System.currentTimeMillis() + 3_600_000L)
    }
    if (exactHint) {
        // Shown once: without the permission Android may delay reminders by minutes.
        AlertDialog(
            onDismissRequest = { exactHint = false; newReminder() },
            title = { Text(stringResource(R.string.reminder_exact_title)) },
            text = { Text(stringResource(R.string.reminder_exact_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    exactHint = false
                    Reminders.exactAlarmSettings(activity)?.let { activity.startActivity(it) }
                    newReminder()
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reminder_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { exactHint = false; newReminder() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reminder_not_now)) }
            },
        )
        // A dialog replaces the list; over a sheet it simply lies on top of it.
        if (host == null) return
    }
    editing?.let { reminder ->
        ReminderEditor(
            activity, reminder,
            onSave = {
                if (onPut != null) onPut(it) else Reminders.put(activity, account, noteId, it)
                editing = null
                onChange()
            },
            onCancel = { editing = null },
            host = host,
        )
        if (host == null) return
    }
    val notificationsOn = rememberNotificationsOn(activity)
    /** Reminders only make sense with notifications: say so, and offer the permission where Android still allows asking. */
    fun needNotifications() {
        Toast.makeText(activity, R.string.reminder_needs_notifications, Toast.LENGTH_LONG).show()
        askNotifications()
    }
    val list: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!notificationsOn) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).background(MaterialTheme.colorScheme.error, MaterialTheme.shapes.small).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.reminder_notifications_off), color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.reminder_open_settings),
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { activity.startActivity(Reminders.notificationSettings(activity)) },
                    )
                }
            }
            if (reminders.isEmpty()) Text(stringResource(R.string.reminders_none))
            reminders.forEach { reminder ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Reminders.label(activity, reminder.at),
                        modifier = Modifier.weight(1f).clickable { if (notificationsOn) editing = reminder else needNotifications() }.padding(vertical = 12.dp),
                    )
                    IconButton(onClick = {
                        if (onRemove != null) onRemove(reminder.id) else Reminders.remove(activity, account, noteId, reminder.id)
                        onChange()
                    }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.reminder_remove))
                    }
                }
            }
        }
    }
    val add: () -> Unit = {
        if (!notificationsOn) {
            needNotifications()
        } else {
            val prefs = activity.getSharedPreferences("reminder_hints", android.content.Context.MODE_PRIVATE)
            if (!Reminders.canScheduleExact(activity) && !prefs.getBoolean("exact_asked", false)) {
                prefs.edit { putBoolean("exact_asked", true) }
                exactHint = true
            } else {
                newReminder()
            }
        }
    }
    if (host != null) {
        host(onDismiss) { close ->
            Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.titleLarge)
            list()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = close, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.ok)) }
                TextButton(onClick = add, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reminder_add)) }
            }
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminders_title)) },
        text = { list() },
        confirmButton = { TextButton(onClick = add, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reminder_add)) } },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.ok)) } },
    )
}

/** One reminder: its time, and sound, vibration and silent mode where they differ from the defaults. */
@Composable
private fun ReminderEditor(
    activity: Activity, initial: Reminder, onSave: (Reminder) -> Unit, onCancel: () -> Unit,
    host: (@Composable (onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) -> Unit)? = null,
) {
    var reminder by remember { mutableStateOf(initial) }
    val global = SettingsStore.loadGlobal(activity)
    val pickSound = rememberSoundPicker(reminder.sound ?: global.reminderSound) { reminder = reminder.copy(sound = it) }
    val fields: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = if (host == null) Modifier.verticalScroll(rememberScrollState()) else Modifier) {
                // Quick times from the settings; the button below picks any other date and time.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderPresets.parse(global.reminderPresets).forEach { preset ->
                        AssistChip(
                            onClick = { ReminderPresets.at(preset)?.let { reminder = reminder.copy(at = it) } },
                            label = { Text(ReminderPresets.label(activity, preset)) },
                        )
                    }
                }
                var pickingTime by remember { mutableStateOf(false) }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pickingTime = true },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(Reminders.label(activity, reminder.at)) }
                if (pickingTime) DateTimePickerDialog(
                    current = reminder.at,
                    onPicked = { reminder = reminder.copy(at = it); pickingTime = false },
                    onDismiss = { pickingTime = false },
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.reminder_sound), style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            reminder.sound == null, { reminder = reminder.copy(sound = null) },
                            { Text(stringResource(R.string.reminder_default)) },
                        )
                        FilterChip(
                            reminder.sound != null, { pickSound() },
                            { Text(reminder.sound?.let { Reminders.soundLabel(activity, it) } ?: stringResource(R.string.reminder_sound_choose)) },
                        )
                    }
                }
                TriState(R.string.reminder_vibrate, reminder.vibrate) { reminder = reminder.copy(vibrate = it) }
                TriState(R.string.reminder_override, reminder.override) { reminder = reminder.copy(override = it) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.reminder_snooze), style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(reminder.snooze == null, { reminder = reminder.copy(snooze = null) }, { Text(stringResource(R.string.reminder_default)) })
                        listOf(5, 10, 30, 60).forEach { minutes ->
                            FilterChip(reminder.snooze == minutes, { reminder = reminder.copy(snooze = minutes) }, { Text("$minutes min") })
                        }
                    }
                }
            }
    }
    if (host != null) {
        host(onCancel) { close ->
            Text(stringResource(R.string.reminder), style = MaterialTheme.typography.titleLarge)
            fields()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = close, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onSave(reminder) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.ok)) }
            }
        }
        return
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.reminder)) },
        text = { fields() },
        confirmButton = { TextButton(onClick = { onSave(reminder) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** The reminders of one note as a small dialog over the home screen, opened from a widget. */
class RemindersActivity : androidx.activity.ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val account = CredentialStore.resolve(this, intent.getStringExtra(EditNoteActivity.EXTRA_ACCOUNT).orEmpty())
        val noteId = intent.getStringExtra(EditNoteActivity.EXTRA_NOTE_ID)
        if (account == null || noteId.isNullOrEmpty()) {
            finish()
            return
        }
        val themeMode = SettingsStore.loadGlobal(this).themeMode
        setContent {
            KeepTheme(themeMode, opaque = false) {
                var reminders by remember { mutableStateOf(Reminders.list(this, account, noteId)) }
                val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                RemindersDialog(
                    activity = this, account = account, noteId = noteId, reminders = reminders,
                    onChange = { reminders = Reminders.list(this, account, noteId) },
                    onDismiss = { finish() },
                    askNotifications = {
                        if (android.os.Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(this)) {
                            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }
    }
}

/** Whether the app may show notifications; checked again whenever the screen comes back, e.g. from the permission dialog or the settings. */
@Composable
fun rememberNotificationsOn(activity: Activity): Boolean {
    var on by remember { mutableStateOf(Reminders.canNotify(activity)) }
    DisposableEffect(activity) {
        val lifecycle = (activity as androidx.lifecycle.LifecycleOwner).lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) on = Reminders.canNotify(activity)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return on
}
