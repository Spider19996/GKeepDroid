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

/**
 * The categories of a checklist while it is edited in [EditNoteActivity]: which ones there are in this session, the items in
 * their categories, moving an item to another one. A category is a bold word in front of an item's text (see [Categories]), so
 * every change here is a change of the items' texts and styles.
 */
internal class EditorCategories(
    private val context: android.content.Context,
    private val account: String,
    private val noteKey: String,
    private val items: androidx.compose.runtime.snapshots.SnapshotStateList<KeepItem>,
    private val format: FormatState,
    private val enabled: Boolean,
) {
    /**
     * The categories of this editing session: one that loses its last item (moved elsewhere) stays until the note is saved, so
     * items can be moved back into it, and a new one can be empty for a moment. Also the order of their headers.
     */
    val session = androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(Categories.names(items, rich())) }

    init {
        // The categories this checklist had on this device before (also ones that are gone), for choosing one again.
        CategoryHistory.add(context, account, noteKey, session)
    }

    /** The styles of the items as they are now in this editor, for finding their categories. */
    fun rich(): RichNote? =
        if (!enabled) null else RichNote("", items.associate { it.id to RichDoc(it.text, format.flagsFor(it.id, it.text).runs()) })

    /** [name] is a category of this session (and of the history) from now on. */
    fun keep(name: String?) {
        if (name == null || name.trim().isEmpty()) return
        if (name.trim() !in session) session.add(name.trim())
        CategoryHistory.add(context, account, noteKey, listOf(name))
    }

    /**
     * The items in their categories, the categories in a fixed order (the order of this session, not of the items), so a
     * header stays where it is while items move between categories; categories that lost their items stay as empty headers
     * until the note is saved. Null for a list without categories.
     */
    fun groups(): List<Categories.Group>? {
        if (!enabled) return null
        val rich = rich()
        val nameOf = items.associate { it.id to Categories.split(it, rich?.docs?.get(it.id))?.name }
        val order = session.toList() + nameOf.values.filterNotNull().filter { it !in session }.distinct()
        if (order.isEmpty()) return null
        return (listOf<String?>(null) + order).map { name -> Categories.Group(name, items.filter { nameOf[it.id] == name }) }
            .filter { it.name != null || it.items.isNotEmpty() }
    }

    /** Moves an item to category [name] (null: none): the old name in front of it goes, the new one comes in bold. */
    fun set(id: String, name: String?) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = items[i]
        val cut = Categories.split(item, rich()?.docs?.get(id))?.restStart ?: 0
        val rest = item.text.substring(cut)
        val old = format.flagsFor(id, item.text).flags
        // The word is already in front (a category that was dissolved, or typed by hand): it only becomes bold again.
        val clean = name?.trim()
        if (clean != null && cut == 0 && rest.startsWith("$clean ", ignoreCase = true)) {
            val flags = old.copyOf()
            for (k in clean.indices) flags[k] = flags[k] or RichNotes.BOLD
            format.replaceFlags(id, flags)
            items[i] = item.copy(text = rest.substring(0, clean.length) + rest.substring(clean.length))
            keep(rest.substring(0, clean.length))
            return
        }
        val front = if (name == null) "" else "${name.trim()} "
        val flags = IntArray(front.length + rest.length)
        for (k in 0 until (name?.trim()?.length ?: 0)) flags[k] = RichNotes.BOLD
        for (k in rest.indices) flags[front.length + k] = old.getOrElse(cut + k) { 0 } and TEXT_STYLE_MASK
        format.replaceFlags(id, flags)
        items[i] = item.copy(text = front + rest)
        keep(name)
    }

    /**
     * Makes the list an ordinary checklist again: the words of the categories stay in front of the items, only no longer in
     * bold, so they are no categories any more.
     */
    fun dissolve() {
        val rich = rich()
        items.toList().forEach { item ->
            val split = Categories.split(item, rich?.docs?.get(item.id)) ?: return@forEach
            val flags = format.flagsFor(item.id, item.text).flags.copyOf()
            for (k in 0 until minOf(split.restStart, flags.size)) flags[k] = flags[k] and RichNotes.BOLD.inv()
            format.replaceFlags(item.id, flags)
        }
        session.clear()
    }

    /** A new item at the end of category [name] (a new category at the end of the list), its name in bold; returns its id. */
    fun addIn(name: String): String {
        val id = java.util.UUID.randomUUID().toString()
        val clean = name.trim()
        keep(clean)
        val text = "$clean "
        val last = Categories.groups(items, rich())?.firstOrNull { it.name == clean }?.items?.lastOrNull()
        val at = last?.let { l -> items.indexOfFirst { it.id == l.id } + 1 }?.takeIf { it > 0 } ?: items.size
        items.add(at, KeepItem(id, text, false))
        format.setStyle(id, text, 0, clean.length, RichNotes.BOLD)
        CategoryFolds.set(context, account, noteKey, clean, false)
        return id
    }
}
