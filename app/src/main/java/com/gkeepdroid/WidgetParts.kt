package com.gkeepdroid

import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
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

private const val KEEP_PACKAGE = "com.google.android.keep"
private const val KEEP_WEB_URL = "https://keep.google.com"

/** Opens the Google Keep app, or keep.google.com in the browser when the app is not installed. */
internal fun keepIntent(context: Context): Intent =
    context.packageManager.getLaunchIntentForPackage(KEEP_PACKAGE)
        ?: Intent(Intent.ACTION_VIEW, KEEP_WEB_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun newNoteIntent(context: Context, appWidgetId: Int, account: String?, isList: Boolean) =
    Intent(context, EditNoteActivity::class.java)
        .putExtra(EditNoteActivity.EXTRA_NEW_LIST, isList)
        .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account.orEmpty())
        // A distinct data URI keeps the PendingIntents of different buttons and widgets apart.
        .setData(("gkeepdroid://new/${if (isList) "list" else "note"}/$appWidgetId").toUri())

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
    picture: android.graphics.Bitmap? = null,
): GlanceModifier = when {
    picture != null -> background(ImageProvider(picture), androidx.glance.layout.ContentScale.Crop)
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
    note.items.filter { !it.isDivider() }.let { real ->
        if (note.isList && real.isNotEmpty()) "${real.count { it.checked }}/${real.size}" else null
    }

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

@Composable
internal fun NoteImage(bitmap: Bitmap, widthDp: Dp, modifier: GlanceModifier = GlanceModifier) {
    val height = (widthDp.value * bitmap.height / bitmap.width).coerceAtMost(MAX_IMAGE_HEIGHT_DP).dp
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = LocalContext.current.getString(R.string.note_image),
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxWidth().height(height).cornerRadius(R.dimen.widget_inner_corner),
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
        (if (collapsed) "▸ " else "▾ ") + context.resources.getQuantityString(R.plurals.checked_count, count, count),
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
    val context = androidx.glance.LocalContext.current
    val pad = settings.listSpacingDp.dp
    if (item.isDivider()) {
        // A line instead of a check box and text; a tap on it does nothing.
        Box(GlanceModifier.fillMaxWidth().padding(vertical = pad + 4.dp)) {
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(palette.provider(0.4f))) {}
        }
        return
    }
    val itemColor = palette.provider(if (item.checked) 0.55f else 1f)
    val tick = if (settings.checkable && item.id.isNotEmpty()) {
        val parameters = actionParametersOf(
            NoteIdKey to note.id, ItemIdKey to item.id, AccountKey to account, CheckedKey to !item.checked,
            // Ticking an open item deletes it when the widget is set to remove checked items.
            DeleteKey to (settings.deleteChecked && !item.checked),
        )
        // With the edit lock on, a small window asks for the lock first.
        if (AppLock.editLockOn(androidx.glance.LocalContext.current)) actionStartActivity<UnlockTickActivity>(parameters)
        else actionRunCallback<ToggleItemAction>(parameters)
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
        val flags = RichNotes.uniformFlags(RichNotes.of(context, account, note.id)?.docs?.get(item.id), item.text)
        Text(
            item.text,
            style = TextStyle(
                color = itemColor,
                fontSize = settings.textSizeSp.sp,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
            ).withFlags(if (item.checked) flags or RichNotes.STRIKE else flags, settings.textSizeSp),
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
    // With the setting on, a numbered list counts again in the order shown here.
    val renumber = AppSettingsStore.load(context).renumberDisplay

    ListNumbering.display(note.items, shown, renumber).forEach { ItemRow(note, it, account, settings, palette) }
    if (shown.size < unchecked.size) {
        Text(
            context.resources.getQuantityString(R.plurals.items_more, unchecked.size - shown.size, unchecked.size - shown.size),
            style = TextStyle(color = palette.provider(0.65f), fontSize = settings.textSizeSp.sp),
            modifier = GlanceModifier.padding(vertical = settings.listSpacingDp.dp),
        )
    }
    AddItemRow(note, account, settings, palette)
    if (checked.isNotEmpty()) {
        CheckedHeader(note, account, settings, palette, collapsed, checked.size)
        if (!collapsed) ListNumbering.display(note.items, checked, renumber, startAt = shown.size + 1).forEach { ItemRow(note, it, account, settings, palette) }
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
