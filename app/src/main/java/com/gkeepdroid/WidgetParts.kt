package com.gkeepdroid

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontStyle
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** Pieces shared by the list widget and the single-note widget. */

internal val NoteIdKey = ActionParameters.Key<String>(EditNoteActivity.EXTRA_NOTE_ID)
internal val AccountKey = ActionParameters.Key<String>(EditNoteActivity.EXTRA_ACCOUNT)
internal val ItemIdKey = ActionParameters.Key<String>("item_id")
internal val CheckedKey = ActionParameters.Key<Boolean>("checked")
internal val CollapsedKey = ActionParameters.Key<Boolean>("collapsed")
internal val DeleteKey = ActionParameters.Key<Boolean>("delete")
internal val DeltaKey = ActionParameters.Key<Int>("delta")
internal val WidgetIdKey = ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID)

internal val DarkNoteText = Color(0xFF202124)

/**
 * The text color of a card. With different day and night colors the widget itself switches when
 * the system theme changes, without being redrawn.
 */
internal class TextPalette(private val day: Color, private val night: Color) {
    fun provider(alpha: Float = 1f): ColorProvider =
        if (day == night) {
            ColorProvider(day.copy(alpha = alpha))
        } else {
            DayNightColorProvider(day = day.copy(alpha = alpha), night = night.copy(alpha = alpha))
        }
}

/** Notes with a color always get dark text, which suits the light note colors. */
internal fun textPalette(settings: WidgetSettings, onNoteColor: Boolean): TextPalette = when {
    onNoteColor -> TextPalette(DarkNoteText, DarkNoteText)
    settings.textColorAuto -> TextPalette(DarkNoteText, Color.White)
    else -> Color(settings.textColor).let { TextPalette(it, it) }
}

private const val KEEP_PACKAGE = "com.google.android.keep"
private const val KEEP_WEB_URL = "https://keep.google.com"

/** Opens the Google Keep app, or keep.google.com in the browser when the app is not installed. */
internal fun keepIntent(context: Context): Intent =
    context.packageManager.getLaunchIntentForPackage(KEEP_PACKAGE)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse(KEEP_WEB_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun newNoteIntent(context: Context, appWidgetId: Int, account: String?, isList: Boolean) =
    Intent(context, EditNoteActivity::class.java)
        .putExtra(EditNoteActivity.EXTRA_NEW_LIST, isList)
        .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account.orEmpty())
        // A distinct data URI keeps the PendingIntents of different buttons and widgets apart.
        .setData(Uri.parse("gkeepdroid://new/${if (isList) "list" else "note"}/$appWidgetId"))

/** Stable id of a list row; with ids the widget keeps its scroll position when it is redrawn. */
internal fun stableId(kind: String, id: String): Long = "$kind:$id".hashCode().toLong()

/**
 * Background of a note card. Always sets one: Glance reuses list item views, so leaving it out
 * can keep the previous item's color. [transparentFallback] is used for notes without a color.
 */
internal fun GlanceModifier.noteBackground(
    noteColor: Int?,
    settings: WidgetSettings,
    transparentFallback: Color = Color.Transparent,
): GlanceModifier = when {
    noteColor != null -> background(ColorProvider(Color(noteColor).copy(alpha = settings.noteAlpha)))
    settings.plainCardStyle == PLAIN_GRAY -> background(ColorProvider(Color(0xFF424242).copy(alpha = settings.noteAlpha)))
    settings.plainCardStyle == PLAIN_BORDER -> background(ImageProvider(R.drawable.card_border))
    else -> background(ColorProvider(transparentFallback))
}

/**
 * A button of the button bar: the icon with 8 dp of padding on each side as its touch target.
 */
@Composable
internal fun HeaderIcon(
    modifier: GlanceModifier, @DrawableRes icon: Int, color: ColorProvider, sizeDp: Int, action: Action, description: String,
) {
    Box(
        modifier.padding(8.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(color),
            modifier = GlanceModifier.size(sizeDp.dp),
        )
    }
}

/** A small arrow button on a note card, shown while the widget is in re-order mode. */
@Composable
internal fun ArrowButton(@DrawableRes icon: Int, color: ColorProvider, description: String, action: Action) {
    Box(GlanceModifier.padding(horizontal = 6.dp, vertical = 2.dp).clickable(action)) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(color),
            modifier = GlanceModifier.size(24.dp),
        )
    }
}

