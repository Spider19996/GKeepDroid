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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

/** Edits an existing note (EXTRA_NOTE_ID) or creates a new note or checklist. */
class EditNoteActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
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
        val needsLock = noteId != null && AppSettingsStore.load(this).lockPrivateNotes &&
            PrivateNotes.key(account, noteId) in PrivateNotes.load(this) && keyguard.isDeviceSecure
        var unlocked by mutableStateOf(!needsLock)
        val unlock = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) unlocked = true else finish()
        }
        if (needsLock) {
            @Suppress("DEPRECATION")
            unlock.launch(keyguard.createConfirmDeviceCredentialIntent(getString(R.string.app_name), getString(R.string.note_private_hint)))
        }
        setContent { KeepTheme(themeMode) { if (unlocked) Loader(account, noteId, newList, pinWidgetId) } }
    }

    /**
     * An existing note opens at once from the cache. Keep is asked in the background (unless a sync
     * just ran); if the note changed there, a hint offers to reload it, so nothing typed is lost.
     */
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
                fresh != cached -> remote = fresh
            }
            loaded = true
        }

        when {
            !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            gone -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.note_gone), style = MaterialTheme.typography.titleMedium)
                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
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
        account: String, existing: KeepNote?, isList: Boolean, offline: Boolean, pinWidgetId: Int?,
        remote: KeepNote?, onReload: () -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        // Text shared from another app arrives as a prefill for a new note.
        var title by remember { mutableStateOf(existing?.title ?: intent.getStringExtra(EXTRA_PREFILL_TITLE).orEmpty()) }
        var text by remember { mutableStateOf(existing?.text ?: intent.getStringExtra(EXTRA_PREFILL_TEXT).orEmpty()) }
        var color by remember { mutableStateOf(existing?.color ?: NoteColors.DEFAULT) }
        var labels by remember { mutableStateOf(existing?.labels.orEmpty()) }
        var newLabel by remember { mutableStateOf("") }
        var manageLabels by remember { mutableStateOf(false) }
        var labelsVersion by remember { mutableStateOf(0) }
        // Label changes show at once; Keep follows in the background.
        var addedLabels by remember { mutableStateOf(setOf<String>()) }
        var removedLabels by remember { mutableStateOf(setOf<String>()) }
        var labelToDelete by remember { mutableStateOf<String?>(null) }
        val items = remember {
            mutableStateListOf<KeepItem>().apply {
                addAll(existing?.items ?: if (isList) listOf(KeepItem(UUID.randomUUID().toString(), "", false)) else emptyList())
            }
        }
        val allAccounts = remember { CredentialStore.accounts(this@EditNoteActivity) }
        var targetAccount by remember { mutableStateOf(account) }
        var hiddenInWidgets by remember {
            mutableStateOf(
                existing != null && PrivateNotes.key(account, existing.id) in PrivateNotes.load(this@EditNoteActivity)
            )
        }
        var saving by remember { mutableStateOf(false) }
        var menuOpen by remember { mutableStateOf(false) }
        val isLocal = CredentialStore.isLocal(account)
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
        var remindersOpen by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        val failedTemplate = stringResource(R.string.save_failed, "%s")

        /** Runs an action that ends the editing of this note (archive, trash) and closes the editor. */
        fun leaveNote(action: suspend () -> Unit) {
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

        fun save() {
            saving = true
            error = ""
            val target = if (existing != null) account else targetAccount
            val function = if (existing != null) "update_note" else "create_note"
            val args = if (existing != null) {
                KeepRepository.updateNoteArgs(existing.id, title, text, items.toList(), color, pinned, removedImages.toList(), labels)
            } else {
                KeepRepository.createNoteArgs(title, text, isList, items.toList(), color, pinned, labels)
            }
            if (!CredentialStore.isLocal(target)) {
                // The change is stored and sent in the background: the editor closes at once, and a failure
                // shows up as a notification instead of losing the change.
                KeepRepository.queueEdit(this@EditNoteActivity, PendingEdit(target, function, args))
                finish()
                return
            }
            scope.launch {
                try {
                    KeepRepository.run(this@EditNoteActivity, target, function, args)
                    finish()
                } catch (e: Exception) {
                    saving = false
                    error = failedTemplate.format(e.message ?: e.javaClass.simpleName)
                }
            }
        }

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconToggleButton(checked = pinned, onCheckedChange = { pinned = it }) {
                    Icon(
                        painterResource(if (pinned) R.drawable.ic_push_pin else R.drawable.ic_push_pin_outline),
                        contentDescription = stringResource(R.string.pin_note),
                    )
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
                    ) {
                        Icon(painterResource(R.drawable.ic_pin_bottom), contentDescription = stringResource(R.string.pin_to_bottom))
                    }
                }
                if (existing != null) {
                    // Less common actions live in an overflow menu.
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_actions))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (existing.serverId.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.open_in_keep)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_open_new), contentDescription = null) },
                                    onClick = { menuOpen = false; openInKeep(account, existing) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_note)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_share), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    shareNote(title, text, isList, items.toList())
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(if (hiddenInWidgets) R.string.show_content else R.string.hide_content))
                                },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    hiddenInWidgets = !hiddenInWidgets
                                    PrivateNotes.set(this@EditNoteActivity, account, existing.id, hiddenInWidgets)
                                    scope.launch { WidgetRefresh.all(this@EditNoteActivity) }
                                },
                            )
                            if (!isLocal) DropdownMenuItem(
                                text = { Text(stringResource(R.string.archive_note)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_archive), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    leaveNote {
                                        KeepRepository.archiveNote(this@EditNoteActivity, account, existing.id)
                                        UndoStore.set(account, existing.id, UndoStore.Kind.ARCHIVE)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (isLocal) R.string.delete_note else R.string.move_to_trash)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                                onClick = {
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
                        }
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
                        }) { Text(stringResource(R.string.delete_note)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) }
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
                TextButton(onClick = { manageLabels = true }) { Text(stringResource(R.string.manage_labels)) }
            }
            if (allLabels.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allLabels.forEach { name ->
                    FilterChip(
                        selected = name in labels,
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
                                ) { Text(stringResource(R.string.add_label)) }
                            }
                            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                                knownLabels.forEach { name ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(name, Modifier.weight(1f))
                                        TextButton(onClick = { labelToDelete = name }) {
                                            Text(stringResource(R.string.delete_label))
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { manageLabels = false }) { Text(stringResource(R.string.done)) }
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
                        }) { Text(stringResource(R.string.delete_label)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { labelToDelete = null }) { Text(stringResource(android.R.string.cancel)) }
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
                    TextButton(onClick = onReload) { Text(stringResource(R.string.reload)) }
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
                        TextButton(onClick = { removedImages = removedImages - imageId }) {
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
                                TextButton(onClick = { openInGallery(account, imageId) }) {
                                    Text(stringResource(R.string.image_open_gallery))
                                }
                                if (existing != null) {
                                    TextButton(onClick = { removedImages = removedImages + imageId }) {
                                        Text(stringResource(R.string.image_remove))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Keep's API has no way to upload pictures, so adding one is left to the Keep app.
            if (existing != null && !isLocal) {
                OutlinedButton(
                    onClick = { startActivity(keepIntent(this@EditNoteActivity)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.image_add_keep)) }
            }
            viewerImage?.let { imageId ->
                val full = remember(imageId) { NoteImages.full(this@EditNoteActivity, account, imageId) }
                if (full != null) ImageViewerDialog(full) { viewerImage = null }
            }

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text(stringResource(R.string.title_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (isList) {
                // Rows can be dragged by their handle; the bounds tell which row the finger is over.
                var dragging by remember { mutableStateOf<String?>(null) }
                val rowBounds = remember { mutableStateMapOf<String, Rect>() }
                /** The first item cannot be a sub-item, as there is nothing above it to belong to. */
                fun fixFirst() {
                    if (items.isNotEmpty() && items[0].indent) items[0] = items[0].copy(indent = false)
                }
                items.forEachIndexed { index, item ->
                    key(item.id) {
                        var handle by remember { mutableStateOf<LayoutCoordinates?>(null) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { rowBounds[item.id] = it.boundsInRoot() }
                                .alpha(if (dragging == item.id) 0.5f else 1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_drag_handle),
                                contentDescription = stringResource(R.string.drag_handle),
                                modifier = Modifier
                                    .size(36.dp)
                                    .padding(6.dp)
                                    .onGloballyPositioned { handle = it }
                                    .pointerInput(item.id) {
                                        detectDragGestures(
                                            onDragStart = { dragging = item.id },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val y = handle?.localToRoot(change.position)?.y ?: return@detectDragGestures
                                                val from = items.indexOfFirst { it.id == item.id }
                                                val to = items.indexOfFirst { rowBounds[it.id]?.let { r -> y >= r.top && y < r.bottom } == true }
                                                if (from >= 0 && to >= 0 && to != from) {
                                                    items.add(to, items.removeAt(from))
                                                    fixFirst()
                                                }
                                            },
                                            onDragEnd = { dragging = null },
                                            onDragCancel = { dragging = null },
                                        )
                                    },
                            )
                            Checkbox(
                                checked = item.checked,
                                onCheckedChange = { checked -> items.indexOfFirst { it.id == item.id }.let { items[it] = item.copy(checked = checked) } },
                            )
                            OutlinedTextField(
                                value = item.text,
                                onValueChange = { text -> items.indexOfFirst { it.id == item.id }.let { items[it] = items[it].copy(text = text) } },
                                placeholder = { Text(stringResource(R.string.item_hint)) },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { items.removeAll { it.id == item.id }; fixFirst() }) { Text("✕") }
                        }
                    }
                }
                OutlinedButton(onClick = { items.add(KeepItem(UUID.randomUUID().toString(), "", false)) }) {
                    Text("＋ " + stringResource(R.string.add_item))
                }
            } else {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text(stringResource(R.string.note_hint)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
            }

            Text(stringResource(R.string.color))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NoteColors.names.forEach { name ->
                    val argb = NoteColors.argb(name)
                    Box(
                        Modifier.size(if (name == color) 44.dp else 36.dp)
                            .clip(CircleShape)
                            .background(if (argb != null) Color(argb) else Color.Transparent)
                            .border(
                                if (name == color) 3.dp else 1.dp,
                                if (name == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            )
                            .clickable { color = name },
                        contentAlignment = Alignment.Center,
                    ) {
                        // The default (uncolored) swatch is transparent, so mark it.
                        if (argb == null) Text("∅")
                    }
                }
            }

            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)

            if (remindersOpen && existing != null) {
                RemindersDialog(
                    activity = this@EditNoteActivity, account = account, noteId = existing.id, reminders = reminders,
                    onChange = { reminders = Reminders.list(this@EditNoteActivity, account, existing.id) },
                    onDismiss = { remindersOpen = false },
                    askNotifications = {
                        if (android.os.Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(this@EditNoteActivity)) {
                            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }

            reminders.firstOrNull()?.let {
                Text(
                    stringResource(R.string.reminder_at, Reminders.label(this@EditNoteActivity, it.at)) +
                        if (reminders.size > 1) "  (+${reminders.size - 1})" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f).fillMaxHeight(), enabled = !saving, onClick = { finish() },
                ) { Text(stringResource(R.string.cancel)) }
                if (existing == null) Box(Modifier.align(Alignment.CenterVertically)) {
                    // A new note has no id yet, so it cannot have a reminder; the button says so.
                    FilledTonalIconToggleButton(
                        checked = false,
                        onCheckedChange = { Toast.makeText(this@EditNoteActivity, R.string.reminder_save_first, Toast.LENGTH_SHORT).show() },
                    ) {
                        Icon(painterResource(R.drawable.ic_alarm), contentDescription = stringResource(R.string.reminders_title))
                    }
                } else Box(Modifier.align(Alignment.CenterVertically)) {
                    // Set: the accent color with a matching icon. None set: a quiet button.
                    FilledTonalIconToggleButton(
                        checked = reminders.isNotEmpty(),
                        onCheckedChange = { remindersOpen = true },
                        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(painterResource(R.drawable.ic_alarm), contentDescription = stringResource(R.string.reminders_title))
                    }
                }
                Button(
                    modifier = Modifier.weight(1f).fillMaxHeight(), enabled = !saving, onClick = { save() },
                ) { Text(stringResource(if (saving) R.string.saving else R.string.save)) }
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
