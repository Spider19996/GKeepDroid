@file:OptIn(ExperimentalLayoutApi::class)

package com.gkeepdroid

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    Text(CredentialStore.displayName(context, account))
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
                }) { Text(stringResource(R.string.remove_account)) }
            }
        }

        if (hasWidget && accounts.size > 1) {
            HorizontalDivider()
            Text(stringResource(R.string.account_for_widget), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val selected = CredentialStore.resolve(context, widgetAccount)
                accounts.forEach { account ->
                    FilterChip(
                        selected = selected == account,
                        onClick = { widgetAccount = account },
                        label = { Text(CredentialStore.displayName(context, account)) },
                    )
                }
            }
        }

        if (accounts.isNotEmpty()) HorizontalDivider()

        // Google sign-in is the normal way; the master token stays as an alternative.
        Button(
            onClick = { loginLauncher.launch(Intent(context, LoginActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.sign_in_google)) }

        OutlinedButton(
            onClick = { addLocalAccount() },
            enabled = LOCAL_ACCOUNT !in accounts,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.use_local_only)) }
        Text(
            stringResource(R.string.local_account_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(onClick = { showTokenForm = !showTokenForm }) {
            Text(stringResource(if (showTokenForm) R.string.hide_token_form else R.string.use_token_instead))
        }
        if (showTokenForm) {
            Text(stringResource(R.string.add_account), style = MaterialTheme.typography.titleMedium)
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
            TextButton(onClick = { uriHandler.openUri(MASTER_TOKEN_HELP_URL) }) {
                Text(stringResource(R.string.token_help))
            }
            Button(onClick = { addAccount() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.add_account))
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
                status = context.getString(R.string.sync_started)
            },
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
        ) { Text(stringResource(R.string.md_sync_choose)) }
        Button(
            enabled = mdEnabled,
            onClick = {
                scope.launch {
                    status = runCatching { MarkdownSync.syncNow(context) }.fold(
                        onSuccess = { context.getString(R.string.md_sync_done, it) },
                        onFailure = { context.getString(R.string.md_sync_failed, it.message ?: it.javaClass.simpleName) },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.md_sync_now)) }

        // Settings: one file with everything except accounts and tokens.
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.backup_note))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { exportLauncher.launch("gkeepdroid-settings.json") },
        ) { Text(stringResource(R.string.export_settings)) }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { importLauncher.launch(arrayOf("*/*")) },
        ) { Text(stringResource(R.string.import_settings)) }
    }
}

@Composable
internal fun ColumnScope.AppViewPage(s: SettingsState) {
    with(s) {
        Text(stringResource(R.string.layout_label), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LAYOUT_GRID to R.string.layout_grid, LAYOUT_LIST to R.string.layout_list).forEach { (layout, label) ->
                FilterChip(
                    selected = appLayout == layout,
                    onClick = { appLayout = layout },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SORT_KEEP to R.string.sort_keep,
                SORT_EDITED to R.string.sort_edited,
                SORT_CREATED to R.string.sort_created,
                SORT_TITLE to R.string.sort_title,
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = appSort == mode,
                    onClick = { appSort = mode },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        SwitchRow(R.string.pinned_section_switch, appPinnedSection) { appPinnedSection = it }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        SwitchRow(R.string.show_images, appShowImages) { appShowImages = it }
        SwitchRow(R.string.show_note_colors, appShowColors) { appShowColors = it }
        SwitchRow(R.string.show_edited_time, appShowEdited) { appShowEdited = it }
        if (appShowEdited) SwitchRow(R.string.edited_as_date, appEditedAsDate) { appEditedAsDate = it }
        SwitchRow(R.string.show_labels, appShowLabels) { appShowLabels = it }
        SwitchRow(R.string.show_progress, appShowProgress) { appShowProgress = it }
        SwitchRow(R.string.lock_private, appLockPrivate) { appLockPrivate = it }

        Text(stringResource(R.string.plain_style))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PLAIN_TRANSPARENT to R.string.plain_none,
                PLAIN_GRAY to R.string.plain_gray,
                PLAIN_BORDER to R.string.plain_border,
            ).forEach { (style, label) ->
                FilterChip(
                    selected = appPlainStyle == style,
                    onClick = { appPlainStyle = style },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        // 0 shows every line of a note and every item of a checklist.
        Text(
            if (appPreviewLines == 0) stringResource(R.string.max_lines_unlimited)
            else stringResource(R.string.preview_lines, appPreviewLines),
        )
        Slider(
            value = appPreviewLines.toFloat(), onValueChange = { appPreviewLines = it.toInt() },
            valueRange = 0f..30f, steps = 29,
        )
    }
}