/** The small pin on a note that is pinned in Keep or shown at the bottom of the widget. */
@Composable
internal fun PinIndicator(settings: WidgetSettings, palette: TextPalette) {
    Image(
        provider = ImageProvider(R.drawable.ic_push_pin),
        contentDescription = LocalContext.current.getString(R.string.pinned_section),
        colorFilter = ColorFilter.tint(palette.provider()),
        modifier = GlanceModifier.size((settings.textSizeSp + 2).dp).padding(start = 6.dp),
    )
}

/** Marks a note whose content is hidden in the widgets. */
@Composable
internal fun LockIndicator(settings: WidgetSettings, palette: TextPalette) {
    Image(
        provider = ImageProvider(R.drawable.ic_lock),
        contentDescription = LocalContext.current.getString(R.string.note_private_hint),
        colorFilter = ColorFilter.tint(palette.provider()),
        modifier = GlanceModifier.size((settings.textSizeSp + 2).dp).padding(start = 6.dp),
    )
}

/** Shown instead of the content of a note that is hidden in the widgets. */
@Composable
internal fun PrivateHint(settings: WidgetSettings, palette: TextPalette) {
    val context = LocalContext.current
    Text(
        context.getString(R.string.note_private_hint),
        style = TextStyle(color = palette.provider(0.65f), fontSize = settings.textSizeSp.sp, fontStyle = FontStyle.Italic),
    )
}

/** "3/7": ticked items of a checklist out of all of them, or null if there is nothing to show. */
internal fun progressText(note: KeepNote): String? =
    if (note.isList && note.items.isNotEmpty()) "${note.items.count { it.checked }}/${note.items.size}" else null

/** The checklist progress in a note's title line. */
@Composable
internal fun ProgressIndicator(note: KeepNote, settings: WidgetSettings, palette: TextPalette) {
    val progress = progressText(note)?.takeIf { settings.showProgress } ?: return
    Text(
        progress,
        style = TextStyle(color = palette.provider(0.7f), fontSize = (settings.textSizeSp - 1).coerceAtLeast(9).sp),
        modifier = GlanceModifier.padding(horizontal = 4.dp),
    )
}

/** The labels of a note on one line, when the widget is set to show them. */
@Composable
internal fun LabelsLine(note: KeepNote, settings: WidgetSettings, palette: TextPalette) {
    if (!settings.showLabels || note.labels.isEmpty()) return
    Text(
        note.labels.joinToString("  ·  "),
        style = TextStyle(color = palette.provider(0.7f), fontSize = (settings.textSizeSp - 2).coerceAtLeast(9).sp),
        modifier = GlanceModifier.padding(bottom = 2.dp),
    )
}

