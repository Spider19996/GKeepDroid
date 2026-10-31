@file:OptIn(ExperimentalLayoutApi::class)

package com.gkeepdroid

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.AlertDialog
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

/** Asks for a date, then a time; a time in the past is moved to the next day. */
internal fun pickDateTime(activity: Activity, current: Long?, onPicked: (Long) -> Unit) {
    val start = java.util.Calendar.getInstance().apply { timeInMillis = current ?: (System.currentTimeMillis() + 3_600_000L) }
    android.app.DatePickerDialog(activity, { _, year, month, day ->
        android.app.TimePickerDialog(activity, { _, hour, minute ->
            val picked = java.util.Calendar.getInstance().apply {
                set(year, month, day, hour, minute, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            if (picked.timeInMillis <= System.currentTimeMillis()) picked.add(java.util.Calendar.DAY_OF_YEAR, 1)
            onPicked(picked.timeInMillis)
        }, start.get(java.util.Calendar.HOUR_OF_DAY), start.get(java.util.Calendar.MINUTE),
            android.text.format.DateFormat.is24HourFormat(activity)).show()
    }, start.get(java.util.Calendar.YEAR), start.get(java.util.Calendar.MONTH), start.get(java.util.Calendar.DAY_OF_MONTH)).show()
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
    }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = { TextButton(onClick = { onSave(reminder) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } },
    )
}
