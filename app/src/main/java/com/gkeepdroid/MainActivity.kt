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
        var menuOpen by remember { mutableStateOf(false) }
        var newMenuOpen by remember { mutableStateOf(false) }
        var deleteTarget by remember { mutableStateOf<Pair<KeepNote, String>?>(null) }
        val accounts = remember(reloadKey) { CredentialStore.accounts(context) }
        val app = remember(reloadKey) { AppSettingsStore.load(context) }

        // Notes changed in the background (a saved or converted note arriving, a sync) show up without reopening.
        val cacheVersion = NotesCache.version.intValue
        LaunchedEffect(cacheVersion) { if (cacheVersion > 0) reloadKey++ }

        BackHandler(enabled = screen != AppScreen.Notes) { screen = AppScreen.Notes }

        fun sync() {
            scope.launch {
                syncing = true
                runCatching { KeepRepository.syncAll(context) }
                syncing = false
                reloadKey++
            }
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
                            if (screen == AppScreen.Notes) {
                                if (app.foldNotes) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.fold_all)) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_fold), contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            FoldStore.setAll(context, shown.map { FoldStore.key(it.second, it.first.id) }, true)
                                            scope.launch { WidgetRefresh.all(context) }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.unfold_all)) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_unfold), contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            FoldStore.setAll(context, shown.map { FoldStore.key(it.second, it.first.id) }, false)
                                            scope.launch { WidgetRefresh.all(context) }
                                        },
                                    )
                                }
                            }
                            if (app.lockApp && AppLock.enabled(this@MainActivity)) DropdownMenuItem(
                                text = { Text(stringResource(R.string.app_lock_title)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
                                onClick = { menuOpen = false; AppLock.lockNow() },
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
                        IconButton(onClick = { filterDialog = true }) {
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
                        confirmButton = { TextButton(onClick = { filterDialog = false }) { Text(stringResource(R.string.done)) } },
                    )
                }

                // Without the right to notify, a failed sync or save can only be told here, at the top.
                val notificationsOn = rememberNotificationsOn(this@MainActivity)
                val hintPrefs = remember { context.getSharedPreferences("reminder_hints", android.content.Context.MODE_PRIVATE) }
                var notifHintHidden by remember { mutableStateOf(hintPrefs.getBoolean("notif_hint_dismissed", false)) }
                if (!notificationsOn && !notifHintHidden) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 4.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)).padding(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.notif_off_main),
                            color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { context.startActivity(Reminders.notificationSettings(context)) }) {
                                Text(stringResource(R.string.reminder_open_settings))
                            }
                            TextButton(onClick = {
                                hintPrefs.edit().putBoolean("notif_hint_dismissed", true).apply()
                                notifHintHidden = true
                            }) { Text(stringResource(R.string.notif_hint_dismiss)) }
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
                    val canReorder = app.sortMode == SORT_KEEP && query.isBlank() && (screen != AppScreen.Notes || filterLabel == null) &&
                        shown.map { it.second }.distinct().size == 1
                    NotesGrid(shown, screen, app, canReorder, ::openEditor, { denied, block -> act(denied, block) }, { deleteTarget = it }, ::swiped)
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
                        onDragEnd = { dropNow() },
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
                    SwipeCard(app, screen == AppScreen.Notes, { onSwiped(note, account, it) }, Modifier.animateItem()) {
                        NoteTile(note, account, app, onClick = { open(note, account, false) }, modifier = dragModifier(account + "|" + note.id))
                    }
                }
                if (others.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) { SectionLabel(stringResource(R.string.others_section)) }
                }
            }
            items(others, key = { it.second + "|" + it.first.id }) { (note, account) ->
                SwipeCard(app, screen == AppScreen.Notes, { onSwiped(note, account, it) }, Modifier.animateItem()) {
                NoteTile(note, account, app, onClick = { open(note, account, false) }, modifier = dragModifier(account + "|" + note.id)) {
                    // Archive and trash get their own actions below the note.
                    if (screen == AppScreen.Archive) {
                        TextButton(onClick = { act({}) { KeepRepository.unarchiveNote(context, account, note.id) } }) {
                            Text(stringResource(R.string.unarchive_note))
                        }
                    }
                    if (screen == AppScreen.Trash) {
                        TextButton(onClick = { act({}) { KeepRepository.untrashNote(context, account, note.id) } }) {
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
    }

    /**
     * Wraps a card so it can be swiped sideways, but only as far as the symbol behind it is fully shown. The action
     * runs when the finger lets go past the trigger point, and the card slides back.
     */
    @Composable
    private fun SwipeCard(app: AppSettings, enabled: Boolean, onAction: (Int) -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
        if (!enabled || (app.swipeLeft == SWIPE_NONE && app.swipeRight == SWIPE_NONE)) {
            Box(modifier) { content() }
            return
        }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val maxPx = with(density) { 72.dp.toPx() }
        val trigger = maxPx * 0.75f
        var offsetX by remember { mutableStateOf(0f) }
        val current by androidx.compose.runtime.rememberUpdatedState(app)
        val action by androidx.compose.runtime.rememberUpdatedState(onAction)
        val toRight = offsetX > 0f
        val kind = if (toRight) app.swipeRight else app.swipeLeft
        Box(modifier) {
            if (offsetX != 0f && kind != SWIPE_NONE) {
                Box(
                    Modifier.matchParentSize().clip(RoundedCornerShape(14.dp))
                        .background(
                            if (kind == SWIPE_TRASH) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    val (icon, name) = when (kind) {
                        SWIPE_ARCHIVE -> R.drawable.ic_archive to R.string.archive_note
                        SWIPE_TRASH -> R.drawable.ic_delete to R.string.move_to_trash
                        SWIPE_PIN -> R.drawable.ic_push_pin to R.string.pin_note
                        SWIPE_REMINDER -> R.drawable.ic_alarm to R.string.reminders_title
                        SWIPE_SHARE -> R.drawable.ic_share to R.string.share_note
                        SWIPE_HIDE -> R.drawable.ic_lock to R.string.hide_content
                        else -> return@Box
                    }
                    Icon(painterResource(icon), contentDescription = stringResource(name))
                }
            }
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            val lowest = if (current.swipeLeft != SWIPE_NONE) -maxPx else 0f
                            val highest = if (current.swipeRight != SWIPE_NONE) maxPx else 0f
                            offsetX = (offsetX + delta).coerceIn(lowest, highest)
                        },
                        onDragStopped = {
                            val passed = kotlin.math.abs(offsetX) >= trigger
                            val chosen = if (offsetX > 0f) current.swipeRight else current.swipeLeft
                            androidx.compose.animation.core.animate(offsetX, 0f) { value, _ -> offsetX = value }
                            if (passed) action(chosen)
                        },
                    )
            ) { content() }
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
        val scope = rememberCoroutineScope()
        val foldedKeys = remember(FoldStore.version.intValue) { FoldStore.load(context) }
        val folded = app.foldNotes && FoldStore.key(account, note.id) in foldedKeys
        /** Ticks the item right on the card (or removes it): shown at once, sent to Keep a few seconds later. */
        fun toggleItem(item: KeepItem) {
            fun apply() {
                val checked = !item.checked
                val delete = app.listDeleteChecked && checked
                PendingToggles.add(context, PendingToggle(account, note.id, item.id, checked, delete))
                if (delete) NotesCache.removeItem(context, account, note.id, item.id)
                else NotesCache.setChecked(context, account, note.id, item.id, checked)
                NotesCache.version.intValue++
                scope.launch { WidgetRefresh.all(context) }
                ToggleItemWorker.schedule(context, account)
            }
            if (AppLock.editAllowed(this@MainActivity)) apply() else unlocker?.invoke(R.string.lock_prompt_edit) { if (it) apply() }
        }
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
        // Web addresses, e-mail addresses and phone numbers open in the app made for them.
        val linkColor = if (noteColor != null) Color(0xFF0B57D0) else MaterialTheme.colorScheme.primary
        // A private note shows only its title until it is opened with the device lock.
        val hidden = PrivateNotes.isPrivate(note, account, remember(PrivateNotes.version.intValue) { PrivateNotes.load(context) })
        val thumb = remember(note.id, note.images.firstOrNull(), app.showImages, hidden) {
            if (app.showImages && !hidden) note.images.firstOrNull()?.let { NoteImages.thumbnail(context, account, it) } else null
        }
        val lines = if (app.previewLines <= 0) Int.MAX_VALUE else app.previewLines
        val progress = progressText(note)?.takeIf { app.showProgress && !hidden }
        // No title (and nothing else for that line): no room is kept for it.
        val hasTitleRow = note.title.isNotBlank() || note.pinned || progress != null
        // The fold arrow floats over the card's corner: its touch area can be large without making the title row taller.
        Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
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
                // Tall enough for the fold arrow, also when a folded note has nothing else to show.
                .then(if (app.foldNotes) Modifier.heightIn(min = 44.dp) else Modifier)
                // Without a title row the arrow floats over the first line, so that line stops short of it.
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = if (app.foldNotes && !hasTitleRow) 36.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = stringResource(R.string.note_image),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                )
            }
            if (hasTitleRow) {
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
                            contentDescription = stringResource(R.string.pinned_section),
                            tint = textColor,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp),
                        )
                    }
                    // Room for the arrow that floats over the corner; its height is only the title's.
                    if (app.foldNotes) Spacer(Modifier.size(width = 30.dp, height = 20.dp))
                }
            }
            // Read again whenever the screen comes back, as reminders are changed in the editor.
            remember(resumeKey, note.id) { Reminders.list(context, account, note.id) }.let { reminders ->
                if (!folded && app.showReminders && reminders.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(painterResource(R.drawable.ic_alarm), contentDescription = stringResource(R.string.reminder), tint = textColor, modifier = Modifier.size(14.dp))
                    Text(
                        Reminders.label(context, reminders.first().at) + if (reminders.size > 1) "  (+${reminders.size - 1})" else "",
                        color = textColor.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (folded) {
                // Folded: the title is all there is; a note without one shows its first line instead.
                if (note.title.isBlank() && !hidden) {
                    val first = (if (note.isList) note.items.firstOrNull { it.text.isNotBlank() }?.text else note.text.lineSequence().firstOrNull { it.isNotBlank() }).orEmpty()
                    if (first.isNotBlank()) Text(
                        first, color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (hidden) {
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
                // Checked items go last, and can be left out altogether.
                val ordered = if (app.listCheckedToEnd) open + done else note.items
                val visible = if (app.listCheckedToEnd && app.listCollapseChecked) open else ordered
                visible.take(lines).forEach { item ->
                    Row(Modifier.padding(vertical = (app.listSpacingDp / 2f).dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clickable(enabled = app.listCheckInApp) { toggleItem(item) }.padding(2.dp)) {
                            CheckboxPreview(app.listCheckboxStyle, item.checked, app.listCheckboxSizeDp, textColor.copy(alpha = if (item.checked) 0.55f else 1f))
                        }
                        Text(
                            NoteLinks.annotate(item.text, linkColor),
                            color = if (item.checked) textColor.copy(alpha = 0.55f) else textColor,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                if (visible.size > lines) {
                    Text(
                        stringResource(R.string.items_more, visible.size - lines),
                        color = textColor.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else if (note.text.isNotBlank()) {
                Text(
                    NoteLinks.annotate(note.text, linkColor),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (note.title.isBlank() && thumb == null) {
                Text(stringResource(R.string.note_empty), color = textColor.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium)
            }
            }
            if (!folded && app.showEditedTime && note.updated > 0) {
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
                    // Android's narrow font, so "Edited 28 minutes ago" fits on one line more often.
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)),
                    ),
                )
            }
            actions?.let { Column { it() } }
        }
        if (app.foldNotes) {
            Box(
                Modifier.align(Alignment.TopEnd).size(40.dp).clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) {
                    FoldStore.set(context, account, note.id, !folded)
                    scope.launch { WidgetRefresh.all(context) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (folded) R.drawable.ic_arrow_left else R.drawable.ic_arrow_down),
                    contentDescription = stringResource(if (folded) R.string.unfold_all else R.string.fold_all),
                    tint = textColor, modifier = Modifier.size(24.dp),
                )
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
