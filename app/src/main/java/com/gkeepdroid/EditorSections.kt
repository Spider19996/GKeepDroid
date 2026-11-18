package com.gkeepdroid

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The parts of the note editor that stand on their own: its sections, each with the state it needs only itself. */

/** The side padding of the editor's content, which scrolling rows reach through to the screen edge. */
internal val EDITOR_PADDING = 20.dp

/** Makes the row as wide as the screen: its items scroll out under the edge instead of being cut off at the padding. */
private fun Modifier.fullBleed() = layout { measurable, constraints ->
    val extra = EDITOR_PADDING.roundToPx()
    val wide = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * extra, maxWidth = constraints.maxWidth + 2 * extra))
    layout(constraints.maxWidth, wide.height) { wide.place(-extra, 0) }
}

/** A square preview in the background row; the chosen one is larger and has a thick frame. */
@Composable
private fun BackgroundTile(chosen: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.size(if (chosen) 44.dp else 36.dp)
            .clip(MaterialTheme.shapes.small)
            .border(
                if (chosen) 3.dp else 1.dp,
                if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Color, picture and text color of a note. The color and the picture (Keep's or the user's own photo) are set separately; the
 * first item of each row takes it away. [onBackground] gets "" for none, a name of Keep's pictures or [CUSTOM_BACKGROUND].
 */
@Composable
internal fun ColumnScope.AppearanceSection(
    color: String, onColor: (String) -> Unit,
    background: String, onBackground: (String) -> Unit,
    darkVersion: Boolean, onDarkVersion: (Boolean) -> Unit,
    canEdit: Boolean, isExisting: Boolean, isLocal: Boolean,
    ownText: Int?, onOwnText: (Int?) -> Unit,
    picture: android.graphics.Bitmap?,
    onPickPhoto: () -> Unit,
) {
    val context = LocalContext.current
    var textPicker by remember { mutableStateOf(false) }
    // The color and the picture (Keep's or the user's own photo) are set separately; the first item of each row takes it away.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.color_and_background), modifier = Modifier.weight(1f))
        // Light or dark version of the color and of Keep's picture; on the phone only, and the theme decides until it is chosen here.
        // The row keeps its height whether the button shows or not.
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (isExisting && (color != NoteColors.DEFAULT || (background.isNotEmpty() && background != CUSTOM_BACKGROUND))) {
                androidx.compose.material3.IconToggleButton(
                    checked = darkVersion, onCheckedChange = onDarkVersion, enabled = canEdit,
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        contentColor = LocalContentColor.current, checkedContentColor = LocalContentColor.current,
                    ),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_contrast), contentDescription = stringResource(R.string.background_dark_version),
                        modifier = Modifier.rotate(if (darkVersion) 180f else 0f),
                    )
                }
            }
        }
    }
    Row(Modifier.fullBleed().horizontalScroll(rememberScrollState()).padding(horizontal = EDITOR_PADDING)) {
        NoteColors.names.forEach { name ->
            val argb = NoteColors.argb(name, darkVersion)
            val chosen = name == color
            // The circle keeps its size; the touch area around it is 48 dp.
            RoundTouchTarget(onClick = { onColor(name) }, enabled = canEdit) {
                Box(
                    Modifier.size(if (chosen) 44.dp else 36.dp)
                        .clip(CircleShape)
                        .background(if (argb != null) Color(argb) else Color.Transparent)
                        .border(
                            if (chosen) 3.dp else 1.dp,
                            if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // The default (uncolored) swatch is transparent, so mark it.
                    if (argb == null) Icon(painterResource(R.drawable.ic_color_none), contentDescription = stringResource(R.string.color_background_remove), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    // Keep's pictures exist in Keep only, so local notes have none; the photo of one's own works for every note that exists.
    Row(Modifier.fullBleed().horizontalScroll(rememberScrollState()).padding(horizontal = EDITOR_PADDING)) {
        RoundTouchTarget(onClick = { onBackground("") }, enabled = canEdit, shape = MaterialTheme.shapes.small) {
            BackgroundTile(background.isEmpty()) {
                Icon(painterResource(R.drawable.ic_color_none), contentDescription = stringResource(R.string.color_background_remove), modifier = Modifier.size(20.dp))
            }
        }
        if (!isLocal) NoteBackgrounds.NAMES.forEach { name ->
            val thumb by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, name) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { NoteBackgrounds.load(context, name) }
            }
            RoundTouchTarget(onClick = { onBackground(name) }, enabled = canEdit, shape = MaterialTheme.shapes.small) {
                BackgroundTile(name == background) {
                    thumb?.let { Image(it.asImageBitmap(), contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
        if (isExisting) RoundTouchTarget(onClick = onPickPhoto, enabled = canEdit, shape = MaterialTheme.shapes.small) {
            BackgroundTile(background == CUSTOM_BACKGROUND) {
                val own = picture
                if (background == CUSTOM_BACKGROUND && own != null) {
                    Image(own.asImageBitmap(), contentDescription = stringResource(R.string.background_own), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.background_own), modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    // The note's own text color (only on this phone), for a note of an own photo above all.
    if (isExisting) {
        Text(stringResource(R.string.note_text_color))
        Row(Modifier.fullBleed().horizontalScroll(rememberScrollState()).padding(horizontal = EDITOR_PADDING)) {
            val textChoices = listOf(
                null, 0xFF202124.toInt(), 0xFFFFFFFF.toInt(), 0xFFD93025.toInt(), 0xFFE8710A.toInt(), 0xFFF9AB00.toInt(),
                0xFF188038.toInt(), 0xFF1A73E8.toInt(), 0xFF9334E6.toInt(),
            )
            textChoices.forEach { choice ->
                val chosen = ownText == choice
                RoundTouchTarget(onClick = { onOwnText(choice) }, enabled = canEdit) {
                    Box(
                        Modifier.size(if (chosen) 44.dp else 36.dp)
                            .clip(CircleShape)
                            .background(if (choice != null) Color(choice) else Color.Transparent)
                            .border(
                                if (chosen) 3.dp else 1.dp,
                                if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (choice == null) Icon(painterResource(R.drawable.ic_color_none), contentDescription = stringResource(R.string.note_text_color_auto), modifier = Modifier.size(20.dp))
                    }
                }
            }
            // Any other color.
            RoundTouchTarget(onClick = { textPicker = true }, enabled = canEdit) {
                val custom = ownText != null && ownText !in textChoices
                Box(
                    Modifier.size(if (custom) 44.dp else 36.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        .border(
                            if (custom) 3.dp else 1.dp,
                            if (custom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        ),
                )
            }
        }
        if (textPicker) {
            ColorPickerDialog(initial = ownText ?: 0xFF202124.toInt(), onDismiss = { textPicker = false }, onPick = { onOwnText(it); textPicker = false })
        }
    }

}


/**
 * Labels: tap a known label to add or remove it; a new name creates the label on save. Creating and deleting labels in the
 * dialog shows at once, Keep follows in the background.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.LabelsSection(account: String, labels: List<String>, onLabels: (List<String>) -> Unit, canEdit: Boolean) {
    val context = LocalContext.current
    var newLabel by remember { mutableStateOf("") }
    var manageLabels by remember { mutableStateOf(false) }
    var labelsVersion by remember { mutableStateOf(0) }
    var addedLabels by remember { mutableStateOf(setOf<String>()) }
    var removedLabels by remember { mutableStateOf(setOf<String>()) }
    var labelToDelete by remember { mutableStateOf<String?>(null) }
    // Labels: tap a known label to add or remove it; a new name creates the label on save.
    val knownLabels = remember(account, labelsVersion) {
        (NotesCache.labels(context, account) + addedLabels).filter { it !in removedLabels }
            .distinct().sortedBy { it.lowercase() }
    }
    val allLabels = (knownLabels + labels).distinct().sortedBy { it.lowercase() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (allLabels.isEmpty()) R.string.no_labels else R.string.labels_title), style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { manageLabels = true }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.manage_labels)) }
    }
    if (allLabels.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        allLabels.forEach { name ->
            FilterChip(
                selected = name in labels,
                enabled = canEdit,
                onClick = { onLabels(if (name in labels) labels - name else labels + name) },
                label = { Text(name) },
            )
        }
    }
    if (allLabels.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = newLabel,
            onValueChange = { newLabel = it },
            label = { Text(stringResource(R.string.new_label_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            enabled = newLabel.isNotBlank(),
            onClick = {
                val name = newLabel.trim()
                if (name !in labels) onLabels(labels + name)
                newLabel = ""
            },
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.add_label)) }
    }

    if (manageLabels) {
        var creating by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manageLabels = false },
            title = { Text(stringResource(R.string.manage_labels)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = creating,
                            onValueChange = { creating = it },
                            label = { Text(stringResource(R.string.new_label_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = creating.isNotBlank(),
                            onClick = {
                                val name = creating.trim()
                                creating = ""
                                addedLabels = addedLabels + name
                                removedLabels = removedLabels - name
                                labelsVersion++
                                KeepRepository.createLabel(context, account, name)
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.add_label)) }
                    }
                    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                        knownLabels.forEach { name ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, Modifier.weight(1f))
                                TextButton(onClick = { labelToDelete = name }, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(R.string.delete_label))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { manageLabels = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.done)) }
            },
        )
    }

    labelToDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { labelToDelete = null },
            text = { Text(stringResource(R.string.delete_label_confirm, name)) },
            confirmButton = {
                TextButton(onClick = {
                    labelToDelete = null
                    onLabels(labels - name)
                    removedLabels = removedLabels + name
                    addedLabels = addedLabels - name
                    labelsVersion++
                    KeepRepository.deleteLabel(context, account, name)
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.delete_label)) }
            },
            dismissButton = {
                TextButton(onClick = { labelToDelete = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/** Undo and Redo, small icon buttons for the title row of the editor. */
@Composable
internal fun UndoRedoButtons(canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit) {
    for ((icon, label, enabled, action) in listOf(
        UndoStep(R.drawable.ic_undo, R.string.undo, canUndo, onUndo), UndoStep(R.drawable.ic_redo, R.string.redo, canRedo, onRedo),
    )) {
        IconButton(onClick = action, enabled = enabled, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
            Icon(painterResource(icon), contentDescription = stringResource(label))
        }
    }
}

private class UndoStep(val icon: Int, val label: Int, val enabled: Boolean, val action: () -> Unit) {
    operator fun component1() = icon
    operator fun component2() = label
    operator fun component3() = enabled
    operator fun component4() = action
}

/** The reminder button of the title row: set, it shows the accent color; with notifications off, its icon warns. */
@Composable
internal fun ReminderButton(reminderSet: Boolean, notificationsOn: Boolean, onReminders: () -> Unit) {
    FilledTonalIconToggleButton(
        checked = reminderSet,
        onCheckedChange = { onReminders() },
        colors = quietToggleColors(
            checkedContainer = MaterialTheme.colorScheme.primary, checkedContent = MaterialTheme.colorScheme.onPrimary,
        ),
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_alarm), contentDescription = stringResource(R.string.reminders_title),
            tint = if (notificationsOn) LocalContentColor.current else MaterialTheme.colorScheme.error,
        )
    }
}

/** Toggle buttons of the title row: no container until they are on, so the row stays calm. */
@Composable
internal fun quietToggleColors(
    checkedContainer: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    checkedContent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer,
) = IconButtonDefaults.filledTonalIconToggleButtonColors(
    containerColor = androidx.compose.ui.graphics.Color.Transparent,
    contentColor = LocalContentColor.current,
    checkedContainerColor = checkedContainer,
    checkedContentColor = checkedContent,
)

/** The header of a category in the editor's checklist: its name and number of open items fold it, the "+" adds an item to it. */
@Composable
internal fun EditorCategoryHeader(
    name: String, open: Int, folded: Boolean, canEdit: Boolean, onToggle: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier,
    allChecked: Boolean = false, onCheckAll: () -> Unit = {}, dragged: Boolean = false,
    onDragStart: () -> Unit = {}, onDragTo: (Float) -> Unit = {}, onDragEnd: () -> Unit = {},
    swipeRight: Int = ITEM_SWIPE_NONE, swipeLeft: Int = ITEM_SWIPE_NONE, onSwipe: (Boolean) -> Unit = {},
) {
    // Swiped like an item, for all items of the category; the row stops where the symbol is shown.
    val maxPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    var offsetX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    Box(modifier.fillMaxWidth()) {
    if (offsetX != 0f) {
        val toRight = offsetX > 0f
        val (color, icon, tint) = swipeLook(if (toRight) swipeRight else swipeLeft)
        Box(
            Modifier.matchParentSize().padding(top = 8.dp).clip(MaterialTheme.shapes.medium).background(color).padding(horizontal = 20.dp),
            contentAlignment = if (toRight) androidx.compose.ui.Alignment.CenterStart else androidx.compose.ui.Alignment.CenterEnd,
        ) { Icon(painterResource(icon), contentDescription = null, tint = tint) }
    }
    // Held, the header follows the finger (as an item does); the others make room.
    var rowTop by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    var fingerY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    var grab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    var handle by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    if (!dragged && fingerY != null) fingerY = null
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(top = 8.dp)
            .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
            .draggable(
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                enabled = canEdit && (swipeRight != ITEM_SWIPE_NONE || swipeLeft != ITEM_SWIPE_NONE),
                state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                    offsetX = (offsetX + delta).coerceIn(if (swipeLeft != ITEM_SWIPE_NONE) -maxPx else 0f, if (swipeRight != ITEM_SWIPE_NONE) maxPx else 0f)
                },
                onDragStopped = {
                    val passed = kotlin.math.abs(offsetX) >= maxPx * 0.75f
                    val toRight = offsetX > 0f
                    androidx.compose.animation.core.animate(offsetX, 0f) { value, _ -> offsetX = value }
                    if (passed) onSwipe(toRight)
                },
            )
            .onGloballyPositioned { rowTop = it.boundsInRoot().top }
            .then(if (dragged) Modifier.zIndex(1f) else Modifier)
            .graphicsLayer {
                val y = fingerY
                translationY = if (dragged && y != null) y - grab - rowTop else 0f
                if (dragged) { shadowElevation = 12f; scaleX = 1.02f; scaleY = 1.02f }
            }
            .background(if (dragged) MaterialTheme.colorScheme.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.medium),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        // The same button as an item has: a tap ticks (or unticks) all items of the category, holding it moves the category
        // with its items.
        if (canEdit) Box(
            Modifier.width(48.dp).height(56.dp)
                .onGloballyPositioned { handle = it }
                .pointerInput(name) { detectTapGestures(onTap = { onCheckAll() }) }
                .pointerInput(name) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            handle?.localToRoot(start)?.y?.let { fingerY = it; grab = it - rowTop }
                            onDragStart()
                        },
                        onDrag = { change, _ -> change.consume(); handle?.localToRoot(change.position)?.y?.let { fingerY = it; onDragTo(it) } },
                        onDragEnd = { fingerY = null; onDragEnd() },
                        onDragCancel = { fingerY = null; onDragEnd() },
                    )
                },
            contentAlignment = androidx.compose.ui.Alignment.CenterStart,
        ) {
            androidx.compose.foundation.layout.Column(
                Modifier.padding(start = 11.dp).size(width = 26.dp, height = 40.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall).padding(1.5.dp),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(
                        painterResource(if (allChecked) R.drawable.ic_done_all else R.drawable.ic_done), contentDescription = null,
                        tint = if (allChecked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.5.dp).background(MaterialTheme.colorScheme.outline))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(3) { Box(Modifier.size(width = 12.dp, height = 1.5.dp).background(MaterialTheme.colorScheme.onSurfaceVariant)) }
                    }
                }
            }
        }
        androidx.compose.foundation.layout.Row(
            Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1)
            if (open > 0) Text("  $open", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Icon(
                painterResource(if (folded) R.drawable.ic_arrow_right else R.drawable.ic_arrow_down),
                contentDescription = stringResource(if (folded) R.string.unfold_all else R.string.fold_all), modifier = Modifier.size(20.dp),
            )
        }
        if (canEdit) IconButton(onClick = onAdd, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.category_add_item, name))
        }
    }
}
}

/** Shown at the end of a checklist while an item is held: letting it go here puts it into a new category. */
@Composable
internal fun NewCategoryDropZone(active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.medium)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (active) 2.dp else 1.dp, color, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = color)
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.category_new), color = color, style = MaterialTheme.typography.titleSmall)
    }
}

/** A category to choose; held, [onLongClick] runs (an earlier category turns red, held again it is forgotten). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun CategoryChip(name: String, armed: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val color = if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    Box(
        Modifier.clip(MaterialTheme.shapes.small)
            .background(if (armed) MaterialTheme.colorScheme.errorContainer else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, color, MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = if (armed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
    }
}

/** A place to drop a held item into a category that has nothing else in it; [label] names it where no header does. */
@Composable
internal fun CategoryDropSlot(label: String?, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier.fillMaxWidth().padding(top = 4.dp).height(56.dp).clip(MaterialTheme.shapes.medium)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (active) 2.dp else 1.dp, color, MaterialTheme.shapes.medium),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        if (label != null) Text(label, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
    }
}
