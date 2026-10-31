package com.gkeepdroid

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.text.format.DateUtils
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MASTER_TOKEN_HELP_URL = "https://gkeepapi.readthedocs.io/en/latest/#obtaining-a-master-token"

private val TEXT_COLORS = listOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFD0D0D0.toInt(), 0xFFFFD54F.toInt(), 0xFFFFB74D.toInt(),
    0xFFEF5350.toInt(), 0xFFF48FB1.toInt(), 0xFFCE93D8.toInt(), 0xFF81D4FA.toInt(), 0xFF80CBC4.toInt(),
    0xFFA5D6A7.toInt(),
)

/** The pages of the settings: a main list like the Android settings, each entry opens one page. */
private enum class Page(val title: Int) {
    Main(R.string.app_name),
    Accounts(R.string.tab_accounts),
    Appearance(R.string.appearance),
    Notes(R.string.section_notes),
    ManageNotes(R.string.manage_notes),
    Checklists(R.string.list_section),
    ButtonBar(R.string.section_button_bar),
    AppView(R.string.app_view),
    Backup(R.string.tab_backup),
    Sync(R.string.sync_section),
}

/**
 * The settings, in three levels: global ones (accounts, sync, theme, backup), the app view, and the
 * widgets. Opened for a widget (when adding it, from its ⚙ button, from the launcher's
 * "reconfigure" action) it leads with that widget's settings; opened from the app it lists the
 * global and app settings and the placed widgets.
 */
class SettingsActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    companion object {
        /** Opens the settings on a given page; "accounts" shows the accounts to sign in again. */
        const val EXTRA_PAGE = "page"
    }
    private var themeMode by mutableIntStateOf(THEME_SYSTEM)
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Backing out must cancel adding the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        themeMode = SettingsStore.loadGlobal(this).themeMode

        setContent { KeepTheme(themeMode) { SettingsScreen() } }
    }

    /** Whether this configuration screen was opened for a single-note widget. */
    private fun isSingleNoteWidget(): Boolean {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        return provider?.className == SingleNoteWidgetReceiver::class.java.name
    }

    private fun finishWithOk() {
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    @Composable
    private fun intervalLabel(minutes: Int): String = when {
        minutes == 0 -> stringResource(R.string.sync_manual)
        minutes < 60 -> stringResource(R.string.interval_min, minutes)
        else -> stringResource(R.string.interval_hour, minutes / 60)
    }

    @Composable
    private fun themeLabel(mode: Int): String = stringResource(
        when (mode) {
            THEME_LIGHT -> R.string.theme_light
            THEME_DARK -> R.string.theme_dark
            THEME_OLED -> R.string.theme_oled
            else -> R.string.theme_system
        }
    )

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun SettingsScreen() {
        val context = this
        val uriHandler = LocalUriHandler.current
        val isSingleNote = remember { isSingleNoteWidget() }
        val hasWidget = remember { appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID }
        val initialApp = remember { AppSettingsStore.load(context) }
        var appLayout by remember { mutableIntStateOf(initialApp.layout) }
        var appSort by remember { mutableIntStateOf(initialApp.sortMode) }
        var appPinnedSection by remember { mutableStateOf(initialApp.pinnedSection) }
        var appShowImages by remember { mutableStateOf(initialApp.showImages) }
        var appShowColors by remember { mutableStateOf(initialApp.showNoteColors) }
        var appPlainStyle by remember { mutableIntStateOf(initialApp.plainCardStyle) }
        var appPreviewLines by remember { mutableIntStateOf(initialApp.previewLines) }
        var appEditedAsDate by remember { mutableStateOf(initialApp.editedAsDate) }
        var appShowEdited by remember { mutableStateOf(initialApp.showEditedTime) }
        var appShowLabels by remember { mutableStateOf(initialApp.showLabels) }
        var appShowProgress by remember { mutableStateOf(initialApp.showProgress) }
        var appLockPrivate by remember { mutableStateOf(initialApp.lockPrivateNotes) }
        val scope = rememberCoroutineScope()
        val initialGlobal = remember { SettingsStore.loadGlobal(context) }
        val initialWidget = remember { SettingsStore.loadWidget(context, appWidgetId) }
        val accounts = remember { mutableStateListOf<String>().apply { addAll(CredentialStore.accounts(context)) } }

        var page by remember {
            mutableStateOf(if (accounts.isEmpty() || intent.getStringExtra(EXTRA_PAGE) == "accounts") Page.Accounts else Page.Main)
        }
        var email by remember { mutableStateOf("") }
        var token by remember { mutableStateOf("") }
        var widgetAccount by remember { mutableStateOf(initialWidget.account) }
        var backgroundAlpha by remember { mutableFloatStateOf(initialWidget.backgroundAlpha) }
        var textSize by remember { mutableIntStateOf(initialWidget.textSizeSp) }
        var textColor by remember { mutableIntStateOf(initialWidget.textColor) }
        var showNoteColors by remember { mutableStateOf(initialWidget.showNoteColors) }
        var noteAlpha by remember { mutableFloatStateOf(initialWidget.noteAlpha) }
        var plainStyle by remember { mutableIntStateOf(initialWidget.plainCardStyle) }
        var headerSize by remember { mutableIntStateOf(initialWidget.headerSizeSp) }
        var buttonSides by remember { mutableStateOf(initialWidget.buttonSides) }
        var buttonOrder by remember { mutableStateOf(initialWidget.buttonOrder) }
        var buttonSlots by remember { mutableStateOf(initialWidget.buttonSlots) }
        var checkable by remember { mutableStateOf(initialWidget.checkable) }
        var checkedToEnd by remember { mutableStateOf(initialWidget.checkedToEnd) }
        var collapseChecked by remember { mutableStateOf(initialWidget.collapseChecked) }
        var listSpacingDp by remember { mutableIntStateOf(initialWidget.listSpacingDp) }
        var sortMode by remember { mutableIntStateOf(initialWidget.sortMode) }
        var showImages by remember { mutableStateOf(initialWidget.showImages) }
        var noteId by remember { mutableStateOf(initialWidget.noteId) }
        var hiddenNotes by remember { mutableStateOf(initialWidget.hiddenNotes) }
        var pinBottom by remember { mutableStateOf(initialWidget.pinBottom) }
        var manualOrder by remember { mutableStateOf(initialWidget.manualOrder) }
        var checkboxStyle by remember { mutableIntStateOf(initialWidget.checkboxStyle) }
        var checkboxSizeDp by remember { mutableIntStateOf(initialWidget.checkboxSizeDp) }
        var tapWholeRow by remember { mutableStateOf(initialWidget.tapWholeRow) }
        var deleteChecked by remember { mutableStateOf(initialWidget.deleteChecked) }
        var maxLines by remember { mutableIntStateOf(initialWidget.maxLines) }
        var textColorAuto by remember { mutableStateOf(initialWidget.textColorAuto) }
        var showEditedTime by remember { mutableStateOf(initialWidget.showEditedTime) }
        var showLabels by remember { mutableStateOf(initialWidget.showLabels) }
        var labelFilter by remember { mutableStateOf(initialWidget.labelFilter) }
        var showProgress by remember { mutableStateOf(initialWidget.showProgress) }
        var settingsQuery by remember { mutableStateOf("") }
        // A search hit for a popup setting opens that popup once the main list is shown again.
        var pendingPopup by remember { mutableStateOf<String?>(null) }
        var interval by remember { mutableIntStateOf(initialGlobal.syncIntervalMin) }
        var syncOnUnlock by remember { mutableStateOf(initialGlobal.syncOnUnlock) }
        var syncOnOpen by remember { mutableStateOf(initialGlobal.syncOnOpen) }
        var minGapSec by remember { mutableIntStateOf(initialGlobal.minGapSec) }
        var backgroundSync by remember { mutableStateOf(initialGlobal.backgroundSync) }
        var backgroundInterval by remember { mutableIntStateOf(initialGlobal.backgroundIntervalMin) }
        var wifiOnly by remember { mutableStateOf(initialGlobal.wifiOnly) }
        var demoMode by remember { mutableStateOf(initialGlobal.demoMode) }
        var language by remember { mutableStateOf(initialGlobal.language) }
        var showColorPicker by remember { mutableStateOf(false) }
        var showTokenForm by remember { mutableStateOf(false) }
        var mdEnabled by remember { mutableStateOf(MarkdownSync.isEnabled(context)) }
        var mdFolder by remember { mutableStateOf(MarkdownSync.treeUri(context)?.let { MarkdownSync.folderLabel(it) }) }
        var confirmRemoveLocal by remember { mutableStateOf(false) }
        var confirmSettingsButtonOff by remember { mutableStateOf(false) }
        var confirmLayoutReset by remember { mutableStateOf(false) }
        var layoutResetShown by remember { mutableStateOf(false) }
        // After a reset the button reads "restored" and stays grayed out for a moment.
        LaunchedEffect(layoutResetShown) {
            if (layoutResetShown) {
                delay(3000)
                layoutResetShown = false
            }
        }
        var status by remember { mutableStateOf("") }

        val credentialsRequired = stringResource(R.string.credentials_required)
        val exportDone = stringResource(R.string.export_done)
        val importDone = stringResource(R.string.import_done)
        val layoutSaved = stringResource(R.string.layout_saved)
        val layoutLoaded = stringResource(R.string.layout_loaded)
        val layoutFailed = stringResource(R.string.layout_failed)
        val importFailed = stringResource(R.string.import_failed, "%s")

        BackHandler(enabled = page != Page.Main || settingsQuery.isNotEmpty()) {
            if (settingsQuery.isNotEmpty()) settingsQuery = "" else page = Page.Main
        }

        // Without a widget there are no widget settings to find.
        val entries = remember(isSingleNote, hasWidget) {
            settingEntries(isSingleNote).filter {
                // A widget's settings only list the widget's own pages (and the accounts it can use).
                if (hasWidget) it.page in WIDGET_PAGES || it.page == Page.Accounts || it.popup == "note"
                else it.page !in WIDGET_PAGES && it.popup != "note"
            }
        }

        fun currentWidget() = WidgetSettings(
            backgroundAlpha = backgroundAlpha,
            textColor = textColor,
            textSizeSp = textSize,
            showNoteColors = showNoteColors,
            noteAlpha = noteAlpha,
            plainCardStyle = plainStyle,
            account = CredentialStore.resolve(context, widgetAccount).orEmpty(),
            headerSizeSp = headerSize,
            buttonSides = buttonSides,
            buttonOrder = buttonOrder,
            buttonSlots = buttonSlots,
            checkable = checkable,
            checkedToEnd = checkedToEnd,
            collapseChecked = collapseChecked,
            listSpacingDp = listSpacingDp,
            sortMode = sortMode,
            showImages = showImages,
            noteId = noteId,
            hiddenNotes = hiddenNotes,
            pinBottom = pinBottom,
            manualOrder = manualOrder,
            checkboxStyle = checkboxStyle,
            checkboxSizeDp = checkboxSizeDp,
            tapWholeRow = tapWholeRow,
            deleteChecked = deleteChecked,
            maxLines = maxLines,
            textColorAuto = textColorAuto,
            showEditedTime = showEditedTime,
            showLabels = showLabels,
            labelFilter = labelFilter,
            showProgress = showProgress,
        )

        /** Moves a note in the manual order; the whole displayed order becomes the manual order. */
        fun moveNote(ordered: List<KeepNote>, index: Int, delta: Int) {
            val ids = ordered.map { it.id }.toMutableList()
            val target = index + delta
            if (target !in ids.indices) return
            java.util.Collections.swap(ids, index, target)
            manualOrder = ids
            // Moving a note makes the order manual; the shown order becomes the starting point.
            sortMode = SORT_MANUAL
        }

        fun currentGlobal() = GlobalSettings(
            themeMode = themeMode, syncIntervalMin = interval, syncOnUnlock = syncOnUnlock, syncOnOpen = syncOnOpen,
            minGapSec = minGapSec, backgroundSync = backgroundSync, backgroundIntervalMin = backgroundInterval, wifiOnly = wifiOnly,
            demoMode = demoMode, language = language,
        )

        /** Saves the language at once and reopens this screen in it. */
        fun changeLanguage(tag: String) {
            language = tag
            SettingsStore.saveGlobal(context, currentGlobal())
            LocaleHelper.applyToApp(context)
            scope.launch { WidgetRefresh.all(context) }
            recreate()
        }

        fun currentApp() = AppSettings(
            appLayout, appSort, appPinnedSection, appShowImages, appShowColors, appPlainStyle, appPreviewLines, appShowEdited, appEditedAsDate, appShowLabels, appShowProgress, appLockPrivate,
        )

        fun refreshAccounts() {
            accounts.clear()
            accounts.addAll(CredentialStore.accounts(context))
        }

        fun addAccount() {
            val address = email.trim()
            val newToken = token.trim().ifEmpty { null }
            if (address.isEmpty() || (newToken == null && CredentialStore.token(context, address) == null)) {
                status = credentialsRequired
                return
            }
            CredentialStore.put(context, address, newToken)
            refreshAccounts()
            if (widgetAccount.isBlank()) widgetAccount = address
            email = ""
            token = ""
            status = ""
            SyncWorker.syncNow(context)
        }

        val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // A finished Google sign-in has stored the account itself.
            refreshAccounts()
            if (widgetAccount.isBlank()) widgetAccount = accounts.firstOrNull().orEmpty()
        }

        val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
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

        fun addLocalAccount() {
            CredentialStore.put(context, LOCAL_ACCOUNT, null)
            refreshAccounts()
            if (widgetAccount.isBlank()) widgetAccount = LOCAL_ACCOUNT
            status = ""
        }

        // A new widget counts as configured as soon as the screen is open; every change is saved right away.
        LaunchedEffect(Unit) {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
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
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                SettingsStore.saveWidget(context, appWidgetId, widget)
            }
            SyncWorker.schedule(context)
            ScreenSync.reschedule(context)
            WidgetRefresh.all(context)
        }

        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val json = Backup.export(currentGlobal(), currentWidget())
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                status = exportDone
            } catch (e: Exception) {
                status = importFailed.format(e.message ?: e.javaClass.simpleName)
            }
        }
        /** Puts the button bar of [w] into the editor state. */
        fun applyLayout(w: WidgetSettings) {
            headerSize = w.headerSizeSp
            buttonSides = w.buttonSides
            buttonOrder = w.buttonOrder
            buttonSlots = w.buttonSlots
        }
        val layoutSaveLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            status = try {
                contentResolver.openOutputStream(uri)?.use { it.write(Backup.exportLayout(currentWidget()).toByteArray()) }
                layoutSaved
            } catch (e: Exception) {
                layoutFailed
            }
        }
        val layoutLoadLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            status = try {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Cannot read file")
                applyLayout(Backup.parseLayout(text, currentWidget()))
                layoutLoaded
            } catch (e: Exception) {
                layoutFailed
            }
        }
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: error("Cannot read file")
                val imported = Backup.parse(text)
                themeMode = imported.global.themeMode
                interval = imported.global.syncIntervalMin
                syncOnUnlock = imported.global.syncOnUnlock
                syncOnOpen = imported.global.syncOnOpen
                minGapSec = imported.global.minGapSec
                backgroundSync = imported.global.backgroundSync
                backgroundInterval = imported.global.backgroundIntervalMin
                wifiOnly = imported.global.wifiOnly
                with(imported.widget) {
                    backgroundAlpha = this.backgroundAlpha
                    textColor = this.textColor
                    textSize = textSizeSp
                    showNoteColors = this.showNoteColors
                    noteAlpha = this.noteAlpha
                    plainStyle = plainCardStyle
                    headerSize = headerSizeSp
                    buttonSides = this.buttonSides
                    buttonOrder = this.buttonOrder
                    buttonSlots = this.buttonSlots
                    checkable = this.checkable
                    checkedToEnd = this.checkedToEnd
                    collapseChecked = this.collapseChecked
                    listSpacingDp = this.listSpacingDp
                    sortMode = this.sortMode
                    showImages = this.showImages
                    checkboxStyle = this.checkboxStyle
                    checkboxSizeDp = this.checkboxSizeDp
                    tapWholeRow = this.tapWholeRow
                    deleteChecked = this.deleteChecked
                    maxLines = this.maxLines
                    textColorAuto = this.textColorAuto
                    showEditedTime = this.showEditedTime
                    showLabels = this.showLabels
                    labelFilter = this.labelFilter
                    showProgress = this.showProgress
                }
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
                    Page.Main -> {
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

                    Page.AppView -> {
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

                    Page.Accounts -> {
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

                    Page.Appearance -> {
                        Text(stringResource(R.string.bg_opacity, (backgroundAlpha * 100).toInt()))
                        Slider(value = backgroundAlpha, onValueChange = { backgroundAlpha = it })

                        Text(stringResource(R.string.text_size, textSize))
                        Slider(
                            value = textSize.toFloat(), onValueChange = { textSize = it.toInt() },
                            valueRange = 10f..24f,
                        )

                        SwitchRow(R.string.text_color_auto, textColorAuto) { textColorAuto = it }
                        if (!textColorAuto) {
                        Text(stringResource(R.string.text_color))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TEXT_COLORS.forEach { color ->
                                Box(
                                    Modifier.size(if (color == textColor) 44.dp else 36.dp)
                                        .clip(CircleShape).background(Color(color))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                        .clickable { textColor = color },
                                )
                            }
                            // Opens the color picker; shows the current color when it is a custom one.
                            val isCustom = textColor !in TEXT_COLORS
                            Box(
                                Modifier.size(if (isCustom) 44.dp else 36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCustom) Brush.linearGradient(listOf(Color(textColor), Color(textColor)))
                                        else Brush.sweepGradient(
                                            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                                        )
                                    )
                                    .border(
                                        if (isCustom) 3.dp else 1.dp,
                                        if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        CircleShape,
                                    )
                                    .clickable { showColorPicker = true },
                                contentAlignment = Alignment.Center,
                            ) { if (!isCustom) Text("＋", color = Color.White) }
                        }

                        }

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.show_note_colors), Modifier.weight(1f))
                            Switch(checked = showNoteColors, onCheckedChange = { showNoteColors = it })
                        }
                        Text(stringResource(R.string.note_opacity, (noteAlpha * 100).toInt()))
                        Slider(value = noteAlpha, onValueChange = { noteAlpha = it })

                        Text(stringResource(R.string.plain_style))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                PLAIN_TRANSPARENT to R.string.plain_none,
                                PLAIN_GRAY to R.string.plain_gray,
                                PLAIN_BORDER to R.string.plain_border,
                            ).forEach { (style, label) ->
                                FilterChip(
                                    selected = plainStyle == style,
                                    onClick = { plainStyle = style },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                        }
                    }

                    Page.Notes -> {
                        if (!isSingleNote) {
                            Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.titleMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    SORT_KEEP to R.string.sort_keep,
                                    SORT_EDITED to R.string.sort_edited,
                                    SORT_CREATED to R.string.sort_created,
                                    SORT_TITLE to R.string.sort_title,
                                    SORT_MANUAL to R.string.sort_manual,
                                ).forEach { (mode, label) ->
                                    FilterChip(
                                        selected = sortMode == mode,
                                        onClick = { sortMode = mode },
                                        label = { Text(stringResource(label)) },
                                    )
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        }
                        SwitchRow(R.string.show_images, showImages) { showImages = it }
                        SwitchRow(R.string.show_edited_time, showEditedTime) { showEditedTime = it }
                        SwitchRow(R.string.show_labels, showLabels) { showLabels = it }
                        if (!isSingleNote) {
                            // Only the notes with one label: the labels of the account this widget shows.
                            val labelChoices = remember(widgetAccount, accounts.size) {
                                NotesCache.labels(context, CredentialStore.resolve(context, widgetAccount))
                            }
                            PopupRow(
                                pendingPopup == "labelfilter", { pendingPopup = null },
                                stringResource(R.string.label_filter),
                                labelFilter.ifEmpty { stringResource(R.string.all_notes) },
                                listOf(
                                    ((if (labelFilter.isEmpty()) "✓ " else "") + stringResource(R.string.all_notes)) to { labelFilter = "" },
                                ) + labelChoices.map { name ->
                                    ((if (name == labelFilter) "✓ " else "") + name) to { labelFilter = name }
                                },
                            )
                        }
                        if (!isSingleNote) SwitchRow(R.string.show_progress, showProgress) { showProgress = it }
                        if (!isSingleNote) {
                            // 0 means unlimited. Longer notes are cut off in the list; the editor shows everything.
                            Text(
                                if (maxLines == 0) stringResource(R.string.max_lines_unlimited)
                                else stringResource(R.string.max_lines, maxLines),
                            )
                            Slider(
                                value = maxLines.toFloat(), onValueChange = { maxLines = it.toInt() },
                                valueRange = 0f..30f, steps = 29,
                            )
                        }
                    }

                    Page.ManageNotes -> {
                        Text(
                            stringResource(R.string.manage_notes_help),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val allNotes = remember(widgetAccount, accounts.size) {
                            NotesCache.read(context, CredentialStore.resolve(context, widgetAccount))
                        }
                        val untitled = stringResource(R.string.untitled_note)
                        if (allNotes.isEmpty()) Text(stringResource(R.string.no_notes_to_select))
                        val ordered = sortNotes(allNotes, currentWidget(), includeHidden = true)
                        ordered.forEachIndexed { index, note ->
                            // The note's name has a line of its own; below it the switch and the arrows share one line.
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    noteLabel(note).ifBlank { untitled },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = note.id !in hiddenNotes,
                                        onCheckedChange = { shown ->
                                            hiddenNotes = if (shown) hiddenNotes - note.id else hiddenNotes + note.id
                                        },
                                    )
                                    Spacer(Modifier.weight(1f))
                                    FilledTonalIconButton(
                                        onClick = { moveNote(ordered, index, -1) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(52.dp),
                                    ) { Icon(painterResource(R.drawable.ic_arrow_up), null, Modifier.size(30.dp)) }
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalIconButton(
                                        onClick = { moveNote(ordered, index, 1) },
                                        enabled = index < ordered.lastIndex,
                                        modifier = Modifier.size(52.dp),
                                    ) { Icon(painterResource(R.drawable.ic_arrow_down), null, Modifier.size(30.dp)) }
                                }
                            }
                            HorizontalDivider()
                        }
                    }

                    Page.Checklists -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.check_in_widget), Modifier.weight(1f))
                            Switch(checked = checkable, onCheckedChange = { checkable = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.checked_to_end), Modifier.weight(1f))
                            Switch(checked = checkedToEnd, onCheckedChange = { checkedToEnd = it })
                        }
                        if (checkedToEnd) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.collapse_checked), Modifier.weight(1f))
                                Switch(checked = collapseChecked, onCheckedChange = { collapseChecked = it })
                            }
                        }
                        if (checkable) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.delete_checked), Modifier.weight(1f))
                                Switch(checked = deleteChecked, onCheckedChange = { deleteChecked = it })
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("${stringResource(R.string.list_spacing)}: $listSpacingDp dp")
                        Slider(
                            value = listSpacingDp.toFloat(), onValueChange = { listSpacingDp = it.toInt() },
                            valueRange = 0f..24f,
                        )

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(stringResource(R.string.checkbox_style), style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                CB_SQUARE to R.string.cb_style_square,
                                CB_ROUNDED to R.string.cb_style_rounded,
                                CB_CIRCLE to R.string.cb_style_circle,
                                CB_CLASSIC to R.string.cb_style_classic,
                            ).forEach { (style, label) ->
                                FilterChip(
                                    selected = checkboxStyle == style,
                                    onClick = { checkboxStyle = style },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                        }
                        Text(stringResource(R.string.checkbox_size, checkboxSizeDp))
                        Slider(
                            value = checkboxSizeDp.toFloat(), onValueChange = { checkboxSizeDp = it.toInt() },
                            valueRange = 14f..56f,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.tap_whole_row), Modifier.weight(1f))
                            Switch(checked = tapWholeRow, onCheckedChange = { tapWholeRow = it })
                        }
                        // Live preview of both states with the chosen style, size and spacing.
                        listOf(false, true).forEach { checked ->
                            Row(
                                Modifier.padding(vertical = listSpacingDp.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CheckboxPreview(checkboxStyle, checked, checkboxSizeDp)
                                Text(
                                    stringResource(R.string.item_hint),
                                    Modifier.padding(start = 12.dp),
                                    fontSize = textSize.sp,
                                )
                            }
                        }
                    }

                    Page.ButtonBar -> {
                        Text(
                            stringResource(R.string.button_layout_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // The buttons this widget has, with the side each one sits on (null = not shown).
                        val unordered = buildList {
                            fun add(id: String, icon: Int, label: Int) =
                                add(LayoutButton(id, icon, label, buttonSides[id], buttonSlots[id] ?: ALIGN_CENTER))
                            add(BTN_REFRESH, R.drawable.ic_refresh, R.string.btn_refresh)
                            if (isSingleNote) add(BTN_ADD_ITEM, R.drawable.ic_add, R.string.btn_add_item)
                            if (!isSingleNote) {
                                add(BTN_NEW_NOTE, R.drawable.ic_add, R.string.btn_new_note)
                                add(BTN_NEW_LIST, R.drawable.ic_checklist, R.string.btn_new_list)
                                add(BTN_SORT, R.drawable.ic_sort, R.string.btn_sort)
                                add(BTN_SEARCH, R.drawable.ic_search, R.string.btn_search)
                            }
                            add(BTN_APP, R.drawable.ic_apps, R.string.btn_app)
                            add(BTN_KEEP, R.drawable.ic_open_new, R.string.btn_keep)
                            add(BTN_SETTINGS, R.drawable.ic_settings, R.string.btn_settings)
                        }
                        val order = (buttonOrder + ALL_BUTTONS).distinct()
                        val layout = unordered.sortedBy { order.indexOf(it.id) }
                        ButtonLayoutEditor(layout) { id, side, slot, beforeId ->
                            if (side != null) {
                                buttonSides = buttonSides + (id to side)
                                buttonSlots = buttonSlots + (id to slot)
                                val rest = order - id
                                val at = beforeId?.let { rest.indexOf(it) }?.takeIf { it >= 0 } ?: rest.size
                                buttonOrder = rest.take(at) + id + rest.drop(at)
                            } else if (id == BTN_SETTINGS) {
                                // Without this button the settings are harder to reach, so a dialog asks first.
                                confirmSettingsButtonOff = true
                            } else {
                                buttonSides = buttonSides - id
                            }
                        }
                        Text(stringResource(R.string.header_size, headerSize))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Slider(
                                value = headerSize.toFloat(), onValueChange = { headerSize = it.toInt() },
                                valueRange = 16f..40f,
                                modifier = Modifier.weight(1f),
                            )
                            // The real icon in its real size; the outline is the touch area (8 dp of padding on each side).
                            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier
                                        .size((headerSize + 16).dp)
                                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(headerSize.dp))
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { layoutSaveLauncher.launch("gkeepdroid-layout.json") }) {
                                Text(stringResource(R.string.layout_save))
                            }
                            OutlinedButton(onClick = { layoutLoadLauncher.launch(arrayOf("*/*")) }) {
                                Text(stringResource(R.string.layout_load))
                            }
                            OutlinedButton(enabled = !layoutResetShown, onClick = { confirmLayoutReset = true }) {
                                Text(stringResource(if (layoutResetShown) R.string.layout_reset_done else R.string.layout_reset))
                            }
                        }
                    }

                    Page.Sync -> {
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

                    Page.Backup -> {
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

/** A whole number typed in, such as minutes between two syncs: the name on the left, a small field on the right. */
@Composable
private fun NumberRow(label: Int, value: Int, unit: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { typed -> onChange(typed.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0) },
            suffix = { Text(unit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
    }
}

/** A small explanation under a setting. */
@Composable
private fun Hint(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One entry of the main settings list: title, optional summary and a chevron. */
@Composable
private fun MenuRow(title: String, summary: String?, chevron: String = "›", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(chevron, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The checkbox as the widget draws it, in the settings colors. */
@Composable
private fun CheckboxPreview(style: Int, checked: Boolean, sizeDp: Int) {
    val resource = when (style) {
        CB_SQUARE -> if (checked) R.drawable.cb_square_on else R.drawable.cb_square_off
        CB_ROUNDED -> if (checked) R.drawable.cb_rounded_on else R.drawable.cb_rounded_off
        CB_CIRCLE -> if (checked) R.drawable.cb_circle_on else R.drawable.cb_circle_off
        else -> null
    }
    if (resource != null) {
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.size(sizeDp.dp),
        )
    } else {
        Text(if (checked) "☑" else "☐", fontSize = (sizeDp * 0.85f).sp)
    }
}

/** A setting with one choice: tapping the row opens a popup menu instead of a page. */
@Composable
private fun PopupRow(
    open: Boolean,
    onOpened: () -> Unit,
    title: String,
    summary: String?,
    options: List<Pair<String, () -> Unit>>,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            expanded = true
            onOpened()
        }
    }
    Box {
        MenuRow(title, summary, chevron = "▾") { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; action() })
            }
        }
    }
}

