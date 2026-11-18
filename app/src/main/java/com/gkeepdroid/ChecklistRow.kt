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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.ui.composed
import kotlinx.coroutines.launch
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
import androidx.compose.ui.focus.focusRequester
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
    focusNow: Boolean = false, onFocused: () -> Unit = {},
    /** What swiping to the right and to the left does (ITEM_SWIPE_*); [onSwipe] gets the direction, true for right. */
    swipeRight: Int = ITEM_SWIPE_TICK, swipeLeft: Int = ITEM_SWIPE_REMOVE, onSwipe: ((Boolean) -> Unit)? = null,
    /** An item in a category: a second frame around it shows that it belongs to one. */
    framed: Boolean = false,
) {
    // The field keeps its own selection, which the formatting buttons need.
    var field by remember(item.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(item.text)) }
    val shownField = if (field.text == item.text) field else androidx.compose.ui.text.input.TextFieldValue(item.text, androidx.compose.ui.text.TextRange(item.text.length))
    // A new item that should be typed into at once (one made in a category): the focus and the cursor at its end.
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(focusNow) {
        if (!focusNow) return@LaunchedEffect
        field = androidx.compose.ui.text.input.TextFieldValue(item.text, androidx.compose.ui.text.TextRange(item.text.length))
        runCatching { focusRequester.requestFocus() }
        onFocused()
    }
    val maxPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    val trigger = maxPx * 0.75f
    var offsetX by remember { mutableStateOf(0f) }
    var handle by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The handle stays in the middle of the row; when the text wraps to another line (or loses one) it glides there instead of
    // jumping. The first height is taken as it is, so a long item does not slide into place when the editor opens.
    val handleTop = remember { androidx.compose.animation.core.Animatable(0f) }
    var measured by remember { mutableStateOf(false) }
    val handleHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val green = MaterialTheme.colorScheme.tertiary
    val red = MaterialTheme.colorScheme.error
    // While the row is held it follows the finger: where the finger is now, less where it took hold of the row, less where the
    // row's place in the list is (that place moves as the row passes others). The other rows glide to their new places.
    var rowTop by remember { mutableStateOf(0f) }
    var fingerY by remember { mutableStateOf<Float?>(null) }
    var grab by remember { mutableStateOf(0f) }
    if (!dragged && fingerY != null) fingerY = null
    // Let go, the row glides from under the finger into its place.
    val settle = remember { Animatable(0f) }
    val settleScope = androidx.compose.runtime.rememberCoroutineScope()
    fun release() {
        val y = fingerY
        fingerY = null
        if (y != null) settleScope.launch { settle.snapTo(y - grab - rowTop); settle.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
    }
    Box(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { val bounds = it.boundsInRoot(); rowTop = bounds.top; onBounds(bounds) }
            .then(if (dragged || settle.isRunning) Modifier.zIndex(1f) else Modifier.animatePlacement())
            .graphicsLayer {
                val y = fingerY
                translationY = if (dragged && y != null) y - grab - rowTop else settle.value
                if (dragged) { shadowElevation = 12f; scaleX = 1.02f; scaleY = 1.02f }
            },
    ) {
        if (offsetX != 0f) {
            val toRight = offsetX > 0f
            val (color, icon, tint) = swipeLook(if (toRight) swipeRight else swipeLeft)
            Box(
                Modifier.matchParentSize().clip(MaterialTheme.shapes.medium)
                    .background(color).padding(horizontal = 20.dp),
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = tint)
            }
        }
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
                // In a category, a second, lighter frame around the field.
                .then(
                    if (framed) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium).padding(4.dp)
                    else Modifier,
                )
                .onSizeChanged { size ->
                    val target = ((size.height - handleHeightPx) / 2f).coerceAtLeast(0f)
                    if (!measured) {
                        measured = true
                        scope.launch { handleTop.snapTo(target) }
                    } else if (target != handleTop.targetValue) {
                        scope.launch { handleTop.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f)) }
                    }
                }
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
                        // A direction that does nothing does not move; a divider cannot be ticked.
                        fun allowed(action: Int) = action != ITEM_SWIPE_NONE && !(item.isDivider() && action == ITEM_SWIPE_TICK)
                        offsetX = (offsetX + delta).coerceIn(if (allowed(swipeLeft)) -maxPx else 0f, if (allowed(swipeRight)) maxPx else 0f)
                    },
                    onDragStopped = {
                        val passed = kotlin.math.abs(offsetX) >= trigger
                        val toRight = offsetX > 0f
                        androidx.compose.animation.core.animate(offsetX, 0f) { value, _ -> offsetX = value }
                        if (passed) {
                            val swipe = onSwipe
                            if (swipe != null) swipe(toRight) else if (toRight) onToggle() else onRemove()
                        }
                    },
                )
        ) {
            // Handle and checkbox sit in the left corners, one above the other and small. Every gap to an edge is 8 dp:
            // the handle to the top and left, the checkbox to the left and bottom, and the text to the checkbox.
            val mark = if (item.checked) green else red
            if (item.isDivider()) Box(Modifier.fillMaxWidth().height(56.dp).padding(start = 56.dp, end = 16.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            } else OutlinedTextField(
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
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { if (it.isFocused) onFocus() }.then(modifier),
            )
            // One button for both jobs: a tap switches the item between done and open, holding it and dragging moves the row.
            // The whole strip at the left is the touch area, 48 dp wide as Material asks; the capsule in it is the housing of the
            // check (green) or cross (red), with the grip below it as a hint that it can be dragged. Both sit in the middle of
            // their half, and the capsule has the same 11 dp to the left edge and to the text.
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val dragDescription = stringResource(R.string.drag_handle)
            Box(
                Modifier
                    .align(Alignment.TopStart).offset { androidx.compose.ui.unit.IntOffset(0, handleTop.value.toInt()) }.width(48.dp).height(56.dp)
                    .onGloballyPositioned { handle = it }
                    .pointerInput(item.id, editable) {
                        if (!editable) return@pointerInput
                        detectTapGestures(onTap = { onToggle() })
                    }
                    .pointerInput(item.id, editable) {
                        if (!editable) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start ->
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                handle?.localToRoot(start)?.y?.let { fingerY = it; grab = it - rowTop }
                                onDragStart()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                handle?.localToRoot(change.position)?.y?.let { fingerY = it; onDragTo(it) }
                            },
                            onDragEnd = { release(); onDragEnd() },
                            onDragCancel = { release(); onDragEnd() },
                        )
                    }
                    .semantics { contentDescription = dragDescription },
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(
                    Modifier
                        .padding(start = 11.dp).size(width = 26.dp, height = if (item.isDivider()) 22.dp else 40.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                        // The border is drawn inside the size, so the halves are measured inside it.
                        .padding(1.5.dp),
                ) {
                    // A divider has no check, only the grip to move it.
                    if (!item.isDivider()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (item.checked) R.drawable.ic_done else R.drawable.ic_close),
                            contentDescription = null, tint = mark, modifier = Modifier.size(14.dp),
                        )
                    }
                    // The line between the check and the grip.
                    Box(Modifier.fillMaxWidth().height(1.5.dp).background(MaterialTheme.colorScheme.outline))
                    }
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

