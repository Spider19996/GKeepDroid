package com.gkeepdroid

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.draggable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * One item of a checklist while editing: just the text, with a green check at the bottom right when it is
 * done. Swiping right ticks (or unticks) it, swiping left removes it, and the row stops where the symbol is shown.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ChecklistRow(
    item: KeepItem, editable: Boolean, dragged: Boolean, number: Int?,
    onText: (String) -> Unit, onToggle: () -> Unit, onRemove: () -> Unit,
    onBounds: (Rect) -> Unit, onDragStart: () -> Unit, onDragTo: (Float) -> Unit, onDragEnd: () -> Unit,
    modifier: Modifier = Modifier, flags: DocFlags? = null, flagsVersion: Int = 0, onFocus: () -> Unit = {}, onSelection: (androidx.compose.ui.text.TextRange) -> Unit = {},
) {
    // The field keeps its own selection, which the formatting buttons need.
    var field by remember(item.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(item.text)) }
    val shownField = if (field.text == item.text) field else androidx.compose.ui.text.input.TextFieldValue(item.text, androidx.compose.ui.text.TextRange(item.text.length))
    val maxPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    val trigger = maxPx * 0.75f
    var offsetX by remember { mutableStateOf(0f) }
    var handle by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val green = MaterialTheme.colorScheme.tertiary
    val red = MaterialTheme.colorScheme.error
    Box(Modifier.fillMaxWidth().onGloballyPositioned { onBounds(it.boundsInRoot()) }.alpha(if (dragged) 0.5f else 1f)) {
        if (offsetX != 0f) {
            val toRight = offsetX > 0f
            Box(
                Modifier.matchParentSize().clip(MaterialTheme.shapes.medium)
                    .background(if (toRight) green else red).padding(horizontal = 20.dp),
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(if (toRight) R.drawable.ic_done else R.drawable.ic_close),
                    contentDescription = null,
                    tint = if (toRight) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onError,
                )
            }
        }
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
                // On a note's own color or picture the rows show it instead of covering it; the change fades like the rest.
                .background(
                    androidx.compose.animation.animateColorAsState(
                        MaterialTheme.colorScheme.background.let { if (LocalOnNote.current) it.copy(alpha = 0f) else it },
                        androidx.compose.animation.core.tween(450), label = "row",
                    ).value,
                )
                .draggable(
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    enabled = editable,
                    state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(-maxPx, maxPx)
                    },
                    onDragStopped = {
                        val passed = kotlin.math.abs(offsetX) >= trigger
                        val toRight = offsetX > 0f
                        androidx.compose.animation.core.animate(offsetX, 0f) { value, _ -> offsetX = value }
                        if (passed) {
                            if (toRight) onToggle() else onRemove()
                        }
                    },
                )
        ) {
            // Handle and checkbox sit in the left corners, one above the other and small. Every gap to an edge is 8 dp:
            // the handle to the top and left, the checkbox to the left and bottom, and the text to the checkbox.
            val mark = if (item.checked) green else red
            OutlinedTextField(
                value = shownField, readOnly = !editable,
                onValueChange = { typed ->
                    field = typed
                    onSelection(typed.selection)
                    if (typed.text != item.text) onText(typed.text)
                },
                visualTransformation = if (flags != null) remember(flags, flagsVersion, item.text) { FlagsTransformation(flags) } else VisualTransformation.None,
                placeholder = { Text(stringResource(R.string.item_hint)) },
                // The text starts 48 dp from the left edge (16 dp content padding plus this room), where the strip with the
                // capsule ends; the strip is drawn on top. In a numbered list the number stands in front of the text and is
                // not part of what is edited.
                prefix = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(32.dp))
                        number?.let { Text("$it. ", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                textStyle = if (item.checked) {
                    LocalTextStyle.current.copy(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                } else {
                    LocalTextStyle.current
                },
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() }.then(modifier),
            )
            // One button for both jobs: a tap switches the item between done and open, holding it and dragging moves the row.
            // The whole strip at the left is the touch area, 48 dp wide as Material asks; the capsule in it is the housing of the
            // check (green) or cross (red), with the grip below it as a hint that it can be dragged. Both sit in the middle of
            // their half, and the capsule has the same 11 dp to the left edge and to the text.
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val dragDescription = stringResource(R.string.drag_handle)
            Box(
                Modifier
                    .align(Alignment.CenterStart).width(48.dp).height(56.dp)
                    .onGloballyPositioned { handle = it }
                    .pointerInput(item.id, editable) {
                        if (!editable) return@pointerInput
                        detectTapGestures(onTap = { onToggle() })
                    }
                    .pointerInput(item.id, editable) {
                        if (!editable) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onDragStart()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                handle?.localToRoot(change.position)?.y?.let(onDragTo)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        )
                    }
                    .semantics { contentDescription = dragDescription },
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(
                    Modifier
                        .padding(start = 11.dp).size(width = 26.dp, height = 40.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                        // The border is drawn inside the size, so the halves are measured inside it.
                        .padding(1.5.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (item.checked) R.drawable.ic_done else R.drawable.ic_close),
                            contentDescription = null, tint = mark, modifier = Modifier.size(14.dp),
                        )
                    }
                    // The line between the check and the grip.
                    Box(Modifier.fillMaxWidth().height(1.5.dp).background(MaterialTheme.colorScheme.outline))
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(3) { Box(Modifier.size(width = 12.dp, height = 1.5.dp).background(MaterialTheme.colorScheme.onSurfaceVariant)) }
                        }
                    }
                }
            }
        }
    }
}
