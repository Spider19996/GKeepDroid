package com.gkeepdroid

import android.app.Activity
import androidx.core.content.edit
import androidx.core.net.toUri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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

// The settings page of the reminders (the page itself, the presets of times and the three-way switch).

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

        // Two things Android may take away from a reminder: exact timing and background running.
        SectionTitle(stringResource(R.string.reminder_reliability))
        Reminders.exactAlarmSettings(context)?.let { intent ->
            MenuRow(
                stringResource(R.string.reminder_exact_title),
                stringResource(if (Reminders.canScheduleExact(context)) R.string.reminder_allowed else R.string.reminder_not_allowed),
            ) { startActivity(intent) }
            Hint(R.string.reminder_exact_hint)
        }
        MenuRow(stringResource(R.string.reminder_battery_title), null) { startActivity(Reminders.batterySettings()) }
        Hint(R.string.reminder_battery_hint)

        SectionTitle(stringResource(R.string.reminder_presets))
        val presets = ReminderPresets.parse(reminderPresets)
        // The order here is the order of the chips when a reminder is set.
        fun move(index: Int, delta: Int) {
            val moved = presets.toMutableList()
            java.util.Collections.swap(moved, index, index + delta)
            reminderPresets = moved.joinToString(",")
        }
        presets.forEachIndexed { index, preset ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ReminderPresets.label(context, preset), Modifier.weight(1f))
                IconButton(enabled = index > 0, onClick = { move(index, -1) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = stringResource(R.string.move_up))
                }
                IconButton(enabled = index < presets.lastIndex, onClick = { move(index, 1) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = stringResource(R.string.move_down))
                }
                IconButton(onClick = { reminderPresets = (presets - preset).joinToString(",") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.reminder_remove))
                }
            }
        }
        var adding by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reminder_preset_add)) }
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
    var pickingClock by remember { mutableStateOf(false) }
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
                        onClick = { pickingClock = true },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(ReminderPresets.label(activity, "t:$clock")) }
                    if (pickingClock) {
                        val (hour, minute) = clock.split(":").map { it.toInt() }
                        TimePickerAlert(
                            hour, minute,
                            onConfirm = { h, m -> clock = "%02d:%02d".format(h, m); pickingClock = false },
                            onDismiss = { pickingClock = false },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !relative || (minutes.toIntOrNull() ?: 0) > 0,
                onClick = { onAdd(if (relative) "r:${minutes.toInt()}" else "t:$clock") },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Default, On or Off for one reminder; Default follows the setting. */
@Composable
internal fun TriState(label: Int, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        ChoiceGroup(
            listOf(
                null to stringResource(R.string.reminder_default),
                true to stringResource(R.string.reminder_on),
                false to stringResource(R.string.reminder_off),
            ),
            value,
            onChange,
        )
    }
}
