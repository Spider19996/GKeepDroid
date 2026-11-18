@file:OptIn(ExperimentalLayoutApi::class)

package com.gkeepdroid

import android.widget.Toast
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun ColumnScope.AccountsPage(s: SettingsState) {
    with(s) {
        accounts.forEach { account ->
            val local = CredentialStore.isLocal(account)
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !local) {
                    // Updating the token of an account means using the master token form.
                    email = account
                    showTokenForm = true
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(CredentialStore.displayName(context, account, demoMode))
                    if (!CredentialStore.isUsable(context, account)) {
                        Text(
                            stringResource(R.string.token_missing),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TextButton(onClick = {
                    if (local) {
                        confirmRemoveLocal = true
                    } else {
                        CredentialStore.remove(context, account)
                        refreshAccounts()
                        scope.launch { WidgetRefresh.all(context) }
                    }
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.remove_account)) }
            }
        }

        if (hasWidget && accounts.size > 1) {
            HorizontalDivider()
            Text(stringResource(R.string.account_for_widget), Modifier.searchFlash(R.string.account_for_widget), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val selected = CredentialStore.resolve(context, widgetAccount)
                accounts.forEach { account ->
                    FilterChip(
                        selected = selected == account,
                        onClick = { widgetAccount = account },
                        label = { Text(CredentialStore.displayName(context, account, demoMode)) },
                    )
                }
            }
        }

        if (accounts.isNotEmpty()) HorizontalDivider()

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
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                SyncWorker.syncNow(context)
                Toast.makeText(context, R.string.sync_started, Toast.LENGTH_SHORT).show()
            },
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.sync_now)) }
        // When the last sync worked, and what went wrong if the last one failed.
        val lastOk = SyncPolicy.lastSuccess(context)
        val lastError = SyncPolicy.lastError(context)
        Text(
            if (lastOk == 0L) stringResource(R.string.sync_never)
            else stringResource(
                R.string.sync_last_ok,
                DateUtils.getRelativeTimeSpanString(lastOk, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                    .toString().replaceFirstChar { it.lowercase() },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (lastError != null) {
            Text(
                stringResource(R.string.sync_last_error, lastError),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Every call to Keep's servers of the last 24 hours, so the load on Google can be judged.
        Text(
            stringResource(R.string.sync_requests_24h, RequestStats.rememberLast24h(context)),
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionTitle(stringResource(R.string.sync_group_display))
        NumberRow(R.string.sync_every, interval, "min") { interval = it }
        SwitchRow(R.string.sync_on_unlock, syncOnUnlock) { syncOnUnlock = it }
        SwitchRow(R.string.sync_on_open, syncOnOpen) { syncOnOpen = it }
        NumberRow(R.string.sync_min_gap, minGapSec, "s") { minGapSec = it }
        Hint(R.string.sync_min_gap_hint)

        SectionTitle(stringResource(R.string.sync_group_background))
        SwitchRow(R.string.sync_background, backgroundSync) { backgroundSync = it }
        if (backgroundSync) NumberRow(R.string.sync_every, backgroundInterval, "min") { backgroundInterval = it.coerceAtLeast(1) }
        Hint(R.string.sync_background_hint)

        SectionTitle(stringResource(R.string.sync_group_network))
        SwitchRow(R.string.sync_wifi_only, wifiOnly) { wifiOnly = it }
    }
}

@Composable
internal fun ColumnScope.BackupPage(s: SettingsState) {
    with(s) {
        // Notes: the Markdown folder, synced both ways, is the continuous copy of all notes.
        SectionTitle(stringResource(R.string.md_sync_title))
        Text(
            stringResource(R.string.md_sync_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(R.string.md_sync_enable, mdEnabled) { on ->
            if (on && MarkdownSync.treeUri(context) == null) {
                folderLauncher.launch(null)
            } else {
                MarkdownSync.setEnabled(context, on)
                mdEnabled = on
            }
        }
        mdFolder?.let { Text(stringResource(R.string.md_sync_folder, it)) }
        OutlinedButton(
            onClick = { folderLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.md_sync_choose)) }
        Button(
            enabled = mdEnabled,
            onClick = {
                scope.launch {
                    status = runCatching { MarkdownSync.syncNow(context) }.fold(
                        onSuccess = { context.resources.getQuantityString(R.plurals.md_sync_done, it, it) },
                        onFailure = { context.getString(R.string.md_sync_failed, it.message ?: it.javaClass.simpleName) },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.md_sync_now)) }

        // Settings: one file with everything except accounts and tokens.
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.backup_note), Modifier.searchFlash(R.string.backup_note))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { exportLauncher.launch("gkeepdroid-settings.json") },
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.export_settings)) }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { importLauncher.launch(arrayOf("*/*")) },
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.import_settings)) }
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
        SwitchRow(R.string.lock_private, appLockPrivate) { appLockPrivate = it }
        Hint(R.string.lock_private_hint)
        SwitchRow(R.string.app_lock_title, appLockApp) { appLockApp = it }
        Hint(R.string.app_lock_hint)
        // With the whole app locked, unlocking it already allows editing, so this one would only repeat it.
        if (appLockApp) {
            Hint(R.string.lock_edit_app_hint)
        } else {
            SwitchRow(R.string.lock_edit_title, appLockEdit) { appLockEdit = it }
            Hint(R.string.lock_edit_hint)
        }
        SwitchRow(R.string.lock_widget_tick, appLockWidgetTick) { appLockWidgetTick = it }
        if (appLockApp || appLockEdit) NumberRow(R.string.app_lock_grace, appLockGrace, "s") { appLockGrace = it }
        if (appLockWidgetTick) {
            NumberRow(R.string.lock_widget_grace, appWidgetGrace, "s") { appWidgetGrace = it }
            Hint(R.string.lock_widget_grace_hint)
        }
        if (appLockApp) {
            SwitchRow(R.string.widget_titles_only, appWidgetTitlesOnly) { appWidgetTitlesOnly = it }
            Hint(R.string.widget_titles_only_hint)
        }

    }
}
