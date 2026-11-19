package com.gkeepdroid

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle

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
internal fun ItemRow(note: KeepNote, item: KeepItem, account: String, settings: WidgetSettings, palette: TextPalette, inCategory: Boolean = false) {
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
        // Under a category's header the item shows without its category.
        val (shownText, doc) = RichNotes.of(context, account, note.id)?.docs?.get(item.id).let { if (inCategory) Categories.rest(item, it) else item.text to it }
        // Styles inside the item (a bold word) need a text view of their own; a plain item stays a plain text.
        val spanned = RichNotes.spanned(shownText, doc, if (item.checked) RichNotes.STRIKE else 0)
        if (spanned != null) {
            StyledText(
                spanned, palette, settings.textSizeSp.toFloat(), alpha = if (item.checked) 0.55f else 1f,
                modifier = GlanceModifier.defaultWeight().padding(top = pad, bottom = pad),
            )
            return@Row
        }
        val flags = RichNotes.uniformFlags(doc, shownText)
        Text(
            shownText,
            style = TextStyle(
                color = itemColor,
                fontSize = settings.textSizeSp.sp,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
            ).withFlags(if (item.checked) flags or RichNotes.STRIKE else flags, settings.textSizeSp),
            modifier = GlanceModifier.defaultWeight().padding(top = pad, bottom = pad),
        )
    }
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
    val groups = Categories.groups(note.items, RichNotes.of(context, account, note.id))
    if (groups != null) {
        // A checklist with categories: each under its header; checked items stay in their category.
        val folds = CategoryFolds.load(context)
        var left = if (rowLimit > 0) rowLimit else Int.MAX_VALUE
        // Some room between the note's title and its first category.
        if (groups.first().name != null) Spacer(GlanceModifier.height(4.dp))
        var hidden = 0
        groups.forEach { group ->
            // The items without a category are framed too, only without a name.
            if (group.name == null && group.rows(settings).isEmpty()) return@forEach
            // Each category in a frame of its own, with the line and rounding of the cards' border.
            // A widget's padding before a background is no margin, so the gap between two frames is a box of its own.
            Box(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(
                GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.card_border))
                    // The name as far below the frame's top as the note's title is below the card's top (8 dp, of which the
                    // header row's own top padding is the list spacing).
                    .padding(start = 12.dp, end = 4.dp, top = (8 - settings.listSpacingDp).coerceAtLeast(0).dp, bottom = 6.dp),
            ) {
                val folded = group.name != null && CategoryFolds.key(account, note.id, group.name) in folds
                if (group.name != null) CategoryHeaderRow(note, account, group.name, Categories.progress(group.items), folded, settings, palette)
                if (!folded) group.rows(settings).forEach { item -> if (left > 0) { ItemRow(note, item, account, settings, palette, inCategory = group.name != null); left-- } else hidden++ }
            } }
        }
        if (hidden > 0) {
            Text(
                context.resources.getQuantityString(R.plurals.items_more, hidden, hidden),
                style = TextStyle(color = palette.provider(0.65f), fontSize = settings.textSizeSp.sp),
                modifier = GlanceModifier.padding(vertical = settings.listSpacingDp.dp),
            )
        }
        AddItemRow(note, account, settings, palette)
        return
    }
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
