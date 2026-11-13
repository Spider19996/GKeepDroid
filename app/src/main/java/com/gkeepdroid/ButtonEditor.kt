package com.gkeepdroid

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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

/** What a button may do, as the template shows it: each kind has its colors and its line in the legend. */
enum class ButtonKind { FREE, LIMITED, STAYS, FIXED }

/**
 * A button of a layout: where it stands is its [zone], null while it is off. The order of the list is the order inside a zone.
 * [allowed] says where it may be dragged: null anywhere, a set only to those places (and in front of or behind others in its
 * own), only its own place: it stays where it is and can be put in another order there, an empty set nowhere.
 */
class ButtonItem(
    val id: String, @DrawableRes val icon: Int, @StringRes val label: Int, val zone: Int?, val allowed: Set<Int>? = null,
) {
    val kind = when {
        allowed == null -> ButtonKind.FREE
        allowed.isEmpty() -> ButtonKind.FIXED
        zone != null && allowed.all { it == zone } -> ButtonKind.STAYS
        else -> ButtonKind.LIMITED
    }
    val fixed get() = kind == ButtonKind.FIXED
    fun canGoTo(place: Int) = !fixed && (allowed == null || place in allowed || place == zone)
}

/** The places of a miniature; the settings page lays them out as it likes and names each by a key. */
interface ButtonEditorScope {
    /**
     * A place for buttons. Its buttons are drawn inside, aligned by [contentAlignment]; while one is dragged, the place lights up and
     * shows where the button would land. [vertical] places the buttons in a column, [labeled] draws them with their names (the
     * names are for places that have room), otherwise they are squares of [chipSize]. A [caption] names the place above it; it is
     * for places that grow with their buttons (a place of a fixed height has no room for one). The buttons lie in the place as they
     * will lie in the real thing: [align] says on which side of it they are put (a place at the right edge has them at its right).
     */
    @Composable
    fun Zone(
        key: Int, modifier: Modifier = Modifier, vertical: Boolean = false, labeled: Boolean = false, chipSize: Dp = 34.dp,
        contentAlignment: Alignment = Alignment.CenterStart, scrollable: Boolean = false, @StringRes caption: Int? = null,
        align: Alignment.Horizontal = Alignment.Start,
    )
}

/**
 * What the preview of a page gets: the buttons as they would stand if a dragged one were dropped now, and the button that is
 * tapped or dragged, to be drawn highlighted.
 */