/** The next reminder of a note, with a small bell, when the widget is set to show it. */
@Composable
internal fun ReminderLine(note: KeepNote, account: String, settings: WidgetSettings, palette: TextPalette) {
    if (!settings.showReminders) return
    val context = LocalContext.current
    val reminders = Reminders.list(context, account, note.id)
    val next = reminders.firstOrNull() ?: return
    val size = (settings.textSizeSp - 2).coerceAtLeast(9)
    // A tap on the line opens the reminders of the note.
    Row(
        GlanceModifier.padding(bottom = 2.dp)
            .clickable(actionStartActivity<RemindersActivity>(actionParametersOf(AccountKey to account, NoteIdKey to note.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_alarm),
            contentDescription = context.getString(R.string.reminder),
            colorFilter = ColorFilter.tint(palette.provider(0.7f)),
            modifier = GlanceModifier.size((size + 2).dp),
        )
        Text(
            Reminders.label(context, next.at) + if (reminders.size > 1) "  (+${reminders.size - 1})" else "",
            style = TextStyle(color = palette.provider(0.7f), fontSize = size.sp),
            modifier = GlanceModifier.padding(start = 4.dp),
        )
    }
}

/** "Edited 2 days ago" below a note, when the setting is on. */
@Composable
internal fun EditedTime(note: KeepNote, settings: WidgetSettings, palette: TextPalette) {
    if (!settings.showEditedTime || note.updated <= 0) return
    val context = LocalContext.current
    val relative = DateUtils.getRelativeTimeSpanString(
        // A note edited on another device can look slightly "in the future" when the clocks differ.
        minOf(note.updated, System.currentTimeMillis()), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        // The system text starts a sentence ("Vor 47 Minuten"), but here it continues one.
    ).toString().replaceFirstChar { it.lowercase() }
    Text(
        context.getString(R.string.edited_time, relative),
        style = TextStyle(color = palette.provider(0.6f), fontSize = (settings.textSizeSp - 2).coerceAtLeast(9).sp),
        modifier = GlanceModifier.padding(top = 4.dp),
    )
}

/** The name of a button, which a screen reader speaks for its icon. */
private fun buttonLabelRes(id: String): Int = when (id) {
    BTN_REFRESH -> R.string.btn_refresh
    BTN_ADD_ITEM -> R.string.btn_add_item
    BTN_REMINDER -> R.string.reminders_title
    BTN_NEW_NOTE -> R.string.btn_new_note
    BTN_NEW_LIST -> R.string.btn_new_list
    BTN_SORT -> R.string.btn_sort
    BTN_SEARCH -> R.string.btn_search
    BTN_APP -> R.string.btn_app
    BTN_KEEP -> R.string.btn_keep
    else -> R.string.btn_settings
}

private data class BarButton(val id: String, @DrawableRes val icon: Int, val action: Action)

/** The buttons that are switched on for this widget. */
internal fun enabledButtons(settings: WidgetSettings, withNewButtons: Boolean, hasAddItem: Boolean = false): List<String> {
    val on = enabledSet(settings, withNewButtons, hasAddItem)
    return settings.orderedIds().filter { it in on }
}

private fun enabledSet(settings: WidgetSettings, withNewButtons: Boolean, hasAddItem: Boolean): Set<String> =
    settings.buttonSides.keys.filter {
        when (it) {
            BTN_ADD_ITEM -> hasAddItem
            // Only the single-note widget has one note to set a reminder for.
            BTN_REMINDER -> !withNewButtons
            BTN_NEW_NOTE, BTN_NEW_LIST, BTN_SEARCH -> withNewButtons
            // The sort button only appears with a manual order.
            BTN_SORT -> withNewButtons && settings.sortMode == SORT_MANUAL
            else -> true
        }
    }.toSet()

/** All button ids in the order the user chose; buttons never moved keep their default place at the end. */
internal fun WidgetSettings.orderedIds(): List<String> = (buttonOrder + ALL_BUTTONS).distinct().filter { it in ALL_BUTTONS }

internal fun WidgetSettings.sideOf(button: String): Int = buttonSides[button] ?: POS_TOP

internal fun hasBarAt(settings: WidgetSettings, side: Int, withNewButtons: Boolean, hasAddItem: Boolean = false): Boolean =
    enabledButtons(settings, withNewButtons, hasAddItem).any { settings.sideOf(it) == side }

/** Width the bars at the left and right edge take away from the content (icon plus its padding). */
internal fun sideBarWidth(settings: WidgetSettings, withNewButtons: Boolean, hasAddItem: Boolean = false): Dp {
    val bars = listOf(POS_LEFT, POS_RIGHT).count { hasBarAt(settings, it, withNewButtons, hasAddItem) }
    return ((settings.headerSizeSp + 16) * bars).dp
}

/**
 * One bar of the button bar: the buttons assigned to [side] (top, bottom, left or right). Every
 * button picks its own side. A bar is a row or a column, with the buttons sharing its whole length.
 */
@Composable
internal fun HeaderBar(
    side: Int,
    appWidgetId: Int,
    account: String?,
    settings: WidgetSettings,
    textColor: ColorProvider,
    withNewButtons: Boolean,
    reordering: Boolean = false,
    /** The checklist the single-note widget shows; gives it a button to add an item. */
    addItemNoteId: String? = null,
    /** The note the single-note widget shows; the reminder button is for this note. */
    reminderNoteId: String? = null,
) {
    val context = LocalContext.current
    val buttons = enabledButtons(settings, withNewButtons, addItemNoteId != null)
        .filter { settings.sideOf(it) == side }
        .map { id ->
            when (id) {
                BTN_REFRESH -> BarButton(id, R.drawable.ic_refresh, actionRunCallback<RefreshAction>())
                BTN_ADD_ITEM -> BarButton(
                    id, R.drawable.ic_add,
                    actionStartActivity<AddItemActivity>(actionParametersOf(AccountKey to account.orEmpty(), NoteIdKey to addItemNoteId.orEmpty())),
                )
                BTN_REMINDER -> BarButton(
                    id, R.drawable.ic_alarm,
                    actionStartActivity<RemindersActivity>(actionParametersOf(AccountKey to account.orEmpty(), NoteIdKey to reminderNoteId.orEmpty())),
                )
                BTN_NEW_NOTE -> BarButton(id, R.drawable.ic_add, actionStartActivity(newNoteIntent(context, appWidgetId, account, isList = false)))
                BTN_NEW_LIST -> BarButton(id, R.drawable.ic_checklist, actionStartActivity(newNoteIntent(context, appWidgetId, account, isList = true)))
                // Shows or hides the up/down arrows on the notes; a check mark means "done".
                BTN_SORT -> BarButton(
                    id, if (reordering) R.drawable.ic_done else R.drawable.ic_sort,
                    actionRunCallback<ToggleReorderAction>(actionParametersOf(WidgetIdKey to appWidgetId)),
                )
                // A widget cannot hold a text field, so the button opens the search screen.
                BTN_SEARCH -> BarButton(
                    id, R.drawable.ic_search,
                    actionStartActivity<SearchActivity>(actionParametersOf(AccountKey to account.orEmpty(), WidgetIdKey to appWidgetId)),
                )
                BTN_APP -> BarButton(id, R.drawable.ic_apps, actionStartActivity<MainActivity>())
                BTN_KEEP -> BarButton(id, R.drawable.ic_open_new, actionStartActivity(keepIntent(context)))
                else -> BarButton(
                    id, R.drawable.ic_settings,
                    actionStartActivity<SettingsActivity>(actionParametersOf(WidgetIdKey to appWidgetId)),
                )
            }
        }
    // Without any button there is no bar at all, not even an empty gap.
    if (buttons.isEmpty()) return

    val size = settings.headerSizeSp
    val vertical = side == POS_LEFT || side == POS_RIGHT
    // The buttons sit at the start, in the middle or at the end of the bar.
    fun group(slot: Int) = buttons.filter { (settings.buttonSlots[it.id] ?: ALIGN_CENTER) == slot }

    if (vertical) {
        Column(GlanceModifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            run {
                Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.Top) {
                    group(ALIGN_START).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
                Column {
                    group(ALIGN_CENTER).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
                Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.Bottom) {
                    group(ALIGN_END).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
            }
        }
    } else {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            run {
                // Equal weights on both outer groups keep the middle group centered.
                Row(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Start) {
                    group(ALIGN_START).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
                Row {
                    group(ALIGN_CENTER).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
                Row(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.End) {
                    group(ALIGN_END).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
            }
        }
    }
}

/** Lays out [body] with up to four bars around it; [bar] draws the bar of one side. */
@Composable
internal fun WithHeaderBar(
    modifier: GlanceModifier,
    bar: @Composable (side: Int) -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        bar(POS_TOP)
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            bar(POS_LEFT)
            Column(GlanceModifier.defaultWeight().fillMaxHeight(), content = body)
            bar(POS_RIGHT)
        }
        bar(POS_BOTTOM)
    }
}

@Composable
internal fun NoteImage(bitmap: Bitmap, widthDp: Dp, modifier: GlanceModifier = GlanceModifier) {
    val height = (widthDp.value * bitmap.height / bitmap.width).coerceAtMost(MAX_IMAGE_HEIGHT_DP).dp
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = LocalContext.current.getString(R.string.note_image),
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxWidth().height(height).cornerRadius(8.dp),
    )
}

private const val MAX_IMAGE_HEIGHT_DP = 420f

/** The "▾ 3 checked" line that collapses and expands the checked items of a checklist. */
@Composable
internal fun CheckedHeader(
    note: KeepNote,
    account: String,
    settings: WidgetSettings,
    palette: TextPalette,
    collapsed: Boolean,
    count: Int,
) {
    val context = LocalContext.current
    Text(
        (if (collapsed) "▸ " else "▾ ") + context.getString(R.string.checked_count, count),
        style = TextStyle(color = palette.provider(0.7f), fontSize = settings.textSizeSp.sp),
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = settings.listSpacingDp.dp).clickable(
            actionRunCallback<ToggleCollapseAction>(
                actionParametersOf(NoteIdKey to note.id, AccountKey to account, CollapsedKey to !collapsed)
            )
        ),
    )
}

