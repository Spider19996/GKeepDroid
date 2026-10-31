package com.gkeepdroid

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.UriHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Everything the settings screen shows and changes: one value per setting, the actions on them,
 * and the launchers of the file and sign-in dialogs. The pages are functions on this state.
 */
internal class SettingsState(val context: SettingsActivity) {
    // Set while the screen is composed.
    lateinit var uriHandler: UriHandler
    lateinit var scope: CoroutineScope
    lateinit var loginLauncher: ActivityResultLauncher<Intent>
    lateinit var folderLauncher: ActivityResultLauncher<Uri?>
    lateinit var exportLauncher: ActivityResultLauncher<String>
    lateinit var importLauncher: ActivityResultLauncher<Array<String>>
    lateinit var layoutSaveLauncher: ActivityResultLauncher<String>
    lateinit var layoutLoadLauncher: ActivityResultLauncher<Array<String>>
    var credentialsRequired = ""

    var themeMode: Int
        get() = context.themeMode
        set(value) { context.themeMode = value }

    fun finish() = context.finish()
    fun finishWithOk() = context.finishWithOk()
    fun startActivity(intent: Intent) = context.startActivity(intent)

    val isSingleNote = context.isSingleNoteWidget()
    val hasWidget = context.appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
    val initialApp = AppSettingsStore.load(context)
    var appLayout by mutableIntStateOf(initialApp.layout)
    var appSort by mutableIntStateOf(initialApp.sortMode)
    var appPinnedSection by mutableStateOf(initialApp.pinnedSection)
    var appShowImages by mutableStateOf(initialApp.showImages)
    var appShowColors by mutableStateOf(initialApp.showNoteColors)
    var appPlainStyle by mutableIntStateOf(initialApp.plainCardStyle)
    var appPreviewLines by mutableIntStateOf(initialApp.previewLines)
    var appEditedAsDate by mutableStateOf(initialApp.editedAsDate)
    var appShowEdited by mutableStateOf(initialApp.showEditedTime)
    var appShowLabels by mutableStateOf(initialApp.showLabels)
    var appShowProgress by mutableStateOf(initialApp.showProgress)
    var appLockPrivate by mutableStateOf(initialApp.lockPrivateNotes)
    val initialGlobal = SettingsStore.loadGlobal(context)
    val initialWidget = SettingsStore.loadWidget(context, context.appWidgetId)
    val accounts = mutableStateListOf<String>().apply { addAll(CredentialStore.accounts(context)) }

