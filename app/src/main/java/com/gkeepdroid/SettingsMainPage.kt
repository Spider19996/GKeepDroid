@file:OptIn(ExperimentalLayoutApi::class)

package com.gkeepdroid

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
internal fun ColumnScope.MainPage(s: SettingsState) {
    with(s) {
        val untitled = stringResource(R.string.untitled_note)
        val noteCandidates = remember(widgetAccount, accounts.size) {
            NotesCache.read(context, CredentialStore.resolve(context, widgetAccount))
        }
        OutlinedTextField(
            value = settingsQuery,
            onValueChange = { settingsQuery = it },
            placeholder = { Text(stringResource(R.string.search_settings_hint)) },
            singleLine = true,
            trailingIcon = {
                if (settingsQuery.isNotEmpty()) TextButton(onClick = { settingsQuery = "" }) { Text("✕") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!hasWidget && settingsQuery.isBlank()) {
            Text(
                stringResource(R.string.google_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The version, as set in the build.
            val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            Text(
                "GKeepDroid v$version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (settingsQuery.isNotBlank()) {
            // Search results: every setting whose name or page matches.
            val hits = entries.map { entry ->
                Triple(entry, context.settingLabel(entry.label), context.getString(entry.pageTitle))
            }.filter { (_, label, pageTitle) ->
                label.contains(settingsQuery.trim(), ignoreCase = true) ||
                    pageTitle.contains(settingsQuery.trim(), ignoreCase = true)
            }.distinctBy { it.second }
            if (hits.isEmpty()) Text(stringResource(R.string.search_no_results))
            hits.forEach { (entry, label, pageTitle) ->
                MenuRow(label, if (pageTitle == label) null else pageTitle) {
                    settingsQuery = ""
                    if (entry.popup != null) {
                        page = Page.Main
                        pendingPopup = entry.popup
                    } else {
                        page = entry.page
                    }
                }
            }
        } else {
        val globalSection: @Composable () -> Unit = {
            SectionTitle(stringResource(R.string.settings_global))
            MenuRow(
                stringResource(R.string.tab_accounts),
                accounts.joinToString { CredentialStore.displayName(context, it) }.ifEmpty { "–" },
            ) { page = Page.Accounts }
            MenuRow(stringResource(R.string.sync_section), null) { page = Page.Sync }
            MenuRow(stringResource(R.string.reminders_title), null) { page = Page.Reminders }
            PopupRow(
                pendingPopup == "theme", { pendingPopup = null },
                stringResource(R.string.theme_section),
                themeLabel(themeMode),
                listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK, THEME_OLED).map { mode ->
                    ((if (mode == themeMode) "✓ " else "") + themeLabel(mode)) to { themeMode = mode }
                },
            )
            PopupRow(
                pendingPopup == "language", { pendingPopup = null },
                stringResource(R.string.language),
                LocaleHelper.languages.firstOrNull { it.first == language }?.second ?: stringResource(R.string.language_system),
                listOf(
                    ((if (language.isEmpty()) "✓ " else "") + stringResource(R.string.language_system)) to { changeLanguage("") },
                ) + LocaleHelper.languages.map { (tag, name) ->
                    ((if (tag == language) "✓ " else "") + name) to { changeLanguage(tag) }
                },
            )
            MenuRow(stringResource(R.string.tab_backup), if (mdEnabled) mdFolder else null) { page = Page.Backup }
            SwitchRow(R.string.demo_mode, demoMode) { demoMode = it }
        }
        val appSection: @Composable () -> Unit = {
            SectionTitle(stringResource(R.string.settings_app))
            MenuRow(stringResource(R.string.app_view), null) { page = Page.AppView }
        }
        val widgetSection: @Composable () -> Unit = {
            if (hasWidget) {
                SectionTitle(stringResource(R.string.settings_this_widget))
                if (isSingleNote) {
                    // One choice only, so it is a popup instead of a page of its own.
                    PopupRow(
                        pendingPopup == "note", { pendingPopup = null },
                        stringResource(R.string.select_note),
                        noteCandidates.firstOrNull { it.id == noteId }?.let { noteLabel(it).ifBlank { untitled } } ?: "–",
                        noteCandidates.map { candidate ->
                            ((if (candidate.id == noteId) "✓ " else "") + noteLabel(candidate).ifBlank { untitled }) to
                                { noteId = candidate.id }
                        },
                    )
                }
                MenuRow(
                    stringResource(R.string.tab_accounts),
                    accounts.joinToString { CredentialStore.displayName(context, it) }.ifEmpty { "–" },
                ) { page = Page.Accounts }
                MenuRow(stringResource(R.string.appearance), null) { page = Page.Appearance }
                MenuRow(stringResource(R.string.section_notes), null) { page = Page.Notes }
                if (!isSingleNote) MenuRow(stringResource(R.string.manage_notes), null) { page = Page.ManageNotes }
                MenuRow(stringResource(R.string.list_section), null) { page = Page.Checklists }
                MenuRow(stringResource(R.string.section_button_bar), null) { page = Page.ButtonBar }
            } else {
                // From the app: every placed widget opens its own settings.
                SectionTitle(stringResource(R.string.settings_widgets))
                val manager = AppWidgetManager.getInstance(context)
                val placed = remember {
                    listOf(NotesWidgetReceiver::class.java to false, SingleNoteWidgetReceiver::class.java to true)
                        .flatMap { (kind, single) ->
                            manager.getAppWidgetIds(ComponentName(context, kind)).map { it to single }
                        }
                }
                if (placed.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_no_widgets),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val removed = SettingsStore.deleteOrphans(context, placed.map { it.first })
                        status = context.getString(R.string.orphans_removed, removed)
                    },
                ) { Text(stringResource(R.string.orphans_clean)) }
                placed.forEachIndexed { index, (id, single) ->
                    val name = stringResource(if (single) R.string.widget_label_single else R.string.widget_label_list)
                    val account = SettingsStore.loadWidget(context, id).account
                        .takeIf { it.isNotEmpty() }?.let { CredentialStore.displayName(context, it) }
                    MenuRow("$name · ${index + 1}", account) {
                        startActivity(
                            Intent(context, SettingsActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        )
                    }
                }
            }
        }
        // Opened for a widget, only that widget's settings show; from the app, the rest does.
        val sections = if (hasWidget) listOf(widgetSection)
        else listOf(globalSection, appSection, widgetSection)
        sections.forEachIndexed { index, section ->
            if (index > 0) HorizontalDivider()
            section()
        }
        }
    }
}
