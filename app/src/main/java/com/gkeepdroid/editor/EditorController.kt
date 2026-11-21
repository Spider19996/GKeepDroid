package com.gkeepdroid

import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * What the editor does with its content: saving, changing the kind of the note, the steps of Undo and Redo, leaving it. It works on
 * the [EditorModel] and talks to the activity for what only an activity can do (closing, toasts, the notification right).
 */
internal class EditorController(
    private val activity: EditNoteActivity,
    private val model: EditorModel,
    private val categories: EditorCategories,
    private val format: FormatState,
    private val existing: KeepNote?,
    private val account: String,
    private val noteKey: String,
    private val isLocal: Boolean,
    private val canFormat: Boolean,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val originalGallery: String,
    private val originalCustom: Boolean,
    private val originalLook: NoteLook.Look,
    private val wasHidden: Boolean,
    private val failedTemplate: String,
    private val askEdit: () -> Unit,
    private val askAfterSave: () -> Unit,
    private val notificationsOn: () -> Boolean,
) {
    private val items = model.items
    private val sessionCategories = categories.session
    private fun editorRich() = categories.rich()
    private fun categoryGroups() = categories.groups()
    private val picked get() = activity.pickedBackground
    private var pickedBackground by activity::pickedBackground
    private var canEdit by model::canEdit
    private var isList by model::isList
    private var title by model::title
    private var text by model::text
    private var textSel by model::textSel
    private var textComp by model::textComp
    private var color by model::color
    private var background by model::background
    private var variantDark by model::variantDark
    private var ownText by model::ownText
    private var labels by model::labels
    private var numbered by model::numbered
    private var targetAccount by model::targetAccount
    private var hiddenInWidgets by model::hiddenInWidgets
    private var saving by model::saving
    private var removedImages by model::removedImages
    private var pinned by model::pinned
    private var reminders by model::reminders
    private var draftReminders by model::draftReminders
    private var convertFrom by model::convertFrom
    private var error by model::error

    /** The items as they are saved: with their numbers in the text when the list is numbered. */
    fun outItems(): List<KeepItem> = if (numbered) ListNumbering.apply(items.toList()) else items.toList()

    fun formatTextOf(key: String): String = if (key == "text") text else items.firstOrNull { it.id == key }?.text.orEmpty()

    /** Runs an action that ends the editing of this note (archive, trash) and closes the editor. */
    fun leaveNote(action: suspend () -> Unit) {
        if (!canEdit) return askEdit()
        saving = true
        error = ""
        scope.launch {
            try {
                action()
                activity.finish()
            } catch (e: Exception) {
                saving = false
                error = failedTemplate.format(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * The changed formatting as the Python bridge wants it; a note made from the other kind, and items made in this editor,
     * address their items by their place.
     */
    fun formatJson(): String? = format.toJson(
        ::formatTextOf, if (isList) outItems().filter { it.text.isNotBlank() }.map { it.id } else null, convertFrom != null || existing == null,
        known = if (convertFrom == null) existing?.items?.map { it.id }?.toSet().orEmpty() else emptySet(),
    )

    fun save(fromHome: Boolean = false) {
        if (saving || !canEdit) return
        saving = true
        error = ""
        // An item made in a category but left without text would become a bold word of its own in Keep.
        if (isList && canFormat) {
            val rich = editorRich()
            items.removeAll { item -> Categories.split(item, rich?.docs?.get(item.id))?.let { item.text.substring(it.restStart).isBlank() } == true }
            if (items.isEmpty()) items.add(KeepItem(UUID.randomUUID().toString(), "", false))
            // Kept in Keep in the order shown here: the items without a category, then each category with its items.
            categoryGroups()?.flatMap { it.items }?.let { grouped ->
                if (grouped.size == items.size && grouped.map { it.id } != items.map { it.id }) { items.clear(); items.addAll(grouped) }
            }
            CategoryHistory.add(activity, account, noteKey, sessionCategories)
        }
        if (AppSettingsStore.load(activity).trimEmptyEdges) {
            if (isList) {
                // Only if something stays: an all-empty list is left as it is.
                if (items.any { it.text.isNotBlank() }) {
                    while (items.first().text.isBlank()) items.removeAt(0)
                    while (items.last().text.isBlank()) items.removeAt(items.lastIndex)
                }
            } else {
                val lines = text.split("\n")
                val first = lines.indexOfFirst { it.isNotBlank() }
                if (first >= 0) {
                    val last = lines.indexOfLast { it.isNotBlank() }
                    val trimmed = lines.subList(first, last + 1).joinToString("\n")
                    // The styles stay with their characters.
                    format.edited("text", text, trimmed)
                    text = trimmed
                }
            }
        }
        val target = if (existing != null) account else targetAccount
        val galleryNow = if (background == CUSTOM_BACKGROUND) "" else background
        if (existing != null) NoteLook.save(activity, account, existing.id, ownText, variantDark)
        if (existing != null) KeepRepository.refreshWidgets(activity)
        // The photo of one's own stays on this phone; the widgets learn of a change right away.
        if (existing != null && (background == CUSTOM_BACKGROUND && picked != null || background != CUSTOM_BACKGROUND && originalCustom)) {
            if (background == CUSTOM_BACKGROUND) CustomBackgrounds.save(activity, account, existing.id, picked!!)
            else CustomBackgrounds.remove(activity, account, existing.id)
            pickedBackground = null
            KeepRepository.refreshWidgets(activity)
        }
        val converting = convertFrom != null
        val function = if (existing != null && !converting) "update_note" else "create_note"
        val args = if (existing != null && !converting) {
            KeepRepository.updateNoteArgs(
                existing.id, title, text, outItems(), color, pinned, removedImages.toList(), labels,
                // Only a changed picture of Keep's is sent; "default" takes it away.
                if (isLocal || galleryNow == originalGallery) "" else galleryNow.ifEmpty { "default" },
            )
        } else {
            KeepRepository.createNoteArgs(title, text, isList, outItems(), color, pinned, labels, galleryNow, formatJson().orEmpty())
        }
        // The mark lives on the phones (and is synced between them), not in Keep.
        if (existing != null) PrivateNotes.set(activity, account, existing.id, hiddenInWidgets)
        if (!CredentialStore.isLocal(target) || converting) {
            // The change is stored and sent in the background: the editor closes at once, and a failure
            // shows up as a notification instead of losing the change.
            KeepRepository.queueEdit(
                activity,
                if (converting) {
                    val old = convertFrom!!
                    val moved = Reminders.list(activity, account, old.id)
                    moved.forEach { Reminders.remove(activity, account, old.id, it.id) }
                    PendingEdit(target, function, args, reminders = moved, hidden = hiddenInWidgets, trashAfter = old.id)
                } else {
                    PendingEdit(target, function, args, reminders = if (existing == null) draftReminders else emptyList(), hidden = existing == null && hiddenInWidgets)
                },
            )
            // Sent after the change of the text, so the styles meet the text Keep has by then.
            if (!converting && existing != null) formatJson()?.let { KeepRepository.queueEdit(activity, PendingEdit(target, "format_note", listOf(existing.id, it))) }
            Toast.makeText(activity.applicationContext, R.string.note_saved, Toast.LENGTH_SHORT).show()
            val prefs = activity.getSharedPreferences("reminder_hints", android.content.Context.MODE_PRIVATE)
            if (!fromHome && !CredentialStore.isLocal(target) && android.os.Build.VERSION.SDK_INT >= 33 && !notificationsOn() && !prefs.getBoolean("save_asked", false)) {
                prefs.edit { putBoolean("save_asked", true) }
                askAfterSave()
            } else {
                activity.finish()
            }
            return
        }
        scope.launch {
            try {
                val before = Reminders.noteIds(activity, target)
                // Taken before the note is written, while the items are still the ones the styles belong to.
                val styles = if (existing != null) formatJson() else null
                KeepRepository.run(activity, target, function, args)
                if (styles != null) KeepRepository.run(activity, target, "format_note", listOf(existing!!.id, styles))
                if (existing == null) Reminders.attachToNew(activity, PendingEdit(target, function, args, reminders = draftReminders, hidden = hiddenInWidgets), before)
                Toast.makeText(activity.applicationContext, R.string.note_saved, Toast.LENGTH_SHORT).show()
                activity.finish()
            } catch (e: Exception) {
                saving = false
                error = failedTemplate.format(e.message ?: e.javaClass.simpleName)
            }
        }
    }


    /** Content of the editor as the other kind of note: checklist items become lines, lines become items. */
    fun convertedContent(toList: Boolean): Pair<String, List<KeepItem>> =
        if (toList) {
            "" to ChecklistText.items(text).ifEmpty { listOf(KeepItem(UUID.randomUUID().toString(), "", false)) }
        } else if (numbered) {
            // A numbered list becomes lines that start with their numbers.
            outItems().filter { it.text.isNotBlank() }.joinToString("\n") { it.text } to emptyList()
        } else {
            // The chosen prefix keeps a trace of the list: "- ", "* ", "[ ] / [x] " or numbers.
            val prefix = AppSettingsStore.load(activity).convertPrefix
            items.filter { it.text.isNotBlank() }.mapIndexed { i, item ->
                when (prefix) {
                    PREFIX_DASH -> "- "
                    PREFIX_STAR -> "* "
                    PREFIX_BOX -> if (item.checked) "[x] " else "[ ] "
                    PREFIX_NUMBER -> "${i + 1}. "
                    else -> ""
                } + item.text
            }.joinToString("\n") to emptyList()
        }

    /** A new note changes its kind right here; an existing one is saved as a new note and the old one goes to the trash. */
    /** After text became items: lines that all start with numbers make a numbered list. */
    fun adoptNumbering() {
        numbered = ListNumbering.isNumbered(items.toList())
        if (numbered) {
            val stripped = ListNumbering.strip(items.toList())
            items.clear()
            items.addAll(stripped)
        }
    }

    fun requestConvert() {
        if (!canEdit) {
            askEdit()
        } else if (existing == null) {
            val (newText, newItems) = convertedContent(!isList)
            val (oldText, oldItems) = text to items.toList()
            items.clear()
            items.addAll(newItems)
            text = newText
            isList = !isList
            if (isList) adoptNumbering() else numbered = false
        } else if (existing.images.isNotEmpty()) {
            Toast.makeText(activity, R.string.convert_images_blocked, Toast.LENGTH_LONG).show()
        } else {
            val (newText, newItems) = convertedContent(!isList)
            val (oldText, oldItems) = text to items.toList()
            val toList = !isList
            items.clear()
            items.addAll(newItems)
            text = newText
            isList = !isList
            if (isList) adoptNumbering() else numbered = false
            convertFrom = if (isList != existing.isList) existing else null
            // The styles travel with the text (a numbered list also keeps them: the numbers are not part of the items).
            if (convertFrom != null) format.carry(toList, oldText, oldItems, newText, items.toList())
        }
    }


    fun hasChanges() = if (existing != null) {
        title != existing.title || text != existing.text || outItems() != existing.items || color != existing.color ||
            pinned != existing.pinned || labels != existing.labels || removedImages.isNotEmpty() || convertFrom != null || hiddenInWidgets != wasHidden ||
            background != (if (originalCustom) CUSTOM_BACKGROUND else originalGallery) || picked != null ||
            variantDark != originalLook.dark || ownText != originalLook.textColor || format.touched.isNotEmpty()
    } else {
        title.isNotBlank() || text.isNotBlank() || items.any { it.text.isNotBlank() }
    }


    // Undo and Redo: a step for each change, and for a burst of typing once it pauses. A few minutes after closing, the steps are still there.
    fun capture(): EditSnapshot {
        val (styles, touched) = format.snapshot()
        return EditSnapshot(title, text, textSel, isList, color, background, labels, numbered, items.toList(), ownText, variantDark, styles, touched)
    }

    fun restore(step: EditSnapshot) {
        title = step.title
        text = step.text
        textSel = TextRange(step.selection.start.coerceAtMost(step.text.length), step.selection.end.coerceAtMost(step.text.length))
        textComp = null
        isList = step.isList
        color = step.color
        background = step.background
        labels = step.labels
        numbered = step.numbered
        items.clear()
        items.addAll(step.items)
        ownText = step.ownText
        variantDark = step.variantDark
        format.restore(step.styles, step.touched)
        format.selection["text"] = textSel
        convertFrom = if (existing != null && isList != existing.isList) existing else null
    }

    /** Closes the editor without saving; a note that was changed is reported as discarded. */
    fun discard() {
        if (hasChanges()) Toast.makeText(activity, R.string.note_discarded, Toast.LENGTH_SHORT).show()
        activity.finish()
    }
}
