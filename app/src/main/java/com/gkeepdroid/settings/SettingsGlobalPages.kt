@file:OptIn(ExperimentalLayoutApi::class)

package com.gkeepdroid

import android.widget.Toast
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun ColumnScope.AccountsPage(s: SettingsState) {
    with(s) {
        if (accounts.isNotEmpty()) Group {
            accounts.forEach { account ->
                item { shapes ->
                    val local = CredentialStore.isLocal(account)
                    androidx.compose.material3.SegmentedListItem(
                        // Updating the token of an account means using the master token form.
                        onClick = { if (!local) { email = account; showTokenForm = true } },
                        shapes = shapes,
                        colors = androidx.compose.material3.ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        supportingContent = if (!CredentialStore.isUsable(context, account)) {
                            { Text(stringResource(R.string.token_missing), color = MaterialTheme.colorScheme.error) }
                        } else null,
                        trailingContent = {
                            TextButton(onClick = {
                                if (local) {
                                    confirmRemoveLocal = true
                                } else {
                                    CredentialStore.remove(context, account)
                                    refreshAccounts()
                                    scope.launch { WidgetRefresh.all(context) }
                                }
                            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.remove_account)) }
                        },
                    ) { Text(CredentialStore.displayName(context, account, demoMode)) }
                }
            }
            if (hasWidget && accounts.size > 1) item {
                ChoiceItem(
                    R.string.account_for_widget,
                    accounts.map { it to CredentialStore.displayName(context, it, demoMode) },
                    CredentialStore.resolve(context, widgetAccount).orEmpty(), it,
                ) { v -> widgetAccount = v }
            }
        }

        // Google sign-in is the normal way; the master token stays as an alternative.
        Button(
            onClick = { loginLauncher.launch(Intent(context, LoginActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.sign_in_google)) }

        OutlinedButton(
            onClick = { addLocalAccount() },
            enabled = LOCAL_ACCOUNT !in accounts,
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.use_local_only)) }
        Text(
            stringResource(R.string.local_account_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(onClick = { showTokenForm = !showTokenForm }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(if (showTokenForm) R.string.hide_token_form else R.string.use_token_instead))
        }
        if (showTokenForm) {
            Text(stringResource(R.string.add_account), Modifier.searchFlash(R.string.add_account), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text(stringResource(R.string.email_label)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val tokenSaved = email.trim().isNotEmpty() && CredentialStore.token(context, email.trim()) != null
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = {
                    Text(stringResource(if (tokenSaved) R.string.token_label_saved else R.string.token_label))
                },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { uriHandler.openUri(MASTER_TOKEN_HELP_URL) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.token_help), Modifier.searchFlash(R.string.token_help))
            }
            Button(onClick = { addAccount() }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.add_account), Modifier.searchFlash(R.string.add_account))
            }
        }
    }
}

@Composable
internal fun ColumnScope.SyncPage(s: SettingsState) {
    with(s) {
        // The state at a glance: when the last sync worked, what went wrong, and how much Google was asked.
        val lastOk = SyncPolicy.lastSuccess(context)
        val lastError = SyncPolicy.lastError(context)
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            color = if (lastError != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (lastOk == 0L) stringResource(R.string.sync_never)
                    else stringResource(
                        R.string.sync_last_ok,
                        DateUtils.getRelativeTimeSpanString(lastOk, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                            .toString().replaceFirstChar { it.lowercase() },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (lastError != null) Text(stringResource(R.string.sync_last_error, lastError), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.sync_requests_24h, RequestStats.rememberLast24h(context)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        SyncWorker.syncNow(context)
                        Toast.makeText(context, R.string.sync_started, Toast.LENGTH_SHORT).show()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.sync_now)) }
            }
        }

        SectionTitle(stringResource(R.string.sync_group_display))
        SettingsGroup(
            buildList<@Composable (androidx.compose.material3.ListItemShapes) -> Unit> {
                add { NumberItem(R.string.sync_every, interval, "min", it) { v -> interval = v } }
                add { SwitchItem(R.string.sync_on_open, syncOnOpen, it) { v -> syncOnOpen = v } }
                add { SwitchItem(R.string.sync_on_unlock, syncOnUnlock, it) { v -> syncOnUnlock = v } }
                if (syncOnUnlock) add {
                    NumberItem(R.string.sync_unlock_min_age, unlockMinAgeMin, "min", it, stringResource(R.string.sync_unlock_min_age_hint)) { v -> unlockMinAgeMin = v }
                }
            },
        )

        SectionTitle(stringResource(R.string.sync_group_background))
        SettingsGroup(
            buildList<@Composable (androidx.compose.material3.ListItemShapes) -> Unit> {
                add { SwitchItem(R.string.sync_background, backgroundSync, it, stringResource(R.string.sync_background_hint)) { v -> backgroundSync = v } }
                if (backgroundSync) add { NumberItem(R.string.sync_every, backgroundInterval, "min", it) { v -> backgroundInterval = v.coerceAtLeast(1) } }
            },
        )

        SectionTitle(stringResource(R.string.sync_group_network))
        SettingsGroup(listOf { SwitchItem(R.string.sync_wifi_only, wifiOnly, it) { v -> wifiOnly = v } })

        SectionTitle(stringResource(R.string.sync_group_limits))
        SettingsGroup(listOf { NumberItem(R.string.sync_min_gap, minGapSec, "s", it, stringResource(R.string.sync_min_gap_hint)) { v -> minGapSec = v } })
    }
}

