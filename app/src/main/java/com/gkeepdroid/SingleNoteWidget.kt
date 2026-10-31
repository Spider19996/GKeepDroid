package com.gkeepdroid

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** A widget that shows one note or checklist, for example a shopping list. */
class SingleNoteWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // The real widget size is needed to scale the image to the full width.
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
        val version = currentState<Preferences>()[WidgetRefresh.VersionKey]
        val settings = remember(version) { SettingsStore.loadWidget(context, appWidgetId) }
        val account = remember(version) { CredentialStore.resolve(context, settings.account) }
        val note = remember(version) { NotesCache.read(context, account).firstOrNull { it.id == settings.noteId } }
        val collapsedByNote = remember(version) { CollapseStore.load(context) }
        val privateKeys = remember(version) { PrivateNotes.load(context) }
        val thumb = remember(version) {
            if (settings.showImages && note != null && account != null) {
                note.images.firstOrNull()?.let { NoteImages.thumbnail(context, account, it) }
            } else {
                null
            }
        }

        val noteColor = if (settings.showNoteColors && note != null) NoteColors.argb(note.color) else null
        val palette = textPalette(settings, onNoteColor = noteColor != null)
        val textColor = palette.provider()
        val settingsAction = actionStartActivity<SettingsActivity>(actionParametersOf(WidgetIdKey to appWidgetId))

        // The whole widget is the note's card: its color, or the widget background if it has none.
        WithHeaderBar(
            modifier = GlanceModifier.fillMaxSize()
                .noteBackground(noteColor, settings, Color.Black.copy(alpha = settings.backgroundAlpha))
                .cornerRadius(16.dp)
                .padding(8.dp),
            bar = { side ->
                HeaderBar(
                    side, appWidgetId, account, settings, textColor, withNewButtons = false,
                    addItemNoteId = note?.takeIf { it.isList }?.id,
                )
            },
        ) {
            when {
                account == null || settings.noteId.isEmpty() -> Text(
                    context.getString(R.string.pick_note_hint),
                    style = TextStyle(color = textColor, fontSize = settings.textSizeSp.sp),
                    modifier = GlanceModifier.defaultWeight().clickable(settingsAction),
                )
                note == null -> Text(
                    context.getString(R.string.note_gone),
                    style = TextStyle(color = textColor, fontSize = settings.textSizeSp.sp),
                    modifier = GlanceModifier.defaultWeight().clickable(settingsAction),
                )
                else -> NoteBody(
                    note, account, settings, palette, thumb, collapsedByNote,
                    isPrivate = PrivateNotes.key(account, note.id) in privateKeys,
                )
            }
        }
    }

    @Composable
    private fun ColumnScope.NoteBody(
        note: KeepNote,
        account: String,
        settings: WidgetSettings,
        palette: TextPalette,
        thumb: android.graphics.Bitmap?,
        collapsedByNote: Map<String, Boolean>,
        isPrivate: Boolean,
    ) {
        val textColor = palette.provider()
        val openEditor = actionStartActivity<EditNoteActivity>(
            actionParametersOf(NoteIdKey to note.id, AccountKey to account)
        )
        val collapsed = collapsedByNote[CollapseStore.key(account, note.id)] ?: settings.collapseChecked
        val imageWidth = (LocalSize.current.width - 16.dp - sideBarWidth(settings, withNewButtons = false, hasAddItem = note.isList)).coerceAtLeast(0.dp)
        val unchecked = if (settings.checkedToEnd) note.items.filter { !it.checked } else note.items
        val checked = if (settings.checkedToEnd) note.items.filter { it.checked } else emptyList()

        // Every row is its own list item, so a long shopping list scrolls inside the widget.
        LazyColumn(GlanceModifier.defaultWeight()) {
            if (thumb != null && !isPrivate) item(itemId = 1L) { NoteImage(thumb, imageWidth, GlanceModifier.clickable(openEditor)) }
            if (note.title.isNotBlank() || note.pinned || isPrivate || (settings.showProgress && progressText(note) != null)) {
                item(itemId = 2L) {
                    Row(
                        GlanceModifier.fillMaxWidth().padding(bottom = 4.dp).clickable(openEditor),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (note.title.isNotBlank()) {
                            Text(
                                note.title,
                                style = TextStyle(
                                    color = textColor,
                                    fontSize = (settings.textSizeSp + 4).sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                modifier = GlanceModifier.defaultWeight(),
                            )
                        } else {
                            Box(GlanceModifier.defaultWeight()) {}
                        }
                        if (isPrivate) LockIndicator(settings, palette) else ProgressIndicator(note, settings, palette)
                        if (note.pinned) PinIndicator(settings, palette)
                    }
                }
            }
            if (isPrivate) {
                item(itemId = 7L) { PrivateHint(settings, palette) }
            } else {
                item(itemId = 9L) { LabelsLine(note, settings, palette) }
                item(itemId = 10L) { ReminderLine(note, account, settings, palette) }
                if (thumb == null && note.hasNoContent()) item(itemId = 5L) { EmptyNoteHint(settings, palette) }
                if (note.isList) {
                    items(unchecked, itemId = { stableId("i", it.id) }) { ItemRow(note, it, account, settings, palette) }
                    item(itemId = 8L) { AddItemRow(note, account, settings, palette) }
                    if (checked.isNotEmpty()) {
                        item(itemId = 3L) { CheckedHeader(note, account, settings, palette, collapsed, checked.size) }
                        if (!collapsed) items(checked, itemId = { stableId("i", it.id) }) { ItemRow(note, it, account, settings, palette) }
                    }
                } else if (note.text.isNotBlank()) {
                    item(itemId = 4L) {
                        Text(
                            note.text,
                            style = TextStyle(color = textColor, fontSize = settings.textSizeSp.sp),
                            modifier = GlanceModifier.fillMaxWidth().clickable(openEditor),
                        )
                    }
                }
                item(itemId = 6L) { EditedTime(note, settings, palette) }
            }
        }
    }
}

class SingleNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleNoteWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
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