private fun checkboxRes(style: Int, checked: Boolean): Int? = when (style) {
    CB_SQUARE -> if (checked) R.drawable.cb_square_on else R.drawable.cb_square_off
    CB_ROUNDED -> if (checked) R.drawable.cb_rounded_on else R.drawable.cb_rounded_off
    CB_CIRCLE -> if (checked) R.drawable.cb_circle_on else R.drawable.cb_circle_off
    else -> null
}

/**
 * One checklist row. The checkbox has its own size setting and sits in a padded box, so the
 * area to hit is larger than the drawn box; the whole row can be made tappable instead.
 */
@Composable
internal fun ItemRow(note: KeepNote, item: KeepItem, account: String, settings: WidgetSettings, palette: TextPalette) {
    val pad = settings.listSpacingDp.dp
    val itemColor = palette.provider(if (item.checked) 0.55f else 1f)
    val tick = if (settings.checkable && item.id.isNotEmpty()) {
        actionRunCallback<ToggleItemAction>(
            actionParametersOf(
                NoteIdKey to note.id, ItemIdKey to item.id, AccountKey to account, CheckedKey to !item.checked,
                // Ticking an open item deletes it when the widget is set to remove checked items.
                DeleteKey to (settings.deleteChecked && !item.checked),
            )
        )
    } else {
        null
    }
    var rowModifier = GlanceModifier.fillMaxWidth()
    if (tick != null && settings.tapWholeRow) rowModifier = rowModifier.clickable(tick)
    var boxModifier = GlanceModifier.padding(end = 12.dp, top = pad, bottom = pad)
    if (tick != null && !settings.tapWholeRow) boxModifier = boxModifier.clickable(tick)

    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Box(boxModifier) {
            val resource = checkboxRes(settings.checkboxStyle, item.checked)
            if (resource != null) {
                Image(
                    provider = ImageProvider(resource),
                    contentDescription = LocalContext.current.getString(if (item.checked) R.string.item_checked else R.string.item_unchecked),
                    colorFilter = ColorFilter.tint(itemColor),
                    modifier = GlanceModifier.size(settings.checkboxSizeDp.dp),
                )
            } else {
                Text(
                    if (item.checked) "☑" else "☐",
                    style = TextStyle(color = itemColor, fontSize = (settings.checkboxSizeDp * 0.85f).sp),
                )
            }
        }
        Text(
            item.text,
            style = TextStyle(
                color = itemColor,
                fontSize = settings.textSizeSp.sp,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            modifier = GlanceModifier.defaultWeight().padding(top = pad, bottom = pad),
        )
    }
}

