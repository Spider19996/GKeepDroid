package com.gkeepdroid

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlin.math.roundToInt

/** One button of the bar as the layout editor shows it; [side] is null while the button is off. */
data class LayoutButton(val id: String, @DrawableRes val icon: Int, @StringRes val label: Int, val side: Int?, val slot: Int = ALIGN_CENTER)

private const val OFF_ZONE = -1

private data class DropTarget(val zone: Int, val side: Int?, val slot: Int, val beforeId: String?)

/**
 * A miniature of the widget: the four edges are the bars, the middle lists the buttons that are off.
 * Long-press a button and drag it onto an edge to show it there, or into the middle to hide it.
 */
@Composable
fun ButtonLayoutEditor(
    buttons: List<LayoutButton>,
    /** A button was dropped on [side] at [slot] (side null: hidden), in front of [beforeId] or at the end if that is null. */
    onMove: (id: String, side: Int?, slot: Int, beforeId: String?) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val move by rememberUpdatedState(onMove)
    var dragId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val zones = remember { mutableStateMapOf<Int, Rect>() }
    val chips = remember { mutableStateMapOf<String, Rect>() }

    fun zoneAt(p: Offset): Int? = zones.entries.firstOrNull { it.value.contains(p) }?.key
    val hover = if (dragId != null) zoneAt(pointer) else null

    /** Where the dragged button would land if it were dropped now. */
    fun target(): DropTarget? {
        val id = dragId ?: return null
        val zone = zoneAt(pointer) ?: return null
        val side = if (zone == OFF_ZONE) null else zone / 10
        val slot = if (zone == OFF_ZONE) ALIGN_CENTER else zone % 10
        // Dropped between the buttons of a place: in front of the first one that lies beyond the finger.
        val vertical = side == POS_LEFT || side == POS_RIGHT
        val before = buttons.filter { it.side == side && it.slot == slot && it.id != id }.firstOrNull {
            val r = chips[it.id] ?: return@firstOrNull false
            if (vertical) r.center.y > pointer.y else r.center.x > pointer.x
        }
        return DropTarget(zone, side, slot, before?.id)
    }
    val landing = target()

    // The editor has a fixed height. Icons shrink with the number of buttons on a side, so none is ever cut off.
    val middleHeight = 170.dp
    fun count(side: Int) = buttons.count { it.side == side }
    fun fit(length: Dp, n: Int): Dp = ((length - 8.dp - 3.dp * (n - 1).coerceAtLeast(0)) / n.coerceAtLeast(1))
    val chipSize = minOf(
        34.dp,
        fit(maxWidth, maxOf(count(POS_TOP), count(POS_BOTTOM))),
        fit(middleHeight, maxOf(count(POS_LEFT), count(POS_RIGHT))),
    ).coerceAtLeast(16.dp)

    @Composable
    fun Chip(button: LayoutButton, labeled: Boolean) {
        val inset = if (labeled) 5.dp else 0.dp
        var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        Row(
            Modifier
                .onGloballyPositioned { coords = it; chips[button.id] = it.boundsInRoot() }
                .alpha(if (dragId == button.id) 0.25f else 1f)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .pointerInput(button.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            dragId = button.id
                            pointer = coords?.localToRoot(start) ?: Offset.Zero
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            pointer = coords?.localToRoot(change.position) ?: pointer
                        },
                        onDragEnd = {
                            val id = dragId
                            val drop = target()
                            if (id != null && drop != null) move(id, drop.side, drop.slot, drop.beforeId)
                            dragId = null
                        },
                        onDragCancel = { dragId = null },
                    )
                }
                .then(if (labeled) Modifier else Modifier.size(chipSize))
                .padding(inset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (labeled) Arrangement.spacedBy(5.dp) else Arrangement.Center,
        ) {
            Icon(painterResource(button.icon), contentDescription = stringResource(button.label), Modifier.size(if (labeled) 22.dp else (chipSize - 10.dp).coerceAtLeast(12.dp)))
            if (labeled) Text(stringResource(button.label), style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    fun Zone(key: Int, modifier: Modifier, content: @Composable () -> Unit) {
        val active = hover == key
        Box(
            modifier
                .onGloballyPositioned { zones[key] = it.boundsInRoot() }
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(10.dp),
                )
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }

    // A bar has three equal drop regions (start, middle, end). The buttons are drawn where the widget puts
    // them, and while dragging a placeholder shows the exact spot the button would take.
    @Composable
    fun Bar(side: Int, modifier: Modifier) {
        val vertical = side == POS_LEFT || side == POS_RIGHT
        val places = listOf(ALIGN_START, ALIGN_CENTER, ALIGN_END)
        val dragged = buttons.firstOrNull { it.id == dragId }

        @Composable
        fun Group(slot: Int) {
            val inPlace = buttons.filter { it.side == side && it.slot == slot }
            val here = landing?.takeIf { it.zone == side * 10 + slot }
            @Composable
            fun Placeholder() {
                Box(
                    Modifier
                        .size(chipSize)
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (dragged != null) {
                        Icon(
                            painterResource(dragged.icon), null,
                            Modifier.size((chipSize - 10.dp).coerceAtLeast(12.dp)).alpha(0.6f),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            @Composable
            fun Items() {
                inPlace.forEach {
                    if (here != null && here.beforeId == it.id) Placeholder()
                    Chip(it, false)
                }
                if (here != null && here.beforeId == null) Placeholder()
            }
            if (vertical) Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) { Items() }
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { Items() }
        }

        Box(modifier) {
            // The drop regions, drawn behind the buttons.
            if (vertical) {
                Column(Modifier.matchParentSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    places.forEach { Zone(side * 10 + it, Modifier.weight(1f).fillMaxWidth()) {} }
                }
            } else {
                Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    places.forEach { Zone(side * 10 + it, Modifier.weight(1f).fillMaxHeight()) {} }
                }
            }
            // The buttons: equal outer groups keep the middle one centered, as in the widget.
            if (vertical) {
                Column(Modifier.matchParentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { Group(ALIGN_START) }
                    Group(ALIGN_CENTER)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) { Group(ALIGN_END) }
                }
            } else {
                Row(Modifier.matchParentSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) { Group(ALIGN_START) }
                    Group(ALIGN_CENTER)
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) { Group(ALIGN_END) }
                }
            }
        }
    }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Bar(POS_TOP, Modifier.fillMaxWidth().height(40.dp))
            Row(Modifier.fillMaxWidth().height(middleHeight), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Bar(POS_LEFT, Modifier.width(40.dp).fillMaxHeight())
                Zone(OFF_ZONE, Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) { buttons.filter { it.side == null }.forEach { Chip(it, false) } }
                    }
                }
                Bar(POS_RIGHT, Modifier.width(40.dp).fillMaxHeight())
            }
            Bar(POS_BOTTOM, Modifier.fillMaxWidth().height(40.dp))
        }
        // The button under the finger is drawn on top of every zone.
        buttons.firstOrNull { it.id == dragId }?.let { button ->
            // Icons have no captions, so the dragged button shows its name above the finger.
            Row(
                Modifier
                    .offset { IntOffset((pointer.x - origin.x).roundToInt() - 28, (pointer.y - origin.y).roundToInt() - 80) }
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
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
}
