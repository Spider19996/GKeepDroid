package com.gkeepdroid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// The pieces of a checklist with categories in the editor: the header set into the frame's top line, the frame itself drawn in
// pieces by the rows, the drop places and the chips of the dialogs.
/**
 * The header of a category in the editor's checklist, set into the top line of its frame as the label of a text field is: the name
 * at the left, the progress, the arrow that folds it and the "+" that adds an item at the right. A tap on the name renames the
 * category, holding it moves the category with its items; swiping the line does what swiping an item does, for all of them.
 * [notches] gets the stretches of the line it covers (in pixels from the left of the frame), so the frame leaves them open.
 */
@Composable
internal fun EditorCategoryHeader(
    name: String, progress: String, folded: Boolean, canEdit: Boolean, onToggle: () -> Unit, onAdd: () -> Unit,
    notches: SnapshotStateList<Pair<Float, Float>>, modifier: Modifier = Modifier,
    onRename: () -> Unit = {}, dragged: Boolean = false,
    onDragStart: () -> Unit = {}, onDragTo: (Float) -> Unit = {}, onDragEnd: () -> Unit = {}, onShift: (Float) -> Unit = {},
    swipeRight: Int = ITEM_SWIPE_NONE, swipeLeft: Int = ITEM_SWIPE_NONE, onSwipeMove: (Float) -> Unit = {}, onSwipeStop: () -> Unit = {},
    /** Nothing under the header (the category is empty, folded or held): it is a box of a hand's height then, not just a line. */
    alone: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // The gestures call what the header is given now (the order changes while it is dragged).
    val dragTo by rememberUpdatedState(onDragTo)
    val dragStart by rememberUpdatedState(onDragStart)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val shift by rememberUpdatedState(onShift)
    val rename by rememberUpdatedState(onRename)
    val swipeMove by rememberUpdatedState(onSwipeMove)
    val swipeStop by rememberUpdatedState(onSwipeStop)
    var rowTop by androidx.compose.runtime.remember { mutableStateOf(0f) }
    var grab by androidx.compose.runtime.remember { mutableStateOf(0f) }
    var nameCoords by androidx.compose.runtime.remember { mutableStateOf<LayoutCoordinates?>(null) }
    var nameRange by androidx.compose.runtime.remember { mutableStateOf(0f to 0f) }
    var toolsRange by androidx.compose.runtime.remember { mutableStateOf(0f to 0f) }
    val gapPx = with(density) { 4.dp.toPx() }
    // Read here, in the composition: a read inside the SideEffect is not tracked, so a name measured after the last
    // recomposition left the line unbroken through the name.
    // No gap for the name where there is none (the group without a category).
    val nameGap = if (nameRange.second > nameRange.first) nameRange.first - gapPx to nameRange.second + gapPx else 0f to 0f
    val wanted = listOf(nameGap, toolsRange.first - gapPx to toolsRange.second + gapPx)
    SideEffect {
        if (notches.toList() != wanted) { notches.clear(); notches.addAll(wanted) }
    }
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    Box(
        modifier.fillMaxWidth()
            .onGloballyPositioned { rowTop = it.boundsInRoot().top }
            // Its middle lies on the frame's top line: half of it above the frame, half inside.
            .layout { m, c ->
                val p = m.measure(c)
                // Below the line it takes a little less than its lower half, so the first item is not far from the name.
                val below = if (alone) 36.dp.roundToPx() else (p.height / 2 - 14.dp.roundToPx()).coerceAtLeast(0)
                layout(p.width, below) { p.place(0, -p.height / 2 + (if (alone) 4.dp.roundToPx() else 0)) }
            }
    ) {
    Box {
    Row(
        Modifier.fillMaxWidth()
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = canEdit && (swipeRight != ITEM_SWIPE_NONE || swipeLeft != ITEM_SWIPE_NONE),
                state = androidx.compose.foundation.gestures.rememberDraggableState { delta -> swipeMove(delta) },
                onDragStopped = { swipeStop() },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The group of items without a category has no name (empty): only room to hold it by, and no gap in the line.
        val unnamed = name.isEmpty()
        Text(
            if (unnamed) "\u2007\u2007\u2007\u2007\u2007\u2007" else name, style = labelStyle,
            color = if (dragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1,
            // A little further in than the label of the title field (16 dp), so the corner's arc is whole before it: 22 dp from the frame's
            // line (8 dp of the frame's padding, 10 here, 4 of the name's own).
            modifier = Modifier.padding(start = HEADER_INSET)
                .onGloballyPositioned { c ->
                    nameCoords = c
                    val x = c.positionInParent().x
                    nameRange = if (unnamed) x to x else x to x + c.size.width
                }
                .clip(MaterialTheme.shapes.small)
                .pointerInput(name, canEdit) {
                    if (!canEdit || unnamed) return@pointerInput
                    detectTapGestures(onTap = { rename() })
                }
                .pointerInput(name, canEdit) {
                    if (!canEdit) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
        Spacer(Modifier.weight(1f))
        Row(
            // The last symbol ends 16 dp from the frame's line, as the title field's content does (its box is 4 dp wider than the symbol).
            Modifier.padding(end = 12.dp).onGloballyPositioned { c ->
                val x = c.positionInParent().x
                toolsRange = x to x + c.size.width
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (folded) R.drawable.ic_arrow_left else R.drawable.ic_arrow_down),
                    contentDescription = stringResource(if (folded) R.string.unfold_all else R.string.fold_all), modifier = Modifier.size(24.dp),
                )
            }
            if (canEdit) Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.category_add_item, name), modifier = Modifier.size(24.dp))
            }
        }
    }
    }
    }
}