/** True when there is nothing to show of a note except possibly an image. */
internal fun KeepNote.hasNoContent(): Boolean =
    title.isBlank() && (if (isList) items.isEmpty() else text.isBlank())

/** Italic hint for notes without title and text, so an empty card does not look broken. */
@Composable
internal fun EmptyNoteHint(settings: WidgetSettings, palette: TextPalette) {
    val context = LocalContext.current
    Text(
        context.getString(R.string.note_empty),
        style = TextStyle(
            color = palette.provider(0.65f),
            fontSize = settings.textSizeSp.sp,
            fontStyle = FontStyle.Italic,
        ),
    )
}

/**
 * Unchecked items first when the setting is on; the checked ones follow behind their header. A
 * [rowLimit] above 0 shows only that many open items and a "+N more" line.
 */
@Composable
internal fun ChecklistBody(
    note: KeepNote,
    account: String,
    settings: WidgetSettings,
    palette: TextPalette,
    collapsed: Boolean,
    rowLimit: Int = 0,
) {
    val context = LocalContext.current
    val unchecked = if (settings.checkedToEnd) note.items.filter { !it.checked } else note.items
    val checked = if (settings.checkedToEnd) note.items.filter { it.checked } else emptyList()
    val shown = if (rowLimit > 0) unchecked.take(rowLimit) else unchecked

    shown.forEach { ItemRow(note, it, account, settings, palette) }
    if (shown.size < unchecked.size) {
        Text(
            context.getString(R.string.items_more, unchecked.size - shown.size),
            style = TextStyle(color = palette.provider(0.65f), fontSize = settings.textSizeSp.sp),
            modifier = GlanceModifier.padding(vertical = settings.listSpacingDp.dp),
        )
    }
    AddItemRow(note, account, settings, palette)
    if (checked.isNotEmpty()) {
        CheckedHeader(note, account, settings, palette, collapsed, checked.size)
        if (!collapsed) checked.forEach { ItemRow(note, it, account, settings, palette) }
    }
}