@Composable
internal fun ColumnScope.BackupPage(s: SettingsState) {
    with(s) {
        // Notes: the Markdown folder, synced both ways, is the continuous copy of all notes.
        Group(stringResource(R.string.md_sync_title)) {
            item {
                SwitchItem(R.string.md_sync_enable, mdEnabled, it, stringResource(R.string.md_sync_hint)) { on ->
                    if (on && MarkdownSync.treeUri(context) == null) {
                        folderLauncher.launch(null)
                    } else {
                        MarkdownSync.setEnabled(context, on)
                        mdEnabled = on
                    }
                }
            }
            if (mdFolder != null) item {
                PlainItem(it) {
                    FolderRow(R.string.md_sync_folder, mdFolder.orEmpty(), R.string.md_sync_choose, mdEnabled, mdBusy, R.string.md_sync_now,
                        onChoose = { folderLauncher.launch(null) },
                        onSync = {
                            scope.launch {
                                mdBusy = true
                                status = runCatching { MarkdownSync.syncNow(context) }.fold(
                                    onSuccess = { context.resources.getQuantityString(R.plurals.md_sync_done, it, it) },
                                    onFailure = { context.getString(R.string.md_sync_failed, it.message ?: it.javaClass.simpleName) },
                                )
                                mdBusy = false
                            }
                        },
                    )
                }
            }
        }

        // What Keep has no place for, kept in step on all phones through Google Drive or a folder.
        Group(stringResource(R.string.device_sync_title)) {
            item {
                SwitchItem(R.string.device_sync_enable, dsEnabled, it, stringResource(R.string.device_sync_hint)) { on ->
                    if (on && dsMode == DeviceSync.MODE_FOLDER && DeviceSync.treeUri(context) == null) {
                        deviceFolderLauncher.launch(null)
                    } else {
                        DeviceSync.setEnabled(context, on)
                        dsEnabled = on
                    }
                }
            }
            if (dsEnabled) item {
                PlainItem(it) {
                    RadioRow(R.string.device_sync_via_drive, dsMode == DeviceSync.MODE_DRIVE) {
                        DeviceSync.setMode(context, DeviceSync.MODE_DRIVE)
                        dsMode = DeviceSync.MODE_DRIVE
                    }
                    RadioRow(R.string.device_sync_via_folder, dsMode == DeviceSync.MODE_FOLDER) {
                        dsMode = DeviceSync.MODE_FOLDER
                        if (DeviceSync.treeUri(context) == null) deviceFolderLauncher.launch(null) else DeviceSync.setMode(context, DeviceSync.MODE_FOLDER)
                    }
                    if (dsMode == DeviceSync.MODE_DRIVE) {
                        DeviceSync.driveAccount(context)?.let { Text(stringResource(R.string.device_sync_account, it), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (dsEnabled) item { MenuRow(stringResource(R.string.device_sync_name), dsName, shapes = it) { dsNameEdit = dsName } }
            if (dsEnabled) item {
                SwitchItem(R.string.device_sync_reminders, dsReminders, it, stringResource(R.string.device_sync_reminders_hint)) { on ->
                    DeviceSync.setRemindersEnabled(context, on)
                    dsReminders = on
                }
            }
            if (dsEnabled) item {
                SwitchItem(R.string.device_sync_auto_delete, dsAutoDelete, it) { on ->
                    DeviceSync.setAutoDelete(context, on)
                    dsAutoDelete = on
                }
            }
            if (dsEnabled && dsAutoDelete) item {
                NumberItem(R.string.device_sync_auto_delete_after, dsAutoDays, stringResource(R.string.device_sync_days), it) { v ->
                    dsAutoDays = v
                    // Saved as typed, but never below two weeks: the value used is the one shown after the next visit.
                    DeviceSync.setAutoDeleteDays(context, v)
                }
            }
            if (dsEnabled || dsFolder != null) item {
                PlainItem(it) {
                    FolderRow(
                        R.string.device_sync_folder, if (dsMode == DeviceSync.MODE_FOLDER) dsFolder else null, R.string.device_sync_choose, dsEnabled, dsBusy, R.string.device_sync_now,
                        onChoose = { deviceFolderLauncher.launch(null) },
                        onSync = {
                            scope.launch {
                                dsBusy = true
                                dsStatus = ""
                                dsStatus = runCatching { DeviceSync.syncNow(context) }.fold(
                                    onSuccess = { context.getString(R.string.device_sync_done, it) },
                                    onFailure = { context.getString(R.string.device_sync_failed, it.message ?: it.javaClass.simpleName) },
                                )
                                dsBusy = false
                            }
                        },
                    )
                    if (dsEnabled) TextButton(
                        onClick = {
                            scope.launch {
                                dsBusy = true
                                runCatching { DeviceSync.contents(context) }.fold(
                                    onSuccess = { dsFiles = it },
                                    onFailure = { dsStatus = context.getString(R.string.device_sync_failed, it.message ?: it.javaClass.simpleName) },
                                )
                                dsBusy = false
                            }
                        },
                        enabled = !dsBusy,
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.device_sync_contents)) }
                }
            }
        }
        if (dsStatus.isNotEmpty()) Text(dsStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        dsNameEdit?.let { edit ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dsNameEdit = null },
                title = { Text(stringResource(R.string.device_sync_name)) },
                text = { OutlinedTextField(value = edit, onValueChange = { dsNameEdit = it.take(40) }, singleLine = true) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dsNameEdit = null
                            if (edit.isNotBlank() && !edit.trim().equals(dsName, ignoreCase = true)) scope.launch {
                                // Two phones with one name could not be told apart in the list.
                                if (DeviceSync.nameTaken(context, edit)) {
                                    dsNameClash = edit
                                } else {
                                    DeviceSync.setDeviceName(context, edit)
                                    dsName = DeviceSync.deviceName(context)
                                }
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { dsNameEdit = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
        // A phone that came with the name of another one is asked about it as soon as the page is opened.
        androidx.compose.runtime.LaunchedEffect(dsEnabled, dsMode) {
            if (dsEnabled) DeviceSync.nameClash(context)?.let { dsNameClash = it; dsNameClashDefault = true }
        }
        dsNameClash?.let { taken ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dsNameClash = null },
                title = { Text(stringResource(R.string.device_sync_name)) },
                text = { Text(stringResource(R.string.device_sync_name_taken, taken.trim())) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            DeviceSync.setDeviceName(context, taken)
                            dsName = DeviceSync.deviceName(context)
                            dsNameClash = null
                            dsNameClashDefault = false
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.device_sync_name_use)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            dsNameClash = null
                            if (dsNameClashDefault) dsNameEdit = taken
                            dsNameClashDefault = false
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(if (dsNameClashDefault) R.string.device_sync_name_change else android.R.string.cancel)) }
                },
            )
        }
        dsDelete?.let { item ->
            val title = item.label ?: stringResource(R.string.device_sync_photo)
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dsDelete = null },
                title = { Text(stringResource(R.string.device_sync_delete)) },
                text = { Text(stringResource(R.string.device_sync_delete_confirm, title)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dsDelete = null
                            scope.launch {
                                dsBusy = true
                                runCatching { DeviceSync.deleteFile(context, item.file.name); DeviceSync.contents(context) }.fold(
                                    onSuccess = { dsFiles = it },
                                    onFailure = { dsStatus = context.getString(R.string.device_sync_failed, it.message ?: it.javaClass.simpleName) },
                                )
                                dsBusy = false
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.device_sync_delete)) }
                },
                dismissButton = { TextButton(onClick = { dsDelete = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
        dsViewed?.let { viewed ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dsViewed = null },
                title = { Text(dsViewTitle) },
                text = {
                    Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        val bitmap = remember(viewed) { viewed.photo?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                        if (bitmap != null) androidx.compose.foundation.Image(bitmap, contentDescription = null, Modifier.fillMaxWidth())
                        // Selectable, so a part of it can be copied.
                        viewed.text?.let { androidx.compose.foundation.text.selection.SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) } }
                    }
                },
                confirmButton = { TextButton(onClick = { dsViewed = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.ok)) } },
                dismissButton = viewed.text?.let { text ->
                    {
                        TextButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                context.startActivity(Intent.createChooser(send, null))
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.device_sync_share)) }
                    }
                },
            )
        }
        dsFiles?.let { files ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dsFiles = null },
                title = { Text(stringResource(R.string.device_sync_title)) },
                text = {
                    if (files.isEmpty()) {
                        Text(stringResource(R.string.device_sync_contents_empty))
                    } else {
                        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            files.sortedBy { it.file.name }.forEach { item ->
                                val shownTitle = item.label ?: stringResource(R.string.device_sync_photo)
                                Row(
                                    Modifier.clickable {
                                        scope.launch {
                                            val viewed = runCatching { DeviceSync.view(context, item.file.name) }.getOrNull()
                                            if (viewed != null) { dsViewTitle = shownTitle; dsViewed = viewed }
                                        }
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        val title = shownTitle
                                        Text(if (item.own) stringResource(R.string.device_sync_this_phone, title) else title, style = MaterialTheme.typography.bodyMedium)
                                        if (item.file.modified > 0) Text(
                                            DateUtils.formatDateTime(context, item.file.modified, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    // The file of this phone would only be written again, so it has no button.
                                    if (!item.own) androidx.compose.material3.IconButton(onClick = { dsDelete = item }) {
                                        androidx.compose.material3.Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.device_sync_delete))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dsFiles = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.ok)) } },
            )
        }

        // Settings: one file with everything except accounts and tokens.
        Group(stringResource(R.string.group_settings_file)) {
            item {
                PlainItem(it, R.string.backup_note) {
                    Hint(R.string.backup_note)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { exportLauncher.launch("gkeepdroid-settings.json") },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.export_settings)) }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.import_settings)) }
                    }
                }
            }
        }
    }
}

