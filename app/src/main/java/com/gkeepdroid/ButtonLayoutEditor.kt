package com.gkeepdroid

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One button of the bar as the layout editor shows it; [side] is null while the button is off. */
data class LayoutButton(val id: String, @DrawableRes val icon: Int, @StringRes val label: Int, val side: Int?, val slot: Int = ALIGN_CENTER)

private const val OFF_ZONE = -1

/**
 * A miniature of the widget: the four edges are the bars, each with a start, a middle and an end place, and the middle lists
 * the buttons that are off. Built on [ButtonEditor]; a place is [side] * 10 + slot.
 */
@Composable
fun ButtonLayoutEditor(
    buttons: List<LayoutButton>,
    /** A button was dropped on [side] at [slot] (side null: hidden), in front of [beforeId] or at the end if that is null. */
    onMove: (id: String, side: Int?, slot: Int, beforeId: String?) -> Unit,
) {
    val items = buttons.map { b -> ButtonItem(b.id, b.icon, b.label, b.side?.let { it * 10 + b.slot } ?: OFF_ZONE) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        ButtonEditor(items, { id, zone, before ->
            if (zone == OFF_ZONE) onMove(id, null, ALIGN_CENTER, before) else onMove(id, zone / 10, zone % 10, before)
        }) {
            // The editor has a fixed height. Icons shrink with the number of buttons on a side, so none is ever cut off.
            val middleHeight = 170.dp
            fun count(side: Int) = buttons.count { it.side == side }
            fun fit(length: Dp, n: Int): Dp = ((length - 8.dp - 3.dp * (n - 1).coerceAtLeast(0)) / n.coerceAtLeast(1))
            val chipSize = minOf(
                34.dp,
                fit(maxWidth, maxOf(count(POS_TOP), count(POS_BOTTOM))),
                fit(middleHeight, maxOf(count(POS_LEFT), count(POS_RIGHT))),
            ).coerceAtLeast(16.dp)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Bar(buttons, POS_TOP, chipSize, Modifier.fillMaxWidth().height(40.dp))
                Row(Modifier.fillMaxWidth().height(middleHeight), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Bar(buttons, POS_LEFT, chipSize, Modifier.width(40.dp).fillMaxHeight())
                    Zone(OFF_ZONE, Modifier.weight(1f).fillMaxHeight(), chipSize = chipSize, contentAlignment = Alignment.Center, scrollable = true)
                    Bar(buttons, POS_RIGHT, chipSize, Modifier.width(40.dp).fillMaxHeight())
                }
                Bar(buttons, POS_BOTTOM, chipSize, Modifier.fillMaxWidth().height(40.dp))
            }
        }
    }
}

/**
 * One edge: three places (start, middle, end). A place grows with the buttons in it, and equal outer places keep the middle
 * one centered, as in the widget.
 */
@Composable
private fun ButtonEditorScope.Bar(buttons: List<LayoutButton>, side: Int, chipSize: Dp, modifier: Modifier) {
    val vertical = side == POS_LEFT || side == POS_RIGHT
    fun regionWeight(slot: Int) = maxOf(1f, 0.5f * buttons.count { it.side == side && it.slot == slot } + 0.5f)
    val outer = maxOf(regionWeight(ALIGN_START), regionWeight(ALIGN_END))
    val middle = regionWeight(ALIGN_CENTER)
    if (vertical) Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Zone(side * 10 + ALIGN_START, Modifier.weight(outer).fillMaxWidth(), vertical = true, chipSize = chipSize, contentAlignment = Alignment.TopCenter)
        Zone(side * 10 + ALIGN_CENTER, Modifier.weight(middle).fillMaxWidth(), vertical = true, chipSize = chipSize, contentAlignment = Alignment.Center)
        Zone(side * 10 + ALIGN_END, Modifier.weight(outer).fillMaxWidth(), vertical = true, chipSize = chipSize, contentAlignment = Alignment.BottomCenter)
    } else Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Zone(side * 10 + ALIGN_START, Modifier.weight(outer).fillMaxHeight(), chipSize = chipSize, contentAlignment = Alignment.CenterStart)
        Zone(side * 10 + ALIGN_CENTER, Modifier.weight(middle).fillMaxHeight(), chipSize = chipSize, contentAlignment = Alignment.Center)
        Zone(side * 10 + ALIGN_END, Modifier.weight(outer).fillMaxHeight(), chipSize = chipSize, contentAlignment = Alignment.CenterEnd)
    }
}