/**
 * Lets a row glide to its new place when the rows around it change (one is moved, added or removed) instead of jumping there.
 * The first place is taken as it is.
 */
internal fun Modifier.animatePlacement(): Modifier = composed {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var target by remember { mutableStateOf<IntOffset?>(null) }
    val animatable = remember { mutableStateOf<Animatable<IntOffset, androidx.compose.animation.core.AnimationVector2D>?>(null) }
    this.onPlaced { target = it.positionInParent().round() }
        .offset {
            val to = target ?: return@offset IntOffset.Zero
            val anim = animatable.value ?: Animatable(to, IntOffset.VectorConverter).also { animatable.value = it }
            if (anim.targetValue != to) scope.launch { anim.animateTo(to, spring(dampingRatio = 0.85f, stiffness = 600f)) }
            anim.value - to
        }
}

/** How a swipe looks under the row: ticking green, removing red, moving to a category in the primary color. */
@Composable
internal fun swipeLook(action: Int): Triple<androidx.compose.ui.graphics.Color, Int, androidx.compose.ui.graphics.Color> = when (action) {
    ITEM_SWIPE_TICK -> Triple(MaterialTheme.colorScheme.tertiary, R.drawable.ic_done, MaterialTheme.colorScheme.onTertiary)
    ITEM_SWIPE_CATEGORY -> Triple(MaterialTheme.colorScheme.primary, R.drawable.ic_category, MaterialTheme.colorScheme.onPrimary)
    else -> Triple(MaterialTheme.colorScheme.error, R.drawable.ic_close, MaterialTheme.colorScheme.onError)
}
