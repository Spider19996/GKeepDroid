package com.gkeepdroid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A whole category swiped: every piece of it (header, items, drop place) moves by [offset], on a band in the swipe's color that
 * has the frame's rounding at its ends, and the symbol sits at the header. Nothing is drawn while [offset] is 0.
 */
@Composable
internal fun CategorySwipeBand(
    offset: Float, action: Int, frame: Int, topInset: Dp, category: String, index: Int,
    bounds: SnapshotStateMap<String, Pair<Float, Float>>, content: @Composable () -> Unit,
) {
    val (color, icon, tint) = swipeLook(action)
    val surface = MaterialTheme.colorScheme.background
    val top = frame == FRAME_FIRST || frame == FRAME_ONLY
    val bottom = frame == FRAME_LAST || frame == FRAME_ONLY
    val active = offset != 0f && frame != FRAME_NONE
    // The same structure while swiping or not, so the rows (and the header's gesture) are not made anew when it starts.
    Box(
        // The header's piece is drawn above the pieces below it, so its symbol (in the middle of the category) is not covered by them.
        Modifier.zIndex(if (active && top) 1f else 0f).onGloballyPositioned {
            val b = it.boundsInRoot()
            val now = b.top to b.bottom
            if (bounds["$category#$index"] != now) bounds["$category#$index"] = now
        }.then(
            if (!active) Modifier else Modifier.drawBehind {
                // The band is the frame's place less the frame where it is now, both on the line's centre path: there is nothing
                // to cover (so no seam or sliver beside the line), and the line itself lies over the band's edge.
                val half = 0.5.dp.toPx()
                val r = FRAME_RADIUS.toPx()
                val from = if (top) topInset.toPx() + half else 0f
                val to = if (bottom) size.height - half else size.height + ROW_GAP.toPx()
                // The frame that is cut out is a hair smaller than the line's centre path, so the band reaches under the line (which is
                // 1 dp thick) and the antialiasing of its arcs, which meet the line at a very shallow angle at the top, leaves no gap.
                val e = 0.5.dp.toPx()
                fun shape(dx: Float, grow: Float): Path {
                    val zero = CornerRadius.Zero
                    val round = CornerRadius(r - grow)
                    return Path().apply {
                        addRoundRect(
                            RoundRect(
                                half + dx + grow, if (top) from + grow else from, size.width - half + dx - grow, if (bottom) to - grow else to,
                                topLeftCornerRadius = if (top) round else zero, topRightCornerRadius = if (top) round else zero,
                                bottomLeftCornerRadius = if (bottom) round else zero, bottomRightCornerRadius = if (bottom) round else zero,
                            ),
                        )
                    }
                }
                val band = Path.combine(PathOperation.Difference, shape(0f, 0f), shape(offset, e))
                // Only as far as the arcs reach: what is left of the shrunk frame along the top (over the title's gap) is not drawn.
                val reach = 14.dp.toPx()
                clipRect(
                    if (offset > 0f) 0f else size.width + offset - half - reach, -100000f,
                    if (offset > 0f) offset + half + reach else size.width, 100000f,
                ) { drawPath(band, color) }
            },
        ),
    ) {
        // The symbol sits at the header, on the side the swipe comes from.
        if (active && top) {
            // The symbol in the middle of the whole category (its pieces report where they are), not of the header's piece.
            val mine = bounds["$category#$index"]
            val all = bounds.filterKeys { it.startsWith("$category#") }.values
            val middle = if (all.isEmpty()) null else (all.minOf { it.first } + with(LocalDensity.current) { topInset.toPx() } + all.maxOf { it.second }) / 2f
            // Only the strip the category has left shows it: it lies under the moving rows, not on top of them.
            Box(
                Modifier.matchParentSize().drawWithContent {
                    val from = if (offset > 0f) 0f else size.width + offset
                    val to = if (offset > 0f) offset else size.width
                    clipRect(from, -100000f, to, 100000f) { this@drawWithContent.drawContent() }
                }.padding(start = 20.dp, end = 20.dp), contentAlignment = if (offset > 0f) Alignment.TopStart else Alignment.TopEnd) {
                Icon(
                    painterResource(icon), contentDescription = null, tint = tint,
                    modifier = Modifier.wrapContentSize(if (offset > 0f) Alignment.TopStart else Alignment.TopEnd, unbounded = true).size(24.dp).offset { IntOffset(0, if (middle != null && mine != null) (middle - mine.first - 12.dp.toPx()).toInt() else topInset.roundToPx() + 4.dp.roundToPx()) },
                )
            }
        }
        Box(
            Modifier.graphicsLayer { translationX = if (active) offset else 0f },
        ) { content() }
    }
}