    var page by mutableStateOf(if (accounts.isEmpty() || context.intent.getStringExtra(SettingsActivity.EXTRA_PAGE) == "accounts") Page.Accounts else Page.Main)
    var email by mutableStateOf("")
    var token by mutableStateOf("")
    var widgetAccount by mutableStateOf(initialWidget.account)
    var backgroundAlpha by mutableFloatStateOf(initialWidget.backgroundAlpha)
    var textSize by mutableIntStateOf(initialWidget.textSizeSp)
    var textColor by mutableIntStateOf(initialWidget.textColor)
    var showNoteColors by mutableStateOf(initialWidget.showNoteColors)
    var noteAlpha by mutableFloatStateOf(initialWidget.noteAlpha)
    var plainStyle by mutableIntStateOf(initialWidget.plainCardStyle)
    var headerSize by mutableIntStateOf(initialWidget.headerSizeSp)
    var buttonSides by mutableStateOf(initialWidget.buttonSides)
    var buttonOrder by mutableStateOf(initialWidget.buttonOrder)
    var buttonSlots by mutableStateOf(initialWidget.buttonSlots)
    var checkable by mutableStateOf(initialWidget.checkable)
    var checkedToEnd by mutableStateOf(initialWidget.checkedToEnd)
    var collapseChecked by mutableStateOf(initialWidget.collapseChecked)
    var listSpacingDp by mutableIntStateOf(initialWidget.listSpacingDp)
    var sortMode by mutableIntStateOf(initialWidget.sortMode)
    var showImages by mutableStateOf(initialWidget.showImages)
    var noteId by mutableStateOf(initialWidget.noteId)
    var hiddenNotes by mutableStateOf(initialWidget.hiddenNotes)
    var pinBottom by mutableStateOf(initialWidget.pinBottom)
    var manualOrder by mutableStateOf(initialWidget.manualOrder)
    var checkboxStyle by mutableIntStateOf(initialWidget.checkboxStyle)
    var checkboxSizeDp by mutableIntStateOf(initialWidget.checkboxSizeDp)
    var tapWholeRow by mutableStateOf(initialWidget.tapWholeRow)
    var deleteChecked by mutableStateOf(initialWidget.deleteChecked)
    var maxLines by mutableIntStateOf(initialWidget.maxLines)
    var textColorAuto by mutableStateOf(initialWidget.textColorAuto)
    var showEditedTime by mutableStateOf(initialWidget.showEditedTime)
    var showLabels by mutableStateOf(initialWidget.showLabels)
    var labelFilter by mutableStateOf(initialWidget.labelFilter)
    var showProgress by mutableStateOf(initialWidget.showProgress)
    var settingsQuery by mutableStateOf("")
    // A search hit for a popup setting opens that popup once the main list is shown again.
    var pendingPopup by mutableStateOf<String?>(null)
    var interval by mutableIntStateOf(initialGlobal.syncIntervalMin)
    var syncOnUnlock by mutableStateOf(initialGlobal.syncOnUnlock)
    var syncOnOpen by mutableStateOf(initialGlobal.syncOnOpen)
    var minGapSec by mutableIntStateOf(initialGlobal.minGapSec)
    var backgroundSync by mutableStateOf(initialGlobal.backgroundSync)
    var backgroundInterval by mutableIntStateOf(initialGlobal.backgroundIntervalMin)
    var wifiOnly by mutableStateOf(initialGlobal.wifiOnly)
    var demoMode by mutableStateOf(initialGlobal.demoMode)
    var language by mutableStateOf(initialGlobal.language)
    var reminderSound by mutableStateOf(initialGlobal.reminderSound)
    var reminderVibrate by mutableStateOf(initialGlobal.reminderVibrate)
    var reminderOverride by mutableStateOf(initialGlobal.reminderOverride)
    var showColorPicker by mutableStateOf(false)
    var showTokenForm by mutableStateOf(false)
    var mdEnabled by mutableStateOf(MarkdownSync.isEnabled(context))
    var mdFolder by mutableStateOf(MarkdownSync.treeUri(context)?.let { MarkdownSync.folderLabel(it) })
    var confirmRemoveLocal by mutableStateOf(false)
    var confirmSettingsButtonOff by mutableStateOf(false)
    var confirmLayoutReset by mutableStateOf(false)
    // After adding an account: a note that the first sync takes a while.
    var showFirstSync by mutableStateOf(false)
    var layoutResetShown by mutableStateOf(false)
    var status by mutableStateOf("")

    // Without a widget there are no widget settings to find.
    val entries = settingEntries(isSingleNote).filter {
        // A widget's settings only list the widget's own pages (and the accounts it can use).
        if (hasWidget) it.page in WIDGET_PAGES || it.page == Page.Accounts || it.popup == "note"
        else it.page !in WIDGET_PAGES && it.popup != "note"
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
        reminderSound = reminderSound, reminderVibrate = reminderVibrate, reminderOverride = reminderOverride,
    )

    /** Saves the language at once and reopens this screen in it. */
    fun changeLanguage(tag: String) {
        language = tag
        SettingsStore.saveGlobal(context, currentGlobal())
        LocaleHelper.applyToApp(context)
        scope.launch { WidgetRefresh.all(context) }
        context.recreate()
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
        showFirstSync = true
        SyncWorker.syncNow(context)
    }

    fun addLocalAccount() {
        CredentialStore.put(context, LOCAL_ACCOUNT, null)
        refreshAccounts()
        if (widgetAccount.isBlank()) widgetAccount = LOCAL_ACCOUNT
        status = ""
    }

    /** Puts the button bar of [w] into the editor state. */
    fun applyLayout(w: WidgetSettings) {
        headerSize = w.headerSizeSp
        buttonSides = w.buttonSides
        buttonOrder = w.buttonOrder
        buttonSlots = w.buttonSlots
    }
}