/** One of several exclusive choices. */
@Composable
private fun RadioRow(label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Text(stringResource(label), Modifier.weight(1f))
    }
}

/** The chosen folder of a sync, with a button to change it, and the button that syncs now. */
@Composable
private fun FolderRow(folder: Int, name: String?, choose: Int, enabled: Boolean, busy: Boolean, now: Int, onChoose: () -> Unit, onSync: () -> Unit) {
    if (name != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(folder, name), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        TextButton(onClick = onChoose, shapes = ButtonDefaults.shapes()) { Text(stringResource(choose)) }
    }
    OutlinedButton(enabled = enabled && !busy, onClick = onSync, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
        // Something visible while it runs, so a tap is never met with silence.
        if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(stringResource(if (busy) R.string.device_sync_busy else now), Modifier.padding(start = if (busy) 8.dp else 0.dp))
    }
}

internal val swipeChoices = listOf(
    SWIPE_NONE to R.string.swipe_none, SWIPE_ARCHIVE to R.string.archive_note, SWIPE_TRASH to R.string.move_to_trash,
    SWIPE_PIN to R.string.pin_note, SWIPE_REMINDER to R.string.reminders_title, SWIPE_SHARE to R.string.share_note,
    SWIPE_HIDE to R.string.hide_content,
)

