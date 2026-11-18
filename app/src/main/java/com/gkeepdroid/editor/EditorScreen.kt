package com.gkeepdroid

import android.appwidget.AppWidgetManager
import androidx.core.content.edit
import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.app.KeyguardManager
import android.os.Bundle
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.draggable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.key
import java.util.UUID
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun EditorScreen(
    activity: EditNoteActivity, account: String, existing: KeepNote?, startAsList: Boolean, offline: Boolean, pinWidgetId: Int?,
    remote: KeepNote?, onReload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val model = remember { EditorModel() }
    // With the edit lock on, a note opens for reading only until the lock is passed.
    remember { model.canEdit = AppLock.editAllowed(activity) }
    var canEdit by model::canEdit
    val unlock = AppLock.rememberUnlock(activity)
    fun toastUnlockFirst() = Toast.makeText(activity, R.string.edit_first_hint, Toast.LENGTH_SHORT).show()
    fun askEdit() {
        unlock(R.string.lock_prompt_edit) { ok ->
            if (ok) canEdit = true else if (existing == null) activity.finish()
        }
    }
    // A new note is all editing, so the lock is asked for at once.
    LaunchedEffect(Unit) { if (!canEdit && existing == null) askEdit() }
    // A new note can become a checklist while typing (see the offer under the text field).
    remember { model.isList = startAsList }
    var isList by model::isList
    // Text shared from another app arrives as a prefill for a new note.
    remember { model.title = existing?.title ?: activity.intent.getStringExtra(EditNoteActivity.EXTRA_PREFILL_TITLE).orEmpty() }
    var title by model::title
    remember { model.text = existing?.text ?: activity.intent.getStringExtra(EditNoteActivity.EXTRA_PREFILL_TEXT).orEmpty() }
    var text by model::text
    // The editor stays in the list of recent apps under the note's title, so it can be found again after leaving it.
    val untitled = stringResource(if (existing != null) R.string.app_name else if (isList) R.string.new_list else R.string.new_note)
    val firstLine = if (title.isBlank() && !isList) text.lineSequence().firstOrNull { it.isNotBlank() }?.take(40) else null
    LaunchedEffect(title, firstLine, untitled) {
        val label = title.ifBlank { firstLine ?: untitled }
        @Suppress("DEPRECATION")
        activity.setTaskDescription(
            if (android.os.Build.VERSION.SDK_INT >= 33) android.app.ActivityManager.TaskDescription.Builder().setLabel(label).build()
            else android.app.ActivityManager.TaskDescription(label),
        )
    }
    remember { model.textSel = androidx.compose.ui.text.TextRange(text.length) }
    var textSel by model::textSel
    // The keyboard's composing region; dropping it on every keystroke makes the word suggestions flicker.
    var textComp by model::textComp
    remember { model.color = existing?.color ?: NoteColors.DEFAULT }
    var color by model::color
    // The note's own look fills the screen behind the editor: a color, one of Keep's pictures or a photo of the user's own.
    // The color and the picture are set separately (the picture hides the color); CUSTOM_BACKGROUND stands for the photo.
    val originalGallery = remember { existing?.let { RichNotes.of(activity, account, it.id)?.background }.orEmpty() }
    val originalCustom = remember { existing != null && CustomBackgrounds.stamp(activity, account, existing.id) != 0L }
    remember { model.background = if (originalCustom) CUSTOM_BACKGROUND else originalGallery }
    var background by model::background
    val picked = activity.pickedBackground
    val showColors = remember { AppSettingsStore.load(activity).showNoteColors }
    LaunchedEffect(picked) {
        if (picked != null) background = CUSTOM_BACKGROUND
    }
    // A photo of one's own stays on this phone; that is said before it is chosen, every time until it is marked as understood.
    val hintPrefs = remember { activity.getSharedPreferences("hints", android.content.Context.MODE_PRIVATE) }
    var localHint by model::localHint
    if (localHint) {
        var never by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { localHint = false },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.own_photo_hint))
                    Row(
                        Modifier.fillMaxWidth().clickable { never = !never },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = never, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.dont_show_again))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (never) hintPrefs.edit { putBoolean("own_photo_local_dismissed", true) }
                    localHint = false
                    activity.pickBackground.launch("image/*")
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { localHint = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
    val darkTheme = LocalDarkTheme.current
    // This phone's own look of the note: a text color, and which version (light or dark) of Keep's picture it shows.
    val originalLook = remember { existing?.let { NoteLook.of(activity, account, it.id) } ?: NoteLook.Look(null, null) }
    remember { model.variantDark = originalLook.dark }
    var variantDark by model::variantDark
    remember { model.ownText = originalLook.textColor }
    var ownText by model::ownText
    val look = rememberEditorLook(activity, model, existing, account, showColors, picked, activity.screenTint, darkTheme)
    val darkVersion = look.darkVersion
    val picture = look.picture
    val tint = look.tint
    val shownPicture = look.shownPicture
    val tinted = look.tinted
    val darkLook = look.darkLook
    val accent = look.accent
    var labels by model::labels
    // A numbered checklist keeps "1. ", "2. " in the text of its items (so Keep shows them too); in the editor
    // the number is a label in front of the field and the field holds only the text.
    val startNumbered = existing != null && existing.isList && ListNumbering.isNumbered(existing.items)
    remember { model.numbered = startNumbered }
    var numbered by model::numbered
    var numberOfferDismissed by model::numberOfferDismissed
    val items = remember {
        model.items.apply {
            val start = existing?.items ?: if (isList) listOf(KeepItem(UUID.randomUUID().toString(), "", false)) else emptyList()
            addAll(if (startNumbered) ListNumbering.strip(start) else start)
        }
    }
    // The item that takes the focus once it is shown (one just made in a category).
    var focusItemId by model::focusItemId
    var addMenuOpen by model::addMenuOpen
    var newCategoryDialog by model::newCategoryDialog
    // What the name of a new category is for: its first item (null), or an item dropped onto "New category", or none
    // (the list is being given categories and the new one starts empty).
    var newCategoryFor by model::newCategoryFor
    var newCategoryEmpty by model::newCategoryEmpty
    // The items (one, or all of a category) that a swipe moves to another category; the dialog then offers all of them.
    var moveItems by model::moveItems
    // An earlier category held once: it turns red, held again it is forgotten.
    var armedForget by model::armedForget
    var historyVersion by model::historyVersion
    // The category whose name is being changed.
    var renameCategory by model::renameCategory
    // The visible part of the note, for scrolling along while an item is dragged near its edges.
    var editorViewport by model::editorViewport
    val allAccounts = remember { CredentialStore.accounts(activity) }
    remember { model.targetAccount = account }
    var targetAccount by model::targetAccount
    val wasHidden = existing != null && PrivateNotes.isPrivate(existing, account, PrivateNotes.load(activity))
    // Changed with "Hide content"; like every other change it only counts once the note is saved.
    remember { model.hiddenInWidgets = wasHidden }
    var hiddenInWidgets by model::hiddenInWidgets
    var saving by model::saving
    var menuOpen by model::menuOpen
    var formatMenuOpen by model::formatMenuOpen
    var menuPage by model::menuPage
    LaunchedEffect(menuOpen) { if (!menuOpen) menuPage = 0 }
    val isLocal = CredentialStore.isLocal(account)

    // Text formatting (bold, italic, underline, strikethrough) of existing notes of a Keep account.
    // Formatting works for every note: a new one sends it along when it is made, a local one keeps it on the phone.
    val canFormat = true
    val format = remember { FormatState(canFormat, existing?.let { RichNotes.of(activity, account, it.id) }) }
    // Folded categories are remembered per note; a note that is being made has no id yet.
    val noteKey = existing?.id ?: "new"
    // The categories of this checklist while it is edited (see EditorCategories).
    val categories = remember { EditorCategories(activity, account, noteKey, items, format, canFormat) }
    fun editorRich(): RichNote? = categories.rich()
    val sessionCategories = categories.session
    fun keepCategory(name: String?) = categories.keep(name)
    fun categoryGroups(): List<Categories.Group>? = categories.groups()
    fun setCategory(id: String, name: String?) = categories.set(id, name)

    // What a swipe does to items in this editor, from the settings.
    val itemSwipe = remember(activity.layoutKey) { AppSettingsStore.load(activity).let { it.itemSwipeRight to it.itemSwipeLeft } }

    /** A swipe on one item, or on a category's header for all its items: tick, remove, or choose another category. */
    fun swipeItems(ids: List<String>, action: Int) {
        when (action) {
            ITEM_SWIPE_TICK -> {
                val all = items.filter { it.id in ids && !it.isDivider() }
                val tick = !all.all { it.checked }
                for (i in items.indices) if (items[i].id in ids && !items[i].isDivider()) items[i] = items[i].copy(checked = if (ids.size == 1) !items[i].checked else tick)
            }
            ITEM_SWIPE_REMOVE -> {
                items.removeAll { it.id in ids }
                if (items.isEmpty()) items.add(KeepItem(UUID.randomUUID().toString(), "", false))
                if (items.isNotEmpty() && items[0].indent) items[0] = items[0].copy(indent = false)
            }
            ITEM_SWIPE_CATEGORY -> {
                moveItems = ids
                newCategoryDialog = true
            }
        }
    }

    /** Makes the list an ordinary checklist again: the words of the categories stay, no longer in bold. */
    fun dissolveCategories() = categories.dissolve()

    /** A new item at the end of category [name] (a new category at the end of the list), its name in bold; null: no category. */
    fun addInCategory(name: String?) {
        if (name == null) {
            val id = UUID.randomUUID().toString()
            items.add(KeepItem(id, "", false))
            focusItemId = id
        } else focusItemId = categories.addIn(name)
    }

    /** The name for a new category was given: for an item dropped onto "New category", as a first empty one, or with a new item. */
    fun createCategory(name: String) {
        val clean = name.trim()
        val forItem = newCategoryFor
        when {
            forItem != null -> {
                setCategory(forItem, clean)
                // To the end, where the new category's header appears.
                val at = items.indexOfFirst { it.id == forItem }
                if (at >= 0) items.add(items.removeAt(at))
            }
            newCategoryEmpty -> keepCategory(clean)
            else -> addInCategory(clean)
        }
        newCategoryFor = null
        newCategoryEmpty = false
    }
    // A change that Keep does not have yet (and why, if sending it failed).
    val unsent = remember {
        existing?.let { note ->
            PendingEdits.all(activity)
                .firstOrNull { it.account == account && it.function == "update_note" && it.args.firstOrNull() == note.id }
        }
    }
    val notificationsOn = rememberNotificationsOn(activity)
    var confirmDelete by model::confirmDelete
    var viewerImage by model::viewerImage
    val images = remember { existing?.images.orEmpty() }
    // Images marked for removal; they are only deleted in Keep when the note is saved.
    var removedImages by model::removedImages
    // Keep's own pin: saved to Google Keep together with the note, like the color.
    remember { model.pinned = existing?.pinned ?: false }
    var pinned by model::pinned
    // Keep has no "bottom": this only changes where the note sits in the widget, and applies at once.
    remember { model.pinBottom = pinWidgetId != null && existing != null &&
                existing.id in SettingsStore.loadWidget(activity, pinWidgetId).pinBottom }
    var pinBottom by model::pinBottom
    // Reminders belong to the phone only and are saved at once, not with the note.
    remember { model.reminders = existing?.let { Reminders.list(activity, account, it.id) }.orEmpty() }
    var reminders by model::reminders
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // Saving in the background is the first thing that needs notifications (they tell about a failed
    // send), so the right is asked for once, when a note is saved; the editor closes after the answer.
    val askAfterSave = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { activity.finish() }
    var remindersOpen by model::remindersOpen
    // Reminders of a new note wait here until the note exists and has its id.
    var draftReminders by model::draftReminders
    // Set while an existing note is turned into the other kind: nothing changes until "Save", which
    // then creates the new note and moves this one to the trash.
    var convertFrom by model::convertFrom
    val shownReminders = if (existing != null) reminders else draftReminders.sortedBy { it.at }
    val convertPrefs = remember { activity.getSharedPreferences("convert_hints", android.content.Context.MODE_PRIVATE) }
    val convertKey = existing?.let { account + "|" + it.id }
    // Asked once per existing note; "No" is remembered.
    remember { model.convertDismissed = convertKey != null && convertPrefs.getBoolean(convertKey, false) }
    var convertDismissed by model::convertDismissed
    var error by model::error
    val failedTemplate = stringResource(R.string.save_failed, "%s")

    // What the editor does with its content: saving, converting, history, leaving (see EditorController).
    val notificationsNow = rememberUpdatedState(notificationsOn)
    val controller = remember {
        EditorController(
            activity, model, categories, format, existing, account, noteKey, isLocal, canFormat, scope,
            originalGallery, originalCustom, originalLook, wasHidden, failedTemplate, { askEdit() }, { askAfterSave.launch(android.Manifest.permission.POST_NOTIFICATIONS) }, { notificationsNow.value },
        )
    }
    fun outItems() = controller.outItems()
    fun formatTextOf(key: String) = controller.formatTextOf(key)
    fun leaveNote(action: suspend () -> Unit) = controller.leaveNote(action)
    fun formatJson() = controller.formatJson()
    fun save(fromHome: Boolean = false) = controller.save(fromHome)
    fun requestConvert() = controller.requestConvert()
    fun hasChanges() = controller.hasChanges()
    fun capture() = controller.capture()
    fun restore(step: EditSnapshot) = controller.restore(step)
    fun discard() = controller.discard()

    var confirmDiscard by model::confirmDiscard
    var paletteOpen by model::paletteOpen
    var labelsOpen by model::labelsOpen

    val historyKey = existing?.let { account + "|" + it.id }
    val history = remember { capture().let { first -> historyKey?.let { EditHistoryKeep.take(it, first) } ?: EditHistory() } }
    LaunchedEffect(title, text, isList, color, background, labels, numbered, items.toList(), ownText, variantDark, format.version) {
        if (history.isTyping(capture())) kotlinx.coroutines.delay(700)
        history.record(capture())
    }
    val latestCapture by rememberUpdatedState { capture() }
    DisposableEffect(Unit) {
        onDispose {
            if (historyKey != null) {
                history.record(latestCapture())
                EditHistoryKeep.put(historyKey, history)
            }
        }
    }
    val undo = {
        history.record(capture())
        history.undo()?.let { restore(it) }
        Unit
    }
    val redo = { history.redo()?.let { restore(it) }; Unit }

    androidx.activity.compose.BackHandler(enabled = !saving) { discard() }

    // Home keeps what was typed: a changed note is saved, not thrown away. Back still discards.
    val saveOnHome by rememberUpdatedState { if (hasChanges() && !saving) save(fromHome = true) }
    DisposableEffect(Unit) {
        activity.onHome = { saveOnHome() }
        onDispose { activity.onHome = null }
    }

    // The text styles live in the selection menu of Android (next to Cut, Copy, Paste), for the text field that has the focus.
    val styleLabels = listOf(R.string.format_bold, R.string.format_italic, R.string.format_underline, R.string.format_strike).map { stringResource(it) }
    val applyStyle: (Int) -> Unit = { bit -> format.toggleSelected(::formatTextOf, bit) }
    val formatOptions = listOf(
        Triple(R.drawable.ic_format_bold, RichNotes.BOLD, 0), Triple(R.drawable.ic_format_italic, RichNotes.ITALIC, 1),
        Triple(R.drawable.ic_format_underlined, RichNotes.UNDERLINE, 2), Triple(R.drawable.ic_format_strike, RichNotes.STRIKE, 3),
    ).map { (icon, bit, i) -> FormatOption(icon, styleLabels[i], format.version.let { format.isActive(::formatTextOf, bit) }) { applyStyle(bit) } }
    // The list stays open, so more than one style can be switched.
    val formatEntries = formatOptions.map { MenuEntry(it.label, it.icon, checked = it.active, onClick = it.toggle) }
    // Every action of the editor with what it does now; the layout of the settings decides which bar or menu shows it.
    // Read again when the screen comes back, as the layout can be changed in the settings that open from here.
    val editorLayout = remember(activity.layoutKey) { AppSettingsStore.load(activity).editorLayout }
    val barSnapBack = remember(activity.layoutKey) { AppSettingsStore.load(activity).editorBarSnapBack }
    val buttons = editorButtons(
        activity, model,
        existing, account, canFormat, isLocal, pinWidgetId, editorLayout, scope, images, picture, darkVersion, hintPrefs, notificationsOn,
        shownReminders, format, history, categories, formatEntries, formatOptions,
        { askEdit() }, { toastUnlockFirst() }, { action -> leaveNote(action) }, undo, redo, { save() }, { requestConvert() },
        { dissolveCategories() }, { addInCategory(it) }, { discard() }, { hasChanges() }, { outItems() },
    )
    val states = buttons.states
    val inlineBlocks = buttons.inlineBlocks
    val scroll = rememberScrollState()
    // The editor opens in its final look; only changes made while it is open fade.
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(500); activity.screenTint.instant = false }
    OnNoteTheme(
        active = tinted && !darkLook, accent = accent, transparentRows = tinted,
        darkOnPicture = darkLook, textColor = ownText,
    ) {
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        // Ctrl+Z, Ctrl+Shift+Z and Ctrl+Y of a hardware keyboard.
        if (!canEdit || event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@onPreviewKeyEvent false
        when {
            event.key == Key.Z && event.isShiftPressed || event.key == Key.Y -> { redo(); true }
            event.key == Key.Z -> { undo(); true }
            else -> false
        }
    }) {
    Column(Modifier.fillMaxSize()) {
    // The bar stays on screen while the note scrolls: discard, the note's switches, save.
        EditorBar(editorLayout.inZone(ZONE_TOP_LEFT), editorLayout.inZone(ZONE_TOP_RIGHT), states, snapBack = barSnapBack)
    // A short note sits in the middle of the screen between the bars; with the keyboard open, at the top, where one types.
    val imeVisible = WindowInsets.isImeVisible
    BoxWithConstraints(Modifier.weight(1f)) {
    val viewport = maxHeight
    Column(Modifier.fillMaxSize().onGloballyPositioned { editorViewport = it.boundsInRoot() }.verticalScroll(scroll).padding(EDITOR_PADDING)) {
    Column(Modifier.fillMaxWidth().heightIn(min = viewport - EDITOR_PADDING * 2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EditorNoteHeader(model, existing, account, allAccounts, unsent, remote, offline, images, scope, onReload, { leaveNote(it) })
        // Where a short note sits is a setting; with the keyboard open it is at the top, where one types.
        if (!imeVisible && editorLayout.option != EDITOR_ALIGN_TOP) Spacer(Modifier.weight(1f))
        // The buttons the layout sets before the note show above its fields, those after it below them.
        val noteZone = editorLayout.inZone(ZONE_NOTE)
        val noteAt = noteZone.indexOfFirst { it.id == NOTE_ID }.coerceAtLeast(0)
        NoteActionButtons(noteZone.take(noteAt), states, inline = inlineBlocks)
        // The rounder fields of Material 3 Expressive; the frame of a category is rounded to match (see categoryFrame). The
        // title has a thicker line than the rest, so it stands out from the items below it.
        run {
            val titleInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val titleColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors()
            val titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            androidx.compose.foundation.text.BasicTextField(
                value = title, onValueChange = { title = it }, readOnly = !canEdit, singleLine = false,
                textStyle = titleStyle, interactionSource = titleInteraction,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            ) { inner ->
                androidx.compose.material3.OutlinedTextFieldDefaults.DecorationBox(
                    value = title, innerTextField = inner, enabled = true, singleLine = false,
                    visualTransformation = VisualTransformation.None, interactionSource = titleInteraction,
                    label = { Text(stringResource(R.string.title_hint)) }, colors = titleColors,
                    container = {
                        androidx.compose.material3.OutlinedTextFieldDefaults.Container(
                            enabled = true, isError = false, interactionSource = titleInteraction, colors = titleColors,
                            shape = MaterialTheme.shapes.medium, focusedBorderThickness = 3.dp, unfocusedBorderThickness = 2.dp,
                        )
                    },
                )
            }
        }

        if (isList) {
            EditorChecklist(
                model, categories, format, itemSwipe, account, noteKey, canFormat, styleLabels, applyStyle,
                { ids, action -> swipeItems(ids, action) }, { addInCategory(it) }, scroll, scope,
            )
        } else {
            EditorTextBody(model, format, existing, canFormat, styleLabels, applyStyle, convertKey, convertPrefs) { requestConvert() }
        }

        NoteActionButtons(editorLayout.inZone(ZONE_NOTE).dropWhile { it.id != NOTE_ID }.drop(1), states, inline = inlineBlocks)
        if (imeVisible || editorLayout.option != EDITOR_ALIGN_BOTTOM) Spacer(Modifier.weight(1f))

        // Web addresses, e-mail addresses and phone numbers in the note can be opened with one tap.
        val links = NoteLinks.find(listOf(title, text) + items.map { it.text })
        if (links.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEach { link ->
                androidx.compose.material3.AssistChip(
                    onClick = {
                        try {
                            activity.startActivity(link.intent)
                        } catch (e: ActivityNotFoundException) {
                            // No app for this kind of link.
                        }
                    },
                    label = { Text(link.label, maxLines = 1) },
                )
            }
        }


        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)

        if (remindersOpen) {
            RemindersDialog(
                activity = activity, account = account, noteId = existing?.id.orEmpty(), reminders = shownReminders,
                host = { dismiss, content -> EditorSheet(dismiss, content) },
                onChange = { if (existing != null) reminders = Reminders.list(activity, account, existing.id) },
                onPut = if (existing == null) { r -> draftReminders = draftReminders.filter { it.id != r.id } + r } else null,
                onRemove = if (existing == null) { id -> draftReminders = draftReminders.filter { it.id != id } } else null,
                onDismiss = { remindersOpen = false },
                askNotifications = {
                    if (android.os.Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(activity)) {
                        askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }

        shownReminders.firstOrNull()?.let {
            Text(
                stringResource(R.string.reminder_at, Reminders.label(activity, it.at)) +
                    if (shownReminders.size > 1) "  (+${shownReminders.size - 1})" else "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    }
    }
    // The bottom bar sits above the keyboard, where the thumb is while typing, and stays when the note scrolls.
    EditorBar(editorLayout.inZone(ZONE_BOTTOM_LEFT), editorLayout.inZone(ZONE_BOTTOM_RIGHT), states, snapBack = barSnapBack)
    }
    if (paletteOpen) EditorSheet({ paletteOpen = false }) {
        AppearanceSection(
            color = color, onColor = { color = it },
            background = background, onBackground = { background = it; activity.pickedBackground = null },
            darkVersion = darkVersion, onDarkVersion = { variantDark = it },
            canEdit = canEdit, isExisting = existing != null, isLocal = isLocal,
            ownText = ownText, onOwnText = { ownText = it },
            picture = picture,
            onPickPhoto = { if (hintPrefs.getBoolean("own_photo_local_dismissed", false)) activity.pickBackground.launch("image/*") else localHint = true },
        )
    }
    if (labelsOpen) EditorSheet({ labelsOpen = false }) {
        LabelsSection(account = targetAccount, labels = labels, onLabels = { labels = it }, canEdit = canEdit)
    }
    EditorDialogs(model, categories, account, noteKey, { createCategory(it) }, { discard() })
    }
    }
}
