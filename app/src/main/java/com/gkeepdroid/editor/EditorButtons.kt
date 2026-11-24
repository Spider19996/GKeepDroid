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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

/** What the editor offers as buttons: what each action does and looks like now, and the blocks that can be shown in the note's place. */
internal class EditorButtons(val states: Map<String, EditorActionState>, val inlineBlocks: Map<String, @Composable (Boolean) -> Unit>)

@Composable
internal fun editorButtons(
    activity: EditNoteActivity, model: EditorModel,
    existing: KeepNote?, account: String, canFormat: Boolean, isLocal: Boolean, pinWidgetId: Int?, editorLayout: ButtonLayout,
    scope: kotlinx.coroutines.CoroutineScope, images: List<String>, picture: android.graphics.Bitmap?, darkVersion: Boolean,
    hintPrefs: android.content.SharedPreferences, notificationsOn: Boolean, shownReminders: List<Reminder>,
    format: FormatState, history: EditHistory, categories: EditorCategories, formatEntries: List<MenuEntry>, formatOptions: List<FormatOption>,
    askEdit: () -> Unit, toastUnlockFirst: () -> Unit, leaveNote: (suspend () -> Unit) -> Unit, undo: () -> Unit, redo: () -> Unit,
    save: () -> Unit, requestConvert: () -> Unit, dissolveCategories: () -> Unit, addInCategory: (String?) -> Unit, discard: () -> Unit,
    hasChanges: () -> Boolean, outItems: () -> List<KeepItem>,

): EditorButtons {
    val items = model.items
    val sessionCategories = categories.session
    fun editorRich() = categories.rich()
    var canEdit by model::canEdit
    var isList by model::isList
    var title by model::title
    var text by model::text
    var color by model::color
    var background by model::background
    var localHint by model::localHint
    var variantDark by model::variantDark
    var ownText by model::ownText
    var labels by model::labels
    var numbered by model::numbered
    var addMenuOpen by model::addMenuOpen
    var newCategoryDialog by model::newCategoryDialog
    var newCategoryEmpty by model::newCategoryEmpty
    var targetAccount by model::targetAccount
    var hiddenInWidgets by model::hiddenInWidgets
    var saving by model::saving
    var menuOpen by model::menuOpen
    var formatMenuOpen by model::formatMenuOpen
    var menuPage by model::menuPage
    var confirmDelete by model::confirmDelete
    var pinned by model::pinned
    var pinBottom by model::pinBottom
    var remindersOpen by model::remindersOpen
    var error by model::error
    var confirmDiscard by model::confirmDiscard
    var paletteOpen by model::paletteOpen
    var labelsOpen by model::labelsOpen
        val states = mutableMapOf<String, EditorActionState>()
        states["close"] = EditorActionState(R.drawable.ic_close, stringResource(R.string.discard_action), { if (hasChanges()) confirmDiscard = true else discard() }, enabled = !saving)
        states["pin"] = EditorActionState(
            if (pinned) R.drawable.ic_push_pin else R.drawable.ic_push_pin_outline, stringResource(R.string.pin_note),
            { if (canEdit) pinned = !pinned else toastUnlockFirst() }, checked = pinned, dimmed = !canEdit,
        )
        states["pin_bottom"] = EditorActionState(
            R.drawable.ic_pin_bottom, stringResource(R.string.pin_to_bottom),
            {
                pinBottom = !pinBottom
                if (pinWidgetId != null && existing != null) {
                    val widget = SettingsStore.loadWidget(activity, pinWidgetId)
                    SettingsStore.saveWidget(
                        activity, pinWidgetId,
                        widget.copy(pinBottom = if (pinBottom) widget.pinBottom + existing.id else widget.pinBottom - existing.id),
                    )
                    scope.launch { WidgetRefresh.all(activity) }
                }
            },
            visible = pinWidgetId != null && existing != null, checked = pinBottom,
        )
        states["reminder"] = EditorActionState(
            R.drawable.ic_alarm, stringResource(R.string.reminders_title), { if (canEdit) remindersOpen = true else askEdit() },
            checked = shownReminders.isNotEmpty(), tint = if (notificationsOn) null else MaterialTheme.colorScheme.error,
        )
        states["save"] = EditorActionState(
            if (canEdit) R.drawable.ic_done else R.drawable.ic_lock,
            stringResource(if (canEdit) R.string.save else if (isList) R.string.edit_list else R.string.edit_note),
            { if (canEdit) save() else askEdit() }, enabled = !saving,
        )
        states["palette"] = EditorActionState(R.drawable.ic_palette, stringResource(R.string.color_and_background), { paletteOpen = true }, visible = canEdit)
        states["labels"] = EditorActionState(R.drawable.ic_label, stringResource(R.string.labels_title), { labelsOpen = true }, visible = canEdit)
        // Keep's API has no way to upload pictures, so adding one is left to the Keep app.
        states["image"] = EditorActionState(
            R.drawable.ic_image, stringResource(R.string.image_add_keep), { activity.startActivity(keepIntent(activity)) },
            visible = canEdit && existing != null && !isLocal && images.isEmpty(),
        )
        // What the sheets of the bottom bar hold, to show where the layout sets them beside the note (the highlight is for the preview).
        val inlineBlocks: Map<String, @Composable (Boolean) -> Unit> = mapOf(
            "palette" to { _ ->
                if (canEdit) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            },
            "format" to { highlighted -> if (canFormat && canEdit) FormatBar(formatOptions, highlighted) },
            "labels" to { _ ->
                if (canEdit) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabelsSection(account = targetAccount, labels = labels, onLabels = { labels = it }, canEdit = canEdit)
                }
            },
        )
        // The text styles: one button with a list of them, or a bar of them in the note's place. They work on the selection of the
        // focused field, or on what is typed next at the cursor.
        states["format"] = EditorActionState(
            R.drawable.ic_text_format, stringResource(R.string.format_title), { formatMenuOpen = true }, visible = canFormat && canEdit,
            popup = { GroupedMenu(expanded = formatMenuOpen, onDismissRequest = { formatMenuOpen = false }, groups = listOf(formatEntries)) },
        )
        states["quote_add"] = EditorActionState(
            R.drawable.ic_format_quote, stringResource(R.string.quote_add),
            {
                // The lines of the selection (or the line of the cursor) get "> " in front, and the cursor goes to the end of them.
                val t = text
                val sel = model.textSel
                val start = sel.min.coerceIn(0, t.length)
                val end = sel.max.coerceIn(0, t.length)
                val lineStart = if (start == 0) 0 else t.lastIndexOf('\n', start - 1) + 1
                val lineEnd = t.indexOf('\n', end).let { if (it < 0) t.length else it }
                val quoted = t.substring(lineStart, lineEnd).split('\n').joinToString("\n") { if (it.startsWith("> ") || it == ">") it else "> $it" }
                val changed = t.substring(0, lineStart) + quoted + t.substring(lineEnd)
                format.edited("text", t, changed)
                text = changed
                val caret = TextRange(lineStart + quoted.length)
                format.selection["text"] = caret
                model.textSel = caret
                model.textComp = null
            },
            visible = !isList && canEdit,
        )
        states["undo"] = EditorActionState(R.drawable.ic_undo, stringResource(R.string.undo), undo, visible = canEdit, enabled = history.canUndo)
        states["redo"] = EditorActionState(R.drawable.ic_redo, stringResource(R.string.redo), redo, visible = canEdit, enabled = history.canRedo)
        states["convert"] = EditorActionState(
            if (isList) R.drawable.ic_subject else R.drawable.ic_checklist, stringResource(if (isList) R.string.convert_to_note else R.string.convert_to_list), { requestConvert() },
        )
        states["list_number"] = EditorActionState(
            R.drawable.ic_list_numbered, stringResource(if (numbered) R.string.list_unnumber else R.string.list_number),
            {
                // Numbers are shown from the position; an old "1. " typed by hand is not counted twice.
                val stripped = ListNumbering.strip(items.toList())
                items.clear()
                items.addAll(stripped)
                numbered = !numbered
            },
            visible = isList,
        )
        states["list_check_all"] = EditorActionState(
            R.drawable.ic_done_all, stringResource(R.string.list_check_all), { for (i in items.indices) if (!items[i].isDivider()) items[i] = items[i].copy(checked = true) }, visible = isList,
        )
        states["list_uncheck_all"] = EditorActionState(
            R.drawable.ic_remove_done, stringResource(R.string.list_uncheck_all), { for (i in items.indices) items[i] = items[i].copy(checked = false) }, visible = isList,
        )
        states["list_sort"] = EditorActionState(
            R.drawable.ic_sort, stringResource(R.string.list_sort_alpha),
            {
                // Sub-items lose their parent when the order changes, so they become ordinary items.
                // Dividers stay where they are: the items between two of them are sorted among themselves.
                val sorted = buildList {
                    var segment = mutableListOf<KeepItem>()
                    fun flush() { addAll(segment.sortedBy { it.text.lowercase() }.map { it.copy(indent = false) }); segment = mutableListOf() }
                    items.forEach { if (it.isDivider()) { flush(); add(it.copy(indent = false)) } else segment.add(it) }
                    flush()
                }
                items.clear()
                items.addAll(sorted)
            },
            visible = isList,
        )
        val quantitiesOn = remember { AppSettingsStore.load(activity).listQuantities }
        states["list_quantity"] = EditorActionState(
            R.drawable.ic_add, stringResource(R.string.quantity_action),
            {
                // The item last typed into; without one there is nothing to count.
                val id = format.focus?.takeIf { f -> items.any { it.id == f && !it.isDivider() } }
                if (id == null) Toast.makeText(activity, R.string.quantity_pick_item, Toast.LENGTH_SHORT).show()
                else model.quantityItem = id
            },
            visible = isList && quantitiesOn,
        )
        val hasCategories = isList && canFormat && (sessionCategories.isNotEmpty() || Categories.names(items, editorRich()).isNotEmpty())
        states["list_categories"] = EditorActionState(
            R.drawable.ic_category, stringResource(if (hasCategories) R.string.list_uncategorize else R.string.list_categorize),
            {
                if (hasCategories) dissolveCategories()
                else { newCategoryEmpty = true; newCategoryDialog = true }
            },
            visible = isList && canEdit && canFormat,
        )
        states["list_delete_checked"] = EditorActionState(
            R.drawable.ic_delete_sweep, stringResource(R.string.list_delete_checked),
            {
                items.removeAll { it.checked }
                if (items.isEmpty()) items.add(KeepItem(UUID.randomUUID().toString(), "", false))
            },
            // Always there, so it can be found; it works once something is ticked.
            visible = isList && canEdit, enabled = items.any { it.checked },
        )
        // In a list with categories adding asks where the item goes: no category, one of them, or a new one.
        val categoryNames = if (isList && canFormat) Categories.names(items, editorRich()) else emptyList()
        states["add_item"] = EditorActionState(
            R.drawable.ic_add, stringResource(R.string.add_item),
            { if (categoryNames.isEmpty()) addInCategory(null) else addMenuOpen = true },
            visible = isList && canEdit,
            // Held, it offers the categories in a list without any yet too: that is where the first one is made.
            onLongClick = if (isList && canEdit && canFormat) ({ addMenuOpen = true }) else null,
            popup = {
                GroupedMenu(
                    expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false },
                    groups = listOf(
                        listOf(MenuEntry(stringResource(R.string.category_none)) { addMenuOpen = false; addInCategory(null) }),
                        categoryNames.map { name -> MenuEntry(name) { addMenuOpen = false; addInCategory(name) } },
                        listOf(MenuEntry(stringResource(R.string.category_new), R.drawable.ic_add) { addMenuOpen = false; newCategoryDialog = true }),
                    ),
                )
            },
        )

        states["add_divider"] = EditorActionState(
            R.drawable.ic_divider, stringResource(R.string.add_divider),
            {
                // Right after the item being edited, or at the end.
                val at = items.indexOfFirst { it.id == format.focus }.let { if (it >= 0) it + 1 else items.size }
                items.add(at, KeepItem(UUID.randomUUID().toString(), DIVIDER_TEXT, false))
            },
            visible = isList && canEdit,
        )
        states["open_in_keep"] = EditorActionState(
            R.drawable.ic_open_new, stringResource(R.string.open_in_keep), { if (existing != null) activity.openInKeep(account, existing) },
            visible = existing != null && existing.serverId.isNotEmpty(),
        )
        states["editor_settings"] = EditorActionState(
            R.drawable.ic_settings, stringResource(R.string.editor_buttons_title),
            { activity.startActivity(Intent(activity, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, "editor_buttons")) },
        )
        states["share"] = EditorActionState(R.drawable.ic_share, stringResource(R.string.share_note), { activity.shareNote(title, text, isList, outItems()) }, visible = existing != null)
        states["hide_content"] = EditorActionState(
            if (hiddenInWidgets) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
            stringResource(if (hiddenInWidgets) R.string.show_content else R.string.hide_content),
            {
                if (canEdit) {
                    hiddenInWidgets = !hiddenInWidgets
                    scope.launch { WidgetRefresh.all(activity) }
                } else {
                    askEdit()
                }
            },
            visible = existing != null,
        )
        states["archive"] = EditorActionState(
            R.drawable.ic_archive, stringResource(R.string.archive_note),
            {
                if (existing != null) leaveNote {
                    KeepRepository.archiveNote(activity, account, existing.id)
                    UndoStore.set(account, existing.id, UndoStore.Kind.ARCHIVE)
                }
            },
            visible = existing != null && !isLocal,
        )
        states["delete"] = EditorActionState(
            R.drawable.ic_delete, stringResource(if (isLocal) R.string.delete_note else R.string.move_to_trash),
            {
                if (existing != null) {
                    // Local notes have no trash, so deleting them asks first.
                    if (isLocal) {
                        confirmDelete = true
                    } else {
                        leaveNote {
                            KeepRepository.trashNote(activity, account, existing.id)
                            UndoStore.set(account, existing.id, UndoStore.Kind.TRASH)
                        }
                    }
                }
            },
            visible = existing != null,
        )
        // The overflow menu is shown when any of its actions is possible; it grays out until the note is open for editing.
        val listToolsLabel = stringResource(R.string.list_tools)
        states["more"] = EditorActionState(
            R.drawable.ic_more_vert, stringResource(R.string.more_actions), { if (canEdit) menuOpen = true else toastUnlockFirst() },
            visible = editorLayout.inZone(ZONE_MENU).any { states[it.id]?.visible == true }, dimmed = !canEdit,
            popup = {
                GroupedMenu(
                    expanded = menuOpen && canEdit,
                    onDismissRequest = { menuOpen = false },
                    groups = menuGroups(
                        editorLayout, states, menuPage, { menuPage = it }, listToolsLabel, R.drawable.ic_arrow_left,
                        { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }, { menuOpen = false },
                        formatEntries = formatEntries,
                    ),
                )
            },
        )
    return EditorButtons(states, inlineBlocks)
}
