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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    Row(Modifier.horizontalScroll(rememberScrollState())) {
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
    Row(Modifier.horizontalScroll(rememberScrollState())) {
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
        Row(Modifier.horizontalScroll(rememberScrollState())) {
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
 * Cancel and save. A button group: the pressed button grows a little and its neighbor makes room. Both always fit, so
 * nothing ever moves into the overflow menu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EditorButtonBar(
    canEdit: Boolean, saving: Boolean, isList: Boolean, onCancel: () -> Unit, onSave: () -> Unit,
) {
    val barHeight = ButtonDefaults.MediumContainerHeight
    val barPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ButtonGroup(
        overflowIndicator = {},
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        customItem(
            buttonGroupContent = {
                val pressed = remember { MutableInteractionSource() }
                androidx.compose.material3.FilledTonalButton(
                    onClick = onCancel, enabled = !saving,
                    shapes = ButtonDefaults.shapesFor(barHeight), contentPadding = barPadding, interactionSource = pressed,
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = barHeight).animateWidth(pressed),
                ) { Text(stringResource(R.string.cancel), style = ButtonDefaults.textStyleFor(barHeight), textAlign = TextAlign.Center) }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                val pressed = remember { MutableInteractionSource() }
                Button(
                    onClick = onSave,
                    shapes = ButtonDefaults.shapesFor(barHeight), contentPadding = barPadding, interactionSource = pressed,
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = barHeight).animateWidth(pressed),
                ) {
                    Text(
                        stringResource(if (canEdit) R.string.save else if (isList) R.string.edit_list else R.string.edit_note),
                        style = ButtonDefaults.textStyleFor(barHeight), textAlign = TextAlign.Center,
                    )
                }
            },
            menuContent = {},
        )
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
