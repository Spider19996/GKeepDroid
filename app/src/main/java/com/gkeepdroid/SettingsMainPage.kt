@file:OptIn(ExperimentalLayoutApi::class)

package com.gkeepdroid

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource

@Composable
internal fun ColumnScope.MainPage(s: SettingsState) {
    with(s) {
        val untitled = stringResource(R.string.untitled_note)
        val noteCandidates = remember(widgetAccount, accounts.size) {
            NotesCache.read(context, CredentialStore.resolve(context, widgetAccount))
        }
        AppSearchField(
            query = settingsQuery,
            onQueryChange = { settingsQuery = it },
            placeholder = stringResource(R.string.search_settings_hint),
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
            if (hits.isEmpty()) Text(stringResource(R.string.search_settings_no_results))
            SettingsGroup(
                hits.map { (entry, label, pageTitle) ->
                    { shapes ->
                        MenuRow(label, if (pageTitle == label) null else pageTitle, shapes = shapes) {
                            settingsQuery = ""
                            if (entry.popup != null) {
                                page = Page.Main
                                pendingPopup = entry.popup
                            } else {
                                goTo(entry.page)
                            }
                        }
                    }
                },
            )
        } else {
        val globalSection: @Composable () -> Unit = {
            SectionTitle(stringResource(R.string.section_accounts_sync))
            SettingsGroup(
                listOf(
                    { shapes ->
                        MenuRow(
                            stringResource(R.string.tab_accounts),
                            accounts.joinToString { CredentialStore.displayName(context, it, demoMode) }.ifEmpty { "–" },
                            shapes = shapes,
                        ) { page = Page.Accounts }
                    },
                    { shapes -> MenuRow(stringResource(R.string.sync_section), null, shapes = shapes) { page = Page.Sync } },
                ),
            )

            SectionTitle(stringResource(R.string.section_look))
            SettingsGroup(
                listOf(
                    { shapes ->
                        PopupRow(
                            pendingPopup == "theme", { pendingPopup = null },
                            stringResource(R.string.theme_section),
                            themeLabel(themeMode),
                            listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK, THEME_OLED).map { mode ->
                                MenuEntry(themeLabel(mode), selected = mode == themeMode) { themeMode = mode }
                            },
                            shapes = shapes,
                        )
                    },
                    { shapes ->
                        PopupRow(
                            pendingPopup == "language", { pendingPopup = null },
                            stringResource(R.string.language),
                            LocaleHelper.languages.firstOrNull { it.first == language }?.second ?: stringResource(R.string.language_system),
                            listOf(
                                MenuEntry(stringResource(R.string.language_system), selected = language.isEmpty()) { changeLanguage("") },
                            ) + LocaleHelper.languages.map { (tag, name) ->
                                MenuEntry(name, selected = tag == language) { changeLanguage(tag) }
                            },
                            shapes = shapes,
                        )
                    },
                    { shapes -> MenuRow(stringResource(R.string.app_layout_sort), null, shapes = shapes) { page = Page.AppLayout } },
                    { shapes -> MenuRow(stringResource(R.string.app_cards), null, shapes = shapes) { page = Page.AppCards } },
                    { shapes -> MenuRow(stringResource(R.string.list_section), null, shapes = shapes) { page = Page.AppChecklists } },
                    { shapes -> MenuRow(stringResource(R.string.swipe_title), null, shapes = shapes) { page = Page.AppSwipe } },
                    { shapes -> MenuRow(stringResource(R.string.filter_bar_title), null, shapes = shapes) { page = Page.AppFilters } },
                ),
            )

            SectionTitle(stringResource(R.string.section_misc))
            SettingsGroup(
                listOf(
                    { shapes -> MenuRow(stringResource(R.string.reminders_title), null, shapes = shapes) { page = Page.Reminders } },
                    { shapes -> MenuRow(stringResource(R.string.section_security), null, shapes = shapes) { goTo(Page.Security) } },
                    { shapes ->
                        MenuRow(stringResource(R.string.tab_backup), if (mdEnabled) mdFolder else null, shapes = shapes) { page = Page.Backup }
                    },
                    // Brings back every hint that was dismissed and every one-time question.
                    { shapes ->
                        MenuRow(stringResource(R.string.reset_hints), null, chevron = null, shapes = shapes) {
                            Hints.resetAll(context)
                            android.widget.Toast.makeText(context, R.string.reset_hints_done, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                ),
            )
        }
        val widgetSection: @Composable () -> Unit = {
            if (hasWidget) {
                SectionTitle(stringResource(R.string.settings_this_widget))
                SettingsGroup(
                    buildList {
                        if (isSingleNote) add { shapes ->
                            // One choice only, so it is a popup instead of a page of its own.
                            PopupRow(
                                pendingPopup == "note", { pendingPopup = null },
                                stringResource(R.string.select_note),
                                noteCandidates.firstOrNull { it.id == noteId }?.let { noteLabel(it).ifBlank { untitled } } ?: "–",
                                noteCandidates.map { candidate ->
                                    MenuEntry(noteLabel(candidate).ifBlank { untitled }, selected = candidate.id == noteId) { noteId = candidate.id }
                                },
                                shapes = shapes,
                            )
                        }
                        add { shapes ->
                            MenuRow(
                                stringResource(R.string.tab_accounts),
                                accounts.joinToString { CredentialStore.displayName(context, it, demoMode) }.ifEmpty { "–" },
                                shapes = shapes,
                            ) { page = Page.Accounts }
                        }
                        add { shapes -> MenuRow(stringResource(R.string.appearance), null, shapes = shapes) { page = Page.Appearance } }
                        add { shapes -> MenuRow(stringResource(R.string.section_notes), null, shapes = shapes) { page = Page.Notes } }
                        if (!isSingleNote) add { shapes ->
                            MenuRow(stringResource(R.string.manage_notes), null, shapes = shapes) { page = Page.ManageNotes }
                        }
                        add { shapes -> MenuRow(stringResource(R.string.list_section), null, shapes = shapes) { page = Page.Checklists } }
                        add { shapes -> MenuRow(stringResource(R.string.section_button_bar), null, shapes = shapes) { page = Page.ButtonBar } }
                    },
                )
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
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.orphans_clean)) }
                SettingsGroup(
                    placed.mapIndexed { index, (id, single) ->
                        { shapes ->
                            val name = stringResource(if (single) R.string.widget_label_single else R.string.widget_label_list)
                            val account = SettingsStore.loadWidget(context, id).account
                                .takeIf { it.isNotEmpty() }?.let { CredentialStore.displayName(context, it, demoMode) }
                            MenuRow("$name · ${index + 1}", account, shapes = shapes) {
                                startActivity(
                                    Intent(context, SettingsActivity::class.java)
                                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                )
                            }
                        }
                    },
                )
            }
        }
        // Opened for a widget, only that widget's settings show; from the app, the rest does.
        val sections = if (hasWidget) listOf(widgetSection)
        else listOf(globalSection, widgetSection)
        sections.forEach { section -> section() }
        }
    }
}
