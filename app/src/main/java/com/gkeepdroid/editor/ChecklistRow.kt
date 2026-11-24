package com.gkeepdroid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/**
 * One item of a checklist while editing: just the text, with a green check at the bottom right when it is
 * done. Swiping right ticks (or unticks) it, swiping left removes it, and the row stops where the symbol is shown.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChecklistRow(
    item: KeepItem, editable: Boolean, dragged: Boolean, number: Int?,
    onText: (String) -> Unit, onToggle: () -> Unit, onRemove: () -> Unit,
    onBounds: (Rect) -> Unit, onDragStart: () -> Unit, onDragTo: (Float) -> Unit, onDragEnd: () -> Unit,
    modifier: Modifier = Modifier, flags: DocFlags? = null, flagsVersion: Int = 0, onFocus: () -> Unit = {}, onSelection: (TextRange) -> Unit = {},
    focusNow: Boolean = false, onFocused: () -> Unit = {},
    /** What swiping to the right and to the left does (ITEM_SWIPE_*); [onSwipe] gets the direction, true for right. */
    swipeRight: Int = ITEM_SWIPE_TICK, swipeLeft: Int = ITEM_SWIPE_REMOVE, onSwipe: ((Boolean) -> Unit)? = null,
    /** An item in a category: a second frame around it shows that it belongs to one. */
    framed: Boolean = false,
    /** Moved along by something else (its category is held): it does not glide on its own then. */
    carried: Boolean = false,
) {
    // The gesture lives as long as the row; it calls what the row is given now, not what it was given when it started (the
    // category of the item changes while it is dragged).
    val dragTo by rememberUpdatedState(onDragTo)
    val dragStart by rememberUpdatedState(onDragStart)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val toggleNow by rememberUpdatedState(onToggle)
    // The field keeps its own selection, which the formatting buttons need.
    var field by remember(item.id) { mutableStateOf(TextFieldValue(item.text)) }
    val shownField = if (field.text == item.text) field else TextFieldValue(item.text, TextRange(item.text.length))
    // A new item that should be typed into at once (one made in a category): the focus and the cursor at its end.
    val focusRequester = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(focusNow) {
        if (!focusNow) return@LaunchedEffect
        field = TextFieldValue(item.text, TextRange(item.text.length))
        runCatching { focusRequester.requestFocus() }
        onFocused()
    }
    val swipe = rememberSwipeState()
    val offsetX = swipe.offset
    var handle by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // A number written into the item ("2x Tortellini") stands in a pill at its end instead of in the text.
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val quantities = remember { AppSettingsStore.load(appContext).listQuantities } && !item.isDivider()
    val quantity = if (quantities) Quantities.find(item.text) else null
    var focused by remember { mutableStateOf(false) }
    val showPill = quantity != null || (quantities && editable && focused && item.text.isNotBlank())
    var pillOpen by remember { mutableStateOf(false) }
    // The handle stays in the middle of the row; when the text wraps to another line (or loses one) it glides there instead of
    // jumping. The first height is taken as it is, so a long item does not slide into place when the editor opens.
    val handleTop = remember { Animatable(0f) }
    var measured by remember { mutableStateOf(false) }
    val handleHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
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
            .then(if (dragged || settle.isRunning) Modifier.zIndex(1f) else if (carried) Modifier else Modifier.animatePlacement())
            .graphicsLayer {
                val y = fingerY
                translationY = if (dragged && y != null) y - grab - rowTop else settle.value
                if (dragged) { shadowElevation = 12f; scaleX = 1.02f; scaleY = 1.02f }
            },
    ) {
        if (offsetX != 0f) {
            val toRight = offsetX > 0f
            val (color, icon, tint) = swipeLook(if (toRight) swipeRight else swipeLeft)
            // The color fills exactly what the row has uncovered: the row's place less the row where it is now, both with the row's
            // rounding. So it looks like the row sliding off the color (the color follows its rounded corners), not like a second
            // box beside it, and on a note's own color, where the row is see-through, nothing shines through the row.
            val shape = MaterialTheme.shapes.medium
            Box(
                Modifier.matchParentSize().drawBehind {
                    val r = CornerRadius(shape.topStart.toPx(size, this))
                    val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
                    val inner = Path().apply { addRoundRect(RoundRect(offsetX, 0f, offsetX + size.width, size.height, r)) }
                    drawPath(Path.combine(PathOperation.Difference, outer, inner), color)
                },
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Box(
                    Modifier.fillMaxHeight().width(with(LocalDensity.current) { kotlin.math.abs(offsetX).toDp() }).clipToBounds()
                        .padding(horizontal = 20.dp),
                    contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.requiredWidth(24.dp))
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offsetX.toInt(), 0) }
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
                        scope.launch { handleTop.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                    }
                }
                // On a note's own color or picture the rows show it instead of covering it; the change fades like the rest.
                .background(
                    androidx.compose.animation.animateColorAsState(
                        MaterialTheme.colorScheme.background.let { if (LocalOnNote.current) it.copy(alpha = 0f) else it },
                        tween(450), label = "row",
                    ).value,
                    // Rounded like the frame, so a swipe's color is not covered at the corners behind the line.
                    MaterialTheme.shapes.medium,
                )
                // A direction that does nothing does not move; a divider cannot be ticked.
                .swipeDrag(
                    swipe, canLeft = swipeAllowed(swipeLeft, item), canRight = swipeAllowed(swipeRight, item), enabled = editable,
                ) { toRight ->
                    val onSwipe = onSwipe
                    if (onSwipe != null) onSwipe(toRight) else if (toRight) onToggle() else onRemove()
                }
        ) {
            // Handle and checkbox sit in the left corners, one above the other and small. Every gap to an edge is 8 dp:
            // the handle to the top and left, the checkbox to the left and bottom, and the text to the checkbox.
            val mark = if (item.checked) green else red
            if (item.isDivider()) Box(Modifier.fillMaxWidth().height(56.dp).padding(start = 56.dp, end = 16.dp), contentAlignment = Alignment.Center) {
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            } else OutlinedTextField(
                shape = MaterialTheme.shapes.medium,
                value = shownField, readOnly = !editable,
                onValueChange = { typed ->
                    field = typed
                    onSelection(typed.selection)
                    if (typed.text != item.text) onText(typed.text)
                },
                visualTransformation = (if (flags != null) remember(flags, flagsVersion, item.text) { FlagsTransformation(flags) } else VisualTransformation.None)
                    .let { if (quantities) QuantityTransformation(it) else it },
                // The pill: the number when there is one, and on the item being typed into a quiet "1×" to start counting.
                // Room for the pill, which lies above the row's end (see below), so the text never runs under it.
                trailingIcon = if (showPill) {
                    { Spacer(Modifier.width(animateDpAsState(if (pillOpen) 120.dp else 64.dp, label = "pill").value)) }
                } else null,
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
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                } else {
                    LocalTextStyle.current
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.then(modifier),
            )
            // The pill above the end of the field: a slot of the field would cut off the taps of the opened − and +.
            if (showPill) Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                if (editable) QuantityPill(quantity?.count ?: 1, offer = quantity == null, open = pillOpen, onOpen = { pillOpen = it }) { onText(Quantities.withCount(item.text, it)) }
                else QuantityBadge(quantity?.count ?: 1, MaterialTheme.colorScheme.onSurface)
            }
            // One button for both jobs: a tap switches the item between done and open, holding it and dragging moves the row.
            // The whole strip at the left is the touch area, 48 dp wide as Material asks; the capsule in it is the housing of the
            // check (green) or cross (red), with the grip below it as a hint that it can be dragged. Both sit in the middle of
            // their half, and the capsule has the same 11 dp to the left edge and to the text.
            val haptic = LocalHapticFeedback.current
            val dragDescription = stringResource(R.string.drag_handle)
            Box(
                Modifier
                    .align(Alignment.TopStart).offset { IntOffset(0, handleTop.value.toInt()) }.width(48.dp).height(56.dp)
                    .onGloballyPositioned { handle = it }
                    .pointerInput(item.id, editable) {
                        if (!editable) return@pointerInput
                        detectTapGestures(onTap = { toggleNow() })
                    }
                    .pointerInput(item.id, editable) {
                        if (!editable) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                handle?.localToRoot(start)?.y?.let { fingerY = it; grab = it - rowTop }
                                dragStart()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                handle?.localToRoot(change.position)?.y?.let { fingerY = it; dragTo(it) }
                            },
                            onDragEnd = { release(); dragEnd() },
                            onDragCancel = { release(); dragEnd() },
                        )
                    }
                    .semantics { contentDescription = dragDescription },
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(
                    Modifier
                        .padding(start = 11.dp).size(width = 26.dp, height = if (item.isDivider()) 22.dp else 40.dp)
                        // A filled capsule as Material 3 Expressive has it, see-through in the text's color, so it suits every
                        // note color and picture; once done it takes on the check's color.
                        .clip(CircleShape)
                        .background(
                            androidx.compose.animation.animateColorAsState(
                                if (item.checked && !item.isDivider()) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                label = "capsule",
                            ).value,
                        )
                        .padding(vertical = 2.dp),
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
                    Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
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
    val animatable = remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
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
internal fun swipeLook(action: Int): Triple<Color, Int, Color> = when (action) {
    // On a note's own color the theme's accent is the note's text color, which says nothing here: a fixed green.
    ITEM_SWIPE_TICK -> if (LocalOnNote.current) Triple(Color(0xFF2E7D32), R.drawable.ic_done, Color.White)
        else Triple(MaterialTheme.colorScheme.tertiary, R.drawable.ic_done, MaterialTheme.colorScheme.onTertiary)
    ITEM_SWIPE_CATEGORY -> Triple(MaterialTheme.colorScheme.primary, R.drawable.ic_category, MaterialTheme.colorScheme.onPrimary)
    else -> Triple(MaterialTheme.colorScheme.error, R.drawable.ic_close, MaterialTheme.colorScheme.onError)
}

private fun swipeAllowed(action: Int, item: KeepItem) = action != ITEM_SWIPE_NONE && !(item.isDivider() && action == ITEM_SWIPE_TICK)