/** The "+ Add item" row at the end of a checklist; tapping it opens the add-item dialog. */
@Composable
internal fun AddItemRow(note: KeepNote, account: String, settings: WidgetSettings, palette: TextPalette) {
    val context = LocalContext.current
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = settings.listSpacingDp.dp).clickable(
            actionStartActivity<AddItemActivity>(actionParametersOf(AccountKey to account, NoteIdKey to note.id))
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_add),
            contentDescription = LocalContext.current.getString(R.string.add_item),
            colorFilter = ColorFilter.tint(palette.provider(0.7f)),
            modifier = GlanceModifier.size(settings.checkboxSizeDp.dp).padding(end = 12.dp),
        )
        Text(
            context.getString(R.string.add_item),
            style = TextStyle(color = palette.provider(0.7f), fontSize = settings.textSizeSp.sp),
        )
    }
}

/** Redraws every placed widget of both kinds with the current notes cache and settings. */
object WidgetRefresh {
    internal val VersionKey = longPreferencesKey("version")

    suspend fun all(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val kinds = listOf(NotesWidget::class.java, SingleNoteWidget::class.java)
        kinds.forEach { kind ->
            manager.getGlanceIds(kind).forEach { id ->
                updateAppWidgetState(context, id) { it[VersionKey] = System.currentTimeMillis() }
            }
        }
        NotesWidget().updateAll(context)
        SingleNoteWidget().updateAll(context)
    }

    suspend fun hasWidgets(context: Context): Boolean {
        val manager = GlanceAppWidgetManager(context)
        return manager.getGlanceIds(NotesWidget::class.java).isNotEmpty() ||
            manager.getGlanceIds(SingleNoteWidget::class.java).isNotEmpty()
    }
}

/** Ticks or unticks a checklist item: instant local change, Keep is updated in the background. */
class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val account = parameters[AccountKey] ?: return
        val noteId = parameters[NoteIdKey] ?: return
        val itemId = parameters[ItemIdKey] ?: return
        val checked = parameters[CheckedKey] ?: return
        val delete = parameters[DeleteKey] ?: false
        // Instant local change; Keep is updated a few seconds after the last tick.
        PendingToggles.add(context, PendingToggle(account, noteId, itemId, checked, delete))
        val changed = if (delete) {
            NotesCache.removeItem(context, account, noteId, itemId)
        } else {
            NotesCache.setChecked(context, account, noteId, itemId, checked)
        }
        if (changed) WidgetRefresh.all(context)
        ToggleItemWorker.schedule(context, account)
    }
}

class ToggleCollapseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val account = parameters[AccountKey] ?: return
        val noteId = parameters[NoteIdKey] ?: return
        CollapseStore.set(context, account, noteId, parameters[CollapsedKey] ?: return)
        WidgetRefresh.all(context)
    }
}

/** Switches the arrows for re-ordering notes on or off for one widget. */
class ToggleReorderAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = parameters[WidgetIdKey] ?: return
        SettingsStore.setReordering(context, widgetId, !SettingsStore.isReordering(context, widgetId))
        WidgetRefresh.all(context)
    }
}

/** Moves a note up or down in the widget's manual order. */
class MoveNoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = parameters[WidgetIdKey] ?: return
        val noteId = parameters[NoteIdKey] ?: return
        val delta = parameters[DeltaKey] ?: return
        val settings = SettingsStore.loadWidget(context, widgetId)
        val account = CredentialStore.resolve(context, settings.account) ?: return
        val updated = reorderedSettings(NotesCache.read(context, account), settings, noteId, delta) ?: return
        SettingsStore.saveWidget(context, widgetId, updated)
        WidgetRefresh.all(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SyncWorker.syncNow(context)
    }
}
