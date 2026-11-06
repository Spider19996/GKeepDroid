package com.gkeepdroid

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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private class EditorDrop(val zone: Int, val beforeId: String?)

/**
 * A miniature of the note editor: the top bar, the note, the bottom bar, the menu and a place for hidden buttons. Long-press
 * a button and drag it to another place; where it lands in front of another button, it takes that spot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorLayoutEditor(layout: EditorLayout, onChange: (EditorLayout) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(layout)
    var dragId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val zones = remember { mutableStateMapOf<Int, Rect>() }
    val chips = remember { mutableStateMapOf<String, Rect>() }

    fun target(): EditorDrop? {
        val id = dragId ?: return null
        val zone = zones.entries.firstOrNull { it.value.contains(pointer) }?.key ?: return null
        // In front of the first button of the zone that lies behind the finger: further down, or on the same line further right.
        val before = current.inZone(zone).filter { it.id != id }.firstOrNull {
            val r = chips[it.id] ?: return@firstOrNull false
            r.center.y > pointer.y + r.height / 2 || (abs(r.center.y - pointer.y) <= r.height / 2 && r.center.x > pointer.x)
        }
        return EditorDrop(zone, before?.id)
    }
    val landing = target()
    val specs = EDITOR_ACTIONS.associateBy { it.id }

    @Composable
    fun Chip(id: String, labeled: Boolean) {
        val spec = specs.getValue(id)
        var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        Row(
            Modifier
                .onGloballyPositioned { coords = it; chips[id] = it.boundsInRoot() }
                .alpha(if (dragId == id) 0.25f else 1f)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .pointerInput(id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            dragId = id
                            pointer = coords?.localToRoot(start) ?: Offset.Zero
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            pointer = coords?.localToRoot(change.position) ?: pointer
                        },
                        onDragEnd = {
                            val drop = target()
                            if (drop != null) change(current.moved(id, drop.zone, drop.beforeId))
                            dragId = null
                        },
                        onDragCancel = { dragId = null },
                    )
                }
                .padding(horizontal = if (labeled) 8.dp else 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(spec.icon), contentDescription = stringResource(spec.label), Modifier.size(22.dp))
            if (labeled) Text(stringResource(spec.label), style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    fun Zone(zone: Int, modifier: Modifier, labeled: Boolean = false) {
        val active = dragId != null && landing?.zone == zone
        FlowRow(
            modifier
                .onGloballyPositioned { zones[zone] = it.boundsInRoot() }
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.medium,
                )
                .heightIn(min = 44.dp)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { layout.inZone(zone).forEach { Chip(it.id, labeled) } }
    }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Zone(ZONE_TOP_LEFT, Modifier.weight(1f))
                Zone(ZONE_TOP_RIGHT, Modifier.weight(2f))
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.editor_buttons_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Zone(ZONE_BOTTOM_LEFT, Modifier.weight(1f))
                Zone(ZONE_BOTTOM_RIGHT, Modifier.weight(1f))
            }
            Text(stringResource(R.string.editor_zone_menu), style = MaterialTheme.typography.titleSmall)
            Zone(ZONE_MENU, Modifier.fillMaxWidth(), labeled = true)
            Text(stringResource(R.string.editor_zone_hidden), style = MaterialTheme.typography.titleSmall)
            Zone(ZONE_HIDDEN, Modifier.fillMaxWidth(), labeled = true)
        }
        // The button under the finger is drawn on top, with its name.
        dragId?.let { id ->
            val spec = specs.getValue(id)
            Row(
                Modifier
                    .offset { IntOffset((pointer.x - origin.x).roundToInt() - 28, (pointer.y - origin.y).roundToInt() - 80) }
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(spec.icon), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary)
                Text(stringResource(spec.label), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
