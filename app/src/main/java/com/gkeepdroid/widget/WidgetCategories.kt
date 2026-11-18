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
import androidx.glance.text.FontWeight
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
import androidx.glance.layout.Column
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

// The categories of a checklist in the widgets: the frame (as pieces where every row is a view of its own), the header row and
// the rows of a category.
/**
 * The piece of a category's frame for one of its rows in a list where every row is a view of its own: [index] of [count] rows
 * (the header is the first).
 */
internal fun GlanceModifier.categoryFramePiece(index: Int, count: Int): GlanceModifier = background(
    ImageProvider(
        when {
            count == 1 -> R.drawable.card_border
            index == 0 -> R.drawable.category_frame_top
            index == count - 1 -> R.drawable.category_frame_bottom
            else -> R.drawable.category_frame_middle
        },
    ),
).padding(start = 8.dp, end = 6.dp, top = if (index == 0) 6.dp else 0.dp, bottom = if (index == count - 1) 6.dp else 0.dp)

/** The items of a category in a widget: open ones first when the widget moves checked items to the end, checked ones after. */
internal fun Categories.Group.rows(settings: WidgetSettings): List<KeepItem> =
    if (settings.checkedToEnd) items.filter { !it.checked } + items.filter { it.checked } else items

/**
 * The header of a category in a widget's checklist: its name and number of open items fold it, the "+" beside it adds an item
 * to it (the add-item dialog opens with the category chosen).
 */
@Composable
internal fun CategoryHeaderRow(
    note: KeepNote, account: String, name: String, progress: String, folded: Boolean, settings: WidgetSettings, palette: TextPalette,
) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().padding(top = settings.listSpacingDp.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            GlanceModifier.defaultWeight().clickable(
                actionRunCallback<ToggleCategoryAction>(
                    actionParametersOf(NoteIdKey to note.id, AccountKey to account, CategoryKey to name, CollapsedKey to !folded),
                ),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                maxLines = 1,
                // On one line with the boxes of the items below.
                style = TextStyle(color = palette.provider(0.85f), fontSize = settings.textSizeSp.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            // Where the progress of a checklist stands, and in its style: right before the arrow.
            if (settings.showProgress) Text(
                progress,
                style = TextStyle(color = palette.provider(0.7f), fontSize = (settings.textSizeSp - 1).coerceAtLeast(9).sp),
                modifier = GlanceModifier.padding(horizontal = 4.dp),
            )
        }
        // The same arrows as on the notes: down when open, left when folded.
        ArrowButton(
            if (folded) R.drawable.ic_arrow_left else R.drawable.ic_arrow_down, palette.provider(),
            context.getString(if (folded) R.string.unfold_all else R.string.fold_all),
            actionRunCallback<ToggleCategoryAction>(
                actionParametersOf(NoteIdKey to note.id, AccountKey to account, CategoryKey to name, CollapsedKey to !folded),
            ),
        )
        // The padding belongs to the box around the symbol, so the area to tap is larger than the symbol.
        Box(
            GlanceModifier.padding(horizontal = 8.dp, vertical = 2.dp).clickable(
                actionStartActivity<AddItemActivity>(actionParametersOf(AccountKey to account, NoteIdKey to note.id, CategoryKey to name)),
            ),
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_add),
                contentDescription = context.getString(R.string.category_add_item, name),
                colorFilter = ColorFilter.tint(palette.provider(0.85f)),
                modifier = GlanceModifier.size(settings.textSizeSp.dp + 4.dp),
            )
        }
    }
}
