package com.gkeepdroid

import android.content.Intent
import android.text.format.DateUtils
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class AppScreen { Notes, Archive, Trash }

/** The app itself: all notes in full screen, with search, new notes, archive and trash. */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private var reloadKey by mutableIntStateOf(0)
    private var resumeKey by mutableIntStateOf(0)
    private var themeMode by mutableIntStateOf(THEME_SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KeepTheme(themeMode) { NotesApp() } }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the editor or the settings: re-read the cache and the theme.
        themeMode = SettingsStore.loadGlobal(this).themeMode
        reloadKey++
        resumeKey++
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun NotesApp() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var screen by remember { mutableStateOf(AppScreen.Notes) }
        var query by remember { mutableStateOf("") }
        var filterAccount by remember { mutableStateOf<String?>(null) }
        var filterLabel by remember { mutableStateOf<String?>(null) }
        var syncing by remember { mutableStateOf(false) }
        var menuOpen by remember { mutableStateOf(false) }
        var newMenuOpen by remember { mutableStateOf(false) }
        var deleteTarget by remember { mutableStateOf<Pair<KeepNote, String>?>(null) }
        val accounts = remember(reloadKey) { CredentialStore.accounts(context) }
        val app = remember(reloadKey) { AppSettingsStore.load(context) }

        BackHandler(enabled = screen != AppScreen.Notes) { screen = AppScreen.Notes }

        fun sync() {
            scope.launch {
                syncing = true
                runCatching { KeepRepository.syncAll(context) }
                syncing = false
                reloadKey++
            }
        }
        fun act(block: suspend () -> Unit) {
            scope.launch {
                syncing = true
                runCatching { block() }
                syncing = false
                reloadKey++
            }
        }
        // Opening the app syncs, unless that was done a moment ago (see SyncPolicy).
        LaunchedEffect(resumeKey) { if (SyncPolicy.claim(context, SyncPolicy.Reason.APP_OPEN)) sync() }

        // After archiving or trashing a note in the editor, offer to take it back.
        val snackbar = remember { SnackbarHostState() }
        val trashedText = stringResource(R.string.note_trashed)
        val archivedText = stringResource(R.string.note_archived)
        val undoText = stringResource(R.string.image_undo)
        LaunchedEffect(resumeKey) {
            val undo = UndoStore.take() ?: return@LaunchedEffect
            val result = snackbar.showSnackbar(
                if (undo.kind == UndoStore.Kind.TRASH) trashedText else archivedText,
                actionLabel = undoText, duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                act {
                    if (undo.kind == UndoStore.Kind.TRASH) KeepRepository.untrashNote(context, undo.account, undo.noteId)
                    else KeepRepository.unarchiveNote(context, undo.account, undo.noteId)
                }
            }
        }

        // The labels that the notes on screen carry, as a filter.
        val labelNames = remember(reloadKey, accounts, filterAccount) {
            accounts.filter { filterAccount == null || it == filterAccount }
                .flatMap { account -> NotesCache.read(context, account).flatMap { it.labels } }
                .distinct().sortedBy { it.lowercase() }
        }
        if (filterLabel != null && filterLabel !in labelNames) filterLabel = null

        val shown = remember(reloadKey, screen, accounts, filterAccount, filterLabel, query, app) {
            val used = accounts.filter { filterAccount == null || it == filterAccount }
            val entries = used.flatMap { account ->
                val notes = when (screen) {
                    AppScreen.Notes -> NotesCache.read(context, account)
                    AppScreen.Archive -> NotesCache.readOther(context, account).filter { it.state == "archived" }
                    AppScreen.Trash -> NotesCache.readOther(context, account).filter { it.state == "trashed" }
                }
                notes.map { it to account }
            }.filter { it.first.matches(query) && (filterLabel == null || filterLabel in it.first.labels) }
            if (screen == AppScreen.Notes) {
                when (app.sortMode) {
                    SORT_EDITED -> entries.sortedByDescending { it.first.updated }
                    SORT_CREATED -> entries.sortedByDescending { it.first.created }
                    SORT_TITLE -> entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { noteLabel(it.first) })
                    else -> entries.sortedByDescending { it.first.sort }
                }
            } else {
                entries.sortedByDescending { it.first.updated }
            }
        }

        deleteTarget?.let { (note, account) ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                text = { Text(stringResource(R.string.delete_local_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget = null
                        act { KeepRepository.deleteForever(context, account, note.id) }
                    }) { Text(stringResource(R.string.delete_forever)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            // The default snackbar is light in dark themes; this one follows the app's colors.
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        data,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionColor = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            floatingActionButton = {
                if (screen == AppScreen.Notes && accounts.isNotEmpty()) {
                    Box {
                        FloatingActionButton(onClick = { newMenuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.new_note))
                        }
                        DropdownMenu(expanded = newMenuOpen, onDismissRequest = { newMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.new_note)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                                onClick = { newMenuOpen = false; openEditor(null, filterAccount ?: accounts.first(), newList = false) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.new_list)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_checklist), contentDescription = null) },
                                onClick = { newMenuOpen = false; openEditor(null, filterAccount ?: accounts.first(), newList = true) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                // Search field and the menu.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (screen != AppScreen.Notes) {
                        BackButton { screen = AppScreen.Notes }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                when (screen) {
                                    AppScreen.Notes -> stringResource(R.string.search_notes_hint)
                                    AppScreen.Archive -> stringResource(R.string.archive_note)
                                    AppScreen.Trash -> stringResource(R.string.trash_title)
                                }
                            )
                        },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("✕") }
                        },
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_actions))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.refresh_action)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
                                onClick = { menuOpen = false; sync() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.archive_note)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_archive), contentDescription = null) },
                                onClick = { menuOpen = false; screen = AppScreen.Archive },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.trash_title)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                                onClick = { menuOpen = false; screen = AppScreen.Trash },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_settings)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                                onClick = { menuOpen = false; startActivity(Intent(context, SettingsActivity::class.java)) },
                            )
                        }
                    }
                }

                if (accounts.size > 1) {
                    FlowRow(
                        Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = filterAccount == null,
                            onClick = { filterAccount = null },
                            label = { Text(stringResource(R.string.all_accounts)) },
                        )
                        accounts.forEach { account ->
                            FilterChip(
                                selected = filterAccount == account,
                                onClick = { filterAccount = account },
                                label = { Text(CredentialStore.displayName(context, account)) },
                            )
                        }
                    }
                }

                if (screen == AppScreen.Notes && labelNames.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = filterLabel == null,
                            onClick = { filterLabel = null },
                            label = { Text(stringResource(R.string.all_labels)) },
                        )
                        labelNames.forEach { name ->
                            FilterChip(
                                selected = filterLabel == name,
                                onClick = { filterLabel = if (filterLabel == name) null else name },
                                label = { Text(name) },
                            )
                        }
                    }
                }

                if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                // A failed sync stays visible until the next one works.
                val syncError = remember(reloadKey, syncing) { SyncPolicy.lastError(context) }
                val expired = remember(reloadKey, syncing) { SyncPolicy.expiredAccount(context) }
                if (expired != null && !syncing) {
                    // The login is refused: one tap leads to the accounts to sign in again.
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.sign_in_expired, CredentialStore.displayName(context, expired)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(context, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, "accounts")
                            )
                        }) { Text(stringResource(R.string.sign_in_again)) }
                    }
                } else if (syncError != null && !syncing) {
                    Text(
                        stringResource(R.string.sync_last_error, syncError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (accounts.isEmpty()) {
                    // First start: nothing to show before an account (or local mode) exists.
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.no_notes), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { startActivity(Intent(context, SettingsActivity::class.java)) }) {
                            Text(stringResource(R.string.menu_settings))
                        }
                    }
                } else {
                    // Notes can be dragged into a new place in Keep's order, for one account and without a search.
                    val canReorder = screen == AppScreen.Notes && app.sortMode == SORT_KEEP && query.isBlank() && filterLabel == null &&
                        shown.map { it.second }.distinct().size == 1
                    NotesGrid(shown, screen, app, canReorder, ::openEditor, { block -> act(block) }, { deleteTarget = it })
                }
            }
        }
    }

    @Composable
    private fun NotesGrid(
        shown: List<Pair<KeepNote, String>>,
        screen: AppScreen,
        app: AppSettings,
        canReorder: Boolean,
        open: (KeepNote?, String, Boolean) -> Unit,
        act: (suspend () -> Unit) -> Unit,
        askDelete: (Pair<KeepNote, String>) -> Unit,
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
        var gridBounds by remember { mutableStateOf(Rect.Zero) }
        var dragKey by remember { mutableStateOf<String?>(null) }
        var pointer by remember { mutableStateOf(Offset.Zero) }
        var dragOrder by remember { mutableStateOf<List<Pair<KeepNote, String>>?>(null) }
        val cardBounds = remember { mutableStateMapOf<String, Rect>() }
        var lastMove by remember { mutableStateOf(0L) }
        // The new order is shown until the saved one arrives.
        LaunchedEffect(shown) { dragOrder = null }
        val list = dragOrder ?: shown
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
            // Pinned and other notes keep their own sections.
            if (app.pinnedSection && current[from].first.pinned != current[to].first.pinned) return
            lastMove = now
            dragOrder = current.toMutableList().also { it.add(to, it.removeAt(from)) }
        }

        fun drop() {
            val key = dragKey
            val order = dragOrder
            dragKey = null
            if (key == null || order == null) return
            val entry = order.firstOrNull { keyOf(it) == key } ?: return
            val group = if (app.pinnedSection) order.filter { it.first.pinned == entry.first.pinned } else order
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
                act { KeepRepository.setSort(context, entry.second, entry.first.id, sort) }
            }
        }

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
            if (!canReorder) return Modifier
            val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
            return Modifier
                .onGloballyPositioned { coords[0] = it; cardBounds[key] = it.boundsInRoot() }
                .alpha(if (dragKey == key) 0.4f else 1f)
                .pointerInput(key) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            dragKey = key
                            pointer = coords[0]?.localToRoot(start) ?: Offset.Zero
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            pointer = coords[0]?.localToRoot(change.position) ?: pointer
                            moveUnderFinger()
                        },
                        onDragEnd = { drop() },
                        onDragCancel = { dragKey = null; dragOrder = null },
                    )
                }
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
                    NoteTile(note, account, app, onClick = { open(note, account, false) }, modifier = Modifier.animateItem().then(dragModifier(account + "|" + note.id)))
                }
                if (others.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) { SectionLabel(stringResource(R.string.others_section)) }
                }
            }
            items(others, key = { it.second + "|" + it.first.id }) { (note, account) ->
                NoteTile(note, account, app, onClick = { open(note, account, false) }, modifier = Modifier.animateItem().then(dragModifier(account + "|" + note.id))) {
                    // Archive and trash get their own actions below the note.
                    if (screen == AppScreen.Archive) {
                        TextButton(onClick = { act { KeepRepository.unarchiveNote(context, account, note.id) } }) {
                            Text(stringResource(R.string.unarchive_note))
                        }
                    }
                    if (screen == AppScreen.Trash) {
                        TextButton(onClick = { act { KeepRepository.untrashNote(context, account, note.id) } }) {
                            Text(stringResource(R.string.restore_note))
                        }
                        TextButton(onClick = { askDelete(note to account) }) {
                            Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    /** One note as a card, in its Keep color. */
    @Composable
    private fun NoteTile(
        note: KeepNote,
        account: String,
        app: AppSettings,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        actions: (@Composable () -> Unit)? = null,
    ) {
        val context = LocalContext.current
        val noteColor = if (app.showNoteColors) NoteColors.argb(note.color) else null
        val gray = noteColor == null && app.plainCardStyle == PLAIN_GRAY
        val textColor = when {
            noteColor != null -> DarkNoteText
            gray -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
        val background = when {
            noteColor != null -> Color(noteColor)
            gray -> Color(0xFF424242)
            app.plainCardStyle == PLAIN_BORDER -> MaterialTheme.colorScheme.surface
            else -> Color.Transparent
        }
        val shape = RoundedCornerShape(14.dp)
        // A private note shows only its title until it is opened with the device lock.
        val hidden = app.lockPrivateNotes && PrivateNotes.key(account, note.id) in remember { PrivateNotes.load(context) }
        val thumb = remember(note.id, note.images.firstOrNull(), app.showImages, hidden) {
            if (app.showImages && !hidden) note.images.firstOrNull()?.let { NoteImages.thumbnail(context, account, it) } else null
        }
        val lines = if (app.previewLines <= 0) Int.MAX_VALUE else app.previewLines
        Column(
            modifier.fillMaxWidth()
                .clip(shape)
                .background(background)
                .then(
                    if (noteColor == null && app.plainCardStyle == PLAIN_BORDER) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                )
            }
            val progress = progressText(note)?.takeIf { app.showProgress && !hidden }
            if (note.title.isNotBlank() || note.pinned || progress != null) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        note.title,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (progress != null) {
                        Text(progress, color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    }
                    if (note.pinned) {
                        Icon(
                            painterResource(R.drawable.ic_push_pin),
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp),
                        )
                    }
                }
            }
            if (hidden) {
                Text(
                    stringResource(R.string.note_private_hint),
                    color = textColor.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
            } else {
            if (app.showLabels && note.labels.isNotEmpty()) {
                Text(
                    note.labels.joinToString("  ·  "),
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            if (note.isList) {
                val open = note.items.filter { !it.checked }
                val done = note.items.filter { it.checked }
                (open + done).take(lines).forEach { item ->
                    Text(
                        (if (item.checked) "☑ " else "☐ ") + item.text,
                        color = if (item.checked) textColor.copy(alpha = 0.55f) else textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (note.items.size > lines) {
                    Text(
                        stringResource(R.string.items_more, note.items.size - lines),
                        color = textColor.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else if (note.text.isNotBlank()) {
                Text(
                    note.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (note.title.isBlank() && thumb == null) {
                Text(stringResource(R.string.note_empty), color = textColor.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium)
            }
            }
            if (app.showEditedTime && note.updated > 0) {
                Text(
                    stringResource(
                        R.string.edited_time,
                        if (app.editedAsDate) {
                            DateUtils.formatDateTime(this@MainActivity, note.updated, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME)
                        } else {
                            // The system text starts a sentence ("Vor 47 Minuten"), but here it continues one.
                            DateUtils.getRelativeTimeSpanString(minOf(note.updated, System.currentTimeMillis()), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                                .toString().replaceFirstChar { it.lowercase() }
                        },
                    ),
                    color = textColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            actions?.let { Column { it() } }
        }
    }

    private fun openEditor(note: KeepNote?, account: String, newList: Boolean) {
        startActivity(
            Intent(this, EditNoteActivity::class.java)
                .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account)
                .putExtra(EditNoteActivity.EXTRA_NEW_LIST, newList)
                .also { if (note != null) it.putExtra(EditNoteActivity.EXTRA_NOTE_ID, note.id) }
        )
    }
}
