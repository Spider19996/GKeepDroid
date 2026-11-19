package com.gkeepdroid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** The header of a category in a checklist: its name, how many of its items are open, and the arrow that folds it. */
@Composable
internal fun CategoryHeader(name: String, progress: String?, folded: Boolean, color: Color, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name, color = color, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        // The progress and the arrow as the note's own: the same style and size, the arrow exactly below the note's.
        if (progress != null) Text(progress, color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        Box(Modifier.size(width = 36.dp, height = 28.dp), contentAlignment = Alignment.CenterEnd) {
            Icon(
                painterResource(if (folded) R.drawable.ic_arrow_left else R.drawable.ic_arrow_down),
                contentDescription = stringResource(if (folded) R.string.unfold_all else R.string.fold_all),
                tint = color, modifier = Modifier.padding(end = 6.dp).size(24.dp),
            )
        }
    }
}

/** The symbol and its description for what swiping a card does; null for no action. */
internal fun cardSwipeSymbol(kind: Int): Pair<Int, Int>? = when (kind) {
    SWIPE_ARCHIVE -> R.drawable.ic_archive to R.string.archive_note
    SWIPE_TRASH -> R.drawable.ic_delete to R.string.move_to_trash
    SWIPE_PIN -> R.drawable.ic_push_pin to R.string.pin_note
    SWIPE_REMINDER -> R.drawable.ic_alarm to R.string.reminders_title
    SWIPE_SHARE -> R.drawable.ic_share to R.string.share_note
    SWIPE_HIDE -> R.drawable.ic_lock to R.string.hide_content
    else -> null
}

/**
 * A category of a card that swipes as a whole: it moves by the finger, and the strip it leaves is a band in the swipe's color with
 * its symbol, rounded like the frame. [content] gets the modifier that moves it.
 */
@Composable
internal fun CategorySwipe(
    enabled: Boolean, swipeRight: Int, swipeLeft: Int, onSwipe: (Boolean) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val swipeState = rememberSwipeState()
    val offsetX = swipeState.offset
    val canRight = enabled && swipeRight != ITEM_SWIPE_NONE
    val canLeft = enabled && swipeLeft != ITEM_SWIPE_NONE
    Box(Modifier.fillMaxWidth()) {
        if (offsetX != 0f) {
            val toRight = offsetX > 0f
            val (bg, icon, tint) = swipeLook(if (toRight) swipeRight else swipeLeft)
            // The band is what the frame has left of its place: the frame's shape where it was, less the frame where it is now,
            // so the band's inner corners follow the frame's arcs and nothing has to be covered.
            val shape = MaterialTheme.shapes.medium
            Box(
                Modifier.matchParentSize().drawWithContent {
                    val grow = 8.dp.toPx()
                    val lift = 1.dp.toPx()
                    val frameSize = Size(size.width + grow * 2, size.height + lift * 2)
                    val outline = shape.createOutline(frameSize, layoutDirection, this)
                    fun frameAt(dx: Float) = Path().apply {
                        (outline as? Outline.Rounded)?.let { addRoundRect(it.roundRect) }
                        (outline as? Outline.Generic)?.let { addPath(it.path) }
                        translate(Offset(-grow + dx, -lift))
                    }
                    val band = Path.combine(PathOperation.Difference, frameAt(0f), frameAt(offsetX))
                    drawPath(band, bg)
                    // The symbol lies under the frame like the band: only what the band shows of it is seen.
                    clipPath(band) { this@drawWithContent.drawContent() }
                },
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) { Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.padding(horizontal = 12.dp)) }
        }
        content(
            Modifier.offset { IntOffset(offsetX.toInt(), 0) }.swipeDrag(swipeState, canLeft, canRight, enabled = canRight || canLeft, onSwiped = onSwipe),
        )
    }
}
