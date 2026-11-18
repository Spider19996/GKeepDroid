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
import androidx.compose.foundation.layout.ColumnScope

/**
 * The checklist of the editor: the hint above it, the items (under the headers of their categories, if it has some) with their drag
 * handles and swipes, and what goes with them. Lives in [EditNoteActivity]'s column.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.EditorChecklist(
    model: EditorModel, categories: EditorCategories, format: FormatState, itemSwipe: Pair<Int, Int>,
    account: String, noteKey: String, canFormat: Boolean, styleLabels: List<String>, applyStyle: (Int) -> Unit,
    swipeItems: (List<String>, Int) -> Unit, addInCategory: (String?) -> Unit,
    scroll: androidx.compose.foundation.ScrollState, scope: kotlinx.coroutines.CoroutineScope,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val items = model.items
    val sessionCategories = categories.session
    fun editorRich() = categories.rich()
    fun categoryGroups() = categories.groups()
    fun setCategory(id: String, name: String?) = categories.set(id, name)
    var canEdit by model::canEdit
    var text by model::text
    var color by model::color
    var background by model::background
    var numbered by model::numbered
    var numberOfferDismissed by model::numberOfferDismissed
    var focusItemId by model::focusItemId
    var newCategoryDialog by model::newCategoryDialog
    var newCategoryFor by model::newCategoryFor
    var renameCategory by model::renameCategory
    var editorViewport by model::editorViewport
            // How the rows work; the cross hides this explanation for good.
            val hintPrefs = remember { context.getSharedPreferences("hints", android.content.Context.MODE_PRIVATE) }
            var hintHidden by remember { mutableStateOf(hintPrefs.getBoolean("list_hint_hidden", false)) }
            if (canEdit && !hintHidden) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.list_swipe_hint),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = {
                    hintPrefs.edit { putBoolean("list_hint_hidden", true) }
                    hintHidden = true
                }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.notif_hint_dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // The handle of a row moves it; the bounds tell which row the finger is over.
            var dragging by remember { mutableStateOf<String?>(null) }
            val rowBounds = remember { mutableStateMapOf<String, Rect>() }
            // Where the headers of the categories are, so a dragged item can be dropped onto one.
            val headerBounds = remember { mutableStateMapOf<String, Rect>() }
            // The place to drop an item into a new category, and whether the dragged item is over it.
            var newCategoryZone by remember { mutableStateOf<Rect?>(null) }
            var overNewCategory by remember { mutableStateOf(false) }
            // The category whose header is held: its items are left out while it moves, and follow it when it is let go.
            var draggingCategory by remember { mutableStateOf<String?>(null) }
            // How far the held header is from its place, and the stretches of each frame's top line its header covers.
            // Where the finger wants the held header's top (in the root).
            var categoryFingerTop by remember { mutableStateOf(0f) }
            var categoryStartTop by remember { mutableStateOf(0f) }
            val headerLayoutTop = remember { mutableStateMapOf<String, Float>() }
            val headerNotches = remember { mutableMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<Pair<Float, Float>>>() }
            // The drop places of the categories that have nothing else in them ("" for no category), and the one the item is over.
            val slotBounds = remember { mutableStateMapOf<String, Rect>() }
            var hoverTarget by remember { mutableStateOf<String?>(null) }
            // "1. " typed at the start of the first item offers to number the whole list.
            val firstFilled = items.firstOrNull { it.text.isNotBlank() }?.text.orEmpty()
            if (canEdit && !numbered && !numberOfferDismissed && Regex("""^\s*1[.)]\s""").containsMatchIn(firstFilled)) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
                        .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
                ) {
                    Text(stringResource(R.string.number_offer), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { numberOfferDismissed = true }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.convert_to_list_no)) }
                        TextButton(onClick = {
                            val stripped = ListNumbering.strip(items.toList())
                            items.clear()
                            items.addAll(stripped)
                            numbered = true
                        }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.list_number)) }
                    }
                }
            }
            // With categories the items stand under their category's header (as on the cards); the header folds them and
            // its "+" adds an item to them. The list itself keeps its order.
            val editorGroups = categoryGroups()
            val groupOf = editorGroups?.flatMap { g -> g.items.map { it.id to g.name } }?.toMap().orEmpty()
            // One item of the list as it is edited. Under a category's header it shows without the category's name, which
            // stays in front of it in Keep; what is typed goes behind the name.
            @Composable
            fun ItemEditor(index: Int, item: KeepItem, inCategory: Boolean, frame: Int = FRAME_NONE) {
                val split = if (inCategory) Categories.split(item, editorRich()?.docs?.get(item.id)) else null
                val cut = split?.restStart ?: 0
                val fullFlags = if (canFormat) format.flagsFor(item.id, item.text) else null
                key(item.id) {
                  Box(Modifier.categoryFrame(frame)) {
                    ChecklistRow(
                        if (cut > 0) item.copy(text = item.text.substring(cut)) else item, canEdit, dragging == item.id,
                        number = if (numbered) items.take(index).count { it.text.isNotBlank() && !it.isDivider() } + 1 else null,
                        onText = { text ->
                            items.indexOfFirst { it.id == item.id }.let { i ->
                                if (i >= 0) {
                                    val full = items[i].text.take(cut) + text
                                    format.edited(item.id, items[i].text, full)
                                    items[i] = items[i].copy(text = full)
                                }
                            }
                        },
                        flags = fullFlags?.let { if (cut > 0 && it.flags.size >= cut) DocFlags(it.flags.copyOfRange(cut, it.flags.size)) else it },
                        flagsVersion = format.version,
                        onFocus = { format.focus = item.id },
                        modifier = if (canFormat && canEdit) Modifier.textStyleMenu(styleLabels, applyStyle) else Modifier,
                        onSelection = { format.selection[item.id] = androidx.compose.ui.text.TextRange(it.start + cut, it.end + cut) },
                        onToggle = { items.indexOfFirst { it.id == item.id }.let { i -> if (i >= 0 && !items[i].isDivider()) items[i] = items[i].copy(checked = !items[i].checked) } },
                        onRemove = {
                            items.removeAll { it.id == item.id }
                            if (items.isEmpty()) items.add(KeepItem(UUID.randomUUID().toString(), "", false))
                            if (items.isNotEmpty() && items[0].indent) items[0] = items[0].copy(indent = false)
                        },
                        onBounds = { rowBounds[item.id] = it },
                        onDragStart = { dragging = item.id },
                        onDragTo = { y ->
                            // Near the top or bottom of the visible part the note scrolls along, so every place can be reached.
                            editorViewport?.let { v ->
                                val edge = 160f
                                val step = when {
                                    y > v.bottom - edge -> (y - (v.bottom - edge)) / 4f
                                    y < v.top + edge -> -((v.top + edge) - y) / 4f
                                    else -> 0f
                                }
                                if (step != 0f) scope.launch { scroll.scrollBy(step) }
                            }
                            overNewCategory = newCategoryZone?.let { y >= it.top && y < it.bottom } == true
                            if (overNewCategory) return@ChecklistRow
                            val from = items.indexOfFirst { it.id == item.id }
                            val over = items.firstOrNull { it.id != item.id && rowBounds[it.id]?.let { r -> y >= r.top && y < r.bottom } == true }
                            val header = headerBounds.entries.firstOrNull { (_, r) -> y >= r.top && y < r.bottom }?.key
                            val slot = slotBounds.entries.firstOrNull { (_, r) -> y >= r.top && y < r.bottom }?.key
                            val firstHeader = headerBounds.values.minOfOrNull { it.top }
                            // Where the item is meant to go: a drop place, a header, an item of another category, or above all
                            // categories (no category).
                            val found = slot != null || header != null || (over != null && groupOf[over.id] != groupOf[item.id]) ||
                                (firstHeader != null && y < firstHeader && groupOf[item.id] != null)
                            hoverTarget = slot
                            if (from >= 0 && editorGroups != null && found) {
                                val target = when {
                                    slot != null -> slot.ifEmpty { null }
                                    header != null -> header
                                    over != null && groupOf[over.id] != groupOf[item.id] -> groupOf[over.id]
                                    else -> null
                                }
                                if (target != groupOf[item.id]) {
                                    setCategory(item.id, target)
                                    val at = if (over != null) items.indexOfFirst { it.id == over.id } else if (target == null) 0 else items.indexOfLast { groupOf[it.id] == target } + 1
                                    val now = items.indexOfFirst { it.id == item.id }
                                    if (at >= 0 && now >= 0 && at != now) items.add(at.coerceAtMost(items.lastIndex), items.removeAt(now))
                                }
                            } else if (from >= 0 && over != null) {
                                val to = items.indexOfFirst { it.id == over.id }
                                if (to >= 0 && to != from) items.add(to, items.removeAt(from))
                            }
                            if (items.isNotEmpty() && items[0].indent) items[0] = items[0].copy(indent = false)
                        },
                        onDragEnd = {
                            // Let go over "New category": its name is asked, then the item goes into it.
                            if (overNewCategory) {
                                newCategoryFor = item.id
                                newCategoryDialog = true
                            }
                            overNewCategory = false
                            hoverTarget = null
                            dragging = null
                        },
                        focusNow = focusItemId == item.id, onFocused = { focusItemId = null },
                        carried = draggingCategory != null && groupOf[item.id] == draggingCategory,
                        swipeRight = itemSwipe.first, swipeLeft = itemSwipe.second,
                        onSwipe = { right -> swipeItems(listOf(item.id), if (right) itemSwipe.first else itemSwipe.second) },
                    )
                  }
                }
            }
            // Headers and items in one flat run, each under its own key: an item dragged into another category keeps its
            // place in the composition (and so the finger that holds it).
            val folds = remember(CategoryFolds.version.intValue) { CategoryFolds.load(context) }
            // While an item is held, a category that has nothing else in it (and "no category" at the top) gets a place of
            // the size of a row to drop it into, so an empty category is as easy to hit as a full one.
            val holding = dragging
            val entries: List<Pair<String?, KeepItem?>> = if (editorGroups == null) items.map { null to it } else buildList {
                if (holding != null && editorGroups.none { it.name == null && it.items.any { i -> i.id != holding } }) add(SLOT_PREFIX to null)
                editorGroups.forEach { group ->
                    if (group.name != null) add(group.name to null)
                    // While a category is held every category shows only its header, so they are quick to pass.
                    val hidden = group.name != null && (CategoryFolds.key(account, noteKey, group.name) in folds || draggingCategory != null)
                    if (!hidden) group.items.forEach { add(group.name to it) }
                    if (holding != null && group.name != null && !hidden && group.items.none { it.id != holding }) add(SLOT_PREFIX + group.name to null)
                }
            }
            // Only what is shown can be dropped onto: rows of folded categories and headers that are gone are forgotten.
            val shownRows = entries.mapNotNull { it.second?.id }.toSet()
            val shownHeaders = editorGroups?.mapNotNull { it.name }?.toSet().orEmpty()
            androidx.compose.runtime.SideEffect {
                rowBounds.keys.retainAll(shownRows)
                headerBounds.keys.retainAll(shownHeaders)
                slotBounds.keys.retainAll(entries.mapNotNull { (n, i) -> if (i == null && n != null && n.startsWith(SLOT_PREFIX)) n.removePrefix(SLOT_PREFIX) else null }.toSet())
            }
            // One frame around each category: its header, its items and its drop place, each drawing its piece.
            val categoryOf = entries.map { (n, i) -> if (i != null) n else n?.let { if (it.startsWith(SLOT_PREFIX)) it.removePrefix(SLOT_PREFIX).ifEmpty { null } else it } }
            fun frameAt(index: Int): Int {
                val c = categoryOf[index] ?: return FRAME_NONE
                val first = index == 0 || categoryOf[index - 1] != c
                val last = index == categoryOf.lastIndex || categoryOf[index + 1] != c
                return when { first && last -> FRAME_ONLY; first -> FRAME_FIRST; last -> FRAME_LAST; else -> FRAME_MIDDLE }
            }
            // The key sits right in the loop: inside a function called from it, it would not let the row move along.
            entries.forEachIndexed { entryIndex, (name, item) -> key(item?.id ?: "category:$name") {
                if (item != null) {
                    // The items of a held category move with its header.
                    Box {
                        ItemEditor(items.indexOfFirst { it.id == item.id }, item, inCategory = name != null, frame = frameAt(entryIndex))
                    }
                } else if (name != null && name.startsWith(SLOT_PREFIX)) {
                    val target = name.removePrefix(SLOT_PREFIX)
                    if (target.isNotEmpty()) Box(Modifier.categoryFrame(frameAt(entryIndex))) { CategoryDropSlot(
                        null, active = hoverTarget == target,
                        modifier = Modifier.onGloballyPositioned { slotBounds[target] = it.boundsInRoot() }.animatePlacement(),
                    ) } else CategoryDropSlot(
                        if (target.isEmpty()) stringResource(R.string.category_none) else null,
                        active = hoverTarget == target,
                        modifier = Modifier.onGloballyPositioned { slotBounds[target] = it.boundsInRoot() }.animatePlacement(),
                    )
                } else if (name != null) {
                    val group = editorGroups!!.first { it.name == name }
                    val folded = CategoryFolds.key(account, noteKey, name) in folds
                    val real = group.items.filter { !it.isDivider() }
                    val notches = headerNotches.getOrPut(name) { androidx.compose.runtime.mutableStateListOf() }
                    // Room above for the half of the header that lies over the frame's top line.
                    Box(
                        // Held, it is drawn where the finger has it: its place in the layout is read while it is placed, in the
                        // same frame as a new order, so passing another category does not make it jump.
                        Modifier.padding(top = 10.dp)
                            .then(
                                if (draggingCategory == name) Modifier.zIndex(1f)
                                    .onPlaced { headerLayoutTop[name] = it.positionInRoot().y }
                                    .graphicsLayer { translationY = categoryFingerTop - (headerLayoutTop[name] ?: categoryFingerTop) }
                                // Not gliding by itself: the frame's other pieces would stay behind when the layout moves.
                                else Modifier.onPlaced { headerLayoutTop[name] = it.positionInRoot().y },
                            )
                            .categoryFrame(frameAt(entryIndex), notches),
                    ) { EditorCategoryHeader(
                        name, Categories.progress(group.items), folded, canEdit,
                        onToggle = { CategoryFolds.set(context, account, noteKey, name, !folded) },
                        onAdd = { addInCategory(name) },
                        notches = notches,
                        modifier = Modifier.onGloballyPositioned { headerBounds[name] = it.boundsInRoot() },
                        onRename = { renameCategory = name },
                        dragged = draggingCategory == name,
                        // The order changes at once when the held header passes the middle of a neighbor's; the others glide.
                        onDragStart = { draggingCategory = name; categoryStartTop = headerLayoutTop[name] ?: 0f; categoryFingerTop = categoryStartTop },
                        onShift = { moved -> categoryFingerTop = categoryStartTop + moved },
                        onDragTo = { y ->
                            val order = sessionCategories.toList()
                            val at = order.indexOf(name)
                            val prev = order.getOrNull(at - 1)?.let { headerBounds[it] }
                            val next = order.getOrNull(at + 1)?.let { headerBounds[it] }
                            if (prev != null && y < prev.center.y) { sessionCategories.removeAt(at); sessionCategories.add(at - 1, name) }
                            else if (next != null && y > next.center.y) { sessionCategories.removeAt(at); sessionCategories.add(at + 1, name) }
                            editorViewport?.let { v ->
                                val edge = 160f
                                val step = when { y > v.bottom - edge -> (y - (v.bottom - edge)) / 4f; y < v.top + edge -> -((v.top + edge) - y) / 4f; else -> 0f }
                                if (step != 0f) scope.launch { scroll.scrollBy(step) }
                            }
                        },
                        onDragEnd = { draggingCategory = null },
                        swipeRight = itemSwipe.first, swipeLeft = itemSwipe.second,
                        onSwipe = { right -> swipeItems(group.items.map { it.id }, if (right) itemSwipe.first else itemSwipe.second) },
                        alone = group.items.isEmpty() || folded || draggingCategory != null,
                    ) }
                }
            } }
            // While an item is held, a place at the end takes it into a new category.
            if (dragging != null && canFormat) NewCategoryDropZone(
                active = overNewCategory,
                modifier = Modifier.onGloballyPositioned { newCategoryZone = it.boundsInRoot() },
            ) else if (newCategoryZone != null) androidx.compose.runtime.SideEffect { newCategoryZone = null }
}

/** Marks a drop place among the rows of the editor; what follows is its category ("" for none). */
private const val SLOT_PREFIX = "\u0000slot:"
