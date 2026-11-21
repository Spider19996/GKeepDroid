package com.gkeepdroid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A block of "> " lines as the app shows it: no ">" signs. The first line stands on top in a link's color, underlined, with an arrow
 * that shows what a tap on it does (it folds the block); under it, when the block is open, the other lines with a thin line at
 * their left that ends in a rounded turn to the right, as the corners of the frames do.
 */
@Composable
internal fun QuoteBlockView(
    head: AnnotatedString, body: AnnotatedString?, folded: Boolean, textColor: Color, linkColor: Color, style: TextStyle, onToggle: () -> Unit,
    modifier: Modifier = Modifier, bodyMaxLines: Int = Int.MAX_VALUE,
) {
    // Room above and below, so the text around a block is not taken for its title.
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // The whole first line is the touch target; it stays close to the lines it folds.
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(head)
                    addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), 0, length)
                },
                style = style, modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(if (folded) R.drawable.ic_arrow_left else R.drawable.ic_arrow_down),
                contentDescription = stringResource(if (folded) R.string.unfold_all else R.string.fold_all),
                tint = textColor.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
            )
        }
        if (!folded && body != null) {
            // The same line as the frame of a category on the card: 1 dp in the text color at 35 %.
            val line = textColor.copy(alpha = 0.35f)
            // The line reaches 8 dp into the card's padding and the text stands 7 dp from it, as the frame of a category and its
            // text do on the same card.
            Row(
                Modifier.fillMaxWidth().layout { m, c ->
                    val grow = 8.dp.roundToPx()
                    val p = m.measure(c.copy(minWidth = (c.minWidth + grow).coerceAtMost(c.maxWidth + grow), maxWidth = c.maxWidth + grow))
                    layout(c.maxWidth, p.height) { p.place(-grow, 0) }
                }.height(IntrinsicSize.Min),
            ) {
                Canvas(Modifier.width(7.dp).fillMaxHeight()) { quoteLine(0f, size.height, size.width, line) }
                Text(body, color = textColor, style = style, maxLines = bodyMaxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(bottom = 6.dp))
            }
        }
    }
}

/**
 * The line of a block: it runs down from [top] and ends at [bottom] in a rounded turn to the right, the quarter circle of a frame's
 * corner, then a short stretch to the width [right].
 */
internal fun DrawScope.quoteLine(top: Float, bottom: Float, right: Float, color: Color, left: Float = 0f) {
    val stroke = 1.dp.toPx()
    // Flush with the left edge, where the frame of a category has its line; the text is set in 12 dp from it, as in the frame.
    val x = left + stroke / 2
    // The line's centre runs half a stroke inside the bottom, so the turn and the stretch after it lie on the same level.
    val base = bottom - stroke / 2
    val r = minOf(10.dp.toPx(), (base - top) / 2)
    val path = Path().apply {
        moveTo(x, top)
        lineTo(x, base - r)
        arcTo(Rect(x, base - 2 * r, x + 2 * r, base), 180f, -90f, false)
        // A short stretch after the turn, under the text when the room beside the line is narrower than the turn.
        lineTo(maxOf(right, x + r + 2.dp.toPx()), base)
    }
    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
}
