package com.gkeepdroid

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** The widget that lists all notes of one account. */
class NotesWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // The real widget size is needed to scale images to the full width.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            // In the debug build a "DEBUG" mark lies above the widget and lets touches through.
            Box(GlanceModifier.fillMaxSize()) {
                Content(appWidgetId)
                DebugWatermarkGlance()
            }
        }
    }

    @Composable
    private fun Content(appWidgetId: Int) {
        val context = LocalContext.current
        // An update does not re-run provideGlance for a running session, so notes and settings are
        // read here and re-read whenever the state version changes.
        val version = currentState<Preferences>()[WidgetRefresh.VersionKey]
        val settings = remember(version) { SettingsStore.loadWidget(context, appWidgetId) }
        val account = remember(version) { CredentialStore.resolve(context, settings.account) }
        val notes = remember(version) {
            sortNotes(NotesCache.read(context, account), settings).filter { settings.labelFilter.isEmpty() || settings.labelFilter in it.labels }
        }
        val collapsed = remember(version) { CollapseStore.load(context) }
        val reordering = remember(version) { SettingsStore.isReordering(context, appWidgetId) }
        val privateKeys = remember(version) { PrivateNotes.load(context) }
        val hideAll = remember(version) { AppSettingsStore.load(context).let { it.lockApp && it.widgetTitlesOnly } }
        val foldedKeys = remember(version) { FoldStore.load(context) }
        val thumbs = remember(version) {
            val result = HashMap<String, Bitmap?>()
            if (settings.showImages && account != null) {
                // A widget may only carry a limited amount of bitmap memory, so images stop at a budget.
                var budget = IMAGE_BUDGET_BYTES
                notes.forEach { note ->
                    val bitmap = note.images.firstOrNull()?.let { NoteImages.thumbnail(context, account, it) }
                    if (bitmap != null && budget >= bitmap.byteCount) {
                        budget -= bitmap.byteCount
                        result[note.id] = bitmap
                    }
                }
            }
            result
        }
        // Keep's background pictures, as far as they are on the device; they share a smaller budget with each other.
        val pictures = remember(version) {
            val result = HashMap<String, Bitmap?>()
            if (settings.showNoteColors && account != null) {
                var budget = PICTURE_BUDGET_BYTES
                notes.forEach { note ->
                    val name = RichNotes.of(context, account, note.id)?.background.orEmpty()
                    val bitmap = CustomBackgrounds.forWidget(context, account, note.id, settings.noteAlpha, 0.45f)
                        ?: if (name.isEmpty()) null else NoteBackgrounds.forWidget(context, name, settings.noteAlpha, 0.45f, NoteBackgrounds.isNight(context))
                    if (bitmap != null && budget >= bitmap.byteCount) {
                        budget -= bitmap.byteCount
                        result[note.id] = bitmap
                    }
                }
            }
            result
        }
        Content(appWidgetId, account, notes, settings, collapsed, thumbs, pictures, reordering, privateKeys, hideAll, foldedKeys)
    }

    @Composable
    private fun Content(
        appWidgetId: Int,
        account: String?,
        notes: List<KeepNote>,
        settings: WidgetSettings,
        collapsed: Map<String, Boolean>,
        thumbs: Map<String, Bitmap?>,
        pictures: Map<String, Bitmap?>,
        reordering: Boolean,
        privateKeys: Set<String>,
        hideAll: Boolean,
        foldedKeys: Set<String>,
    ) {
        val context = LocalContext.current
        // Widget padding (8 dp each side) and card padding (8 dp each side) are left free for the image.
        val contentWidth = (LocalSize.current.width - 32.dp - sideBarWidth(settings, withNewButtons = true)).coerceAtLeast(0.dp)
        val textColor = textPalette(settings, onNoteColor = false).provider()
        val background = ColorProvider(Color.Black.copy(alpha = settings.backgroundAlpha))

        WithHeaderBar(
            modifier = GlanceModifier.fillMaxSize().background(background).cornerRadius(R.dimen.widget_corner).padding(8.dp),
            bar = { side ->
                HeaderBar(
                    side, appWidgetId, account, settings, textColor, withNewButtons = true, reordering = reordering,
                    allFolded = notes.isNotEmpty() && notes.all { FoldStore.key(account.orEmpty(), it.id) in foldedKeys },
                )
            },
        ) {
            if (notes.isEmpty()) {
                Text(
                    context.getString(R.string.no_notes),
                    style = TextStyle(color = textColor, fontSize = settings.textSizeSp.sp),
                    modifier = GlanceModifier.defaultWeight().clickable(
                        actionStartActivity<SettingsActivity>(actionParametersOf(WidgetIdKey to appWidgetId))
                    ),
                )
            } else {
                LazyColumn(GlanceModifier.defaultWeight()) {
                    items(notes, itemId = { stableId("n", it.id) }) { note ->
                        val index = notes.indexOf(note)
                        NoteCard(
                            appWidgetId, note, account.orEmpty(), settings, collapsed, thumbs[note.id], pictures[note.id], contentWidth,
                            reordering = reordering, canMoveUp = index > 0, canMoveDown = index < notes.lastIndex,
                            isPrivate = hideAll || PrivateNotes.isPrivate(note, account.orEmpty(), privateKeys),
                            foldedKeys = foldedKeys,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun NoteCard(
        appWidgetId: Int,
        note: KeepNote,
        account: String,
        settings: WidgetSettings,
        collapsedByNote: Map<String, Boolean>,
        thumb: Bitmap?,
        picture: Bitmap?,
        contentWidth: Dp,
        reordering: Boolean,
        canMoveUp: Boolean,
        canMoveDown: Boolean,
        isPrivate: Boolean,
        foldedKeys: Set<String>,
    ) {
        val noteColor = if (settings.showNoteColors) NoteColors.argb(note.color) else null
        // Keep's pictures come in a dark version for the phone's dark mode; a photo of one's own never does.
        val onDarkPicture = picture != null && NoteBackgrounds.isNight(LocalContext.current) &&
            CustomBackgrounds.stamp(LocalContext.current, account, note.id) == 0L
        val palette = textPalette(settings, onNoteColor = noteColor != null || picture != null, onDarkPicture = onDarkPicture)
        val textColor = palette.provider()

        val cardModifier = GlanceModifier.fillMaxWidth().cornerRadius(R.dimen.widget_inner_corner).noteBackground(noteColor, settings, picture = picture)
        // With a picture, the note's color is a frame around it.
        val framed = picture != null && noteColor != null
        val outerModifier = if (framed) {
            GlanceModifier.fillMaxWidth().cornerRadius(R.dimen.widget_inner_corner)
                .background(ColorProvider(Color(noteColor!!).copy(alpha = settings.noteAlpha))).padding(3.dp)
        } else {
            cardModifier
        }
        val innerModifier = if (framed) {
            GlanceModifier.fillMaxWidth().cornerRadius(R.dimen.widget_inner_corner)
                .background(ImageProvider(picture!!), androidx.glance.layout.ContentScale.Crop)
        } else {
            GlanceModifier.fillMaxWidth()
        }
        val folded = settings.foldable && FoldStore.key(account, note.id) in foldedKeys

        // A small pin shows that the note is pinned in Keep or shown at the widget's bottom.
        val pinned = note.pinned || note.id in settings.pinBottom
        // No title (and nothing else for that line): no room is kept for it, as in the app.
        val hasTitleRow = note.title.isNotBlank() || reordering || isPrivate
        // Without a title the pin and the progress sit beside the fold arrow in the corner.
        val showProgress = settings.showProgress && progressText(note) != null
        val cornerExtra = if (hasTitleRow) 0 else (if (pinned) 24 else 0) + (if (showProgress) 36 else 0)
        Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
            // A box, so the fold arrow can float over the corner without making the title line taller.
            Box(
                outerModifier.clickable(
                    actionStartActivity<EditNoteActivity>(
                        actionParametersOf(NoteIdKey to note.id, AccountKey to account, WidgetIdKey to appWidgetId)
                    )
                )
            ) {
              Box(innerModifier) {
                Column(
                    GlanceModifier.fillMaxWidth().padding(
                        start = 8.dp, top = 8.dp, bottom = 8.dp,
                        end = if (!hasTitleRow && (settings.foldable || cornerExtra > 0)) ((if (settings.foldable) 40 else 0) + cornerExtra).dp else 8.dp,
                    )
                ) {
                    // Inside the padding, so the image stays within the note and its border stays visible.
                    if (thumb != null && !isPrivate) Box(GlanceModifier.padding(bottom = 6.dp)) { NoteImage(thumb, contentWidth) }
                    if (hasTitleRow) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            if (note.title.isNotBlank()) {
                                Text(
                                    note.title,
                                    style = TextStyle(
                                        color = textColor,
                                        fontSize = (settings.textSizeSp + 2).sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                            } else {
                                Box(GlanceModifier.defaultWeight()) {}
                            }
                            if (isPrivate) LockIndicator(settings, palette) else ProgressIndicator(note, settings, palette)
                            if (pinned) PinIndicator(settings, palette)
                            // Room for the arrow that floats over the corner.
                            if (settings.foldable) androidx.glance.layout.Spacer(GlanceModifier.width(32.dp))
                            if (reordering) {
                                // Arrows to move this note; the top note cannot go up, the last cannot go down.
                                if (canMoveUp) {
                                    ArrowButton(
                                        R.drawable.ic_arrow_up, textColor, LocalContext.current.getString(R.string.move_up),
                                        actionRunCallback<MoveNoteAction>(
                                            actionParametersOf(WidgetIdKey to appWidgetId, NoteIdKey to note.id, DeltaKey to -1)
                                        ),
                                    )
                                }
                                if (canMoveDown) {
                                    ArrowButton(
                                        R.drawable.ic_arrow_down, textColor, LocalContext.current.getString(R.string.move_down),
                                        actionRunCallback<MoveNoteAction>(
                                            actionParametersOf(WidgetIdKey to appWidgetId, NoteIdKey to note.id, DeltaKey to 1)
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    if (folded) {
                        // Folded: the title is all there is; a note without one shows its first line instead.
                        val first = if (note.title.isBlank() && !isPrivate) {
                            (if (note.isList) note.items.firstOrNull { it.text.isNotBlank() }?.text else note.text.lineSequence().firstOrNull { it.isNotBlank() }).orEmpty()
                        } else ""
                        if (first.isNotBlank()) {
                            Text(first, maxLines = 1, style = TextStyle(color = palette.provider(0.7f), fontSize = settings.textSizeSp.sp))
                        } else if (!hasTitleRow) {
                            androidx.glance.layout.Spacer(GlanceModifier.height(22.dp))
                        }
                    } else if (isPrivate) {
                        PrivateHint(settings, palette)
                    } else {
                        LabelsLine(note, settings, palette)
                        ReminderLine(note, account, settings, palette)
                        if (thumb == null && note.hasNoContent()) EmptyNoteHint(settings, palette)
                        if (note.isList) {
                            val collapsed = collapsedByNote[CollapseStore.key(account, note.id)] ?: settings.collapseChecked
                            ChecklistBody(note, account, settings, palette, collapsed, rowLimit = settings.maxLines)
                        } else if (note.text.isNotBlank()) {
                            Text(
                                note.text,
                                maxLines = if (settings.maxLines <= 0) Int.MAX_VALUE else settings.maxLines,
                                style = TextStyle(color = textColor, fontSize = settings.textSizeSp.sp),
                            )
                        }
                        EditedTime(note, settings, palette)
                    }
                }
                if (settings.foldable || cornerExtra > 0) {
                    Box(GlanceModifier.fillMaxWidth().padding(top = 2.dp, end = 2.dp), contentAlignment = Alignment.TopEnd) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!hasTitleRow) {
                                if (!isPrivate) ProgressIndicator(note, settings, palette)
                                if (pinned) PinIndicator(settings, palette)
                            }
                            if (settings.foldable) {
                                // The same arrows as in the app: down when open, left when folded.
                                ArrowButton(
                                    if (folded) R.drawable.ic_arrow_left else R.drawable.ic_arrow_down, textColor,
                                    LocalContext.current.getString(if (folded) R.string.unfold_all else R.string.fold_all),
                                    actionRunCallback<ToggleFoldAction>(
                                        actionParametersOf(NoteIdKey to note.id, AccountKey to account, CollapsedKey to !folded)
                                    ),
                                )
                            }
                        }
                    }
                }
              }
            }
        }
    }
}

private const val IMAGE_BUDGET_BYTES = 9 * 1024 * 1024
private const val PICTURE_BUDGET_BYTES = 4 * 1024 * 1024

class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Refresh the cache whenever the widget is (re)created, e.g. after an app update or reboot.
        SyncPolicy.request(context, SyncPolicy.Reason.WIDGET)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { SettingsStore.deleteWidget(context, it) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SyncWorker.schedule(context)
    }
}