/** One searchable setting: its label, the page it lives on, and the popup it opens (if any). */
private data class SettingEntry(val label: Int, val page: Page, val popup: String? = null) {
    val pageTitle: Int get() = if (popup != null) label else page.title
}

/** Strings with a value ("Text size: 14 sp") are searched and shown by the part before the colon. */
private fun android.content.Context.settingLabel(res: Int): String =
    getString(res, 0).substringBefore(':').substringBefore('：').trim()

private fun settingEntries(isSingleNote: Boolean): List<SettingEntry> = buildList {
    add(SettingEntry(R.string.tab_accounts, Page.Accounts))
    add(SettingEntry(R.string.app_view, Page.AppView))
    add(SettingEntry(R.string.layout_label, Page.AppView))
    add(SettingEntry(R.string.pinned_section_switch, Page.AppView))
    add(SettingEntry(R.string.preview_lines, Page.AppView))
    add(SettingEntry(R.string.add_account, Page.Accounts))
    add(SettingEntry(R.string.account_for_widget, Page.Accounts))
    add(SettingEntry(R.string.token_help, Page.Accounts))
    if (isSingleNote) add(SettingEntry(R.string.select_note, Page.Main, popup = "note"))

    add(SettingEntry(R.string.appearance, Page.Appearance))
    listOf(
        R.string.bg_opacity, R.string.text_size, R.string.text_color, R.string.text_color_auto, R.string.show_note_colors,
        R.string.note_opacity, R.string.plain_style,
    ).forEach { add(SettingEntry(it, Page.Appearance)) }

    add(SettingEntry(R.string.section_notes, Page.Notes))
    add(SettingEntry(R.string.show_images, Page.Notes))
    add(SettingEntry(R.string.show_edited_time, Page.Notes))
    add(SettingEntry(R.string.show_labels, Page.Notes))
    if (!isSingleNote) add(SettingEntry(R.string.label_filter, Page.Notes, popup = "labelfilter"))
    add(SettingEntry(R.string.show_progress, Page.Notes))
    add(SettingEntry(R.string.lock_private, Page.AppView))
    if (!isSingleNote) add(SettingEntry(R.string.max_lines, Page.Notes))
    if (isSingleNote) add(SettingEntry(R.string.show_add_item_button, Page.ButtonBar))
    if (!isSingleNote) {
        listOf(
            R.string.sort_by, R.string.sort_keep, R.string.sort_edited,
            R.string.sort_created, R.string.sort_title, R.string.sort_manual,
        ).forEach { add(SettingEntry(it, Page.Notes)) }
        add(SettingEntry(R.string.manage_notes, Page.ManageNotes))
    }

    add(SettingEntry(R.string.list_section, Page.Checklists))
    listOf(
        R.string.check_in_widget, R.string.checked_to_end, R.string.collapse_checked, R.string.delete_checked,
        R.string.list_spacing, R.string.checkbox_style, R.string.checkbox_size, R.string.tap_whole_row,
        R.string.cb_style_square, R.string.cb_style_rounded, R.string.cb_style_circle, R.string.cb_style_classic,
    ).forEach { add(SettingEntry(it, Page.Checklists)) }

    add(SettingEntry(R.string.section_button_bar, Page.ButtonBar))
    listOf(R.string.header_position, R.string.header_size, R.string.show_keep_button,
        R.string.show_refresh_button, R.string.show_settings_button,
    ).forEach {
        add(SettingEntry(it, Page.ButtonBar))
    }
    if (!isSingleNote) {
        listOf(R.string.show_new_note_button, R.string.show_new_list_button, R.string.show_sort_button, R.string.show_search_bar)
            .forEach { add(SettingEntry(it, Page.ButtonBar)) }
    }

    add(SettingEntry(R.string.sync_section, Page.Sync))
    add(SettingEntry(R.string.sync_group_display, Page.Sync))
    add(SettingEntry(R.string.sync_every, Page.Sync))
    add(SettingEntry(R.string.sync_on_open, Page.Sync))
    add(SettingEntry(R.string.sync_on_unlock, Page.Sync))
    add(SettingEntry(R.string.sync_min_gap, Page.Sync))
    add(SettingEntry(R.string.sync_background, Page.Sync))
    add(SettingEntry(R.string.sync_wifi_only, Page.Sync))
    add(SettingEntry(R.string.theme_section, Page.Main, popup = "theme"))
    listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark, R.string.theme_oled).forEach {
        add(SettingEntry(it, Page.Main, popup = "theme"))
    }

    add(SettingEntry(R.string.md_sync_title, Page.Backup))
    add(SettingEntry(R.string.md_sync_enable, Page.Backup))
    add(SettingEntry(R.string.md_sync_choose, Page.Backup))
    add(SettingEntry(R.string.md_sync_now, Page.Backup))
    add(SettingEntry(R.string.demo_mode, Page.Main))
    add(SettingEntry(R.string.language, Page.Main, popup = "language"))
    add(SettingEntry(R.string.tab_backup, Page.Backup))
    add(SettingEntry(R.string.export_settings, Page.Backup))
    add(SettingEntry(R.string.import_settings, Page.Backup))
}

/** A labeled on/off switch. */
@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** The pages that only exist for a widget. */
private val WIDGET_PAGES = setOf(Page.Appearance, Page.Notes, Page.ManageNotes, Page.Checklists, Page.ButtonBar)

/** The heading of a group of settings in the main list. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
