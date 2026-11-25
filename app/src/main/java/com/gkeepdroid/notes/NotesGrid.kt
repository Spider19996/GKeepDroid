package com.gkeepdroid

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The grid (or list) of the note cards on the notes screen, with moving a card by dragging it.
@Composable
internal fun NotesGrid(
    activity: androidx.activity.ComponentActivity,
    resumeKey: Int,
    unlocker: ((Int, (Boolean) -> Unit) -> Unit)?,
    shown: List<Pair<KeepNote, String>>,
    screen: AppScreen,
    app: AppSettings,
    canReorder: Boolean,
    query: String,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    open: (KeepNote?, String, Boolean) -> Unit,
    act: (() -> Unit, suspend () -> Unit) -> Unit,
    askDelete: (Pair<KeepNote, String>) -> Unit,
    onSwiped: (KeepNote, String, Int) -> Unit,
) {
    val context = LocalContext.current
    if (shown.isEmpty()) {
        Text(
            stringResource(R.string.empty_notes),
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // Drag and drop: long-press a card and move it; the other cards make room while you drag.
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    var gridBounds by remember { mutableStateOf(Rect.Zero) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var dragOrder by remember { mutableStateOf<List<Pair<KeepNote, String>>?>(null) }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    var lastMove by remember { mutableStateOf(0L) }
    // The new order is shown until the saved one arrives.
    LaunchedEffect(shown) { dragOrder = null }
    val list = dragOrder ?: shown
    // While a note is picked up (chosen or dragged), the numbers show even with the setting off.
    val numbered = screen == AppScreen.Notes && (app.widgetOrderNumbers || selected.isNotEmpty() || dragKey != null)
    val positions = remember(shown, resumeKey, numbered) {
        if (numbered) widgetPositions(context, shown.map { it.second }.distinct()) else emptyMap()
    }
    fun keyOf(entry: Pair<KeepNote, String>) = entry.second + "|" + entry.first.id

    fun moveUnderFinger() {
        val key = dragKey ?: return
        val current = dragOrder ?: shown
        // The cards slide to their new places for a moment; their positions are not reliable until they settle.
        val now = SystemClock.uptimeMillis()
        if (now - lastMove < 250) return
        val from = current.indexOfFirst { keyOf(it) == key }
        // Only the middle of a card counts, so the finger near an edge does not swap back and forth.
        val to = current.indexOfFirst {
            keyOf(it) != key && cardBounds[keyOf(it)]?.let { r -> r.deflate(minOf(r.width, r.height) * 0.25f).contains(pointer) } == true
        }
        if (from < 0 || to < 0) return
        // Pinned and other notes keep their own sections (only the notes screen has them).
        if (screen == AppScreen.Notes && app.pinnedSection && current[from].first.pinned != current[to].first.pinned) return
        lastMove = now
        // The grid keeps its place by the first card it shows; when that card is part of the move, it would jump along with it.
        val index = gridState.firstVisibleItemIndex
        val offset = gridState.firstVisibleItemScrollOffset
        dragOrder = current.toMutableList().also { it.add(to, it.removeAt(from)) }
        scope.launch { gridState.scrollToItem(index, offset) }
    }

    fun drop() {
        val key = dragKey
        val order = dragOrder
        dragKey = null
        if (key == null || order == null) return
        val entry = order.firstOrNull { keyOf(it) == key } ?: return
        val group = if (screen == AppScreen.Notes && app.pinnedSection) order.filter { it.first.pinned == entry.first.pinned } else order
        val at = group.indexOfFirst { keyOf(it) == key }
        val above = group.getOrNull(at - 1)?.first?.sort
        val below = group.getOrNull(at + 1)?.first?.sort
        // A higher value is further up; the note gets a value between its new neighbors.
        val sort = when {
            above != null && below != null -> (above + below) / 2
            below != null -> below + 10_000
            above != null -> above - 10_000
            else -> null
        }
        if (sort == null || order.map(::keyOf) == shown.map(::keyOf)) {
            dragOrder = null
        } else {
            act({ dragOrder = null }) { KeepRepository.setSort(context, entry.second, entry.first.id, sort) }
        }
    }

    // The drag gesture lives longer than one composition: it must always call the current version of drop().
    val dropNow by rememberUpdatedState<() -> Unit>({ drop() })

    // Near the top or bottom edge the list scrolls along with the finger.
    LaunchedEffect(dragKey) {
        while (dragKey != null) {
            val edge = 120f
            if (pointer.y < gridBounds.top + edge) gridState.scrollBy(-24f)
            else if (pointer.y > gridBounds.bottom - edge) gridState.scrollBy(24f)
            moveUnderFinger()
            delay(16)
        }
    }

    /** The long-press drag of one card. */
    @Composable
    fun dragModifier(key: String): Modifier {
        val haptic = LocalHapticFeedback.current
        val toggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle(key) }
        // Once notes are chosen, a tap adds or removes one, and the cards no longer move (the card being pressed keeps its gesture).
        if (selected.isNotEmpty() && dragKey != key) return Modifier
        // Without a place to drag to, a long press chooses the note.
        if (!canReorder) return Modifier.pointerInput(key) { detectTapGestures(onLongPress = { toggle() }) }
        val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
        // Where the card sits without its own shift, and where in it the finger took hold: the held card follows the finger
        // (as a row does in the editor) while the others glide to their new places.
        // [0, 1] are state: when the card's place changes under a finger that holds still, its shift has to follow at once.
        val origin = remember { androidx.compose.runtime.mutableStateListOf(0f, 0f, 0f, 0f) }
        // The long press chooses the card at once, so it shows what is happening; dragging it on turns that back into a move.
        var chosenByPress by remember { mutableStateOf(false) }
        val lift = if (dragKey == key) Modifier.graphicsLayer {
            translationX = pointer.x - origin[2] - origin[0]
            translationY = pointer.y - origin[3] - origin[1]
            shadowElevation = 16f; scaleX = 1.03f; scaleY = 1.03f
        } else Modifier
        // The gesture and the measuring sit outside the shift of the held card: inside it, the finger's position would be read
        // against the moved card, and every move would feed back into the next one.
        return Modifier
            .onGloballyPositioned { coords[0] = it; val r = it.boundsInRoot(); cardBounds[key] = r; if (origin[0] != r.left) origin[0] = r.left; if (origin[1] != r.top) origin[1] = r.top }
            .pointerInput(key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        if (selected.isEmpty()) { toggle(); chosenByPress = true }
                        dragKey = key
                        pointer = coords[0]?.localToRoot(start) ?: Offset.Zero
                        origin[2] = pointer.x - origin[0]; origin[3] = pointer.y - origin[1]
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        pointer = coords[0]?.localToRoot(change.position) ?: pointer
                        moveUnderFinger()
                    },
                    onDragEnd = {
                        // Let go without having moved the card: it stays chosen.
                        if (dragOrder == null) dragKey = null else { dropNow(); if (chosenByPress) onToggle(key) }
                        chosenByPress = false
                    },
                    onDragCancel = { dragKey = null; dragOrder = null; if (chosenByPress) onToggle(key); chosenByPress = false },
                )
            }
            .then(lift)
    }

    /** The held card does not glide to its new place (it is under the finger) and lies above the others. */
    fun LazyStaggeredGridItemScope.heldItem(held: Boolean): Modifier =
        if (held) Modifier.animateItem(placementSpec = null).zIndex(1f) else Modifier.animateItem()

    fun cardClick(note: KeepNote, account: String) {
        if (selected.isNotEmpty()) onToggle(account + "|" + note.id) else open(note, account, false)
    }

    /** The frame around a chosen card; it clips, so it goes to the card alone, not to the number on its corner. */
    @Composable
    fun frameModifier(key: String): Modifier =
        if (key in selected) {
            Modifier.clip(MaterialTheme.shapes.medium).border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
        } else {
            Modifier
        }

    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = if (app.layout == LAYOUT_LIST) StaggeredGridCells.Fixed(1) else StaggeredGridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        modifier = Modifier.fillMaxSize().onGloballyPositioned { gridBounds = it.boundsInRoot() },
    ) {
        if (screen == AppScreen.Trash) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Text(stringResource(R.string.trash_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val pinned = if (screen == AppScreen.Notes && app.pinnedSection) list.filter { it.first.pinned } else emptyList()
        val others = list - pinned.toSet()
        if (pinned.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) { SectionLabel(stringResource(R.string.pinned_section)) }
            items(pinned, key = { it.second + "|" + it.first.id }) { (note, account) ->
                SwipeCard(app, screen == AppScreen.Notes, { onSwiped(note, account, it) }, heldItem(dragKey == account + "|" + note.id)) {
                    NoteTile(activity, resumeKey, unlocker, note, account, app, query, widgetPosition = positions[account + "|" + note.id], onClick = { cardClick(note, account) }, modifier = dragModifier(account + "|" + note.id), frame = frameModifier(account + "|" + note.id))
                }
            }
            if (others.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) { SectionLabel(stringResource(R.string.others_section)) }
            }
        }
        items(others, key = { it.second + "|" + it.first.id }) { (note, account) ->
            SwipeCard(app, screen == AppScreen.Notes, { onSwiped(note, account, it) }, heldItem(dragKey == account + "|" + note.id)) {
            NoteTile(activity, resumeKey, unlocker, note, account, app, query, widgetPosition = positions[account + "|" + note.id], onClick = { cardClick(note, account) }, modifier = dragModifier(account + "|" + note.id), frame = frameModifier(account + "|" + note.id)) {
                // Archive and trash get their own actions below the note.
                if (screen == AppScreen.Archive) {
                    TextButton(onClick = { act({}) { KeepRepository.unarchiveNote(context, account, note.id) } }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.unarchive_note))
                    }
                }
                if (screen == AppScreen.Trash) {
                    TextButton(onClick = { act({}) { KeepRepository.untrashNote(context, account, note.id) } }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.restore_note))
                    }
                    TextButton(onClick = { askDelete(note to account) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            }
        }
    }
}
