package com.gkeepdroid

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.app.KeyguardManager
import android.os.Bundle
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.draggable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.key
import java.util.UUID
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

/** Edits an existing note (EXTRA_NOTE_ID) or creates a new note or checklist. */
class EditNoteActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    /** Set by the open editor: saves what was typed when the user leaves with the Home button. */
    private var onHome: (() -> Unit)? = null

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onHome?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val account = CredentialStore.resolve(this, intent.getStringExtra(EXTRA_ACCOUNT).orEmpty())
        if (account == null) {
            finish()
            return
        }
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        // Launcher shortcuts and the widget buttons say "list" in their data address.
        val newList = intent.getBooleanExtra(EXTRA_NEW_LIST, false) || intent.data?.pathSegments?.firstOrNull() == "list"
        val themeMode = SettingsStore.loadGlobal(this).themeMode
        // Pinning applies to the list widget the note was opened from.
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val provider = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        } else {
            null
        }
        val pinWidgetId = widgetId.takeIf { provider?.className == NotesWidgetReceiver::class.java.name }
        // A private note opens only after the device lock (PIN, pattern, fingerprint) was confirmed.
        val keyguard = getSystemService(KeyguardManager::class.java)
        val cachedNote = noteId?.let { id -> NotesCache.readAll(this, account).firstOrNull { it.id == id } }
        val needsLock = cachedNote != null && AppSettingsStore.load(this).lockPrivateNotes &&
            PrivateNotes.isPrivate(cachedNote, account, PrivateNotes.load(this)) && keyguard.isDeviceSecure
        var unlocked by mutableStateOf(!needsLock)
        val unlock = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) unlocked = true else finish()
        }
        if (needsLock) {
            @Suppress("DEPRECATION")
            unlock.launch(keyguard.createConfirmDeviceCredentialIntent(getString(R.string.app_name), getString(R.string.note_private_hint)))
        }
        setContent { KeepTheme(themeMode) { AppLock.Gate(this) { if (unlocked) Loader(account, noteId, newList, pinWidgetId) } } }
    }

    /**
     * An existing note opens at once from the cache. Keep is asked in the background (unless a sync
     * just ran); if the note changed there, a hint offers to reload it, so nothing typed is lost.
     */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun Loader(account: String, noteId: String?, newList: Boolean, pinWidgetId: Int?) {
        val cached = remember { noteId?.let { id -> NotesCache.readAll(this@EditNoteActivity, account).firstOrNull { it.id == id } } }
        var loaded by remember { mutableStateOf(noteId == null || cached != null) }
        var existing by remember { mutableStateOf(cached) }
        var gone by remember { mutableStateOf(false) }
        var offline by remember { mutableStateOf(false) }
        var remote by remember { mutableStateOf<KeepNote?>(null) }
        var screenKey by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            if (noteId == null) return@LaunchedEffect
            // The sample notes of the demo mode are made anew every time they are read, so they would always look "changed in Keep".
            if (DemoNotes.enabled(this@EditNoteActivity)) {
                loaded = true
                return@LaunchedEffect
            }
            val recentlySynced = cached != null && System.currentTimeMillis() - SyncPolicy.lastSuccess(this@EditNoteActivity) < 90_000
            if (!recentlySynced) {
                try {
                    KeepRepository.sync(this@EditNoteActivity, account)
                } catch (e: Exception) {
                    offline = true
                }
            }
            val fresh = NotesCache.readAll(this@EditNoteActivity, account).firstOrNull { it.id == noteId }
            when {
                fresh == null -> gone = true
                cached == null -> existing = fresh
                // Our own save changes only the bookkeeping (timestamps, sort value, ids) once Keep confirms it, not what the user sees.
                fresh.copy(updated = 0, created = 0, sort = 0, serverId = "") != cached.copy(updated = 0, created = 0, sort = 0, serverId = "") -> remote = fresh
            }
            loaded = true
        }

        when {
            !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            gone -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.note_gone), style = MaterialTheme.typography.titleMedium)
                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(android.R.string.ok))
                }
            }
            else -> key(screenKey) {
                EditScreen(
                    account, existing, existing?.isList ?: newList, offline, pinWidgetId, remote,
                ) {
                    existing = remote
                    remote = null
                    screenKey++
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun EditScreen(
        account: String, existing: KeepNote?, startAsList: Boolean, offline: Boolean, pinWidgetId: Int?,
        remote: KeepNote?, onReload: () -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        // With the edit lock on, a note opens for reading only until the lock is passed.
        var canEdit by remember { mutableStateOf(AppLock.editAllowed(this@EditNoteActivity)) }
        val unlock = AppLock.rememberUnlock(this@EditNoteActivity)
        fun toastUnlockFirst() = Toast.makeText(this@EditNoteActivity, R.string.edit_first_hint, Toast.LENGTH_SHORT).show()
        fun askEdit() {
            unlock(R.string.lock_prompt_edit) { ok ->
                if (ok) canEdit = true else if (existing == null) finish()
            }
        }
        // A new note is all editing, so the lock is asked for at once.
        LaunchedEffect(Unit) { if (!canEdit && existing == null) askEdit() }
        // A new note can become a checklist while typing (see the offer under the text field).
        var isList by remember { mutableStateOf(startAsList) }
        // Text shared from another app arrives as a prefill for a new note.
        var title by remember { mutableStateOf(existing?.title ?: intent.getStringExtra(EXTRA_PREFILL_TITLE).orEmpty()) }
        var text by remember { mutableStateOf(existing?.text ?: intent.getStringExtra(EXTRA_PREFILL_TEXT).orEmpty()) }
        var textSel by remember { mutableStateOf(androidx.compose.ui.text.TextRange(text.length)) }
        // The keyboard's composing region; dropping it on every keystroke makes the word suggestions flicker.
        var textComp by remember { mutableStateOf<androidx.compose.ui.text.TextRange?>(null) }
        var color by remember { mutableStateOf(existing?.color ?: NoteColors.DEFAULT) }
        // The note's own look fills the screen behind the editor, as long as it has a color or a picture.
        val originalBackground = remember { existing?.let { RichNotes.of(this@EditNoteActivity, account, it.id)?.background }.orEmpty() }
        var background by remember { mutableStateOf(originalBackground) }
        val showColors = remember { AppSettingsStore.load(this@EditNoteActivity).showNoteColors }
        val picture by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, background) {
            value = if (background.isEmpty()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { NoteBackgrounds.load(this@EditNoteActivity, background) }
        }
        val tint = if (showColors) NoteColors.argb(color) else null
        val shownPicture = if (showColors) picture else null
        val tinted = tint != null || shownPicture != null
        DisposableEffect(tint, shownPicture) {
            ScreenTint.color = tint
            ScreenTint.image = shownPicture
            onDispose { ScreenTint.color = null; ScreenTint.image = null }
        }
        var labels by remember { mutableStateOf(existing?.labels.orEmpty()) }
        var newLabel by remember { mutableStateOf("") }
        var manageLabels by remember { mutableStateOf(false) }
        var labelsVersion by remember { mutableStateOf(0) }
        // Label changes show at once; Keep follows in the background.
        var addedLabels by remember { mutableStateOf(setOf<String>()) }
        var removedLabels by remember { mutableStateOf(setOf<String>()) }
        var labelToDelete by remember { mutableStateOf<String?>(null) }
        // A numbered checklist keeps "1. ", "2. " in the text of its items (so Keep shows them too); in the editor
        // the number is a label in front of the field and the field holds only the text.
        val startNumbered = existing != null && existing.isList && ListNumbering.isNumbered(existing.items)
        var numbered by remember { mutableStateOf(startNumbered) }
        var numberOfferDismissed by remember { mutableStateOf(false) }
        val items = remember {
            mutableStateListOf<KeepItem>().apply {
                val start = existing?.items ?: if (isList) listOf(KeepItem(UUID.randomUUID().toString(), "", false)) else emptyList()
                addAll(if (startNumbered) ListNumbering.strip(start) else start)
            }
        }
        /** The items as they are saved: with their numbers in the text when the list is numbered. */
        fun outItems(): List<KeepItem> = if (numbered) ListNumbering.apply(items.toList()) else items.toList()
        val allAccounts = remember { CredentialStore.accounts(this@EditNoteActivity) }
        var targetAccount by remember { mutableStateOf(account) }
        val wasHidden = existing != null && PrivateNotes.isPrivate(existing, account, PrivateNotes.load(this@EditNoteActivity))
        // Changed with "Hide content"; like every other change it only counts once the note is saved.
        var hiddenInWidgets by remember { mutableStateOf(wasHidden) }
        var saving by remember { mutableStateOf(false) }
        var menuOpen by remember { mutableStateOf(false) }
        var menuPage by remember { mutableStateOf(0) }
        LaunchedEffect(menuOpen) { if (!menuOpen) menuPage = 0 }
        val isLocal = CredentialStore.isLocal(account)
        // A change that Keep does not have yet (and why, if sending it failed).
        val unsent = remember {
            existing?.let { note ->
                PendingEdits.all(this@EditNoteActivity)
                    .firstOrNull { it.account == account && it.function == "update_note" && it.args.firstOrNull() == note.id }
            }
        }
        val notificationsOn = rememberNotificationsOn(this@EditNoteActivity)
        var confirmDelete by remember { mutableStateOf(false) }
        var viewerImage by remember { mutableStateOf<String?>(null) }
        val images = remember { existing?.images.orEmpty() }
        // Images marked for removal; they are only deleted in Keep when the note is saved.
        var removedImages by remember { mutableStateOf(setOf<String>()) }
        // Keep's own pin: saved to Google Keep together with the note, like the color.
        var pinned by remember { mutableStateOf(existing?.pinned ?: false) }
        // Keep has no "bottom": this only changes where the note sits in the widget, and applies at once.
        var pinBottom by remember {
            mutableStateOf(
                pinWidgetId != null && existing != null &&
                    existing.id in SettingsStore.loadWidget(this@EditNoteActivity, pinWidgetId).pinBottom
            )
        }
        // Reminders belong to the phone only and are saved at once, not with the note.
        var reminders by remember { mutableStateOf(existing?.let { Reminders.list(this@EditNoteActivity, account, it.id) }.orEmpty()) }
        val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        // Saving in the background is the first thing that needs notifications (they tell about a failed
        // send), so the right is asked for once, when a note is saved; the editor closes after the answer.
        val askAfterSave = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }
        var remindersOpen by remember { mutableStateOf(false) }
        // Reminders of a new note wait here until the note exists and has its id.
        var draftReminders by remember { mutableStateOf(listOf<Reminder>()) }
        // Set while an existing note is turned into the other kind: nothing changes until "Save", which
        // then creates the new note and moves this one to the trash.
        var convertFrom by remember { mutableStateOf<KeepNote?>(null) }
        val shownReminders = if (existing != null) reminders else draftReminders.sortedBy { it.at }
        val convertPrefs = remember { getSharedPreferences("convert_hints", MODE_PRIVATE) }
        val convertKey = existing?.let { account + "|" + it.id }
        // Asked once per existing note; "No" is remembered.
        var convertDismissed by remember { mutableStateOf(convertKey != null && convertPrefs.getBoolean(convertKey, false)) }
        var error by remember { mutableStateOf("") }
        val failedTemplate = stringResource(R.string.save_failed, "%s")

        /** Runs an action that ends the editing of this note (archive, trash) and closes the editor. */
        fun leaveNote(action: suspend () -> Unit) {
            if (!canEdit) return askEdit()
            saving = true
            error = ""
            scope.launch {
                try {
                    action()
                    finish()
                } catch (e: Exception) {
                    saving = false
                    error = failedTemplate.format(e.message ?: e.javaClass.simpleName)
                }
            }
        }

        fun save(fromHome: Boolean = false) {
            if (saving || !canEdit) return
            saving = true
            error = ""
            val target = if (existing != null) account else targetAccount
            val converting = convertFrom != null
            val function = if (existing != null && !converting) "update_note" else "create_note"
            val args = if (existing != null && !converting) {
                KeepRepository.updateNoteArgs(
                    existing.id, title, text, outItems(), color, pinned, removedImages.toList(), withHiddenLabel(labels, hiddenInWidgets),
                    // Only a changed picture is sent; "default" takes it away.
                    if (background != originalBackground) background.ifEmpty { "default" } else "",
                )
            } else {
                KeepRepository.createNoteArgs(title, text, isList, outItems(), color, pinned, withHiddenLabel(labels, hiddenInWidgets), background)
            }
            // The mark in Keep replaces the older one that only this phone knew.
            if (existing != null) PrivateNotes.set(this@EditNoteActivity, account, existing.id, false)
            if (!CredentialStore.isLocal(target) || converting) {
                // The change is stored and sent in the background: the editor closes at once, and a failure
                // shows up as a notification instead of losing the change.
                KeepRepository.queueEdit(
                    this@EditNoteActivity,
                    if (converting) {
                        val old = convertFrom!!
                        val moved = Reminders.list(this@EditNoteActivity, account, old.id)
                        moved.forEach { Reminders.remove(this@EditNoteActivity, account, old.id, it.id) }
                        PendingEdit(target, function, args, reminders = moved, trashAfter = old.id)
                    } else {
                        PendingEdit(target, function, args, reminders = if (existing == null) draftReminders else emptyList())
                    },
                )
                Toast.makeText(applicationContext, R.string.note_saved, Toast.LENGTH_SHORT).show()
                val prefs = getSharedPreferences("reminder_hints", MODE_PRIVATE)
                if (!fromHome && !CredentialStore.isLocal(target) && android.os.Build.VERSION.SDK_INT >= 33 && !notificationsOn && !prefs.getBoolean("save_asked", false)) {
                    prefs.edit().putBoolean("save_asked", true).apply()
                    askAfterSave.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    finish()
                }
                return
            }
            scope.launch {
                try {
                    val before = Reminders.noteIds(this@EditNoteActivity, target)
                    KeepRepository.run(this@EditNoteActivity, target, function, args)
                    if (existing == null) Reminders.attachToNew(this@EditNoteActivity, PendingEdit(target, function, args, reminders = draftReminders), before)
                    Toast.makeText(applicationContext, R.string.note_saved, Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    saving = false
                    error = failedTemplate.format(e.message ?: e.javaClass.simpleName)
                }
            }
        }

        /** Content of the editor as the other kind of note: checklist items become lines, lines become items. */
        fun convertedContent(toList: Boolean): Pair<String, List<KeepItem>> =
            if (toList) {
                "" to ChecklistText.items(text).ifEmpty { listOf(KeepItem(UUID.randomUUID().toString(), "", false)) }
            } else if (numbered) {
                // A numbered list becomes lines that start with their numbers.
                outItems().filter { it.text.isNotBlank() }.joinToString("\n") { it.text } to emptyList()
            } else {
                // The chosen prefix keeps a trace of the list: "- ", "* ", "[ ] / [x] " or numbers.
                val prefix = AppSettingsStore.load(this@EditNoteActivity).convertPrefix
                items.filter { it.text.isNotBlank() }.mapIndexed { i, item ->
                    when (prefix) {
                        PREFIX_DASH -> "- "
                        PREFIX_STAR -> "* "
                        PREFIX_BOX -> if (item.checked) "[x] " else "[ ] "
                        PREFIX_NUMBER -> "${i + 1}. "
                        else -> ""
                    } + item.text
                }.joinToString("\n") to emptyList()
            }

        /** A new note changes its kind right here; an existing one is saved as a new note and the old one goes to the trash. */
        /** After text became items: lines that all start with numbers make a numbered list. */
        fun adoptNumbering() {
            numbered = ListNumbering.isNumbered(items.toList())
            if (numbered) {
                val stripped = ListNumbering.strip(items.toList())
                items.clear()
                items.addAll(stripped)
            }
        }

        fun requestConvert() {
            if (!canEdit) {
                askEdit()
            } else if (existing == null) {
                val (newText, newItems) = convertedContent(!isList)
                items.clear()
                items.addAll(newItems)
                text = newText
                isList = !isList
                if (isList) adoptNumbering() else numbered = false
            } else if (existing.images.isNotEmpty()) {
                Toast.makeText(this@EditNoteActivity, R.string.convert_images_blocked, Toast.LENGTH_LONG).show()
            } else {
                val (newText, newItems) = convertedContent(!isList)
                items.clear()
                items.addAll(newItems)
                text = newText
                isList = !isList
                if (isList) adoptNumbering() else numbered = false
                convertFrom = if (isList != existing.isList) existing else null
            }
        }

        fun hasChanges() = if (existing != null) {
            title != existing.title || text != existing.text || outItems() != existing.items || color != existing.color ||
                pinned != existing.pinned || labels != existing.labels || removedImages.isNotEmpty() || convertFrom != null || hiddenInWidgets != wasHidden
        } else {
            title.isNotBlank() || text.isNotBlank() || items.any { it.text.isNotBlank() }
        }

        /** Closes the editor without saving; a note that was changed is reported as discarded. */
        fun discard() {
            if (hasChanges()) Toast.makeText(this@EditNoteActivity, R.string.note_discarded, Toast.LENGTH_SHORT).show()
            finish()
        }
        androidx.activity.compose.BackHandler(enabled = !saving) { discard() }

        // Home keeps what was typed: a changed note is saved, not thrown away. Back still discards.
        val saveOnHome by rememberUpdatedState { if (hasChanges() && !saving) save(fromHome = true) }
        DisposableEffect(Unit) {
            onHome = { saveOnHome() }
            onDispose { onHome = null }
        }

        OnNoteTheme(tinted) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (unsent != null) {
                Text(
                    if (unsent.error.isBlank()) stringResource(R.string.note_unsent)
                    else stringResource(R.string.note_unsent_failed, unsent.error),
                    color = if (unsent.error.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Title with its actions on the right: pin, show at the widget's bottom, open in Keep.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        when {
                            existing != null -> R.string.edit_note
                            isList -> R.string.new_list
                            else -> R.string.new_note
                        }
                    ),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.clickable(enabled = !canEdit) { toastUnlockFirst() }) {
                FilledTonalIconToggleButton(checked = pinned, onCheckedChange = { pinned = it }, enabled = canEdit, shapes = IconButtonDefaults.toggleableShapes()) {
                    Icon(
                        painterResource(if (pinned) R.drawable.ic_push_pin else R.drawable.ic_push_pin_outline),
                        contentDescription = stringResource(R.string.pin_note),
                    )
                }
                }
                if (pinWidgetId != null && existing != null) {
                    FilledTonalIconToggleButton(
                        checked = pinBottom,
                        onCheckedChange = { checked ->
                            pinBottom = checked
                            val widget = SettingsStore.loadWidget(this@EditNoteActivity, pinWidgetId)
                            SettingsStore.saveWidget(
                                this@EditNoteActivity, pinWidgetId,
                                widget.copy(pinBottom = if (checked) widget.pinBottom + existing.id else widget.pinBottom - existing.id),
                            )
                            scope.launch { WidgetRefresh.all(this@EditNoteActivity) }
                        },
                        shapes = IconButtonDefaults.toggleableShapes(),
                    ) {
                        Icon(painterResource(R.drawable.ic_pin_bottom), contentDescription = stringResource(R.string.pin_to_bottom))
                    }
                }
                // Less common actions live in an overflow menu; grayed out until the note is open for editing.
                run {
                    Box {
                        IconButton(onClick = { if (canEdit) menuOpen = true else toastUnlockFirst() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_actions),
                                tint = if (canEdit) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f),
                            )
                        }
                        GroupedMenu(
                            expanded = menuOpen && canEdit,
                            onDismissRequest = { menuOpen = false },
                            groups = if (menuPage == 1) listOf(
                                // The checklist tools have a page of their own, so the main menu stays short.
                                listOf(MenuEntry(stringResource(R.string.list_tools), R.drawable.ic_arrow_left) { menuPage = 0 }),
                                listOf(
                                    MenuEntry(
                                        stringResource(if (numbered) R.string.list_unnumber else R.string.list_number),
                                        leading = { Text("1.") },
                                    ) {
                                        menuOpen = false
                                        // Numbers are shown from the position; an old "1. " typed by hand is not counted twice.
                                        val stripped = ListNumbering.strip(items.toList())
                                        items.clear()
                                        items.addAll(stripped)
                                        numbered = !numbered
                                    },
                                    MenuEntry(stringResource(R.string.list_check_all), R.drawable.ic_done) {
                                        menuOpen = false
                                        for (i in items.indices) items[i] = items[i].copy(checked = true)
                                    },
                                    MenuEntry(stringResource(R.string.list_uncheck_all), R.drawable.ic_close) {
                                        menuOpen = false
                                        for (i in items.indices) items[i] = items[i].copy(checked = false)
                                    },
                                    MenuEntry(stringResource(R.string.list_sort_alpha), R.drawable.ic_sort) {
                                        menuOpen = false
                                        // Sub-items lose their parent when the order changes, so they become ordinary items.
                                        val sorted = items.sortedBy { it.text.lowercase() }.map { it.copy(indent = false) }
                                        items.clear()
                                        items.addAll(sorted)
                                    },
                                    MenuEntry(stringResource(R.string.list_delete_checked), R.drawable.ic_delete) {
                                        menuOpen = false
                                        items.removeAll { it.checked }
                                        if (items.isEmpty()) items.add(KeepItem(UUID.randomUUID().toString(), "", false))
                                    },
                                ),
                            ) else listOf(
                                buildList {
                                    add(
                                        MenuEntry(
                                            stringResource(if (isList) R.string.convert_to_note else R.string.convert_to_list),
                                            R.drawable.ic_checklist,
                                        ) { menuOpen = false; requestConvert() },
                                    )
                                    if (isList) add(
                                        MenuEntry(stringResource(R.string.list_tools), R.drawable.ic_done, trailing = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }) { menuPage = 1 },
                                    )
                                },
                                if (existing == null) emptyList() else buildList {
                                    if (existing.serverId.isNotEmpty()) add(
                                        MenuEntry(stringResource(R.string.open_in_keep), R.drawable.ic_open_new) {
                                            menuOpen = false
                                            openInKeep(account, existing)
                                        },
                                    )
                                    // Keep's API has no way to upload pictures, so adding one is left to the Keep app.
                                    if (!isLocal && images.isEmpty()) add(
                                        MenuEntry(stringResource(R.string.image_add_keep), R.drawable.ic_add) {
                                            menuOpen = false
                                            startActivity(keepIntent(this@EditNoteActivity))
                                        },
                                    )
                                    add(
                                        MenuEntry(stringResource(R.string.share_note), R.drawable.ic_share) {
                                            menuOpen = false
                                            shareNote(title, text, isList, outItems())
                                        },
                                    )
                                    add(
                                        MenuEntry(stringResource(if (hiddenInWidgets) R.string.show_content else R.string.hide_content), R.drawable.ic_lock) {
                                            menuOpen = false
                                            if (canEdit) {
                                                hiddenInWidgets = !hiddenInWidgets
                                                scope.launch { WidgetRefresh.all(this@EditNoteActivity) }
                                            } else {
                                                askEdit()
                                            }
                                        },
                                    )
                                },
                                if (existing == null) emptyList() else buildList {
                                    if (!isLocal) add(
                                        MenuEntry(stringResource(R.string.archive_note), R.drawable.ic_archive) {
                                            menuOpen = false
                                            leaveNote {
                                                KeepRepository.archiveNote(this@EditNoteActivity, account, existing.id)
                                                UndoStore.set(account, existing.id, UndoStore.Kind.ARCHIVE)
                                            }
                                        },
                                    )
                                    add(
                                        MenuEntry(stringResource(if (isLocal) R.string.delete_note else R.string.move_to_trash), R.drawable.ic_delete) {
                                            menuOpen = false
                                            // Local notes have no trash, so deleting them asks first.
                                            if (isLocal) {
                                                confirmDelete = true
                                            } else {
                                                leaveNote {
                                                    KeepRepository.trashNote(this@EditNoteActivity, account, existing.id)
                                                    UndoStore.set(account, existing.id, UndoStore.Kind.TRASH)
                                                }
                                            }
                                        },
                                    )
                                },
                            ),
                        )
                    }
                }
            }

            if (confirmDelete && existing != null) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    text = { Text(stringResource(R.string.delete_local_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDelete = false
                            leaveNote { KeepRepository.trashNote(this@EditNoteActivity, account, existing.id) }
                        }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.delete_note)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
                    },
                )
            }

            if (existing == null && allAccounts.size > 1) {
                Text(stringResource(R.string.save_to_account), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allAccounts.forEach { candidate ->
                        FilterChip(
                            selected = targetAccount == candidate,
                            onClick = { targetAccount = candidate },
                            label = { Text(CredentialStore.displayName(this@EditNoteActivity, candidate)) },
                        )
                    }
                }
            }

            // Labels: tap a known label to add or remove it; a new name creates the label on save.
            val knownLabels = remember(targetAccount, labelsVersion) {
                (NotesCache.labels(this@EditNoteActivity, targetAccount) + addedLabels).filter { it !in removedLabels }
                    .distinct().sortedBy { it.lowercase() }
            }
            val allLabels = (knownLabels + labels).distinct().sortedBy { it.lowercase() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (allLabels.isEmpty()) R.string.no_labels else R.string.labels_title), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { manageLabels = true }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.manage_labels)) }
            }
            if (allLabels.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allLabels.forEach { name ->
                    FilterChip(
                        selected = name in labels,
                        enabled = canEdit,
                        onClick = { labels = if (name in labels) labels - name else labels + name },
                        label = { Text(name) },
                    )
                }
            }
            if (allLabels.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = { Text(stringResource(R.string.new_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = newLabel.isNotBlank(),
                    onClick = {
                        val name = newLabel.trim()
                        if (name !in labels) labels = labels + name
                        newLabel = ""
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.add_label)) }
            }

            if (manageLabels) {
                var creating by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { manageLabels = false },
                    title = { Text(stringResource(R.string.manage_labels)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = creating,
                                    onValueChange = { creating = it },
                                    label = { Text(stringResource(R.string.new_label_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    enabled = creating.isNotBlank(),
                                    onClick = {
                                        val name = creating.trim()
                                        creating = ""
                                        addedLabels = addedLabels + name
                                        removedLabels = removedLabels - name
                                        labelsVersion++
                                        KeepRepository.createLabel(this@EditNoteActivity, targetAccount, name)
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) { Text(stringResource(R.string.add_label)) }
                            }
                            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                                knownLabels.forEach { name ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(name, Modifier.weight(1f))
                                        TextButton(onClick = { labelToDelete = name }, shapes = ButtonDefaults.shapes()) {
                                            Text(stringResource(R.string.delete_label))
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { manageLabels = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.done)) }
                    },
                )
            }

            labelToDelete?.let { name ->
                AlertDialog(
                    onDismissRequest = { labelToDelete = null },
                    text = { Text(stringResource(R.string.delete_label_confirm, name)) },
                    confirmButton = {
                        TextButton(onClick = {
                            labelToDelete = null
                            labels = labels - name
                            removedLabels = removedLabels + name
                            addedLabels = addedLabels - name
                            labelsVersion++
                            KeepRepository.deleteLabel(this@EditNoteActivity, targetAccount, name)
                        }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.delete_label)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { labelToDelete = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
                    },
                )
            }

            if (remote != null) {
                // The note changed in Keep while it was open here.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.note_changed_remote),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onReload, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reload)) }
                }
            }

            if (offline) {
                Text(
                    stringResource(R.string.offline_note),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Tap an image to view it full screen; it can also be opened in the gallery or marked for
            // removal. Nothing is deleted before "Save", and "Cancel" keeps every image.
            images.forEach { imageId ->
                if (imageId in removedImages) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.image_removal_pending),
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { removedImages = removedImages - imageId }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.image_undo))
                        }
                    }
                } else {
                    val bitmap = remember(imageId) { NoteImages.thumbnail(this@EditNoteActivity, account, imageId) }
                    if (bitmap != null) {
                        Column {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.note_image),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clickable { viewerImage = imageId },
                            )
                            // Centered under the image, sharing the width equally.
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                TextButton(onClick = { openInGallery(account, imageId) }, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(R.string.image_open_gallery))
                                }
                                if (existing != null) {
                                    TextButton(onClick = { removedImages = removedImages + imageId }, shapes = ButtonDefaults.shapes()) {
                                        Text(stringResource(R.string.image_remove))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            viewerImage?.let { imageId ->
                val full = remember(imageId) { NoteImages.full(this@EditNoteActivity, account, imageId) }
                if (full != null) ImageViewerDialog(full) { viewerImage = null }
            }

            if (convertFrom != null) {
                Text(
                    stringResource(R.string.convert_existing_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = title, onValueChange = { title = it }, readOnly = !canEdit,
                label = { Text(stringResource(R.string.title_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (isList) {
                // How the rows work; the cross hides this explanation for good.
                val hintPrefs = remember { getSharedPreferences("hints", MODE_PRIVATE) }
                var hintHidden by remember { mutableStateOf(hintPrefs.getBoolean("list_hint_hidden", false)) }
                if (canEdit && !hintHidden) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.list_swipe_hint),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = {
                        hintPrefs.edit().putBoolean("list_hint_hidden", true).apply()
                        hintHidden = true
                    }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.notif_hint_dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // The handle of a row moves it; the bounds tell which row the finger is over.
                var dragging by remember { mutableStateOf<String?>(null) }
                val rowBounds = remember { mutableStateMapOf<String, Rect>() }
                // "1. " typed at the start of the first item offers to number the whole list.
                val firstFilled = items.firstOrNull { it.text.isNotBlank() }?.text.orEmpty()
                if (canEdit && !numbered && !numberOfferDismissed && Regex("""^\s*1[.)]\s""").containsMatchIn(firstFilled)) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
                            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
                    ) {
                        Text(stringResource(R.string.number_offer), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { numberOfferDismissed = true }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.convert_to_list_no)) }
                            TextButton(onClick = {
                                val stripped = ListNumbering.strip(items.toList())
                                items.clear()
                                items.addAll(stripped)
                                numbered = true
                            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.list_number)) }
                        }
                    }
                }
                items.forEachIndexed { index, item ->
                    key(item.id) {
                        ChecklistRow(
                            item, canEdit, dragging == item.id,
                            number = if (numbered) items.take(index).count { it.text.isNotBlank() } + 1 else null,
                            onText = { text -> items.indexOfFirst { it.id == item.id }.let { i -> if (i >= 0) items[i] = items[i].copy(text = text) } },
                            onToggle = { items.indexOfFirst { it.id == item.id }.let { i -> if (i >= 0) items[i] = items[i].copy(checked = !items[i].checked) } },
                            onRemove = {
                                items.removeAll { it.id == item.id }
                                if (items.isEmpty()) items.add(KeepItem(UUID.randomUUID().toString(), "", false))
                                if (items.isNotEmpty() && items[0].indent) items[0] = items[0].copy(indent = false)
                            },
                            onBounds = { rowBounds[item.id] = it },
                            onDragStart = { dragging = item.id },
                            onDragTo = { y ->
                                val from = items.indexOfFirst { it.id == item.id }
                                val to = items.indexOfFirst { rowBounds[it.id]?.let { r -> y >= r.top && y < r.bottom } == true }
                                if (from >= 0 && to >= 0 && to != from) {
                                    items.add(to, items.removeAt(from))
                                    if (items.isNotEmpty() && items[0].indent) items[0] = items[0].copy(indent = false)
                                }
                            },
                            onDragEnd = { dragging = null },
                        )
                    }
                }
                if (canEdit) OutlinedButton(onClick = { items.add(KeepItem(UUID.randomUUID().toString(), "", false)) }, shapes = ButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.add_item))
                }
            } else {
                // Lines that start with "-" (or "*", "[ ]") are a list: offer to make it one, once.
                if (!convertDismissed && ChecklistText.looksLikeList(text) && existing?.images.isNullOrEmpty()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
                            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.convert_to_list_hint),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                convertDismissed = true
                                convertKey?.let { convertPrefs.edit().putBoolean(it, true).apply() }
                            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.convert_to_list_no)) }
                            TextButton(onClick = { requestConvert() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.convert_to_list)) }
                        }
                    }
                }
                OutlinedTextField(
                    // Enter after "1. ..." (or "- ...") starts the next line with "2. " (or "- "); Enter on an empty one ends the list.
                    value = androidx.compose.ui.text.input.TextFieldValue(text, textSel, textComp),
                    onValueChange = { typed ->
                        val result = ListContinue.apply(text, typed)
                        text = result.text
                        textSel = result.selection
                        textComp = if (result === typed) typed.composition else null
                    },
                    readOnly = !canEdit,
                    label = { Text(stringResource(R.string.note_hint)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
            }

            // Web addresses, e-mail addresses and phone numbers in the note can be opened with one tap.
            val links = NoteLinks.find(listOf(title, text) + items.map { it.text })
            if (links.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEach { link ->
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            try {
                                startActivity(link.intent)
                            } catch (e: ActivityNotFoundException) {
                                // No app for this kind of link.
                            }
                        },
                        label = { Text(link.label, maxLines = 1) },
                    )
                }
            }

            Text(stringResource(R.string.color))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                NoteColors.names.forEach { name ->
                    val argb = NoteColors.argb(name)
                    // The circle keeps its size; the touch area around it is 48 dp.
                    RoundTouchTarget(onClick = { color = name }, enabled = canEdit) {
                        Box(
                            Modifier.size(if (name == color) 44.dp else 36.dp)
                                .clip(CircleShape)
                                .background(if (argb != null) Color(argb) else Color.Transparent)
                                .border(
                                    if (name == color) 3.dp else 1.dp,
                                    if (name == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            // The default (uncolored) swatch is transparent, so mark it.
                            if (argb == null) Icon(painterResource(R.drawable.ic_color_none), contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Keep's pictures behind a note: they exist in Keep only, so local notes have none.
            if (!isLocal) {
                Text(stringResource(R.string.note_background))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("") .plus(NoteBackgrounds.NAMES).forEach { name ->
                        val thumb by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, name) {
                            value = if (name.isEmpty()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { NoteBackgrounds.load(this@EditNoteActivity, name) }
                        }
                        RoundTouchTarget(onClick = { background = name }, enabled = canEdit) {
                            Box(
                                Modifier.size(if (name == background) 44.dp else 36.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .border(
                                        if (name == background) 3.dp else 1.dp,
                                        if (name == background) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        MaterialTheme.shapes.small,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (name.isEmpty()) Icon(painterResource(R.drawable.ic_color_none), contentDescription = stringResource(R.string.note_background_none), modifier = Modifier.size(20.dp))
                                else thumb?.let { Image(it.asImageBitmap(), contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            }
                        }
                    }
                }
            }

            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)

            if (remindersOpen) {
                RemindersDialog(
                    activity = this@EditNoteActivity, account = account, noteId = existing?.id.orEmpty(), reminders = shownReminders,
                    onChange = { if (existing != null) reminders = Reminders.list(this@EditNoteActivity, account, existing.id) },
                    onPut = if (existing == null) { r -> draftReminders = draftReminders.filter { it.id != r.id } + r } else null,
                    onRemove = if (existing == null) { id -> draftReminders = draftReminders.filter { it.id != id } } else null,
                    onDismiss = { remindersOpen = false },
                    askNotifications = {
                        if (android.os.Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(this@EditNoteActivity)) {
                            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }

            shownReminders.firstOrNull()?.let {
                Text(
                    stringResource(R.string.reminder_at, Reminders.label(this@EditNoteActivity, it.at)) +
                        if (shownReminders.size > 1) "  (+${shownReminders.size - 1})" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            // A button group: the pressed button grows a little and its neighbors make room. The three always fit,
            // so nothing ever moves into the overflow menu.
            val barHeight = ButtonDefaults.MediumContainerHeight
            val barPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                customItem(
                    buttonGroupContent = {
                        val pressed = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = { discard() }, enabled = !saving,
                            shapes = ButtonDefaults.shapesFor(barHeight), contentPadding = barPadding, interactionSource = pressed,
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = barHeight).animateWidth(pressed),
                        ) { Text(stringResource(R.string.cancel), style = ButtonDefaults.textStyleFor(barHeight), textAlign = TextAlign.Center) }
                    },
                    menuContent = {},
                )
                customItem(
                    buttonGroupContent = {
                        val pressed = remember { MutableInteractionSource() }
                        // Set: the accent color with a matching icon. None set: a quiet button.
                        FilledTonalIconToggleButton(
                            checked = shownReminders.isNotEmpty(),
                            onCheckedChange = { if (canEdit) remindersOpen = true else askEdit() },
                            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shapes = IconButtonDefaults.toggleableShapes(),
                            interactionSource = pressed,
                            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()).animateWidth(pressed),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_alarm), contentDescription = stringResource(R.string.reminders_title),
                                tint = if (notificationsOn) LocalContentColor.current else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                            )
                        }
                    },
                    menuContent = {},
                )
                customItem(
                    buttonGroupContent = {
                        val pressed = remember { MutableInteractionSource() }
                        Button(
                            onClick = { if (canEdit) save() else askEdit() },
                            shapes = ButtonDefaults.shapesFor(barHeight), contentPadding = barPadding, interactionSource = pressed,
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = barHeight).animateWidth(pressed),
                        ) {
                            Text(
                                stringResource(if (canEdit) R.string.save else if (isList) R.string.edit_list else R.string.edit_note),
                                style = ButtonDefaults.textStyleFor(barHeight), textAlign = TextAlign.Center,
                            )
                        }
                    },
                    menuContent = {},
                )
            }
        }
        }
    }

    /**
     * One item of a checklist while editing: just the text, with a green check at the bottom right when it is
     * done. Swiping right ticks (or unticks) it, swiping left removes it, and the row stops where the symbol is shown.
     */
    @Composable
    private fun ChecklistRow(
        item: KeepItem, editable: Boolean, dragged: Boolean, number: Int?,
        onText: (String) -> Unit, onToggle: () -> Unit, onRemove: () -> Unit,
        onBounds: (Rect) -> Unit, onDragStart: () -> Unit, onDragTo: (Float) -> Unit, onDragEnd: () -> Unit,
    ) {
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
                    .background(MaterialTheme.colorScheme.background)
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
                    value = item.text, readOnly = !editable, onValueChange = onText,
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
                    modifier = Modifier.fillMaxWidth(),
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

    /**
     * Opens this note in the Google Keep app, or on keep.google.com if the app is not installed or
     * does not handle the link. Keep has no documented link format for the app, so the web link of
     * the note is tried with the app first.
     */
    private fun openInKeep(account: String, note: KeepNote) {
        val kind = if (note.isList) "LIST" else "NOTE"
        // With several signed-in accounts the web link has to say which one it means.
        val authUser = if (CredentialStore.accounts(this).size > 1) "?authuser=${Uri.encode(account)}" else ""
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://keep.google.com/$authUser#$kind/${note.serverId}"))
        val app = Intent(web).setPackage("com.google.android.keep")
        try {
            startActivity(if (app.resolveActivity(packageManager) != null) app else web)
        } catch (e: ActivityNotFoundException) {
            // No app can open the link; nothing sensible to do.
        }
    }

    /** Hands the note, as it is shown in the editor, to another app as plain text. */
    private fun shareNote(title: String, text: String, isList: Boolean, items: List<KeepItem>) {
        val body = if (isList) {
            items.filter { it.text.isNotBlank() }.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text }
        } else {
            text
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, listOf(title, body).filter { it.isNotBlank() }.joinToString("\n\n"))
        startActivity(Intent.createChooser(send, null))
    }

    /** Opens the original image in the gallery (or any other app that shows images). */
    private fun openInGallery(account: String, imageId: String) {
        val file = NoteImages.exportForSharing(this, account, imageId) ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, if (file.extension == "png") "image/png" else "image/jpeg")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No app can show images; nothing sensible to do.
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_ACCOUNT = "account"
        const val EXTRA_PREFILL_TITLE = "prefill_title"
        const val EXTRA_PREFILL_TEXT = "prefill_text"
        const val EXTRA_NEW_LIST = "new_list"
    }
}

/** Recognises text typed as a list ("- milk", "* eggs", "[ ] bread") and turns it into checklist items. */
internal object ChecklistText {
    private val marker = Regex("""^\s*(?:[-–—*•](?:\s+|(?=\p{L}))(?:\[([ xX]?)\]\s*)?|\[([ xX]?)\]\s*|\d{1,3}[.)]\s+)(.*)$""")

    /** At least one line that starts with a list marker ("- ", "* ", "• ", "[ ] " or "1. ") followed by text. */
    fun looksLikeList(text: String): Boolean =
        text.lineSequence().any { line -> marker.matchEntire(line)?.groupValues?.get(3)?.isNotBlank() == true }

    private val numberedLine = Regex("""^\s*\d{1,3}[.)]\s+\S""")

    fun items(text: String): List<KeepItem> = text.lineSequence().filter { it.isNotBlank() }.map { line ->
        // A numbered line keeps its number in the text: the list is then a numbered list.
        if (numberedLine.containsMatchIn(line)) return@map KeepItem(UUID.randomUUID().toString(), line.trim(), false)
        val m = marker.find(line)
        val checked = m != null && (m.groupValues[1].equals("x", true) || m.groupValues[2].equals("x", true))
        KeepItem(UUID.randomUUID().toString(), (m?.groupValues?.get(3) ?: line).trim(), checked)
    }.toList()
}

/** A web address, e-mail address or phone number found in a note, with the intent that opens it. */
internal data class NoteLink(val label: String, val intent: Intent)

internal object NoteLinks {
    private val phone = Regex("""[+0][\d ()/.-]{5,}\d""")

    /** The text with its links made tappable: the system opens each in the app made for it (browser, mail, phone). */
    fun annotate(text: String, color: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString {
        val taken = mutableListOf<IntRange>()
        val ranges = mutableListOf<Pair<IntRange, String>>()
        fun add(range: IntRange, url: String) {
            if (taken.none { it.first <= range.last && range.first <= it.last }) {
                taken += range
                ranges += range to url
            }
        }
        // E-mail addresses first, so their domain is not taken for a web address.
        android.util.Patterns.EMAIL_ADDRESS.matcher(text).let { m ->
            while (m.find()) add(m.start() until m.end(), "mailto:" + m.group())
        }
        android.util.Patterns.WEB_URL.matcher(text).let { m ->
            while (m.find()) add(m.start() until m.end(), if (m.group().contains("://")) m.group() else "https://" + m.group())
        }
        phone.findAll(text).forEach { m ->
            if (m.value.count { it.isDigit() } >= 7) add(m.range, "tel:" + m.value.filter { it.isDigit() || it == '+' })
        }
        if (ranges.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
        return androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            ranges.forEach { (range, url) ->
                addLink(
                    androidx.compose.ui.text.LinkAnnotation.Url(
                        url,
                        androidx.compose.ui.text.TextLinkStyles(
                            androidx.compose.ui.text.SpanStyle(color = color, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                        ),
                    ),
                    range.first, range.last + 1,
                )
            }
        }
    }

    fun find(texts: List<String>): List<NoteLink> {
        val all = texts.joinToString("\n")
        val found = linkedMapOf<String, NoteLink>()
        fun add(label: String, intent: Intent) {
            // The chip shows the address without "https://www." and a trailing slash: shorter, and still clear.
            val shown = label.removePrefix("https://").removePrefix("http://").removePrefix("www.").removeSuffix("/")
            if (found.size < 8) found.putIfAbsent(label, NoteLink(shown.take(36), intent))
        }
        android.util.Patterns.WEB_URL.matcher(all).let { m ->
            while (m.find()) {
                val url = m.group()
                add(url, Intent(Intent.ACTION_VIEW, Uri.parse(if (url.contains("://")) url else "https://$url")))
            }
        }
        android.util.Patterns.EMAIL_ADDRESS.matcher(all).let { m ->
            while (m.find()) add(m.group(), Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + m.group())))
        }
        phone.findAll(all).forEach { m ->
            if (m.value.count { it.isDigit() } >= 7) add(m.value.trim(), Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + m.value.filter { it.isDigit() || it == '+' })))
        }
        // An address that is part of an e-mail address is not offered a second time.
        return found.values.filterNot { link -> link.intent.action == Intent.ACTION_VIEW && found.keys.any { it.contains("@") && it.contains(link.label) } }
    }
}

