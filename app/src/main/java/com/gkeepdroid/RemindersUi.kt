@file:OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

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

/** Asks for a date, then a time; neither may lie in the past, so an earlier time today is asked for again. */
@Composable
internal fun DateTimePickerDialog(current: Long?, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val start = remember {
        java.util.Calendar.getInstance().apply { timeInMillis = current?.takeIf { it > now } ?: (now + 3_600_000L) }
    }
    // The date picker works in UTC days; today's local date as a UTC day is the first one that can be chosen.
    val todayUtc = remember {
        val local = java.util.Calendar.getInstance()
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH), local.get(java.util.Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(start.get(java.util.Calendar.YEAR), start.get(java.util.Calendar.MONTH), start.get(java.util.Calendar.DAY_OF_MONTH))
        }.timeInMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
            override fun isSelectableYear(year: Int) = year >= java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        },
    )
    var day by remember { mutableStateOf<Long?>(null) }
    val chosenDay = day
    if (chosenDay == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(enabled = dateState.selectedDateMillis != null, onClick = { day = dateState.selectedDateMillis }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } },
        ) { DatePicker(dateState) }
    } else {
        val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = chosenDay }
        TimePickerAlert(
            hour = start.get(java.util.Calendar.HOUR_OF_DAY), minute = start.get(java.util.Calendar.MINUTE),
            onConfirm = { hour, minute ->
                val picked = java.util.Calendar.getInstance().apply {
                    set(utc.get(java.util.Calendar.YEAR), utc.get(java.util.Calendar.MONTH), utc.get(java.util.Calendar.DAY_OF_MONTH), hour, minute, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                if (picked.timeInMillis <= System.currentTimeMillis()) {
                    Toast.makeText(context, R.string.reminder_past, Toast.LENGTH_SHORT).show()
                } else {
                    onPicked(picked.timeInMillis)
                }
            },
            onDismiss = onDismiss,
        )
    }
}

/** A time of day in a dialog, in the system's 12 or 24 hour style. */
@Composable
internal fun TimePickerAlert(hour: Int, minute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(hour, minute, android.text.format.DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } },
    )
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
                        else -> current.toUri()
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
private fun TriState(label: Int, value: Boolean?, onChange: (Boolean?) -> Unit) {
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
        return
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
        )
        return
    }
    val notificationsOn = rememberNotificationsOn(activity)
    /** Reminders only make sense with notifications: say so, and offer the permission where Android still allows asking. */
    fun needNotifications() {
        Toast.makeText(activity, R.string.reminder_needs_notifications, Toast.LENGTH_LONG).show()
        askNotifications()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminders_title)) },
        text = {
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
        },
        confirmButton = {
            TextButton(onClick = {
                if (!notificationsOn) {
                    needNotifications()
                    return@TextButton
                }
                val prefs = activity.getSharedPreferences("reminder_hints", android.content.Context.MODE_PRIVATE)
                if (!Reminders.canScheduleExact(activity) && !prefs.getBoolean("exact_asked", false)) {
                    prefs.edit { putBoolean("exact_asked", true) }
                    exactHint = true
                } else {
                    newReminder()
                }
            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reminder_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.ok)) } },
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
        },
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
    androidx.compose.runtime.DisposableEffect(activity) {
        val lifecycle = (activity as androidx.lifecycle.LifecycleOwner).lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) on = Reminders.canNotify(activity)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return on
}
