@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gkeepdroid

import android.appwidget.AppWidgetManager
import androidx.core.content.edit
import androidx.core.net.toUri
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.rotate
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

/** What the background choice holds while the note's background is a photo of the user's own. */
internal const val CUSTOM_BACKGROUND = "custom"

/** Edits an existing note (EXTRA_NOTE_ID) or creates a new note or checklist. */
/** Marks a drop place among the rows of the editor; what follows is its category ("" for none). */
private const val SLOT_PREFIX = "\u0000slot:"

class EditNoteActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    /** Set by the open editor: saves what was typed when the user leaves with the Home button. */
    private var onHome: (() -> Unit)? = null

    // The color or picture behind this editor; it belongs to this activity, so the screen it returns to is not tinted by it.
    private val screenTint = ScreenTint().apply { instant = true }

    /** A photo chosen as the note's own background (kept on this phone only), until the note is saved. */
    private var pickedBackground by androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null)
    private val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) pickedBackground = uri }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onHome?.invoke()
    }

    private var layoutKey by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        layoutKey++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opened from a widget or a shortcut the editor is a task of its own, which stays in the recent apps unless that is
        // switched off. Opened from the app it belongs to the app's task, which is left as it is.
        if (isTaskRoot && !AppSettingsStore.load(this).editorInRecents) {
            getSystemService(android.app.ActivityManager::class.java).appTasks
                .firstOrNull { task ->
                    @Suppress("DEPRECATION")
                    task.taskInfo?.let { if (android.os.Build.VERSION.SDK_INT >= 29) it.taskId else it.persistentId } == taskId
                }?.setExcludeFromRecents(true)
        }
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
        setContent { KeepTheme(themeMode, tint = screenTint) { AppLock.Gate(this) { if (unlocked) Loader(account, noteId, newList, pinWidgetId) } } }
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

    @OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
        // The editor stays in the list of recent apps under the note's title, so it can be found again after leaving it.
        val untitled = stringResource(if (existing != null) R.string.app_name else if (isList) R.string.new_list else R.string.new_note)
        val firstLine = if (title.isBlank() && !isList) text.lineSequence().firstOrNull { it.isNotBlank() }?.take(40) else null
        LaunchedEffect(title, firstLine, untitled) {
            val label = title.ifBlank { firstLine ?: untitled }
            @Suppress("DEPRECATION")
            setTaskDescription(
                if (android.os.Build.VERSION.SDK_INT >= 33) android.app.ActivityManager.TaskDescription.Builder().setLabel(label).build()
                else android.app.ActivityManager.TaskDescription(label),
            )
        }
        var textSel by remember { mutableStateOf(androidx.compose.ui.text.TextRange(text.length)) }
        // The keyboard's composing region; dropping it on every keystroke makes the word suggestions flicker.
        var textComp by remember { mutableStateOf<androidx.compose.ui.text.TextRange?>(null) }
        var color by remember { mutableStateOf(existing?.color ?: NoteColors.DEFAULT) }
        // The note's own look fills the screen behind the editor: a color, one of Keep's pictures or a photo of the user's own.
        // The color and the picture are set separately (the picture hides the color); CUSTOM_BACKGROUND stands for the photo.
        val originalGallery = remember { existing?.let { RichNotes.of(this@EditNoteActivity, account, it.id)?.background }.orEmpty() }
        val originalCustom = remember { existing != null && CustomBackgrounds.stamp(this@EditNoteActivity, account, existing.id) != 0L }
        var background by remember { mutableStateOf(if (originalCustom) CUSTOM_BACKGROUND else originalGallery) }
        val picked = pickedBackground
        val showColors = remember { AppSettingsStore.load(this@EditNoteActivity).showNoteColors }
        LaunchedEffect(picked) {
            if (picked != null) background = CUSTOM_BACKGROUND
        }
        // A photo of one's own stays on this phone; that is said before it is chosen, every time until it is marked as understood.
        val hintPrefs = remember { getSharedPreferences("hints", MODE_PRIVATE) }
        var localHint by remember { mutableStateOf(false) }
        if (localHint) {
            var never by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { localHint = false },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.own_photo_hint))
                        Row(
                            Modifier.fillMaxWidth().clickable { never = !never },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(checked = never, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.dont_show_again))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (never) hintPrefs.edit { putBoolean("own_photo_local_dismissed", true) }
                        localHint = false
                        pickBackground.launch("image/*")
                    }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { localHint = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        val darkTheme = LocalDarkTheme.current
        // This phone's own look of the note: a text color, and which version (light or dark) of Keep's picture it shows.
        val originalLook = remember { existing?.let { NoteLook.of(this@EditNoteActivity, account, it.id) } ?: NoteLook.Look(null, null) }
        var variantDark by remember { mutableStateOf(originalLook.dark) }
        var ownText by remember { mutableStateOf(originalLook.textColor) }
        val darkVersion = variantDark ?: darkTheme
        // What is on the device already is there in the first frame, so the picture does not pop in.
        val firstPicture = remember {
            when {
                !showColors -> null
                background == CUSTOM_BACKGROUND -> existing?.let { CustomBackgrounds.peek(this@EditNoteActivity, account, it.id) }
                background.isEmpty() -> null
                else -> NoteBackgrounds.peek(this@EditNoteActivity, background, darkVersion)
            }
        }
        val picture by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(firstPicture, background, picked, darkVersion) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                when {
                    background == CUSTOM_BACKGROUND ->
                        if (picked != null) CustomBackgrounds.preview(this@EditNoteActivity, picked)
                        else existing?.let { CustomBackgrounds.load(this@EditNoteActivity, account, it.id) }
                    background.isEmpty() -> null
                    else -> NoteBackgrounds.load(this@EditNoteActivity, background, darkVersion)
                }
            }
        }
        // The dark version of one of Keep's pictures keeps the editor dark, with light text.
        val darkPicture = darkVersion && background != CUSTOM_BACKGROUND && background.isNotEmpty()
        val tint = if (showColors) NoteColors.argb(color, darkVersion) else null
        val shownPicture = if (showColors) picture else null
        val tinted = tint != null || shownPicture != null
        // Dark colors and dark pictures keep the editor dark, with light text.
        val darkLook = (shownPicture != null && darkPicture) || (shownPicture == null && tint != null && darkVersion)
        // The buttons take their colors from the note's color; without one, from the average color of its picture.
        val accent = tint ?: shownPicture?.let { remember(it) { NoteBackgrounds.averageColor(it) } }
        DisposableEffect(tint, shownPicture, darkLook) {
            screenTint.color = tint
            screenTint.image = shownPicture
            screenTint.darkImage = darkLook
            onDispose { screenTint.color = null; screenTint.image = null; screenTint.darkImage = false }
        }
        var labels by remember { mutableStateOf(existing?.labels.orEmpty()) }
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
        // The item that takes the focus once it is shown (one just made in a category).
        var focusItemId by remember { mutableStateOf<String?>(null) }
        var addMenuOpen by remember { mutableStateOf(false) }
        var newCategoryDialog by remember { mutableStateOf(false) }
        // What the name of a new category is for: its first item (null), or an item dropped onto "New category", or none
        // (the list is being given categories and the new one starts empty).
        var newCategoryFor by remember { mutableStateOf<String?>(null) }
        var newCategoryEmpty by remember { mutableStateOf(false) }
        // The items (one, or all of a category) that a swipe moves to another category; the dialog then offers all of them.
        var moveItems by remember { mutableStateOf<List<String>?>(null) }
        // An earlier category held once: it turns red, held again it is forgotten.
        var armedForget by remember { mutableStateOf<String?>(null) }
        var historyVersion by remember { mutableIntStateOf(0) }
        // The visible part of the note, for scrolling along while an item is dragged near its edges.
        var editorViewport by remember { mutableStateOf<Rect?>(null) }
        val allAccounts = remember { CredentialStore.accounts(this@EditNoteActivity) }
        var targetAccount by remember { mutableStateOf(account) }
        val wasHidden = existing != null && PrivateNotes.isPrivate(existing, account, PrivateNotes.load(this@EditNoteActivity))
        // Changed with "Hide content"; like every other change it only counts once the note is saved.
        var hiddenInWidgets by remember { mutableStateOf(wasHidden) }
        var saving by remember { mutableStateOf(false) }
        var menuOpen by remember { mutableStateOf(false) }
        var formatMenuOpen by remember { mutableStateOf(false) }
        var menuPage by remember { mutableStateOf(0) }
        LaunchedEffect(menuOpen) { if (!menuOpen) menuPage = 0 }
        val isLocal = CredentialStore.isLocal(account)

        // Text formatting (bold, italic, underline, strikethrough) of existing notes of a Keep account.
        // Formatting works for every note: a new one sends it along when it is made, a local one keeps it on the phone.
        val canFormat = true
        val format = remember { FormatState(canFormat, existing?.let { RichNotes.of(this@EditNoteActivity, account, it.id) }) }
        // Folded categories are remembered per note; a note that is being made has no id yet.
        val noteKey = existing?.id ?: "new"
        /** The styles of the items as they are now in this editor, for finding their categories. */
        fun editorRich(): RichNote? =
            if (!canFormat) null else RichNote("", items.associate { it.id to RichDoc(it.text, format.flagsFor(it.id, it.text).runs()) })

        // The categories of this editing session: one that loses its last item (moved elsewhere) stays until the note is
        // saved, so items can be moved back into it, and a new one can be empty for a moment.
        val sessionCategories = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(Categories.names(items, editorRich())) } }
        fun keepCategory(name: String?) {
            if (name == null || name.trim().isEmpty()) return
            if (name.trim() !in sessionCategories) sessionCategories.add(name.trim())
            CategoryHistory.add(this@EditNoteActivity, account, noteKey, listOf(name))
        }
        // The categories this checklist had on this device before (also ones that are gone), for choosing one again.
        remember { CategoryHistory.add(this@EditNoteActivity, account, noteKey, sessionCategories) }

        /**
         * The items in their categories, the categories in a fixed order (the order of this session, not of the items), so a
         * header stays where it is while items move between categories; categories that lost their items stay as empty headers
         * until the note is saved. Null for a list without categories.
         */
        fun categoryGroups(): List<Categories.Group>? {
            if (!canFormat) return null
            val rich = editorRich()
            val nameOf = items.associate { it.id to Categories.split(it, rich?.docs?.get(it.id))?.name }
            val order = sessionCategories.toList() + nameOf.values.filterNotNull().filter { it !in sessionCategories }.distinct()
            if (order.isEmpty()) return null
            return (listOf<String?>(null) + order).map { name -> Categories.Group(name, items.filter { nameOf[it.id] == name }) }
                .filter { it.name != null || it.items.isNotEmpty() }
        }

        /** Moves an item to category [name] (null: none): the old name in front of it goes, the new one comes in bold. */
        fun setCategory(id: String, name: String?) {
            val i = items.indexOfFirst { it.id == id }
            if (i < 0) return
            val item = items[i]
            val cut = Categories.split(item, editorRich()?.docs?.get(id))?.restStart ?: 0
            val rest = item.text.substring(cut)
            val old = format.flagsFor(id, item.text).flags
            val front = if (name == null) "" else "${name.trim()} "
            val flags = IntArray(front.length + rest.length)
            for (k in 0 until (name?.trim()?.length ?: 0)) flags[k] = RichNotes.BOLD
            for (k in rest.indices) flags[front.length + k] = old.getOrElse(cut + k) { 0 } and TEXT_STYLE_MASK
            format.replaceFlags(id, flags)
            items[i] = item.copy(text = front + rest)
            keepCategory(name)
        }

        // What a swipe does to items in this editor, from the settings.
        val itemSwipe = remember(layoutKey) { AppSettingsStore.load(this@EditNoteActivity).let { it.itemSwipeRight to it.itemSwipeLeft } }

        /** A swipe on one item, or on a category's header for all its items: tick, remove, or choose another category. */
        fun swipeItems(ids: List<String>, action: Int) {
            when (action) {
                ITEM_SWIPE_TICK -> {
                    val all = items.filter { it.id in ids && !it.isDivider() }
                    val tick = !all.all { it.checked }
                    for (i in items.indices) if (items[i].id in ids && !items[i].isDivider()) items[i] = items[i].copy(checked = if (ids.size == 1) !items[i].checked else tick)
                }
                ITEM_SWIPE_REMOVE -> {
                    items.removeAll { it.id in ids }
                    if (items.isEmpty()) items.add(KeepItem(UUID.randomUUID().toString(), "", false))
                    if (items.isNotEmpty() && items[0].indent) items[0] = items[0].copy(indent = false)
                }
                ITEM_SWIPE_CATEGORY -> {
                    moveItems = ids
                    newCategoryDialog = true
                }
            }
        }

        /**
         * Makes the list an ordinary checklist again: the words of the categories stay in front of the items, only no longer in
         * bold, so they are no categories any more.
         */
        fun dissolveCategories() {
            val rich = editorRich()
            items.toList().forEach { item ->
                val split = Categories.split(item, rich?.docs?.get(item.id)) ?: return@forEach
                val flags = format.flagsFor(item.id, item.text).flags.copyOf()
                for (k in 0 until minOf(split.restStart, flags.size)) flags[k] = flags[k] and RichNotes.BOLD.inv()
                format.replaceFlags(item.id, flags)
            }
            sessionCategories.clear()
        }


        /** A new item at the end of category [name] (a new category at the end of the list), its name in bold; null: no category. */
        fun addInCategory(name: String?) {
            val id = UUID.randomUUID().toString()
            if (name == null) {
                items.add(KeepItem(id, "", false))
                focusItemId = id
                return
            }
            val clean = name.trim()
            keepCategory(clean)
            val text = "$clean "
            val last = Categories.groups(items, editorRich())?.firstOrNull { it.name == clean }?.items?.lastOrNull()
            val at = last?.let { l -> items.indexOfFirst { it.id == l.id } + 1 }?.takeIf { it > 0 } ?: items.size
            items.add(at, KeepItem(id, text, false))
            format.setStyle(id, text, 0, clean.length, RichNotes.BOLD)
            CategoryFolds.set(this@EditNoteActivity, account, noteKey, clean, false)
            focusItemId = id
        }

        /** The name for a new category was given: for an item dropped onto "New category", as a first empty one, or with a new item. */
        fun createCategory(name: String) {
            val clean = name.trim()
            val forItem = newCategoryFor
            when {
                forItem != null -> {
                    setCategory(forItem, clean)
                    // To the end, where the new category's header appears.
                    val at = items.indexOfFirst { it.id == forItem }
                    if (at >= 0) items.add(items.removeAt(at))
                }
                newCategoryEmpty -> keepCategory(clean)
                else -> addInCategory(clean)
            }
            newCategoryFor = null
            newCategoryEmpty = false
        }
        fun formatTextOf(key: String): String = if (key == "text") text else items.firstOrNull { it.id == key }?.text.orEmpty()
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

        /**
         * The changed formatting as the Python bridge wants it; a note made from the other kind, and items made in this editor,
         * address their items by their place.
         */
        fun formatJson(): String? = format.toJson(
            ::formatTextOf, if (isList) outItems().filter { it.text.isNotBlank() }.map { it.id } else null, convertFrom != null || existing == null,
            known = if (convertFrom == null) existing?.items?.map { it.id }?.toSet().orEmpty() else emptySet(),
        )

        fun save(fromHome: Boolean = false) {
            if (saving || !canEdit) return
            saving = true
            error = ""
            // An item made in a category but left without text would become a bold word of its own in Keep.
            if (isList && canFormat) {
                val rich = editorRich()
                items.removeAll { item -> Categories.split(item, rich?.docs?.get(item.id))?.let { item.text.substring(it.restStart).isBlank() } == true }
                if (items.isEmpty()) items.add(KeepItem(UUID.randomUUID().toString(), "", false))
                // Kept in Keep in the order shown here: the items without a category, then each category with its items.
                categoryGroups()?.flatMap { it.items }?.let { grouped ->
                    if (grouped.size == items.size && grouped.map { it.id } != items.map { it.id }) { items.clear(); items.addAll(grouped) }
                }
                CategoryHistory.add(this@EditNoteActivity, account, noteKey, sessionCategories)
            }
            if (AppSettingsStore.load(this@EditNoteActivity).trimEmptyEdges) {
                if (isList) {
                    // Only if something stays: an all-empty list is left as it is.
                    if (items.any { it.text.isNotBlank() }) {
                        while (items.first().text.isBlank()) items.removeAt(0)
                        while (items.last().text.isBlank()) items.removeAt(items.lastIndex)
                    }
                } else {
                    val lines = text.split("\n")
                    val first = lines.indexOfFirst { it.isNotBlank() }
                    if (first >= 0) {
                        val last = lines.indexOfLast { it.isNotBlank() }
                        val trimmed = lines.subList(first, last + 1).joinToString("\n")
                        // The styles stay with their characters.
                        format.edited("text", text, trimmed)
                        text = trimmed
                    }
                }
            }
            val target = if (existing != null) account else targetAccount
            val galleryNow = if (background == CUSTOM_BACKGROUND) "" else background
            if (existing != null) NoteLook.save(this@EditNoteActivity, account, existing.id, ownText, variantDark)
            if (existing != null) scope.launch { WidgetRefresh.all(this@EditNoteActivity) }
            // The photo of one's own stays on this phone; the widgets learn of a change right away.
            if (existing != null && (background == CUSTOM_BACKGROUND && picked != null || background != CUSTOM_BACKGROUND && originalCustom)) {
                if (background == CUSTOM_BACKGROUND) CustomBackgrounds.save(this@EditNoteActivity, account, existing.id, picked!!)
                else CustomBackgrounds.remove(this@EditNoteActivity, account, existing.id)
                pickedBackground = null
                scope.launch { WidgetRefresh.all(this@EditNoteActivity) }
            }
            val converting = convertFrom != null
            val function = if (existing != null && !converting) "update_note" else "create_note"
            val args = if (existing != null && !converting) {
                KeepRepository.updateNoteArgs(
                    existing.id, title, text, outItems(), color, pinned, removedImages.toList(), withHiddenLabel(labels, hiddenInWidgets),
                    // Only a changed picture of Keep's is sent; "default" takes it away.
                    if (isLocal || galleryNow == originalGallery) "" else galleryNow.ifEmpty { "default" },
                )
            } else {
                KeepRepository.createNoteArgs(title, text, isList, outItems(), color, pinned, withHiddenLabel(labels, hiddenInWidgets), galleryNow, formatJson().orEmpty())
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
                // Sent after the change of the text, so the styles meet the text Keep has by then.
                if (!converting && existing != null) formatJson()?.let { KeepRepository.queueEdit(this@EditNoteActivity, PendingEdit(target, "format_note", listOf(existing.id, it))) }
                Toast.makeText(applicationContext, R.string.note_saved, Toast.LENGTH_SHORT).show()
                val prefs = getSharedPreferences("reminder_hints", MODE_PRIVATE)
                if (!fromHome && !CredentialStore.isLocal(target) && android.os.Build.VERSION.SDK_INT >= 33 && !notificationsOn && !prefs.getBoolean("save_asked", false)) {
                    prefs.edit { putBoolean("save_asked", true) }
                    askAfterSave.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    finish()
                }
                return
            }
            scope.launch {
                try {
                    val before = Reminders.noteIds(this@EditNoteActivity, target)
                    // Taken before the note is written, while the items are still the ones the styles belong to.
                    val styles = if (existing != null) formatJson() else null
                    KeepRepository.run(this@EditNoteActivity, target, function, args)
                    if (styles != null) KeepRepository.run(this@EditNoteActivity, target, "format_note", listOf(existing!!.id, styles))
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
                val (oldText, oldItems) = text to items.toList()
                items.clear()
                items.addAll(newItems)
                text = newText
                isList = !isList
                if (isList) adoptNumbering() else numbered = false
            } else if (existing.images.isNotEmpty()) {
                Toast.makeText(this@EditNoteActivity, R.string.convert_images_blocked, Toast.LENGTH_LONG).show()
            } else {
                val (newText, newItems) = convertedContent(!isList)
                val (oldText, oldItems) = text to items.toList()
                val toList = !isList
                items.clear()
                items.addAll(newItems)
                text = newText
                isList = !isList
                if (isList) adoptNumbering() else numbered = false
                convertFrom = if (isList != existing.isList) existing else null
                // The styles travel with the text (a numbered list also keeps them: the numbers are not part of the items).
                if (convertFrom != null) format.carry(toList, oldText, oldItems, newText, items.toList())
            }
        }

        var confirmDiscard by remember { mutableStateOf(false) }
        var paletteOpen by remember { mutableStateOf(false) }
        var labelsOpen by remember { mutableStateOf(false) }

        fun hasChanges() = if (existing != null) {
            title != existing.title || text != existing.text || outItems() != existing.items || color != existing.color ||
                pinned != existing.pinned || labels != existing.labels || removedImages.isNotEmpty() || convertFrom != null || hiddenInWidgets != wasHidden ||
                background != (if (originalCustom) CUSTOM_BACKGROUND else originalGallery) || picked != null ||
                variantDark != originalLook.dark || ownText != originalLook.textColor || format.touched.isNotEmpty()
        } else {
            title.isNotBlank() || text.isNotBlank() || items.any { it.text.isNotBlank() }
        }

        // Undo and Redo: a step for each change, and for a burst of typing once it pauses. A few minutes after closing, the steps are still there.
        fun capture(): EditSnapshot {
            val (styles, touched) = format.snapshot()
            return EditSnapshot(title, text, textSel, isList, color, background, labels, numbered, items.toList(), ownText, variantDark, styles, touched)
        }
        val historyKey = existing?.let { account + "|" + it.id }
        val history = remember { capture().let { first -> historyKey?.let { EditHistoryKeep.take(it, first) } ?: EditHistory() } }
        LaunchedEffect(title, text, isList, color, background, labels, numbered, items.toList(), ownText, variantDark, format.version) {
            if (history.isTyping(capture())) kotlinx.coroutines.delay(700)
            history.record(capture())
        }
        val latestCapture by rememberUpdatedState { capture() }
        DisposableEffect(Unit) {
            onDispose {
                if (historyKey != null) {
                    history.record(latestCapture())
                    EditHistoryKeep.put(historyKey, history)
                }
            }
        }
        fun restore(step: EditSnapshot) {
            title = step.title
            text = step.text
            textSel = androidx.compose.ui.text.TextRange(step.selection.start.coerceAtMost(step.text.length), step.selection.end.coerceAtMost(step.text.length))
            textComp = null
            isList = step.isList
            color = step.color
            background = step.background
            labels = step.labels
            numbered = step.numbered
            items.clear()
            items.addAll(step.items)
            ownText = step.ownText
            variantDark = step.variantDark
            format.restore(step.styles, step.touched)
            format.selection["text"] = textSel
            convertFrom = if (existing != null && isList != existing.isList) existing else null
        }
        val undo = {
            history.record(capture())
            history.undo()?.let { restore(it) }
            Unit
        }
        val redo = { history.redo()?.let { restore(it) }; Unit }

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

        // The text styles live in the selection menu of Android (next to Cut, Copy, Paste), for the text field that has the focus.
        val styleLabels = listOf(R.string.format_bold, R.string.format_italic, R.string.format_underline, R.string.format_strike).map { stringResource(it) }
        val applyStyle: (Int) -> Unit = { bit -> format.toggleSelected(::formatTextOf, bit) }
        val formatOptions = listOf(
            Triple(R.drawable.ic_format_bold, RichNotes.BOLD, 0), Triple(R.drawable.ic_format_italic, RichNotes.ITALIC, 1),
            Triple(R.drawable.ic_format_underlined, RichNotes.UNDERLINE, 2), Triple(R.drawable.ic_format_strike, RichNotes.STRIKE, 3),
        ).map { (icon, bit, i) -> FormatOption(icon, styleLabels[i], format.version.let { format.isActive(::formatTextOf, bit) }) { applyStyle(bit) } }
        // The list stays open, so more than one style can be switched.
        val formatEntries = formatOptions.map { MenuEntry(it.label, it.icon, checked = it.active, onClick = it.toggle) }
        // Every action of the editor with what it does now; the layout of the settings decides which bar or menu shows it.
        // Read again when the screen comes back, as the layout can be changed in the settings that open from here.
        val editorLayout = remember(layoutKey) { AppSettingsStore.load(this@EditNoteActivity).editorLayout }
        val barSnapBack = remember(layoutKey) { AppSettingsStore.load(this@EditNoteActivity).editorBarSnapBack }
        val states = mutableMapOf<String, EditorActionState>()
        states["close"] = EditorActionState(R.drawable.ic_close, stringResource(R.string.discard_action), { if (hasChanges()) confirmDiscard = true else discard() }, enabled = !saving)
        states["pin"] = EditorActionState(
            if (pinned) R.drawable.ic_push_pin else R.drawable.ic_push_pin_outline, stringResource(R.string.pin_note),
            { if (canEdit) pinned = !pinned else toastUnlockFirst() }, checked = pinned, dimmed = !canEdit,
        )
        states["pin_bottom"] = EditorActionState(
            R.drawable.ic_pin_bottom, stringResource(R.string.pin_to_bottom),
            {
                pinBottom = !pinBottom
                if (pinWidgetId != null && existing != null) {
                    val widget = SettingsStore.loadWidget(this@EditNoteActivity, pinWidgetId)
                    SettingsStore.saveWidget(
                        this@EditNoteActivity, pinWidgetId,
                        widget.copy(pinBottom = if (pinBottom) widget.pinBottom + existing.id else widget.pinBottom - existing.id),
                    )
                    scope.launch { WidgetRefresh.all(this@EditNoteActivity) }
                }
            },
            visible = pinWidgetId != null && existing != null, checked = pinBottom,
        )
        states["reminder"] = EditorActionState(
            R.drawable.ic_alarm, stringResource(R.string.reminders_title), { if (canEdit) remindersOpen = true else askEdit() },
            checked = shownReminders.isNotEmpty(), tint = if (notificationsOn) null else MaterialTheme.colorScheme.error,
        )
        states["save"] = EditorActionState(
            if (canEdit) R.drawable.ic_done else R.drawable.ic_lock,
            stringResource(if (canEdit) R.string.save else if (isList) R.string.edit_list else R.string.edit_note),
            { if (canEdit) save() else askEdit() }, enabled = !saving,
        )
        states["palette"] = EditorActionState(R.drawable.ic_palette, stringResource(R.string.color_and_background), { paletteOpen = true }, visible = canEdit)
        states["labels"] = EditorActionState(R.drawable.ic_label, stringResource(R.string.labels_title), { labelsOpen = true }, visible = canEdit)
        // Keep's API has no way to upload pictures, so adding one is left to the Keep app.
        states["image"] = EditorActionState(
            R.drawable.ic_image, stringResource(R.string.image_add_keep), { startActivity(keepIntent(this@EditNoteActivity)) },
            visible = canEdit && existing != null && !isLocal && images.isEmpty(),
        )
        // What the sheets of the bottom bar hold, to show where the layout sets them beside the note (the highlight is for the preview).
        val inlineBlocks: Map<String, @Composable (Boolean) -> Unit> = mapOf(
            "palette" to { _ ->
                if (canEdit) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppearanceSection(
                        color = color, onColor = { color = it },
                        background = background, onBackground = { background = it; pickedBackground = null },
                        darkVersion = darkVersion, onDarkVersion = { variantDark = it },
                        canEdit = canEdit, isExisting = existing != null, isLocal = isLocal,
                        ownText = ownText, onOwnText = { ownText = it },
                        picture = picture,
                        onPickPhoto = { if (hintPrefs.getBoolean("own_photo_local_dismissed", false)) pickBackground.launch("image/*") else localHint = true },
                    )
                }
            },
            "format" to { highlighted -> if (canFormat && canEdit) FormatBar(formatOptions, highlighted) },
            "labels" to { _ ->
                if (canEdit) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabelsSection(account = targetAccount, labels = labels, onLabels = { labels = it }, canEdit = canEdit)
                }
            },
        )
        // The text styles: one button with a list of them, or a bar of them in the note's place. They work on the selection of the
        // focused field, or on what is typed next at the cursor.
        states["format"] = EditorActionState(
            R.drawable.ic_text_format, stringResource(R.string.format_title), { formatMenuOpen = true }, visible = canFormat && canEdit,
            popup = { GroupedMenu(expanded = formatMenuOpen, onDismissRequest = { formatMenuOpen = false }, groups = listOf(formatEntries)) },
        )
        states["undo"] = EditorActionState(R.drawable.ic_undo, stringResource(R.string.undo), undo, visible = canEdit, enabled = history.canUndo)
        states["redo"] = EditorActionState(R.drawable.ic_redo, stringResource(R.string.redo), redo, visible = canEdit, enabled = history.canRedo)
        states["convert"] = EditorActionState(
            if (isList) R.drawable.ic_subject else R.drawable.ic_checklist, stringResource(if (isList) R.string.convert_to_note else R.string.convert_to_list), { requestConvert() },
        )
        states["list_number"] = EditorActionState(
            R.drawable.ic_list_numbered, stringResource(if (numbered) R.string.list_unnumber else R.string.list_number),
            {
                // Numbers are shown from the position; an old "1. " typed by hand is not counted twice.
                val stripped = ListNumbering.strip(items.toList())
                items.clear()
                items.addAll(stripped)
                numbered = !numbered
            },
            visible = isList,
        )
        states["list_check_all"] = EditorActionState(
            R.drawable.ic_done_all, stringResource(R.string.list_check_all), { for (i in items.indices) if (!items[i].isDivider()) items[i] = items[i].copy(checked = true) }, visible = isList,
        )
        states["list_uncheck_all"] = EditorActionState(
            R.drawable.ic_remove_done, stringResource(R.string.list_uncheck_all), { for (i in items.indices) items[i] = items[i].copy(checked = false) }, visible = isList,
        )
        states["list_sort"] = EditorActionState(
            R.drawable.ic_sort, stringResource(R.string.list_sort_alpha),
            {
                // Sub-items lose their parent when the order changes, so they become ordinary items.
                // Dividers stay where they are: the items between two of them are sorted among themselves.
                val sorted = buildList {
                    var segment = mutableListOf<KeepItem>()
                    fun flush() { addAll(segment.sortedBy { it.text.lowercase() }.map { it.copy(indent = false) }); segment = mutableListOf() }
                    items.forEach { if (it.isDivider()) { flush(); add(it.copy(indent = false)) } else segment.add(it) }
                    flush()
                }
                items.clear()
                items.addAll(sorted)
            },
            visible = isList,
        )
        val hasCategories = isList && canFormat && (sessionCategories.isNotEmpty() || Categories.names(items, editorRich()).isNotEmpty())
        states["list_categories"] = EditorActionState(
            R.drawable.ic_category, stringResource(if (hasCategories) R.string.list_uncategorize else R.string.list_categorize),
            {
                if (hasCategories) dissolveCategories()
                else { newCategoryEmpty = true; newCategoryDialog = true }
            },
            visible = isList && canEdit && canFormat,
        )
        states["list_delete_checked"] = EditorActionState(
            R.drawable.ic_delete_sweep, stringResource(R.string.list_delete_checked),
            {
                items.removeAll { it.checked }
                if (items.isEmpty()) items.add(KeepItem(UUID.randomUUID().toString(), "", false))
            },
            // Always there, so it can be found; it works once something is ticked.
            visible = isList && canEdit, enabled = items.any { it.checked },
        )
        // In a list with categories adding asks where the item goes: no category, one of them, or a new one.
        val categoryNames = if (isList && canFormat) Categories.names(items, editorRich()) else emptyList()
        states["add_item"] = EditorActionState(
            R.drawable.ic_add, stringResource(R.string.add_item),
            { if (categoryNames.isEmpty()) addInCategory(null) else addMenuOpen = true },
            visible = isList && canEdit,
            // Held, it offers the categories in a list without any yet too: that is where the first one is made.
            onLongClick = if (isList && canEdit && canFormat) ({ addMenuOpen = true }) else null,
            popup = {
                GroupedMenu(
                    expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false },
                    groups = listOf(
                        listOf(MenuEntry(stringResource(R.string.category_none)) { addMenuOpen = false; addInCategory(null) }),
                        categoryNames.map { name -> MenuEntry(name) { addMenuOpen = false; addInCategory(name) } },
                        listOf(MenuEntry(stringResource(R.string.category_new), R.drawable.ic_add) { addMenuOpen = false; newCategoryDialog = true }),
                    ),
                )
            },
        )

        states["add_divider"] = EditorActionState(
            R.drawable.ic_divider, stringResource(R.string.add_divider),
            {
                // Right after the item being edited, or at the end.
                val at = items.indexOfFirst { it.id == format.focus }.let { if (it >= 0) it + 1 else items.size }
                items.add(at, KeepItem(UUID.randomUUID().toString(), DIVIDER_TEXT, false))
            },
            visible = isList && canEdit,
        )
        states["open_in_keep"] = EditorActionState(
            R.drawable.ic_open_new, stringResource(R.string.open_in_keep), { if (existing != null) openInKeep(account, existing) },
            visible = existing != null && existing.serverId.isNotEmpty(),
        )
        states["editor_settings"] = EditorActionState(
            R.drawable.ic_settings, stringResource(R.string.editor_buttons_title),
            { startActivity(Intent(this@EditNoteActivity, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, "editor_buttons")) },
        )
        states["share"] = EditorActionState(R.drawable.ic_share, stringResource(R.string.share_note), { shareNote(title, text, isList, outItems()) }, visible = existing != null)
        states["hide_content"] = EditorActionState(
            if (hiddenInWidgets) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
            stringResource(if (hiddenInWidgets) R.string.show_content else R.string.hide_content),
            {
                if (canEdit) {
                    hiddenInWidgets = !hiddenInWidgets
                    scope.launch { WidgetRefresh.all(this@EditNoteActivity) }
                } else {
                    askEdit()
                }
            },
            visible = existing != null,
        )
        states["archive"] = EditorActionState(
            R.drawable.ic_archive, stringResource(R.string.archive_note),
            {
                if (existing != null) leaveNote {
                    KeepRepository.archiveNote(this@EditNoteActivity, account, existing.id)
                    UndoStore.set(account, existing.id, UndoStore.Kind.ARCHIVE)
                }
            },
            visible = existing != null && !isLocal,
        )
        states["delete"] = EditorActionState(
            R.drawable.ic_delete, stringResource(if (isLocal) R.string.delete_note else R.string.move_to_trash),
            {
                if (existing != null) {
                    // Local notes have no trash, so deleting them asks first.
                    if (isLocal) {
                        confirmDelete = true
                    } else {
                        leaveNote {
                            KeepRepository.trashNote(this@EditNoteActivity, account, existing.id)
                            UndoStore.set(account, existing.id, UndoStore.Kind.TRASH)
                        }
                    }
                }
            },
            visible = existing != null,
        )
        // The overflow menu is shown when any of its actions is possible; it grays out until the note is open for editing.
        val listToolsLabel = stringResource(R.string.list_tools)
        states["more"] = EditorActionState(
            R.drawable.ic_more_vert, stringResource(R.string.more_actions), { if (canEdit) menuOpen = true else toastUnlockFirst() },
            visible = editorLayout.inZone(ZONE_MENU).any { states[it.id]?.visible == true }, dimmed = !canEdit,
            popup = {
                GroupedMenu(
                    expanded = menuOpen && canEdit,
                    onDismissRequest = { menuOpen = false },
                    groups = menuGroups(
                        editorLayout, states, menuPage, { menuPage = it }, listToolsLabel, R.drawable.ic_arrow_left,
                        { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }, { menuOpen = false },
                        formatEntries = formatEntries,
                    ),
                )
            },
        )
        val scroll = rememberScrollState()
        // The editor opens in its final look; only changes made while it is open fade.
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(500); screenTint.instant = false }
        OnNoteTheme(
            active = tinted && !darkLook, accent = accent, transparentRows = tinted,
            darkOnPicture = darkLook, textColor = ownText,
        ) {
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            // Ctrl+Z, Ctrl+Shift+Z and Ctrl+Y of a hardware keyboard.
            if (!canEdit || event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@onPreviewKeyEvent false
            when {
                event.key == Key.Z && event.isShiftPressed || event.key == Key.Y -> { redo(); true }
                event.key == Key.Z -> { undo(); true }
                else -> false
            }
        }) {
        Column(Modifier.fillMaxSize()) {
        // The bar stays on screen while the note scrolls: discard, the note's switches, save.
            EditorBar(editorLayout.inZone(ZONE_TOP_LEFT), editorLayout.inZone(ZONE_TOP_RIGHT), states, snapBack = barSnapBack)
        // A short note sits in the middle of the screen between the bars; with the keyboard open, at the top, where one types.
        val imeVisible = WindowInsets.isImeVisible
        BoxWithConstraints(Modifier.weight(1f)) {
        val viewport = maxHeight
        Column(Modifier.fillMaxSize().onGloballyPositioned { editorViewport = it.boundsInRoot() }.verticalScroll(scroll).padding(EDITOR_PADDING)) {
        Column(Modifier.fillMaxWidth().heightIn(min = viewport - EDITOR_PADDING * 2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (unsent != null) {
                Text(
                    if (unsent.error.isBlank()) stringResource(R.string.note_unsent)
                    else stringResource(R.string.note_unsent_failed, unsent.error),
                    color = if (unsent.error.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
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

            // The labels of the note as chips; they are chosen in the sheet of the bottom bar.
            if (labels.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEach { name -> AssistChip(onClick = { labelsOpen = true }, label = { Text(name) }) }
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
            // Where a short note sits is a setting; with the keyboard open it is at the top, where one types.
            if (!imeVisible && editorLayout.option != EDITOR_ALIGN_TOP) Spacer(Modifier.weight(1f))
            // The buttons the layout sets before the note show above its fields, those after it below them.
            val noteZone = editorLayout.inZone(ZONE_NOTE)
            val noteAt = noteZone.indexOfFirst { it.id == NOTE_ID }.coerceAtLeast(0)
            NoteActionButtons(noteZone.take(noteAt), states, inline = inlineBlocks)
            OutlinedTextField(
                value = title, onValueChange = { title = it }, readOnly = !canEdit,
                label = { Text(stringResource(R.string.title_hint)) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
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
                        hintPrefs.edit { putBoolean("list_hint_hidden", true) }
                        hintHidden = true
                    }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.notif_hint_dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // The handle of a row moves it; the bounds tell which row the finger is over.
                var dragging by remember { mutableStateOf<String?>(null) }
                val rowBounds = remember { mutableStateMapOf<String, Rect>() }
                // Where the headers of the categories are, so a dragged item can be dropped onto one.
                val headerBounds = remember { mutableStateMapOf<String, Rect>() }
                // The place to drop an item into a new category, and whether the dragged item is over it.
                var newCategoryZone by remember { mutableStateOf<Rect?>(null) }
                var overNewCategory by remember { mutableStateOf(false) }
                // The category whose header is held: its items are left out while it moves, and follow it when it is let go.
                var draggingCategory by remember { mutableStateOf<String?>(null) }
                // The drop places of the categories that have nothing else in them ("" for no category), and the one the item is over.
                val slotBounds = remember { mutableStateMapOf<String, Rect>() }
                var hoverTarget by remember { mutableStateOf<String?>(null) }
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
                // With categories the items stand under their category's header (as on the cards); the header folds them and
                // its "+" adds an item to them. The list itself keeps its order.
                val editorGroups = categoryGroups()
                val groupOf = editorGroups?.flatMap { g -> g.items.map { it.id to g.name } }?.toMap().orEmpty()
                // One item of the list as it is edited. Under a category's header it shows without the category's name, which
                // stays in front of it in Keep; what is typed goes behind the name.
                @Composable
                fun ItemEditor(index: Int, item: KeepItem, inCategory: Boolean) {
                    val split = if (inCategory) Categories.split(item, editorRich()?.docs?.get(item.id)) else null
                    val cut = split?.restStart ?: 0
                    val fullFlags = if (canFormat) format.flagsFor(item.id, item.text) else null
                    key(item.id) {
                        ChecklistRow(
                            if (cut > 0) item.copy(text = item.text.substring(cut)) else item, canEdit, dragging == item.id,
                            number = if (numbered) items.take(index).count { it.text.isNotBlank() && !it.isDivider() } + 1 else null,
                            onText = { text ->
                                items.indexOfFirst { it.id == item.id }.let { i ->
                                    if (i >= 0) {
                                        val full = items[i].text.take(cut) + text
                                        format.edited(item.id, items[i].text, full)
                                        items[i] = items[i].copy(text = full)
                                    }
                                }
                            },
                            flags = fullFlags?.let { if (cut > 0 && it.flags.size >= cut) DocFlags(it.flags.copyOfRange(cut, it.flags.size)) else it },
                            flagsVersion = format.version,
                            onFocus = { format.focus = item.id },
                            modifier = if (canFormat && canEdit) Modifier.textStyleMenu(styleLabels, applyStyle) else Modifier,
                            onSelection = { format.selection[item.id] = androidx.compose.ui.text.TextRange(it.start + cut, it.end + cut) },
                            onToggle = { items.indexOfFirst { it.id == item.id }.let { i -> if (i >= 0 && !items[i].isDivider()) items[i] = items[i].copy(checked = !items[i].checked) } },
                            onRemove = {
                                items.removeAll { it.id == item.id }
                                if (items.isEmpty()) items.add(KeepItem(UUID.randomUUID().toString(), "", false))
                                if (items.isNotEmpty() && items[0].indent) items[0] = items[0].copy(indent = false)
                            },
                            onBounds = { rowBounds[item.id] = it },
                            onDragStart = { dragging = item.id },
                            onDragTo = { y ->
                                // Near the top or bottom of the visible part the note scrolls along, so every place can be reached.
                                editorViewport?.let { v ->
                                    val edge = 160f
                                    val step = when {
                                        y > v.bottom - edge -> (y - (v.bottom - edge)) / 4f
                                        y < v.top + edge -> -((v.top + edge) - y) / 4f
                                        else -> 0f
                                    }
                                    if (step != 0f) scope.launch { scroll.scrollBy(step) }
                                }
                                overNewCategory = newCategoryZone?.let { y >= it.top && y < it.bottom } == true
                                if (overNewCategory) return@ChecklistRow
                                val from = items.indexOfFirst { it.id == item.id }
                                val over = items.firstOrNull { it.id != item.id && rowBounds[it.id]?.let { r -> y >= r.top && y < r.bottom } == true }
                                val header = headerBounds.entries.firstOrNull { (_, r) -> y >= r.top && y < r.bottom }?.key
                                val slot = slotBounds.entries.firstOrNull { (_, r) -> y >= r.top && y < r.bottom }?.key
                                val firstHeader = headerBounds.values.minOfOrNull { it.top }
                                // Where the item is meant to go: a drop place, a header, an item of another category, or above all
                                // categories (no category).
                                val found = slot != null || header != null || (over != null && groupOf[over.id] != groupOf[item.id]) ||
                                    (firstHeader != null && y < firstHeader && groupOf[item.id] != null)
                                hoverTarget = slot
                                if (from >= 0 && editorGroups != null && found) {
                                    val target = when {
                                        slot != null -> slot.ifEmpty { null }
                                        header != null -> header
                                        over != null && groupOf[over.id] != groupOf[item.id] -> groupOf[over.id]
                                        else -> null
                                    }
                                    if (target != groupOf[item.id]) {
                                        setCategory(item.id, target)
                                        val at = if (over != null) items.indexOfFirst { it.id == over.id } else if (target == null) 0 else items.indexOfLast { groupOf[it.id] == target } + 1
                                        val now = items.indexOfFirst { it.id == item.id }
                                        if (at >= 0 && now >= 0 && at != now) items.add(at.coerceAtMost(items.lastIndex), items.removeAt(now))
                                    }
                                } else if (from >= 0 && over != null) {
                                    val to = items.indexOfFirst { it.id == over.id }
                                    if (to >= 0 && to != from) items.add(to, items.removeAt(from))
                                }
                                if (items.isNotEmpty() && items[0].indent) items[0] = items[0].copy(indent = false)
                            },
                            onDragEnd = {
                                // Let go over "New category": its name is asked, then the item goes into it.
                                if (overNewCategory) {
                                    newCategoryFor = item.id
                                    newCategoryDialog = true
                                }
                                overNewCategory = false
                                hoverTarget = null
                                dragging = null
                            },
                            focusNow = focusItemId == item.id, onFocused = { focusItemId = null },
                            swipeRight = itemSwipe.first, swipeLeft = itemSwipe.second,
                            onSwipe = { right -> swipeItems(listOf(item.id), if (right) itemSwipe.first else itemSwipe.second) },
                            framed = inCategory,
                        )
                    }
                }
                // Headers and items in one flat run, each under its own key: an item dragged into another category keeps its
                // place in the composition (and so the finger that holds it).
                val folds = remember(CategoryFolds.version.intValue) { CategoryFolds.load(this@EditNoteActivity) }
                // While an item is held, a category that has nothing else in it (and "no category" at the top) gets a place of
                // the size of a row to drop it into, so an empty category is as easy to hit as a full one.
                val holding = dragging
                val entries: List<Pair<String?, KeepItem?>> = if (editorGroups == null) items.map { null to it } else buildList {
                    if (holding != null && editorGroups.none { it.name == null && it.items.any { i -> i.id != holding } }) add(SLOT_PREFIX to null)
                    editorGroups.forEach { group ->
                        if (group.name != null) add(group.name to null)
                        val hidden = group.name != null && (CategoryFolds.key(account, noteKey, group.name) in folds || group.name == draggingCategory)
                        if (!hidden) group.items.forEach { add(group.name to it) }
                        if (holding != null && group.name != null && !hidden && group.items.none { it.id != holding }) add(SLOT_PREFIX + group.name to null)
                    }
                }
                // Only what is shown can be dropped onto: rows of folded categories and headers that are gone are forgotten.
                val shownRows = entries.mapNotNull { it.second?.id }.toSet()
                val shownHeaders = editorGroups?.mapNotNull { it.name }?.toSet().orEmpty()
                androidx.compose.runtime.SideEffect {
                    rowBounds.keys.retainAll(shownRows)
                    headerBounds.keys.retainAll(shownHeaders)
                    slotBounds.keys.retainAll(entries.mapNotNull { (n, i) -> if (i == null && n != null && n.startsWith(SLOT_PREFIX)) n.removePrefix(SLOT_PREFIX) else null }.toSet())
                }
                // The key sits right in the loop: inside a function called from it, it would not let the row move along.
                entries.forEach { (name, item) -> key(item?.id ?: "category:$name") {
                    if (item != null) {
                        ItemEditor(items.indexOfFirst { it.id == item.id }, item, inCategory = name != null)
                    } else if (name != null && name.startsWith(SLOT_PREFIX)) {
                        val target = name.removePrefix(SLOT_PREFIX)
                        CategoryDropSlot(
                            if (target.isEmpty()) stringResource(R.string.category_none) else null,
                            active = hoverTarget == target,
                            modifier = Modifier.onGloballyPositioned { slotBounds[target] = it.boundsInRoot() }.animatePlacement(),
                        )
                    } else if (name != null) {
                        val group = editorGroups!!.first { it.name == name }
                        val folded = CategoryFolds.key(account, noteKey, name) in folds
                        val real = group.items.filter { !it.isDivider() }
                        EditorCategoryHeader(
                            name, real.count { !it.checked }, folded, canEdit,
                            onToggle = { CategoryFolds.set(this@EditNoteActivity, account, noteKey, name, !folded) },
                            onAdd = { addInCategory(name) },
                            modifier = Modifier.onGloballyPositioned { headerBounds[name] = it.boundsInRoot() }
                                .then(if (draggingCategory == name) Modifier else Modifier.animatePlacement()),
                            allChecked = real.isNotEmpty() && real.all { it.checked },
                            onCheckAll = {
                                val tick = !(real.isNotEmpty() && real.all { it.checked })
                                val ids = real.map { it.id }.toSet()
                                for (i in items.indices) if (items[i].id in ids) items[i] = items[i].copy(checked = tick)
                            },
                            dragged = draggingCategory == name,
                            onDragStart = { draggingCategory = name },
                            onDragTo = { y ->
                                // Over another header: the held category takes its place in the order.
                                val over = headerBounds.entries.firstOrNull { (other, r) -> other != name && y >= r.top && y < r.bottom }?.key
                                if (over != null) {
                                    val from = sessionCategories.indexOf(name)
                                    val to = sessionCategories.indexOf(over)
                                    if (from >= 0 && to >= 0) sessionCategories.add(to, sessionCategories.removeAt(from))
                                }
                                editorViewport?.let { v ->
                                    val edge = 160f
                                    val step = when { y > v.bottom - edge -> (y - (v.bottom - edge)) / 4f; y < v.top + edge -> -((v.top + edge) - y) / 4f; else -> 0f }
                                    if (step != 0f) scope.launch { scroll.scrollBy(step) }
                                }
                            },
                            onDragEnd = { draggingCategory = null },
                            swipeRight = itemSwipe.first, swipeLeft = itemSwipe.second,
                            onSwipe = { right -> swipeItems(group.items.map { it.id }, if (right) itemSwipe.first else itemSwipe.second) },
                        )
                    }
                } }
                // While an item is held, a place at the end takes it into a new category.
                if (dragging != null && canFormat) NewCategoryDropZone(
                    active = overNewCategory,
                    modifier = Modifier.onGloballyPositioned { newCategoryZone = it.boundsInRoot() },
                ) else if (newCategoryZone != null) androidx.compose.runtime.SideEffect { newCategoryZone = null }
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
                                convertKey?.let { convertPrefs.edit { putBoolean(it, true) } }
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
                        format.edited("text", text, result.text)
                        text = result.text
                        format.selection["text"] = result.selection
                        textSel = result.selection
                        textComp = if (result === typed) typed.composition else null
                    },
                    readOnly = !canEdit,
                    label = { Text(stringResource(R.string.note_hint)) },
                    visualTransformation = if (canFormat) remember(format.version, text) { FlagsTransformation(format.flagsFor("text", text)) } else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).onFocusChanged { if (it.isFocused) format.focus = "text" }
                        .then(if (canFormat && canEdit) Modifier.textStyleMenu(styleLabels, applyStyle) else Modifier),
                )
            }

            NoteActionButtons(editorLayout.inZone(ZONE_NOTE).dropWhile { it.id != NOTE_ID }.drop(1), states, inline = inlineBlocks)
            if (imeVisible || editorLayout.option != EDITOR_ALIGN_BOTTOM) Spacer(Modifier.weight(1f))

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


            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)

            if (remindersOpen) {
                RemindersDialog(
                    activity = this@EditNoteActivity, account = account, noteId = existing?.id.orEmpty(), reminders = shownReminders,
                    host = { dismiss, content -> EditorSheet(dismiss, content) },
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
        }
        }
        }
        // The bottom bar sits above the keyboard, where the thumb is while typing, and stays when the note scrolls.
        EditorBar(editorLayout.inZone(ZONE_BOTTOM_LEFT), editorLayout.inZone(ZONE_BOTTOM_RIGHT), states, snapBack = barSnapBack)
        }
        if (paletteOpen) EditorSheet({ paletteOpen = false }) {
            AppearanceSection(
                color = color, onColor = { color = it },
                background = background, onBackground = { background = it; pickedBackground = null },
                darkVersion = darkVersion, onDarkVersion = { variantDark = it },
                canEdit = canEdit, isExisting = existing != null, isLocal = isLocal,
                ownText = ownText, onOwnText = { ownText = it },
                picture = picture,
                onPickPhoto = { if (hintPrefs.getBoolean("own_photo_local_dismissed", false)) pickBackground.launch("image/*") else localHint = true },
            )
        }
        if (labelsOpen) EditorSheet({ labelsOpen = false }) {
            LabelsSection(account = targetAccount, labels = labels, onLabels = { labels = it }, canEdit = canEdit)
        }
        if (newCategoryDialog) {
            var name by remember { mutableStateOf("") }
            // The name is typed at once.
            val nameFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(100); runCatching { nameFocus.requestFocus() } }
            val create = {
                if (name.isNotBlank()) {
                    if (moveItems != null) { val n = name.trim(); val m = moveItems; newCategoryDialog = false; moveItems = null; m?.forEach { setCategory(it, n) }; keepCategory(n) }
                    else { newCategoryDialog = false; createCategory(name) }
                }
            }
            val cancel = { newCategoryDialog = false; newCategoryFor = null; newCategoryEmpty = false; moveItems = null; armedForget = null }
            // Moving items by a swipe: every category, the ones they are in too, and "no category".
            val moving = moveItems
            fun moveTo(target: String?) {
                newCategoryDialog = false; moveItems = null; armedForget = null
                moving?.forEach { setCategory(it, target) }
                if (target != null) keepCategory(target)
            }
            AlertDialog(
                onDismissRequest = cancel,
                title = { Text(stringResource(if (moving != null) R.string.category_move else R.string.add_category)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // An item dropped here can also leave its category, or go into one this list had before (also one that
                        // is gone since it was last saved); the ones it has now are its own headers.
                        val current = categoryGroups()?.mapNotNull { it.name }.orEmpty()
                        val earlier = historyVersion.let { CategoryHistory.load(this@EditNoteActivity, account, noteKey) }.filter { it !in current }
                        if (moving != null || earlier.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (moving != null) {
                                CategoryChip(stringResource(R.string.category_none)) { moveTo(null) }
                                current.forEach { CategoryChip(it) { moveTo(it) } }
                            }
                            earlier.forEach { old ->
                                CategoryChip(
                                    old, armed = armedForget == old,
                                    onLongClick = {
                                        if (armedForget == old) {
                                            CategoryHistory.remove(this@EditNoteActivity, account, noteKey, old)
                                            armedForget = null
                                            historyVersion++
                                        } else armedForget = old
                                    },
                                ) { if (moving != null) moveTo(old) else { newCategoryDialog = false; createCategory(old) } }
                            }
                        }
                        if (earlier.isNotEmpty()) Text(
                            stringResource(R.string.category_forget_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = name, onValueChange = { name = it }, singleLine = true,
                            placeholder = { Text(stringResource(R.string.category_name)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { create() }),
                            modifier = Modifier.focusRequester(nameFocus),
                        )
                    }
                },
                confirmButton = { TextButton(onClick = create, enabled = name.isNotBlank(), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.add_category_confirm)) } },
                dismissButton = { TextButton(onClick = cancel, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
        if (confirmDiscard) AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; discard() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.discard_action)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.keep_editing)) } },
        )
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
        val web = Intent(Intent.ACTION_VIEW, "https://keep.google.com/$authUser#$kind/${note.serverId}".toUri())
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