/** Continues a typed list: numbers count up, dashes and stars repeat, and an empty item ends the list. */
internal object ListContinue {
    private val numbered = Regex("""^(\s*)(\d{1,3})([.)])\s(.*)$""")
    private val bulleted = Regex("""^(\s*)([-*•])\s(.*)$""")

    fun apply(old: String, typed: androidx.compose.ui.text.input.TextFieldValue): androidx.compose.ui.text.input.TextFieldValue {
        val text = typed.text
        val pos = typed.selection.start
        // Only one Enter typed at the cursor counts; anything else (paste, delete, other typing) is left alone.
        val enter = typed.selection.collapsed && text.length == old.length + 1 && pos > 0 && text[pos - 1] == '\n' &&
            text.removeRange(pos - 1, pos) == old
        if (!enter) return typed
        val lineStart = old.lastIndexOf('\n', pos - 2).let { if (pos - 2 < 0) 0 else it + 1 }
        val line = old.substring(lineStart, pos - 1)
        val marker: String
        val empty: Boolean
        val n = numbered.matchEntire(line)
        val b = bulleted.matchEntire(line)
        when {
            n != null -> {
                marker = n.groupValues[1] + (n.groupValues[2].toInt() + 1) + n.groupValues[3] + " "
                empty = n.groupValues[4].isBlank()
            }
            b != null -> {
                marker = b.groupValues[1] + b.groupValues[2] + " "
                empty = b.groupValues[3].isBlank()
            }
            else -> return typed
        }
        return if (empty) {
            // Enter on an empty item: the marker goes away and the list is over.
            androidx.compose.ui.text.input.TextFieldValue(
                old.substring(0, lineStart) + old.substring(pos - 1), androidx.compose.ui.text.TextRange(lineStart),
            )
        } else {
            androidx.compose.ui.text.input.TextFieldValue(
                text.substring(0, pos) + marker + text.substring(pos), androidx.compose.ui.text.TextRange(pos + marker.length),
            )
        }
    }
}

/** Numbered checklists: the numbers are part of the items' text ("1. Milk"), so Keep shows them as well. */
internal object ListNumbering {
    private val marker = Regex("""^\s*\d{1,3}[.)]\s+""")

    /** Every filled item starts with a number. */
    fun isNumbered(items: List<KeepItem>): Boolean {
        val filled = items.filter { it.text.isNotBlank() }
        return filled.isNotEmpty() && filled.all { marker.containsMatchIn(it.text) }
    }

    fun strip(items: List<KeepItem>): List<KeepItem> = items.map { it.copy(text = it.text.replaceFirst(marker, "")) }

    /** Puts "1. ", "2. " ... in front of the filled items, counting from [startAt]. */
    fun apply(items: List<KeepItem>, startAt: Int = 1): List<KeepItem> {
        var n = startAt - 1
        return items.map { if (it.text.isBlank()) it else { n++; it.copy(text = "$n. ${it.text}") } }
    }

    /** For cards and widgets: with the setting on, a numbered list counts again in the order it is shown. */
    fun display(all: List<KeepItem>, shown: List<KeepItem>, renumber: Boolean, startAt: Int = 1): List<KeepItem> =
        if (renumber && isNumbered(all)) apply(strip(shown), startAt) else shown
}