class ButtonPreviewState(val items: List<ButtonItem>, val highlightId: String?)

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
    /** A mirror of the layout as the real thing looks, shown below the places; it follows a drag, and a tap on a button highlights it. */
    preview: (@Composable (ButtonPreviewState) -> Unit)? = null,
    content: @Composable ButtonEditorScope.() -> Unit,
) {
    val move by rememberUpdatedState(onMove)
    val current by rememberUpdatedState(items)
    var dragId by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val zones = remember { mutableStateMapOf<Int, Rect>() }
    val columns = remember { mutableStateMapOf<Int, Boolean>() }
    val chips = remember { mutableStateMapOf<String, Rect>() }

    /** The place under the finger and the button it would be dropped in front of: the first one that lies beyond the finger. */
    fun target(): Pair<Int, String?>? {
        val id = dragId ?: return null
        val zone = zones.entries.firstOrNull { it.value.contains(pointer) }?.key ?: return null
        // A place the button may not go to does not light up and takes nothing.
        if (current.firstOrNull { it.id == id }?.canGoTo(zone) == false) return null
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

    /** The layout as it would be after dropping the dragged button where it is now. */
    fun withDrop(): List<ButtonItem> {
        val d = dragged ?: return items
        val drop = landing ?: return items
        val rest = items.filter { it.id != d.id }
        val at = drop.second?.let { b -> rest.indexOfFirst { it.id == b } }?.takeIf { it >= 0 }
            ?: rest.indexOfLast { it.zone == drop.first }.let { if (it >= 0) it + 1 else rest.size }
        return rest.take(at) + ButtonItem(d.id, d.icon, d.label, drop.first, d.allowed) + rest.drop(at)
    }

    @Composable
    fun Chip(item: ButtonItem, labeled: Boolean, size: Dp) {
        var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        Row(
            Modifier
                .onGloballyPositioned { coords = it; chips[item.id] = it.boundsInRoot() }
                .alpha(if (dragId == item.id) 0.25f else 1f)
                .then(if (selectedId == item.id) Modifier.border(BorderStroke(4.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)), MaterialTheme.shapes.medium) else Modifier)
                .border(BorderStroke(if (item.kind == ButtonKind.FREE) 1.dp else 2.dp, chipEdge(item.kind)), MaterialTheme.shapes.medium)
                .background(chipFill(item.kind), MaterialTheme.shapes.medium)
                .clickable { selectedId = item.id }
                .pointerInput(item.id, item.fixed) {
                    if (item.fixed) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            dragId = item.id
                            selectedId = item.id
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
            caption: Int?, align: Alignment.Horizontal,
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
            @Composable
            fun Place(placeModifier: Modifier) {
                Box(
                    placeModifier
                        .onGloballyPositioned { zones[key] = it.boundsInRoot() }
                        .alpha(if (dragged != null && !dragged.canGoTo(key)) 0.4f else 1f)
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
                        horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) { Buttons() }
                    else if (scrollable) Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, align), verticalArrangement = Arrangement.spacedBy(4.dp)) { Buttons() }
                    } else FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp, align), verticalArrangement = Arrangement.spacedBy(3.dp)) { Buttons() }
                }
            }
            // The name stands above the place, outside of what lights up.
            if (caption == null) Place(modifier) else Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(caption), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Place(Modifier.fillMaxWidth())
            }
            androidx.compose.runtime.SideEffect { columns[key] = vertical }
        }
    }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // On top, so it is on the screen together with the places while one drags; made small like a phone seen from afar.
            if (preview != null) {
                Text(stringResource(R.string.button_preview), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScaledPreview { preview(ButtonPreviewState(withDrop(), dragId ?: selectedId)) }
            }
            scope.content()
        }
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
    // Save and load side by side with the same width, reset below them across the whole width: they never wrap unevenly.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(
                onClick = { save.launch("$filePrefix-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}.json") },
                modifier = Modifier.weight(1f), shapes = androidx.compose.material3.ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.layout_save), maxLines = 1) }
            androidx.compose.material3.OutlinedButton(
                onClick = { load.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f), shapes = androidx.compose.material3.ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.layout_load), maxLines = 1) }
        }
        androidx.compose.material3.OutlinedButton(
            enabled = resetEnabled, onClick = onReset, modifier = Modifier.fillMaxWidth(), shapes = androidx.compose.material3.ButtonDefaults.shapes(),
        ) { Text(resetLabel, maxLines = 1) }
    }
}

/**
 * Fill and edge of a button by what it may do: anywhere (plain), some places (orange), its own place only (violet), nowhere (red).
 * Fixed hues, as the colors of the theme are too close to each other; a tint of the hue over the surface keeps them readable in
 * the light and the dark theme.
 */
private val LIMITED_COLOR = Color(0xFFF29900)
private val STAYS_COLOR = Color(0xFFE5484D)
private val FIXED_COLOR = Color(0xFFE5484D)

@Composable
private fun chipFill(kind: ButtonKind): Color {
    val surface = MaterialTheme.colorScheme.surface
    return when (kind) {
        ButtonKind.FREE -> surface
        ButtonKind.LIMITED -> LIMITED_COLOR.copy(alpha = 0.28f).compositeOver(surface)
        ButtonKind.STAYS -> STAYS_COLOR.copy(alpha = 0.28f).compositeOver(surface)
        ButtonKind.FIXED -> FIXED_COLOR.copy(alpha = 0.28f).compositeOver(surface)
    }
}

@Composable
private fun chipEdge(kind: ButtonKind) = when (kind) {
    ButtonKind.FREE -> MaterialTheme.colorScheme.outline
    ButtonKind.LIMITED -> LIMITED_COLOR
    ButtonKind.STAYS -> STAYS_COLOR
    ButtonKind.FIXED -> FIXED_COLOR
}

private val PREVIEW_WIDTH = 360.dp
private val PREVIEW_HEIGHT = 460.dp
private const val PREVIEW_SCALE = 0.55f

/**
 * Draws [content] in the size of a phone screen (so the real buttons take the room they really take) and shrinks it, so that the
 * preview and the places fit on the screen together.
 */
@Composable
private fun ScaledPreview(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(PREVIEW_WIDTH * PREVIEW_SCALE, PREVIEW_HEIGHT * PREVIEW_SCALE)) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
                    .graphicsLayer(scaleX = PREVIEW_SCALE, scaleY = PREVIEW_SCALE, transformOrigin = TransformOrigin(0f, 0f)),
            ) { content() }
        }
    }
}
