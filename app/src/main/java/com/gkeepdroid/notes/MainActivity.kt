package com.gkeepdroid

import android.content.Intent
import androidx.core.content.edit
import android.text.format.DateUtils
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
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

internal enum class AppScreen { Notes, Archive, Trash }

/** The app itself: all notes in full screen, with search, new notes, archive and trash. */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private var reloadKey by mutableIntStateOf(0)
    private var resumeKey by mutableIntStateOf(0)
    private var themeMode by mutableIntStateOf(THEME_SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switches from the start-screen theme to the normal one; must come before super.onCreate.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { KeepTheme(themeMode) { AppLock.Gate(this) { NotesApp() } } }
    }

    // The cards ask for the lock through this, so only one result launcher exists for all of them.
    private var unlocker: ((Int, (Boolean) -> Unit) -> Unit)? = null

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
        val model = remember { NotesModel() }
        var screen by model::screen
        var query by model::query
        var filterAccount by model::filterAccount
        var filterLabel by model::filterLabel
        // More filters for the search: kind of note, with a reminder, edited lately.
        var filterKind by model::filterKind
        var filterReminder by model::filterReminder
        var filterRecent by model::filterRecent
        var filterImage by model::filterImage
        var filterPinned by model::filterPinned
        var filterDialog by model::filterDialog
        var undoKey by model::undoKey
        var syncing by model::syncing
        var pulling by model::pulling
        // Several notes chosen at once (keys are account|id); while any is chosen, the bar above the list turns into their actions.
        var selected by model::selected
        var selectionMenu by model::selectionMenu
        var colorDialog by model::colorDialog
        var labelDialog by model::labelDialog
        var emptyTrashDialog by model::emptyTrashDialog
        var deleteSelected by model::deleteSelected
        var searchFocused by model::searchFocused
        remember { model.history = SearchHistory.load(context) }
        var history by model::history
        var menuOpen by model::menuOpen
        var newMenuOpen by model::newMenuOpen
        var deleteTarget by model::deleteTarget
        val accounts = remember(reloadKey) { CredentialStore.accounts(context) }
        val app = remember(reloadKey) { AppSettingsStore.load(context) }

        // Notes changed in the background (a saved or converted note arriving, a sync) show up without reopening.
        val cacheVersion = NotesCache.version.intValue
        LaunchedEffect(cacheVersion) { if (cacheVersion > 0) reloadKey++ }

        BackHandler(enabled = screen != AppScreen.Notes) { screen = AppScreen.Notes }
        // Back folds the open new-note menu before it does anything else.
        BackHandler(enabled = newMenuOpen) { newMenuOpen = false }

        fun sync() = scope.launch {
            syncing = true
            runCatching { KeepRepository.syncAll(context) }
            syncing = false
            reloadKey++
        }
        val unlock = AppLock.rememberUnlock(this@MainActivity)
        unlocker = unlock
        fun act(onDenied: () -> Unit = {}, block: suspend () -> Unit) {
            if (!AppLock.editAllowed(this@MainActivity)) {
                unlock(R.string.lock_prompt_edit) { ok -> if (ok) act(onDenied, block) else { onDenied(); reloadKey++ } }
                return
            }
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
        LaunchedEffect(resumeKey, undoKey) {
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
        if (filterLabel != null && (filterLabel !in labelNames || !app.filterBar || app.filterChips and FILTER_LABELS == 0)) filterLabel = null

        val shown = remember(reloadKey, screen, accounts, filterAccount, filterLabel, query, app, filterKind, filterReminder, filterRecent, filterImage, filterPinned) {
            val used = accounts.filter { filterAccount == null || it == filterAccount }
            val entries = used.flatMap { account ->
                val notes = when (screen) {
                    AppScreen.Notes -> NotesCache.read(context, account)
                    AppScreen.Archive -> NotesCache.readOther(context, account).filter { it.state == "archived" }
                    AppScreen.Trash -> NotesCache.readOther(context, account).filter { it.state == "trashed" }
                }
                notes.map { it to account }
            }.filter { it.first.matches(query) && (screen != AppScreen.Notes || filterLabel == null || filterLabel in it.first.labels) }
                .filter { (note, account) ->
                    // A filter that is not on the bar (or a bar that is off) does not filter.
                    val on = { bit: Int -> app.filterBar && app.filterChips and bit != 0 }
                    screen != AppScreen.Notes || (
                        (filterKind == 0 || !(on(FILTER_NOTES) || on(FILTER_LISTS)) || (filterKind == 2) == note.isList) &&
                            (!filterReminder || !on(FILTER_REMINDER) || Reminders.list(context, account, note.id).isNotEmpty()) &&
                            (!filterRecent || !on(FILTER_RECENT) || System.currentTimeMillis() - note.updated < 7 * 24 * 3_600_000L) &&
                            (!filterImage || !on(FILTER_IMAGE) || note.images.isNotEmpty()) &&
                            (!filterPinned || !on(FILTER_PINNED) || note.pinned)
                        )
                }
            // Archive and trash are sorted like the notes.
            when (app.sortMode) {
                SORT_EDITED -> entries.sortedByDescending { it.first.updated }
                SORT_CREATED -> entries.sortedByDescending { it.first.created }
                SORT_TITLE -> entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { noteLabel(it.first) })
                else -> entries.sortedByDescending { it.first.sort }
            }
        }

        fun keyOf(entry: Pair<KeepNote, String>) = entry.second + "|" + entry.first.id
        val selectedEntries = shown.filter { keyOf(it) in selected }
        // Notes that left the list (archived, searched away) are no longer chosen.
        LaunchedEffect(shown) { val keys = shown.map(::keyOf).toSet(); if (!keys.containsAll(selected)) selected = selected intersect keys }
        LaunchedEffect(screen) { selected = emptySet() }
        BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

        /** Changes every chosen note like an edit in the editor would: stored at once, sent in the background. */
        fun editSelected(change: (KeepNote, String) -> KeepNote) {
            val targets = selectedEntries
            selected = emptySet()
            act {
                val privateKeys = PrivateNotes.load(context)
                targets.forEach { (note, account) ->
                    val n = change(note, account)
                    val args = KeepRepository.updateNoteArgs(
                        n.id, n.title, n.text, n.items, n.color, n.pinned, emptyList(),
                        withHiddenLabel(n.labels, PrivateNotes.isPrivate(note, account, privateKeys)),
                    )
                    PrivateNotes.set(context, account, n.id, false)
                    KeepRepository.queueEdit(context, PendingEdit(account, "update_note", args))
                }
            }
        }
        fun moveSelected(block: suspend (KeepNote, String) -> Unit) {
            val targets = selectedEntries
            selected = emptySet()
            act { targets.forEach { (note, account) -> block(note, account) } }
        }
        fun duplicateSelected() {
            val targets = selectedEntries
            selected = emptySet()
            act {
                val privateKeys = PrivateNotes.load(context)
                targets.forEach { (n, account) ->
                    val args = KeepRepository.createNoteArgs(
                        n.title, n.text, n.isList, n.items, n.color, n.pinned,
                        withHiddenLabel(n.labels, PrivateNotes.isPrivate(n, account, privateKeys)),
                    )
                    if (CredentialStore.isLocal(account)) KeepRepository.run(context, account, "create_note", args)
                    else KeepRepository.queueEdit(context, PendingEdit(account, "create_note", args))
                }
            }
        }
        fun trashSelected() {
            // Local notes have no trash: those are deleted, after asking.
            val (local, remote) = selectedEntries.partition { CredentialStore.isLocal(it.second) }
            selected = emptySet()
            if (local.isNotEmpty()) deleteSelected = local
            if (remote.isNotEmpty()) act { remote.forEach { (note, account) -> KeepRepository.trashNote(context, account, note.id) } }
        }

        /** A card was swiped (and let go): the action chosen in the settings, with "Undo" where there is one. */
        fun swiped(note: KeepNote, account: String, action: Int) {
            /** Changes of the note itself (pin, hide) are edits like any other: they ask for the lock when it is on. */
            fun editNote(pinned: Boolean, hide: Boolean) {
                act {
                    val args = KeepRepository.updateNoteArgs(
                        note.id, note.title, note.text, note.items, note.color, pinned, emptyList(), withHiddenLabel(note.labels, hide),
                    )
                    PrivateNotes.set(context, account, note.id, false)
                    KeepRepository.queueEdit(context, PendingEdit(account, "update_note", args))
                }
            }
            when {
                action == SWIPE_ARCHIVE && !CredentialStore.isLocal(account) -> act {
                    KeepRepository.archiveNote(context, account, note.id)
                    UndoStore.set(account, note.id, UndoStore.Kind.ARCHIVE)
                    undoKey++
                }
                // Local notes have no trash: deleting them asks first.
                action == SWIPE_TRASH && CredentialStore.isLocal(account) -> deleteTarget = note to account
                action == SWIPE_TRASH -> act {
                    KeepRepository.trashNote(context, account, note.id)
                    UndoStore.set(account, note.id, UndoStore.Kind.TRASH)
                    undoKey++
                }
                action == SWIPE_PIN -> editNote(!note.pinned, PrivateNotes.isPrivate(note, account, PrivateNotes.load(context)))
                action == SWIPE_HIDE -> editNote(note.pinned, !PrivateNotes.isPrivate(note, account, PrivateNotes.load(context)))
                action == SWIPE_REMINDER -> {
                    val open = {
                        startActivity(
                            Intent(context, RemindersActivity::class.java)
                                .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account)
                                .putExtra(EditNoteActivity.EXTRA_NOTE_ID, note.id)
                        )
                    }
                    if (AppLock.editAllowed(this@MainActivity)) open() else unlock(R.string.lock_prompt_edit) { ok -> if (ok) open() }
                }
                action == SWIPE_SHARE && !PrivateNotes.isPrivate(note, account, PrivateNotes.load(context)) -> {
                    val body = if (note.isList) note.items.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text } else note.text
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, note.title).putExtra(Intent.EXTRA_TEXT, body),
                            null,
                        )
                    )
                }
            }
        }

        NotesDialogs(this@MainActivity, model, app, selectedEntries, scope, { sync() }, { editSelected(it) }, { moveSelected(it) }, { denied, block -> act(denied, block) }, accounts)

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
                    // The button unfolds the two kinds of new note above itself. The menu keeps its own distance to the
                    // screen edges, and so does the scaffold; the offset takes back the scaffold's.
                    FloatingActionButtonMenu(
                        expanded = newMenuOpen,
                        modifier = Modifier.offset(x = 16.dp, y = 16.dp),
                        button = {
                            ToggleFloatingActionButton(checked = newMenuOpen, onCheckedChange = { newMenuOpen = it }) {
                                Icon(
                                    painterResource(if (checkedProgress > 0.5f) R.drawable.ic_close else R.drawable.ic_add),
                                    contentDescription = stringResource(R.string.new_note),
                                    modifier = Modifier.animateIcon({ checkedProgress }),
                                )
                            }
                        },
                    ) {
                        FloatingActionButtonMenuItem(
                            onClick = { newMenuOpen = false; openEditor(null, filterAccount ?: accounts.first(), newList = true) },
                            text = { Text(stringResource(R.string.new_list)) },
                            icon = { Icon(painterResource(R.drawable.ic_checklist), contentDescription = null) },
                        )
                        FloatingActionButtonMenuItem(
                            onClick = { newMenuOpen = false; openEditor(null, filterAccount ?: accounts.first(), newList = false) },
                            text = { Text(stringResource(R.string.new_note)) },
                            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                NotesTopBar(
                    this@MainActivity, model, app, shown, selectedEntries, scope, { sync() }, ::keyOf,
                    { moveSelected(it) }, { editSelected(it) }, { trashSelected() }, { duplicateSelected() },
                )

                if (searchFocused && query.isBlank() && history.isNotEmpty() && selected.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        history.forEach { recent ->
                            AssistChip(onClick = { query = recent }, label = { Text(recent, maxLines = 1) })
                        }
                        IconButton(onClick = { SearchHistory.clear(context); history = emptyList() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_history_clear))
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

                if (screen == AppScreen.Notes && app.filterBar) {
                    val chips = app.filterChips
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (chips and FILTER_NOTES != 0) FilterChip(selected = filterKind == 1, onClick = { filterKind = if (filterKind == 1) 0 else 1 }, label = { Text(stringResource(R.string.filter_notes)) })
                        if (chips and FILTER_LISTS != 0) FilterChip(selected = filterKind == 2, onClick = { filterKind = if (filterKind == 2) 0 else 2 }, label = { Text(stringResource(R.string.list_section)) })
                        if (chips and FILTER_REMINDER != 0) FilterChip(selected = filterReminder, onClick = { filterReminder = !filterReminder }, label = { Text(stringResource(R.string.filter_reminders)) })
                        if (chips and FILTER_RECENT != 0) FilterChip(selected = filterRecent, onClick = { filterRecent = !filterRecent }, label = { Text(stringResource(R.string.filter_recent)) })
                        if (chips and FILTER_IMAGE != 0) FilterChip(selected = filterImage, onClick = { filterImage = !filterImage }, label = { Text(stringResource(R.string.filter_with_image)) })
                        if (chips and FILTER_PINNED != 0) FilterChip(selected = filterPinned, onClick = { filterPinned = !filterPinned }, label = { Text(stringResource(R.string.filter_pinned)) })
                        if (chips and FILTER_LABELS != 0) {
                            if (labelNames.isNotEmpty()) FilterChip(
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
                        // The cog at the end chooses which filters the bar offers.
                        IconButton(onClick = { filterDialog = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.filter_bar_title))
                        }
                    }
                }
                if (filterDialog) {
                    AlertDialog(
                        onDismissRequest = { filterDialog = false },
                        title = { Text(stringResource(R.string.filter_bar_title)) },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                FilterBarOptions(
                                    app.filterBar, app.filterChips,
                                    { on -> AppSettingsStore.save(context, app.copy(filterBar = on)); reloadKey++ },
                                    { bits -> AppSettingsStore.save(context, app.copy(filterChips = bits)); reloadKey++ },
                                )
                            }
                        },
                        confirmButton = { TextButton(onClick = { filterDialog = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.done)) } },
                    )
                }

                // Without the right to notify, a failed sync or save can only be told here, at the top.
                NotesBanners(this@MainActivity, model, reloadKey, accounts, scope, { sync() })

                if (accounts.isEmpty()) {
                    // First start: nothing to show before an account (or local mode) exists.
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.no_notes), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { startActivity(Intent(context, SettingsActivity::class.java)) }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.menu_settings))
                        }
                    }
                } else {
                    // Notes can be dragged into a new place in Keep's order, for one account and without a search.
                    val canReorder = app.sortMode == SORT_KEEP && query.isBlank() && (screen != AppScreen.Notes || filterLabel == null) &&
                        shown.map { it.second }.distinct().size == 1
                    val grid = @Composable {
                        NotesGrid(
                            this@MainActivity, resumeKey, unlocker,
                            shown, screen, app, canReorder, query, selected,
                            { key -> selected = if (key in selected) selected - key else selected + key },
                            { note, account, edit -> if (query.isNotBlank()) history = SearchHistory.add(context, query); openEditor(note, account, edit) },
                            { denied, block -> act(denied, block) }, { deleteTarget = it }, ::swiped,
                        )
                    }
                    if (app.pullToRefresh) {
                        PullToRefreshBox(
                            isRefreshing = pulling,
                            onRefresh = { pulling = true; scope.launch { sync().join(); pulling = false } },
                            modifier = Modifier.fillMaxSize(),
                        ) { grid() }
                    } else grid()
                }
            }
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
