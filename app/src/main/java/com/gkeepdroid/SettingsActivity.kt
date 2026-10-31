@file:OptIn(ExperimentalLayoutApi::class)

package com.gkeepdroid

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    companion object {
        /** Opens the settings on a given page; "accounts" shows the accounts to sign in again. */
        const val EXTRA_PAGE = "page"
    }

    // The settings state reads and writes these.
    internal var themeMode by mutableIntStateOf(THEME_SYSTEM)
    internal var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Backing out must cancel adding the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        themeMode = SettingsStore.loadGlobal(this).themeMode

        setContent { KeepTheme(themeMode) { SettingsScreen() } }
    }

    /** Whether this configuration screen was opened for a single-note widget. */
    internal fun isSingleNoteWidget(): Boolean {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        return provider?.className == SingleNoteWidgetReceiver::class.java.name
    }

    internal fun finishWithOk() {
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    /**
     * The settings, in three levels: global ones (accounts, sync, theme, backup), the app view, and the
     * widgets. Opened for a widget (when adding it, from its ⚙ button, from the launcher's
     * "reconfigure" action) it leads with that widget's settings; opened from the app it lists the
     * global and app settings and the placed widgets.
     */
    @Composable
    private fun SettingsScreen() {
        val state = remember { SettingsState(this) }
        state.uriHandler = LocalUriHandler.current
        state.scope = rememberCoroutineScope()
        with(state) {
            // After a reset the button reads "restored" and stays grayed out for a moment.
            LaunchedEffect(layoutResetShown) {
                if (layoutResetShown) {
                    delay(3000)
                    layoutResetShown = false
                }
            }

            credentialsRequired = stringResource(R.string.credentials_required)
            val exportDone = stringResource(R.string.export_done)
            val importDone = stringResource(R.string.import_done)
            val layoutSaved = stringResource(R.string.layout_saved)
            val layoutLoaded = stringResource(R.string.layout_loaded)
            val layoutFailed = stringResource(R.string.layout_failed)
            val importFailed = stringResource(R.string.import_failed, "%s")

            BackHandler(enabled = page != Page.Main || settingsQuery.isNotEmpty()) {
                if (settingsQuery.isNotEmpty()) settingsQuery = "" else page = Page.Main
            }

            loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                // A finished Google sign-in has stored the account itself.
                if (result.resultCode == android.app.Activity.RESULT_OK) showFirstSync = true
                refreshAccounts()
                if (widgetAccount.isBlank()) widgetAccount = accounts.firstOrNull().orEmpty()
            }

            folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    // Keep the access across restarts, for reading and for writing.
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    MarkdownSync.setFolder(context, uri)
                    mdEnabled = true
                    mdFolder = MarkdownSync.folderLabel(uri)
                }
            }

            // A new widget counts as configured as soon as the screen is open; every change is saved right away.
            LaunchedEffect(Unit) {
                if (context.appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    context.setResult(android.app.Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, context.appWidgetId))
                }
            }
            val global = currentGlobal()
            val app = currentApp()
            val widget = currentWidget()
            LaunchedEffect(global, app, widget) {
                // A short pause merges quick changes such as dragging a slider into one save.
                delay(150)
                SettingsStore.saveGlobal(context, global)
                AppSettingsStore.save(context, app)
                if (context.appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    SettingsStore.saveWidget(context, context.appWidgetId, widget)
                }
                SyncWorker.schedule(context)
                ScreenSync.reschedule(context)
                WidgetRefresh.all(context)
            }

            exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                try {
                    val json = Backup.export(currentGlobal(), currentWidget(), Reminders.exportAll(context))
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    status = exportDone
                } catch (e: Exception) {
                    status = importFailed.format(e.message ?: e.javaClass.simpleName)
                }
            }
            layoutSaveLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                status = try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(Backup.exportLayout(currentWidget()).toByteArray()) }
                    layoutSaved
                } catch (e: Exception) {
                    layoutFailed
                }
            }
            layoutLoadLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                status = try {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Cannot read file")
                    applyLayout(Backup.parseLayout(text, currentWidget()))
                    layoutLoaded
                } catch (e: Exception) {
                    layoutFailed
                }
            }
            importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("Cannot read file")
                    val imported = Backup.parse(text)
                    Reminders.importAll(context, imported.reminders)
                    themeMode = imported.global.themeMode
                    interval = imported.global.syncIntervalMin
                    syncOnUnlock = imported.global.syncOnUnlock
                    syncOnOpen = imported.global.syncOnOpen
                    minGapSec = imported.global.minGapSec
                    backgroundSync = imported.global.backgroundSync
                    backgroundInterval = imported.global.backgroundIntervalMin
                    wifiOnly = imported.global.wifiOnly
                    val w = imported.widget
                    backgroundAlpha = w.backgroundAlpha
                    textColor = w.textColor
                    textSize = w.textSizeSp
                    showNoteColors = w.showNoteColors
                    noteAlpha = w.noteAlpha
                    plainStyle = w.plainCardStyle
                    headerSize = w.headerSizeSp
                    buttonSides = w.buttonSides
                    buttonOrder = w.buttonOrder
                    buttonSlots = w.buttonSlots
                    checkable = w.checkable
                    checkedToEnd = w.checkedToEnd
                    collapseChecked = w.collapseChecked
                    listSpacingDp = w.listSpacingDp
                    sortMode = w.sortMode
                    showImages = w.showImages
                    checkboxStyle = w.checkboxStyle
                    checkboxSizeDp = w.checkboxSizeDp
                    tapWholeRow = w.tapWholeRow
                    deleteChecked = w.deleteChecked
                    maxLines = w.maxLines
                    textColorAuto = w.textColorAuto
                    showEditedTime = w.showEditedTime
                    showLabels = w.showLabels
                    showReminders = w.showReminders
                    labelFilter = w.labelFilter
                    showProgress = w.showProgress
                    status = importDone
                } catch (e: Exception) {
                    status = importFailed.format(e.message ?: e.javaClass.simpleName)
                }
            }

            if (confirmRemoveLocal) {
                AlertDialog(
                    onDismissRequest = { confirmRemoveLocal = false },
                    text = { Text(stringResource(R.string.remove_local_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmRemoveLocal = false
                            CredentialStore.remove(context, LOCAL_ACCOUNT)
                            refreshAccounts()
                            scope.launch { WidgetRefresh.all(context) }
                        }) { Text(stringResource(R.string.remove_account)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmRemoveLocal = false }) { Text(stringResource(android.R.string.cancel)) }
                    },
                )
            }

            if (showFirstSync) FirstSyncDialog { showFirstSync = false }

            if (confirmLayoutReset) {
                AlertDialog(
                    onDismissRequest = { confirmLayoutReset = false },
                    text = { Text(stringResource(R.string.layout_reset_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmLayoutReset = false
                            // The default look of the bar; the settings button is part of it, so nobody is locked out.
                            applyLayout(WidgetSettings())
                            layoutResetShown = true
                        }) { Text(stringResource(R.string.layout_reset)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmLayoutReset = false }) { Text(stringResource(android.R.string.cancel)) }
                    },
                )
            }

            if (confirmSettingsButtonOff) {
                // Switching the settings button off could lock the user out, so it has to be confirmed.
                AlertDialog(
                    onDismissRequest = { confirmSettingsButtonOff = false },
                    title = { Text(stringResource(R.string.show_settings_button)) },
                    text = { Text(stringResource(R.string.settings_button_hint)) },
                    confirmButton = {
                        TextButton(onClick = {
                            buttonSides = buttonSides - BTN_SETTINGS
                            confirmSettingsButtonOff = false
                        }) { Text(stringResource(R.string.turn_off)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmSettingsButtonOff = false }) { Text(stringResource(android.R.string.cancel)) }
                    },
                )
            }

            if (showColorPicker) {
                ColorPickerDialog(
                    initial = textColor,
                    onDismiss = { showColorPicker = false },
                    onPick = { textColor = it; showColorPicker = false },
                )
            }

            Column(Modifier.fillMaxSize()) {
                // Title bar: the back arrow appears on every page below the main list.
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // On a page the arrow leads back to the main list; on the main list it closes the settings
                    // and returns to wherever they were opened from.
                    BackButton { if (page != Page.Main) page = Page.Main else finish() }
                    Text(
                        stringResource(if (page == Page.Main && !hasWidget) R.string.menu_settings else page.title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                HorizontalDivider()

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (page) {
                        Page.Main -> MainPage(state)
                        Page.AppView -> AppViewPage(state)
                        Page.Accounts -> AccountsPage(state)
                        Page.Appearance -> AppearancePage(state)
                        Page.Notes -> NotesPage(state)
                        Page.ManageNotes -> ManageNotesPage(state)
                        Page.Checklists -> ChecklistsPage(state)
                        Page.ButtonBar -> ButtonBarPage(state)
                        Page.Sync -> SyncPage(state)
                        Page.Reminders -> RemindersPage(state)
                        Page.Backup -> BackupPage(state)
                    }
                }

                // Status messages and the close button stay visible on every page.
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (status.isNotEmpty()) Text(status)
                    // Only the main page of a widget's settings needs it; from the app, Back is enough.
                    if (hasWidget && page == Page.Main) {
                        Button(modifier = Modifier.fillMaxWidth(), onClick = { finishWithOk() }) {
                            Text(stringResource(R.string.done))
                        }
                    }
                }
            }
        }
    }
}
