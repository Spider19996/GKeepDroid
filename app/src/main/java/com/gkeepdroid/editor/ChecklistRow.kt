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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
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
    // A number written into the item ("2x Tortellini") stands in the counter on the frame instead of in the text.
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val quantitySettings = remember { AppSettingsStore.load(appContext).let { it.listQuantities to it.listQuantityOne } }
    val quantities = quantitySettings.first && !item.isDivider()
    // With the setting on, an item without a number shows a 1 as well.
    val showOne = quantities && quantitySettings.second && item.text.isNotBlank()
    val quantity = if (quantities) Quantities.find(item.text) else null
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
        // Room around the frame for what is set into its line: the counter above, the grip below, the check at the left.
        // The counter and the grip are 24 dp high, centered on the line; the check at the left is a 32 dp touch area.
        var focused by remember { mutableStateOf(false) }
        var counterWidth by remember { mutableStateOf(0) }
        // − and + show while the item is typed into, or for a moment after the number or one of them was tapped; otherwise the line
        // holds only the number, and an item without one has nothing up there, so the rows stay close together.
        var pressedAt by remember { mutableStateOf(0L) }
        var recent by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(pressedAt) {
            if (pressedAt == 0L) return@LaunchedEffect
            recent = true
            kotlinx.coroutines.delay(3000)
            recent = false
        }
        val showButtons = editable && quantities && (focused || recent)
        val showNumber = quantity != null || showOne
        val topShown = quantities && (showNumber || showButtons)
        if (!topShown && counterWidth != 0) counterWidth = 0
        val notchTop by androidx.compose.animation.core.animateDpAsState(if (topShown) 12.dp else 0.dp, label = "top")
        val notchBottom = 12.dp
        val notchStart = 16.dp
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
                    // The frame of an item lies inside the room left for what sits on its line; a divider has no such room.
                    val l = if (item.isDivider()) 0f else notchStart.toPx()
                    val t = if (item.isDivider()) 0f else notchTop.toPx()
                    val b = size.height - if (item.isDivider()) 0f else notchBottom.toPx()
                    val outer = Path().apply { addRoundRect(RoundRect(l, t, size.width, b, r)) }
                    val inner = Path().apply { addRoundRect(RoundRect(l + offsetX, t, offsetX + size.width, b, r)) }
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
        val haptic = LocalHapticFeedback.current
        val dragDescription = stringResource(R.string.drag_handle)
        Box(
            Modifier
                .offset { IntOffset(offsetX.toInt(), 0) }
                // In a category, a second, lighter frame around the field.
                .then(
                    if (framed) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium).padding(4.dp)
                    else Modifier,
                )
                // A direction that does nothing does not move; a divider cannot be ticked.
                .swipeDrag(
                    swipe, canLeft = swipeAllowed(swipeLeft, item), canRight = swipeAllowed(swipeRight, item), enabled = editable,
                ) { toRight ->
                    val onSwipe = onSwipe
                    if (onSwipe != null) onSwipe(toRight) else if (toRight) onToggle() else onRemove()
                }
        ) {
            val mark = if (item.checked) green else red
            if (item.isDivider()) {
                // A divider has no frame and no check: a line, and the grip at its left to move it.
                Box(Modifier.fillMaxWidth().height(56.dp).padding(start = 56.dp, end = 16.dp), contentAlignment = Alignment.Center) {
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                }
            } else Box(
                Modifier.fillMaxWidth().padding(start = notchStart, top = notchTop, bottom = notchBottom)
                    // On a note's own color or picture the rows show it instead of covering it; the change fades like the rest.
                    .background(
                        androidx.compose.animation.animateColorAsState(
                            MaterialTheme.colorScheme.background.let { if (LocalOnNote.current) it.copy(alpha = 0f) else it },
                            tween(450), label = "row",
                        ).value,
                        MaterialTheme.shapes.medium,
                    ),
            ) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.medium,
                    value = shownField, readOnly = !editable,
                    onValueChange = { typed ->
                        field = typed
                        onSelection(typed.selection)
                        if (typed.text != item.text) onText(typed.text)
                    },
                    visualTransformation = (if (flags != null) remember(flags, flagsVersion, item.text) { FlagsTransformation(flags) } else VisualTransformation.None)
                        .let { if (quantities) QuantityTransformation(it) else it },
                    placeholder = { Text(stringResource(R.string.item_hint)) },
                    // The frame is drawn below (with gaps for what sits on its line), not by the field.
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, disabledBorderColor = Color.Transparent,
                    ),
                    // The text keeps 4 dp from the check, whose inner half reaches 16 dp into the frame. In a numbered list the
                    // number stands in front of the text and is not part of what is edited.
                    prefix = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(4.dp))
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
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.then(modifier),
                )
                // The frame, as the field would draw it, with its line left out where the counter, the check and the grip sit on it
                // (as the title field leaves it out for its label). Drawn in a layer of its own, so the gaps only clear the line.
                val lineColor = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                val lineWidth = if (focused) 2.dp else 1.dp
                val frameShape = MaterialTheme.shapes.medium
                Box(
                    Modifier.matchParentSize()
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawBehind {
                            val w = lineWidth.toPx()
                            val r = CornerRadius(frameShape.topStart.toPx(size, this))
                            drawRoundRect(lineColor, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = r, style = Stroke(w))
                            fun clear(x: Float, y: Float, cw: Float, ch: Float) =
                                drawRect(Color.Black, Offset(x, y), Size(cw, ch), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                            // Where the line meets what sits on it, it leaves out exactly that piece, as a category's header does;
                            // the corners stay whole. The counter and the grip begin 12 dp in, behind the corner's arc.
                            val start = 12.dp.toPx()
                            if (counterWidth > 0) clear(start, -w, counterWidth.toFloat(), 3 * w)
                            clear(-w, size.height / 2 - 16.dp.toPx(), 3 * w, 32.dp.toPx())
                            clear(start, size.height - 2 * w, 24.dp.toPx(), 3 * w)
                        },
                )
            }
            if (!item.isDivider()) {
                // Top left on the line: the number ("2x"), with − and + beside it while they are wanted. Without a number the +
                // alone (or − 1x + with "1x" switched on).
                val count = quantity?.count ?: 1
                fun press() { pressedAt = System.currentTimeMillis() }
                if (topShown) Row(
                    Modifier.align(Alignment.TopStart).offset(x = notchStart + 12.dp).height(24.dp)
                        .onSizeChanged { counterWidth = it.width },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.animation.AnimatedVisibility(showButtons && showNumber) {
                        NotchButton(R.drawable.ic_remove, R.string.quantity_less, enabled = count > 1) { press(); onText(Quantities.withCount(item.text, count - 1)) }
                    }
                    if (showNumber) Text(
                        "${count}x", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(CircleShape).clickable(enabled = editable) { press() }.padding(horizontal = 4.dp),
                    )
                    androidx.compose.animation.AnimatedVisibility(showButtons) {
                        NotchButton(R.drawable.ic_add, R.string.quantity_more) { press(); onText(Quantities.withCount(item.text, count + 1)) }
                    }
                }
                // At the left, on the line in the middle: the check, a cross while open and a green check once done. A tap switches it.
                Box(
                    Modifier.align(Alignment.CenterStart).offset(y = (notchTop - notchBottom) / 2).size(32.dp)
                        .clip(CircleShape)
                        .pointerInput(item.id, editable) {
                            if (!editable) return@pointerInput
                            detectTapGestures(onTap = { toggleNow() })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(if (item.checked) R.drawable.ic_done else R.drawable.ic_close), contentDescription = null, tint = mark, modifier = Modifier.size(24.dp))
                }
            }
            // Bottom left on the line (a divider: at its left): the grip. Holding it and dragging moves the row.
            Box(
                Modifier.align(if (item.isDivider()) Alignment.CenterStart else Alignment.BottomStart)
                    .offset(x = if (item.isDivider()) 12.dp else notchStart + 12.dp).size(24.dp)
                    .clip(CircleShape)
                    .onGloballyPositioned { handle = it }
                    .pointerInput(item.id, editable) {
                        if (!editable) return@pointerInput
                        detectDragGestures(
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
                contentAlignment = Alignment.Center,
            ) {
                // Three short lines, drawn like a 20 dp symbol (16 dp wide, 2 dp thick, 3 dp apart).
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { Box(Modifier.size(width = 16.dp, height = 2.dp).background(LocalContentColor.current)) }
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

/** A − or + set into a row's frame line: a 20 dp symbol in a 24 dp area, centered on the line. */
@Composable
private fun NotchButton(icon: Int, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon), contentDescription = stringResource(label), modifier = Modifier.size(20.dp),
            tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}
