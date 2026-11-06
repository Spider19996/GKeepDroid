package com.gkeepdroid

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** A button of a layout: where it stands is its [zone], null while it is off. The order of the list is the order inside a zone. */
class ButtonItem(val id: String, @DrawableRes val icon: Int, @StringRes val label: Int, val zone: Int?)

/** The places of a miniature; the settings page lays them out as it likes and names each by a key. */
interface ButtonEditorScope {
    /**
     * A place for buttons. Its buttons are drawn inside, aligned by [contentAlignment]; while one is dragged, the place lights up and
     * shows where the button would land. [vertical] places the buttons in a column, [labeled] draws them with their names (the
     * names are for places that have room), otherwise they are squares of [chipSize].
     */
    @Composable
    fun Zone(
        key: Int, modifier: Modifier = Modifier, vertical: Boolean = false, labeled: Boolean = false, chipSize: Dp = 34.dp,
        contentAlignment: Alignment = Alignment.CenterStart, scrollable: Boolean = false,
    )
}

/**
 * The template of the layout settings: a miniature made of places ([ButtonEditorScope.Zone]) where long-pressing a button and
 * dragging it to another place (or in front of another button) moves it. The widget's button bar and the editor's buttons are both
 * built on it; each page only says which places exist and how they are arranged. [onMove] gets the button, the place it was
 * dropped in and the button it was dropped in front of (null: at the end of the place).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonEditor(
    items: List<ButtonItem>,
    onMove: (id: String, zone: Int, beforeId: String?) -> Unit,
    content: @Composable ButtonEditorScope.() -> Unit,
) {
    val move by rememberUpdatedState(onMove)
    val current by rememberUpdatedState(items)
    var dragId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val zones = remember { mutableStateMapOf<Int, Rect>() }
    val columns = remember { mutableStateMapOf<Int, Boolean>() }
    val chips = remember { mutableStateMapOf<String, Rect>() }

    /** The place under the finger and the button it would be dropped in front of: the first one that lies beyond the finger. */
    fun target(): Pair<Int, String?>? {
        val id = dragId ?: return null
        val zone = zones.entries.firstOrNull { it.value.contains(pointer) }?.key ?: return null
        val column = columns[zone] == true
        val before = current.filter { it.zone == zone && it.id != id }.firstOrNull {
            val r = chips[it.id] ?: return@firstOrNull false
            if (column) r.center.y > pointer.y
            else r.center.y > pointer.y + r.height / 2 || (abs(r.center.y - pointer.y) <= r.height / 2 && r.center.x > pointer.x)
        }
        return zone to before?.id
    }
    val landing = target()
    val dragged = current.firstOrNull { it.id == dragId }

    @Composable
    fun Chip(item: ButtonItem, labeled: Boolean, size: Dp) {
        var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        Row(
            Modifier
                .onGloballyPositioned { coords = it; chips[item.id] = it.boundsInRoot() }
                .alpha(if (dragId == item.id) 0.25f else 1f)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .pointerInput(item.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            dragId = item.id
                            pointer = coords?.localToRoot(start) ?: Offset.Zero
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            pointer = coords?.localToRoot(change.position) ?: pointer
                        },
                        onDragEnd = {
                            val drop = target()
                            val id = dragId
                            if (id != null && drop != null) move(id, drop.first, drop.second)
                            dragId = null
                        },
                        onDragCancel = { dragId = null },
                    )
                }
                .then(if (labeled) Modifier.padding(horizontal = 8.dp, vertical = 5.dp) else Modifier.size(size)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (labeled) Arrangement.spacedBy(6.dp) else Arrangement.Center,
        ) {
            Icon(painterResource(item.icon), contentDescription = stringResource(item.label), Modifier.size(if (labeled) 22.dp else (size - 10.dp).coerceAtLeast(12.dp)))
            if (labeled) Text(stringResource(item.label), style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    fun Placeholder(labeled: Boolean, size: Dp) {
        Box(
            Modifier
                .then(if (labeled) Modifier.size(width = 72.dp, height = 34.dp) else Modifier.size(size))
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            if (dragged != null) {
                Icon(
                    painterResource(dragged.icon), null, Modifier.size(if (labeled) 20.dp else (size - 10.dp).coerceAtLeast(12.dp)).alpha(0.6f),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    val scope = object : ButtonEditorScope {
        @Composable
        override fun Zone(
            key: Int, modifier: Modifier, vertical: Boolean, labeled: Boolean, chipSize: Dp, contentAlignment: Alignment, scrollable: Boolean,
        ) {
            val here = landing?.takeIf { dragId != null && it.first == key }
            val inZone = items.filter { it.zone == key }
            @Composable
            fun Buttons() {
                inZone.forEach {
                    if (here != null && here.second == it.id) Placeholder(labeled, chipSize)
                    Chip(it, labeled, chipSize)
                }
                if (here != null && here.second == null) Placeholder(labeled, chipSize)
            }
            Box(
                modifier
                    .onGloballyPositioned { zones[key] = it.boundsInRoot() }
                    .background(
                        if (here != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.medium,
                    )
                    .heightIn(min = if (labeled || !vertical) 40.dp else 0.dp)
                    .padding(2.dp),
                contentAlignment = contentAlignment,
            ) {
                if (vertical) Column(
                    Modifier.then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                ) { Buttons() }
                else if (scrollable) Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Buttons() }
                } else FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Buttons() }
            }
            androidx.compose.runtime.SideEffect { columns[key] = vertical }
        }
    }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }) {
        scope.content()
        // The button under the finger is drawn on top of every place, with its name.
        dragged?.let { button ->
            Row(
                Modifier
                    .offset { IntOffset((pointer.x - origin.x).roundToInt() - 28, (pointer.y - origin.y).roundToInt() - 80) }
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(button.icon), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary)
                Text(stringResource(button.label), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * The buttons under a layout editor: save the layout to a file, load one, and reset it. Every page built on [ButtonEditor] gets
 * them by saying how its file name starts (the date and time are added, so every saved file has its own name), how its layout turns into text and back ([export], [import], which throws if the text
 * is not a layout), and what resetting does. [onStatus] gets the message to show (saved, loaded, could not read).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutFileButtons(
    filePrefix: String,
    export: () -> String,
    import: (String) -> Unit,
    resetLabel: String,
    onReset: () -> Unit,
    onStatus: (String) -> Unit,
    resetEnabled: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val export by rememberUpdatedState(export)
    val import by rememberUpdatedState(import)
    val saved = stringResource(R.string.layout_saved)
    val loaded = stringResource(R.string.layout_loaded)
    val failed = stringResource(R.string.layout_failed)
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onStatus(
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(export().toByteArray()) }
                saved
            } catch (e: Exception) {
                failed
            },
        )
    }
    val load = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onStatus(
            try {
                import(context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Cannot read file"))
                loaded
            } catch (e: Exception) {
                failed
            },
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedButton(onClick = { save.launch("$filePrefix-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}.json") }, shapes = androidx.compose.material3.ButtonDefaults.shapes()) {
            Text(stringResource(R.string.layout_save))
        }
        androidx.compose.material3.OutlinedButton(onClick = { load.launch(arrayOf("*/*")) }, shapes = androidx.compose.material3.ButtonDefaults.shapes()) {
            Text(stringResource(R.string.layout_load))
        }
        androidx.compose.material3.OutlinedButton(enabled = resetEnabled, onClick = onReset, shapes = androidx.compose.material3.ButtonDefaults.shapes()) {
            Text(resetLabel)
        }
    }
}
