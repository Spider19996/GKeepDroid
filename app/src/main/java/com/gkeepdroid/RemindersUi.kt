@file:OptIn(ExperimentalLayoutApi::class)

package com.gkeepdroid

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Asks for a date, then a time; neither may lie in the past, so an earlier time today is asked for again. */
internal fun pickDateTime(activity: Activity, current: Long?, onPicked: (Long) -> Unit) {
    val now = System.currentTimeMillis()
    val start = java.util.Calendar.getInstance().apply { timeInMillis = current?.takeIf { it > now } ?: (now + 3_600_000L) }
    val dateDialog = android.app.DatePickerDialog(activity, { _, year, month, day ->
        fun askTime(hour: Int, minute: Int) {
            android.app.TimePickerDialog(activity, { _, pickedHour, pickedMinute ->
                val picked = java.util.Calendar.getInstance().apply {
                    set(year, month, day, pickedHour, pickedMinute, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                if (picked.timeInMillis <= System.currentTimeMillis()) {
                    android.widget.Toast.makeText(activity, R.string.reminder_past, android.widget.Toast.LENGTH_SHORT).show()
                    askTime(pickedHour, pickedMinute)
                } else {
                    onPicked(picked.timeInMillis)
                }
            }, hour, minute, android.text.format.DateFormat.is24HourFormat(activity)).show()
        }
        askTime(start.get(java.util.Calendar.HOUR_OF_DAY), start.get(java.util.Calendar.MINUTE))
    }, start.get(java.util.Calendar.YEAR), start.get(java.util.Calendar.MONTH), start.get(java.util.Calendar.DAY_OF_MONTH))
    // The calendar starts today; earlier days cannot be chosen.
    dateDialog.datePicker.minDate = now - 1000
    dateDialog.show()
}

/**
 * Opens the system's sound picker and returns the choice as a sound setting ("" = system default,
 * [SOUND_NONE], or a URI). [current] is shown as selected.
 */
@Composable
internal fun rememberSoundPicker(current: String, onPicked: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        onPicked(
            when (uri) {
                null -> SOUND_NONE
                Settings.System.DEFAULT_NOTIFICATION_URI -> ""
                else -> uri.toString()
            }
        )
    }
    return {
        launcher.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
                .putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    when (current) {
                        "" -> Settings.System.DEFAULT_NOTIFICATION_URI
                        SOUND_NONE -> null
                        else -> Uri.parse(current)
                    },
                )
        )
    }
}

/** The settings page with the defaults for all reminders. */
@Composable
internal fun ColumnScope.RemindersPage(s: SettingsState) {
    with(s) {
        val pickSound = rememberSoundPicker(reminderSound) { reminderSound = it }
        MenuRow(stringResource(R.string.reminder_sound), Reminders.soundLabel(context, reminderSound)) { pickSound() }
        SwitchRow(R.string.reminder_vibrate, reminderVibrate) { reminderVibrate = it }
        SwitchRow(R.string.reminder_override, reminderOverride) { reminderOverride = it }
        Hint(R.string.reminder_override_hint)
        NumberRow(R.string.reminder_snooze, reminderSnoozeMin, "min") { reminderSnoozeMin = it.coerceAtLeast(1) }

        SectionTitle(stringResource(R.string.reminder_presets))
        val presets = ReminderPresets.parse(reminderPresets)
        presets.forEach { preset ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ReminderPresets.label(context, preset), Modifier.weight(1f))
                IconButton(onClick = { reminderPresets = (presets - preset).joinToString(",") }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.reminder_remove))
                }
            }
        }
        var adding by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reminder_preset_add)) }
        if (adding) PresetDialog(
            activity = context,
            onAdd = { preset ->
                if (preset !in presets) reminderPresets = (presets + preset).joinToString(",")
                adding = false
            },
            onCancel = { adding = false },
        )
    }
}

/** Adds a quick time: a delay in minutes, or a time of day. */
@Composable
private fun PresetDialog(activity: Activity, onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var relative by remember { mutableStateOf(true) }
    var minutes by remember { mutableStateOf("30") }
    var clock by remember { mutableStateOf("08:00") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.reminder_preset_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(relative, { relative = true }, { Text(stringResource(R.string.reminder_preset_relative)) })
                    FilterChip(!relative, { relative = false }, { Text(stringResource(R.string.reminder_preset_clock)) })
                }
                if (relative) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.reminder_preset_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val (hour, minute) = clock.split(":").map { it.toInt() }
                            android.app.TimePickerDialog(activity, { _, h, m -> clock = "%02d:%02d".format(h, m) }, hour, minute,
                                android.text.format.DateFormat.is24HourFormat(activity)).show()
                        },
                    ) { Text(ReminderPresets.label(activity, "t:$clock")) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !relative || (minutes.toIntOrNull() ?: 0) > 0,
                onClick = { onAdd(if (relative) "r:${minutes.toInt()}" else "t:$clock") },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Default, On or Off for one reminder; Default follows the setting. */
@Composable
private fun TriState(label: Int, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(value == null, { onChange(null) }, { Text(stringResource(R.string.reminder_default)) })
            FilterChip(value == true, { onChange(true) }, { Text(stringResource(R.string.reminder_on)) })
            FilterChip(value == false, { onChange(false) }, { Text(stringResource(R.string.reminder_off)) })
        }
    }
}

/**
 * The list of a note's reminders, with a dialog to add and change one. Every change is saved at once
 * and reported through [onChange].
 */
@Composable
internal fun RemindersDialog(
    activity: Activity, account: String, noteId: String, reminders: List<Reminder>,
    onChange: () -> Unit, onDismiss: () -> Unit, askNotifications: () -> Unit,
) {
    var editing by remember { mutableStateOf<Reminder?>(null) }
    editing?.let { reminder ->
        ReminderEditor(
            activity, reminder,
            onSave = {
                Reminders.put(activity, account, noteId, it)
                editing = null
                onChange()
            },
            onCancel = { editing = null },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminders_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (reminders.isEmpty()) Text(stringResource(R.string.reminders_none))
                reminders.forEach { reminder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Reminders.label(activity, reminder.at),
                            modifier = Modifier.weight(1f).clickable { editing = reminder }.padding(vertical = 12.dp),
                        )
                        IconButton(onClick = {
                            Reminders.remove(activity, account, noteId, reminder.id)
                            onChange()
                        }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.reminder_remove))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                askNotifications()
                // The first reminder asks for a time at once, the default being in an hour.
                editing = Reminder(at = System.currentTimeMillis() + 3_600_000L)
            }) { Text(stringResource(R.string.reminder_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

/** One reminder: its time, and sound, vibration and silent mode where they differ from the defaults. */
@Composable
private fun ReminderEditor(activity: Activity, initial: Reminder, onSave: (Reminder) -> Unit, onCancel: () -> Unit) {
    var reminder by remember { mutableStateOf(initial) }
    val global = SettingsStore.loadGlobal(activity)
    val pickSound = rememberSoundPicker(reminder.sound ?: global.reminderSound) { reminder = reminder.copy(sound = it) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.reminder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Quick times from the settings; the button below picks any other date and time.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderPresets.parse(global.reminderPresets).forEach { preset ->
                        AssistChip(
                            onClick = { ReminderPresets.at(preset)?.let { reminder = reminder.copy(at = it) } },
                            label = { Text(ReminderPresets.label(activity, preset)) },
                        )
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pickDateTime(activity, reminder.at) { reminder = reminder.copy(at = it) } },
                ) { Text(Reminders.label(activity, reminder.at)) }

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
        },
        confirmButton = { TextButton(onClick = { onSave(reminder) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } },
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
