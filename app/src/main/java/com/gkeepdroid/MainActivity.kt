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
        var screen by remember { mutableStateOf(AppScreen.Notes) }
        var query by remember { mutableStateOf("") }
        var filterAccount by remember { mutableStateOf<String?>(null) }
        var filterLabel by remember { mutableStateOf<String?>(null) }
        // More filters for the search: kind of note, with a reminder, edited lately.
        var filterKind by remember { mutableStateOf(0) } // 0 all, 1 notes, 2 checklists
        var filterReminder by remember { mutableStateOf(false) }
        var filterRecent by remember { mutableStateOf(false) }
        var filterImage by remember { mutableStateOf(false) }
        var filterPinned by remember { mutableStateOf(false) }
        var filterDialog by remember { mutableStateOf(false) }
        var undoKey by remember { mutableStateOf(0) }
        var syncing by remember { mutableStateOf(false) }
        var pulling by remember { mutableStateOf(false) }
        // Several notes chosen at once (keys are account|id); while any is chosen, the bar above the list turns into their actions.
        var selected by remember { mutableStateOf(emptySet<String>()) }
        var selectionMenu by remember { mutableStateOf(false) }
        var colorDialog by remember { mutableStateOf(false) }
        var labelDialog by remember { mutableStateOf(false) }
        var emptyTrashDialog by remember { mutableStateOf(false) }
        var deleteSelected by remember { mutableStateOf<List<Pair<KeepNote, String>>?>(null) }
        var searchFocused by remember { mutableStateOf(false) }
        var history by remember { mutableStateOf(SearchHistory.load(context)) }
        var menuOpen by remember { mutableStateOf(false) }
        var newMenuOpen by remember { mutableStateOf(false) }
        var deleteTarget by remember { mutableStateOf<Pair<KeepNote, String>?>(null) }
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

        deleteTarget?.let { (note, account) ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                text = { Text(stringResource(R.string.delete_local_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget = null
                        act { KeepRepository.deleteForever(context, account, note.id) }
                    }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.delete_forever)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }

        deleteSelected?.let { targets ->
            AlertDialog(
                onDismissRequest = { deleteSelected = null },
                text = { Text(stringResource(R.string.delete_selected_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteSelected = null
                        act { targets.forEach { (note, account) -> KeepRepository.deleteForever(context, account, note.id) } }
                    }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.delete_forever)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteSelected = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        if (emptyTrashDialog) {
            AlertDialog(
                onDismissRequest = { emptyTrashDialog = false },
                text = { Text(stringResource(R.string.empty_trash_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        emptyTrashDialog = false
                        // Everything in the trash of the accounts on screen, whatever the search says.
                        val trashed = accounts.filter { filterAccount == null || it == filterAccount }.flatMap { account ->
                            NotesCache.readOther(context, account).filter { it.state == "trashed" }.map { it to account }
                        }
                        act { trashed.forEach { (note, account) -> KeepRepository.deleteForever(context, account, note.id) } }
                    }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.empty_trash)) }
                },
                dismissButton = {
                    TextButton(onClick = { emptyTrashDialog = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        if (colorDialog) {
            ColorChoiceDialog(
                onPick = { name -> colorDialog = false; editSelected { note, _ -> note.copy(color = name) } },
                onDismiss = { colorDialog = false },
            )
        }
        if (labelDialog) {
            val accountsOfSelection = selectedEntries.map { it.second }.distinct()
            val labelOptions = accountsOfSelection.flatMap { NotesCache.labels(context, it) }.distinct().sortedBy { it.lowercase() }
            val allHave = labelOptions.filter { label -> selectedEntries.all { label in it.first.labels } }.toSet()
            LabelChoiceDialog(
                labels = labelOptions,
                allHave = allHave,
                onApply = { added, removed ->
                    labelDialog = false
                    editSelected { note, account ->
                        // A label is only added where the account knows it.
                        val known = NotesCache.labels(context, account)
                        note.copy(labels = ((note.labels + added.filter { it in known }) - removed).distinct())
                    }
                },
                onDismiss = { labelDialog = false },
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
                // Search field and the menu. A running sync shows as a thin line on the row's lower edge, which takes no room.
                Box {
                if (selected.isEmpty()) Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (screen != AppScreen.Notes) {
                        BackButton { screen = AppScreen.Notes }
                    }
                    AppSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { history = SearchHistory.add(context, query) },
                        onFocusChange = {
                            searchFocused = it
                            if (!it) history = SearchHistory.add(context, query)
                        },
                        // The field names how many notes it searches (the ones the filters leave).
                        placeholder = when {
                            else -> androidx.compose.ui.res.pluralStringResource(
                                when (screen) {
                                    AppScreen.Notes -> R.plurals.search_count_notes
                                    AppScreen.Archive -> R.plurals.search_count_archive
                                    AppScreen.Trash -> R.plurals.search_count_trash
                                },
                                shown.size, shown.size,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_actions))
                        }
                        GroupedMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            groups = listOf(
                                listOf(MenuEntry(stringResource(R.string.refresh_action), R.drawable.ic_refresh) { menuOpen = false; sync() }),
                                if (screen == AppScreen.Trash && shown.isNotEmpty()) listOf(
                                    MenuEntry(stringResource(R.string.empty_trash), R.drawable.ic_delete) { menuOpen = false; emptyTrashDialog = true },
                                ) else emptyList(),
                                listOf(
                                    MenuEntry(stringResource(R.string.archive_note), R.drawable.ic_archive) { menuOpen = false; screen = AppScreen.Archive },
                                    MenuEntry(stringResource(R.string.trash_title), R.drawable.ic_delete) { menuOpen = false; screen = AppScreen.Trash },
                                ),
                                if (screen == AppScreen.Notes && app.foldNotes) listOf(
                                    MenuEntry(stringResource(R.string.fold_all), R.drawable.ic_fold) {
                                        menuOpen = false
                                        FoldStore.setAll(context, shown.map { FoldStore.key(it.second, it.first.id) }, true)
                                        scope.launch { WidgetRefresh.all(context) }
                                    },
                                    MenuEntry(stringResource(R.string.unfold_all), R.drawable.ic_unfold) {
                                        menuOpen = false
                                        FoldStore.setAll(context, shown.map { FoldStore.key(it.second, it.first.id) }, false)
                                        scope.launch { WidgetRefresh.all(context) }
                                    },
                                ) else emptyList(),
                                buildList {
                                    if (app.lockApp && AppLock.enabled(this@MainActivity)) {
                                        add(MenuEntry(stringResource(R.string.app_lock_title), R.drawable.ic_lock) { menuOpen = false; AppLock.lockNow() })
                                    }
                                    add(
                                        MenuEntry(stringResource(R.string.menu_settings), R.drawable.ic_settings) {
                                            menuOpen = false
                                            startActivity(Intent(context, SettingsActivity::class.java))
                                        },
                                    )
                                },
                            ),
                        )
                    }
                }
                else Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(SearchBarDefaults.InputFieldHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selected = emptySet() }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(android.R.string.cancel))
                    }
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.selected_count, selected.size, selected.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (screen == AppScreen.Notes) IconButton(onClick = {
                        val pin = selectedEntries.any { !it.first.pinned }
                        editSelected { note, _ -> note.copy(pinned = pin) }
                    }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_push_pin), contentDescription = stringResource(R.string.pinned_section))
                    }
                    if (screen != AppScreen.Trash) IconButton(onClick = {
                        if (screen == AppScreen.Archive) moveSelected { note, account -> KeepRepository.unarchiveNote(context, account, note.id) }
                        else moveSelected { note, account -> if (!CredentialStore.isLocal(account)) KeepRepository.archiveNote(context, account, note.id) }
                    }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painterResource(R.drawable.ic_archive),
                            contentDescription = stringResource(if (screen == AppScreen.Archive) R.string.unarchive_note else R.string.archive_note),
                        )
                    }
                    IconButton(onClick = {
                        if (screen == AppScreen.Trash) deleteSelected = selectedEntries else trashSelected()
                    }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(if (screen == AppScreen.Trash) R.string.delete_forever else R.string.trash_title),
                        )
                    }
                    Box {
                        IconButton(onClick = { selectionMenu = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_actions))
                        }
                        GroupedMenu(
                            expanded = selectionMenu,
                            onDismissRequest = { selectionMenu = false },
                            groups = listOf(
                                if (screen == AppScreen.Trash) listOf(
                                    MenuEntry(stringResource(R.string.restore_note), R.drawable.ic_refresh) {
                                        selectionMenu = false
                                        moveSelected { note, account -> KeepRepository.untrashNote(context, account, note.id) }
                                    },
                                ) else listOf(
                                    MenuEntry(stringResource(R.string.color), R.drawable.ic_color_none) { selectionMenu = false; colorDialog = true },
                                    MenuEntry(stringResource(R.string.labels_title), R.drawable.ic_checklist) { selectionMenu = false; labelDialog = true },
                                    MenuEntry(stringResource(R.string.duplicate_note), R.drawable.ic_open_new) { selectionMenu = false; duplicateSelected() },
                                ),
                                listOf(MenuEntry(stringResource(R.string.select_all), R.drawable.ic_done) {
                                    selectionMenu = false
                                    selected = shown.map(::keyOf).toSet()
                                }),
                            ),
                        )
                    }
                }
                if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(2.dp))
                }

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
                val notificationsOn = rememberNotificationsOn(this@MainActivity)
                val hintPrefs = remember { context.getSharedPreferences("reminder_hints", android.content.Context.MODE_PRIVATE) }
                var notifHintHidden by remember { mutableStateOf(hintPrefs.getBoolean("notif_hint_dismissed", false)) }
                if (!notificationsOn && !notifHintHidden) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 4.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium).padding(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.notif_off_main),
                            color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { context.startActivity(Reminders.notificationSettings(context)) }, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.reminder_open_settings))
                            }
                            TextButton(onClick = {
                                hintPrefs.edit { putBoolean("notif_hint_dismissed", true) }
                                notifHintHidden = true
                            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.notif_hint_dismiss)) }
                        }
                    }
                }
                val failedEdits = remember(reloadKey, syncing) { PendingEdits.all(context).filter { it.error.isNotBlank() } }
                if (!notificationsOn) failedEdits.forEach { edit ->
                    Text(
                        SaveFailures.message(context, edit, edit.error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
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
                        }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.sign_in_again)) }
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

    @Composable
    private fun NotesGrid(
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
            // Pinned and other notes keep their own sections (only the notes screen has them).
            if (screen == AppScreen.Notes && app.pinnedSection && current[from].first.pinned != current[to].first.pinned) return
            lastMove = now
            dragOrder = current.toMutableList().also { it.add(to, it.removeAt(from)) }
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
        val dropNow by androidx.compose.runtime.rememberUpdatedState<() -> Unit>({ drop() })

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
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val toggle = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onToggle(key) }
            // Once notes are chosen, a tap adds or removes one, and the cards no longer move (the card being pressed keeps its gesture).
            if (selected.isNotEmpty() && dragKey != key) return Modifier
            // Without a place to drag to, a long press chooses the note.
            if (!canReorder) return Modifier.pointerInput(key) { detectTapGestures(onLongPress = { toggle() }) }
            val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
            // The long press chooses the card at once, so it shows what is happening; dragging it on turns that back into a move.
            var chosenByPress by remember { mutableStateOf(false) }
            return Modifier
                .onGloballyPositioned { coords[0] = it; cardBounds[key] = it.boundsInRoot() }
                .alpha(if (dragKey == key) 0.4f else 1f)
                .pointerInput(key) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            if (selected.isEmpty()) { toggle(); chosenByPress = true }
                            dragKey = key
                            pointer = coords[0]?.localToRoot(start) ?: Offset.Zero
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
        }

        fun cardClick(note: KeepNote, account: String) {
            if (selected.isNotEmpty()) onToggle(account + "|" + note.id) else open(note, account, false)
        }

        /** The long-press behavior of a card, and a frame around it while it is chosen. */
        @Composable
        fun cardModifier(note: KeepNote, account: String): Modifier {
            val key = account + "|" + note.id
            val frame = if (key in selected) {
                Modifier.clip(MaterialTheme.shapes.medium).border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
            } else {
                Modifier
            }
            return dragModifier(key).then(frame)
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
                    SwipeCard(app, screen == AppScreen.Notes, { onSwiped(note, account, it) }, Modifier.animateItem()) {
                        NoteTile(this@MainActivity, resumeKey, unlocker, note, account, app, query, onClick = { cardClick(note, account) }, modifier = cardModifier(note, account))
                    }
                }
                if (others.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) { SectionLabel(stringResource(R.string.others_section)) }
                }
            }
            items(others, key = { it.second + "|" + it.first.id }) { (note, account) ->
                SwipeCard(app, screen == AppScreen.Notes, { onSwiped(note, account, it) }, Modifier.animateItem()) {
                NoteTile(this@MainActivity, resumeKey, unlocker, note, account, app, query, onClick = { cardClick(note, account) }, modifier = cardModifier(note, account)) {
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

    private fun openEditor(note: KeepNote?, account: String, newList: Boolean) {
        startActivity(
            Intent(this, EditNoteActivity::class.java)
                .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account)
                .putExtra(EditNoteActivity.EXTRA_NEW_LIST, newList)
                .also { if (note != null) it.putExtra(EditNoteActivity.EXTRA_NOTE_ID, note.id) }
        )
    }
}
