package com.gkeepdroid

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    /** The size of the icons in the widget, for the preview. */
    iconSizeDp: Int,
    /** A button was dropped on [side] at [slot] (side null: hidden), in front of [beforeId] or at the end if that is null. */
    onMove: (id: String, side: Int?, slot: Int, beforeId: String?) -> Unit,
) {
    val items = buttons.map { b -> ButtonItem(b.id, b.icon, b.label, b.side?.let { it * 10 + b.slot } ?: OFF_ZONE) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        ButtonEditor(items, { id, zone, before ->
            if (zone == OFF_ZONE) onMove(id, null, ALIGN_CENTER, before) else onMove(id, zone / 10, zone % 10, before)
        }, preview = { WidgetPreview(it, iconSizeDp) }) {
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
                Caption(R.string.button_bar_top)
                Bar(buttons, POS_TOP, chipSize, Modifier.fillMaxWidth().height(40.dp))
                // The names of the three places in the middle row, in the order they stand there.
                Row(Modifier.fillMaxWidth()) {
                    Caption(R.string.button_bar_left)
                    Spacer(Modifier.weight(1f))
                    Caption(R.string.editor_zone_hidden)
                    Spacer(Modifier.weight(1f))
                    Caption(R.string.button_bar_right)
                }
                Row(Modifier.fillMaxWidth().height(middleHeight), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Bar(buttons, POS_LEFT, chipSize, Modifier.width(40.dp).fillMaxHeight())
                    Zone(OFF_ZONE, Modifier.weight(1f).fillMaxHeight(), chipSize = chipSize, contentAlignment = Alignment.Center, scrollable = true)
                    Bar(buttons, POS_RIGHT, chipSize, Modifier.width(40.dp).fillMaxHeight())
                }
                Caption(R.string.button_bar_bottom)
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

/** The name of a place, as in the editor's miniature (the places of a fixed height have no room for one of their own). */
@Composable
private fun Caption(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The widget in small: the four bars with their buttons in their places and a few notes between them. The buttons have the real
 * icons in the size of the setting, so one sees how much room a bar takes; the notes are sketches.
 */
@Composable
private fun WidgetPreview(state: ButtonPreviewState, iconSizeDp: Int) {
    fun inPlace(side: Int, slot: Int) = state.items.filter { it.zone == side * 10 + slot }

    @Composable
    fun Icons(list: List<ButtonItem>) {
        list.forEach { item ->
            Box(
                Modifier.padding(4.dp).then(
                    if (item.id == state.highlightId) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier,
                ).padding(4.dp),
            ) { Icon(painterResource(item.icon), stringResource(item.label), Modifier.size(iconSizeDp.dp)) }
        }
    }

    @Composable
    fun HorizontalBar(side: Int) = Box(Modifier.fillMaxWidth()) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) { Icons(inPlace(side, ALIGN_START)) }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) { Icons(inPlace(side, ALIGN_CENTER)) }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) { Icons(inPlace(side, ALIGN_END)) }
    }

    @Composable
    fun VerticalBar(side: Int) = Box(Modifier.fillMaxHeight()) {
        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) { Icons(inPlace(side, ALIGN_START)) }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { Icons(inPlace(side, ALIGN_CENTER)) }
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) { Icons(inPlace(side, ALIGN_END)) }
    }

    val shape = RoundedCornerShape(16.dp)
    val hidden = state.items.firstOrNull { it.id == state.highlightId }?.zone == OFF_ZONE
    Box(Modifier.fillMaxSize().clip(shape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape).background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(4.dp)) {
            HorizontalBar(POS_TOP)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                VerticalBar(POS_LEFT)
                Column(Modifier.weight(1f).fillMaxHeight().padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.9f, 0.7f, 0.8f).forEach { w ->
                        Box(
                            Modifier.fillMaxWidth().height(56.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp)).padding(14.dp),
                        ) { Box(Modifier.fillMaxWidth(w).height(10.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))) }
                    }
                }
                VerticalBar(POS_RIGHT)
            }
            HorizontalBar(POS_BOTTOM)
        }
        if (hidden) Text(
            stringResource(R.string.editor_zone_hidden), Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