/** The buttons above the notes: shown or not, and which ones. */
internal val filterChoices = listOf(
    FILTER_NOTES to R.string.filter_notes, FILTER_LISTS to R.string.list_section, FILTER_REMINDER to R.string.filter_reminders,
    FILTER_RECENT to R.string.filter_recent, FILTER_IMAGE to R.string.filter_with_image, FILTER_PINNED to R.string.filter_pinned,
    FILTER_LABELS to R.string.labels_title,
)

@Composable
internal fun ColumnScope.SecurityPage(s: SettingsState) {
    with(s) {
        Group(stringResource(R.string.group_locks)) {
            item { SwitchItem(R.string.lock_private, appLockPrivate, it, stringResource(R.string.lock_private_hint)) { v -> appLockPrivate = v } }
            item { SwitchItem(R.string.app_lock_title, appLockApp, it, stringResource(R.string.app_lock_hint)) { v -> appLockApp = v } }
            // With the whole app locked, unlocking it already allows editing, so this one would only repeat it.
            if (!appLockApp) item { SwitchItem(R.string.lock_edit_title, appLockEdit, it, stringResource(R.string.lock_edit_hint)) { v -> appLockEdit = v } }
            if (appLockApp || appLockEdit) item { NumberItem(R.string.app_lock_grace, appLockGrace, "s", it) { v -> appLockGrace = v } }
        }
        if (appLockApp) Hint(R.string.lock_edit_app_hint)
        Group(stringResource(R.string.group_widgets)) {
            item { SwitchItem(R.string.lock_widget_tick, appLockWidgetTick, it) { v -> appLockWidgetTick = v } }
            if (appLockWidgetTick) item { NumberItem(R.string.lock_widget_grace, appWidgetGrace, "s", it, stringResource(R.string.lock_widget_grace_hint)) { v -> appWidgetGrace = v } }
            if (appLockApp) item { SwitchItem(R.string.widget_titles_only, appWidgetTitlesOnly, it, stringResource(R.string.widget_titles_only_hint)) { v -> appWidgetTitlesOnly = v } }
        }
    }
}
