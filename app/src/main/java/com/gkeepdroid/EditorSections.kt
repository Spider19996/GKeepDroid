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
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawBehind
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

/**
 * The header of a category in the editor's checklist, set into the top line of its frame as the label of a text field is: the name
 * at the left, the progress, the arrow that folds it and the "+" that adds an item at the right. A tap on the name renames the
 * category, holding it moves the category with its items; swiping the line does what swiping an item does, for all of them.
 * [notches] gets the stretches of the line it covers (in pixels from the left of the frame), so the frame leaves them open.
 */
@Composable
internal fun EditorCategoryHeader(
    name: String, progress: String, folded: Boolean, canEdit: Boolean, onToggle: () -> Unit, onAdd: () -> Unit,
    notches: androidx.compose.runtime.snapshots.SnapshotStateList<Pair<Float, Float>>, modifier: Modifier = Modifier,
    onRename: () -> Unit = {}, dragged: Boolean = false,
    onDragStart: () -> Unit = {}, onDragTo: (Float) -> Unit = {}, onDragEnd: () -> Unit = {}, onShift: (Float) -> Unit = {},
    swipeRight: Int = ITEM_SWIPE_NONE, swipeLeft: Int = ITEM_SWIPE_NONE, onSwipe: (Boolean) -> Unit = {},
    /** Nothing under the header (the category is empty, folded or held): it is a box of a hand's height then, not just a line. */
    alone: Boolean = false,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // The gestures call what the header is given now (the order changes while it is dragged).
    val dragTo by androidx.compose.runtime.rememberUpdatedState(onDragTo)
    val dragStart by androidx.compose.runtime.rememberUpdatedState(onDragStart)
    val dragEnd by androidx.compose.runtime.rememberUpdatedState(onDragEnd)
    val shift by androidx.compose.runtime.rememberUpdatedState(onShift)
    val rename by androidx.compose.runtime.rememberUpdatedState(onRename)
    val swipe by androidx.compose.runtime.rememberUpdatedState(onSwipe)
    var rowTop by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    var grab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    var nameCoords by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val maxPx = with(density) { 72.dp.toPx() }
    var offsetX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    var nameRange by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f to 0f) }
    var toolsRange by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f to 0f) }
    val gapPx = with(density) { 4.dp.toPx() }
    androidx.compose.runtime.SideEffect {
        val wanted = listOf(nameRange.first - gapPx to nameRange.second + gapPx, toolsRange.first - gapPx to toolsRange.second + gapPx)
        if (notches.toList() != wanted) { notches.clear(); notches.addAll(wanted) }
    }
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth()
            .onGloballyPositioned { rowTop = it.boundsInRoot().top }
            // Its middle lies on the frame's top line: half of it above the frame, half inside.
            .layout { m, c ->
                val p = m.measure(c)
                // Below the line it takes a little less than its lower half, so the first item is not far from the name.
                val below = if (alone) 36.dp.roundToPx() else (p.height / 2 - 14.dp.roundToPx()).coerceAtLeast(0)
                layout(p.width, below) { p.place(0, -p.height / 2 + (if (alone) 4.dp.roundToPx() else 0)) }
            }
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
                    if (passed) swipe(toRight)
                },
            ),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            name, style = labelStyle, color = if (dragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1,
            // After the rounding of the frame's corner, as a text field's label sits after its corner.
            modifier = Modifier.padding(start = 24.dp)
                .onGloballyPositioned { c ->
                    nameCoords = c
                    val x = c.positionInParent().x
                    nameRange = x to x + c.size.width
                }
                .clip(MaterialTheme.shapes.small)
                .pointerInput(name, canEdit) {
                    if (!canEdit) return@pointerInput
                    detectTapGestures(onTap = { rename() })
                }
                .pointerInput(name, canEdit) {
                    if (!canEdit) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            nameCoords?.localToRoot(start)?.y?.let { grab = it }
                            dragStart()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            // Where the header's top should be now; the editor moves it there from its place in the layout.
                            // How far the finger has come since it took hold.
                            nameCoords?.localToRoot(change.position)?.y?.let { y -> shift(y - grab); dragTo(y) }
                        },
                        onDragEnd = { dragEnd() },
                        onDragCancel = { dragEnd() },
                    )
                }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        androidx.compose.foundation.layout.Row(
            Modifier.padding(end = 24.dp).onGloballyPositioned { c ->
                val x = c.positionInParent().x
                toolsRange = x to x + c.size.width
            },
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            Box(
                Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onToggle),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Icon(
                    painterResource(if (folded) R.drawable.ic_arrow_left else R.drawable.ic_arrow_down),
                    contentDescription = stringResource(if (folded) R.string.unfold_all else R.string.fold_all), modifier = Modifier.size(24.dp),
                )
            }
            if (canEdit) Box(
                Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onAdd),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.category_add_item, name), modifier = Modifier.size(24.dp))
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

const val FRAME_NONE = 0
const val FRAME_FIRST = 1
const val FRAME_MIDDLE = 2
const val FRAME_LAST = 3
const val FRAME_ONLY = 4

/**
 * One piece of the frame around a category: the first row draws the top with its round corners, the last one the bottom, all of
 * them the sides; the sides reach over the gaps between the rows, so the pieces make one frame. The row moves in a little.
 */
@Composable
internal fun Modifier.categoryFrame(position: Int, notches: List<Pair<Float, Float>> = emptyList()): Modifier {
    if (position == FRAME_NONE) return this
    // The same line as the fields have (1 dp in the outline color).
    val color = MaterialTheme.colorScheme.outline
    val last = position == FRAME_LAST || position == FRAME_ONLY
    val first = position == FRAME_FIRST || position == FRAME_ONLY
    // The gap to what follows is the editor's own between its rows, as between two fields.
    return this.drawBehind {
        val stroke = 1.dp.toPx()
        val radius = 20.dp.toPx()
        // Each piece draws down over the gap to the next row (the editor's 12 dp), exactly to where that one begins: the pieces
        // meet without overlapping, so the sides show no seam.
        val reach = 12.dp.toPx()
        // The whole line lies inside the piece at the top and the bottom, so it is as thick there as at the sides.
        val top = if (first) stroke / 2 else -radius
        val bottom = if (last) size.height - stroke / 2 else size.height + reach + radius
        // Where it may draw: its own height (and over the gaps), less the stretches of the top line that the header covers, as
        // a text field's line is left open at its label.
        val area = androidx.compose.ui.graphics.Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(-reach, 0f, size.width + reach, if (last) size.height else size.height + reach))
        }
        val inset = 8.dp.toPx()
        val open = if (!first) area else notches.filter { it.second > it.first }.fold(area) { acc, (from, to) ->
            val hole = androidx.compose.ui.graphics.Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(inset + from, -reach, inset + to, 3.dp.toPx()))
            }
            androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, acc, hole)
        }
        clipPath(open) {
            drawRoundRect(
                color, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, top),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, bottom - top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
            )
        }
    }.padding(start = 8.dp, end = 8.dp, bottom = if (last) 8.dp else 0.dp)
}

/** Where a held category would be put: a line in the primary color. */
@Composable
internal fun DropLine() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).height(3.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary))
}