/** Geometry shared by the category frame, its swipe band and the rows around them. */
val FRAME_RADIUS = 18.dp
val FRAME_SIDE = 8.dp
/** The space between two rows of the editor; the frame's line reaches into it, so frame and rows must use the same value. */
val ROW_GAP = 12.dp
val HEADER_INSET = 10.dp
/** How far a row or category can be swiped, and the share of it from which the swipe counts. */
val SWIPE_MAX = 72.dp
const val SWIPE_TRIGGER = 0.75f

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
        // As round as the corners of the fields inside allow: the whole arc lies before the name and after the buttons.
        val radius = FRAME_RADIUS.toPx()
        // Each piece draws down over the gap to the next row (the editor's 12 dp), exactly to where that one begins: the pieces
        // meet without overlapping, so the sides show no seam.
        val reach = ROW_GAP.toPx()
        // The whole line lies inside the piece at the top and the bottom, so it is as thick there as at the sides.
        val top = if (first) stroke / 2 else -radius
        val bottom = if (last) size.height - stroke / 2 else size.height + reach + radius
        // Where it may draw: its own height (and over the gaps), less the stretches of the top line that the header covers, as
        // a text field's line is left open at its label.
        val area = Path().apply {
            addRect(Rect(-reach, 0f, size.width + reach, if (last) size.height else size.height + reach))
        }
        val inset = FRAME_SIDE.toPx()
        val open = if (!first) area else notches.filter { it.second > it.first }.fold(area) { acc, (from, to) ->
            val hole = Path().apply {
                addRect(Rect(inset + from, -reach, inset + to, 3.dp.toPx()))
            }
            Path.combine(PathOperation.Difference, acc, hole)
        }
        clipPath(open) {
            drawRoundRect(
                color, topLeft = Offset(stroke / 2, top),
                size = Size(size.width - stroke, bottom - top),
                cornerRadius = CornerRadius(radius),
                style = Stroke(stroke),
            )
        }
    }.padding(start = FRAME_SIDE, end = FRAME_SIDE, bottom = if (last) FRAME_SIDE else 0.dp)
}
