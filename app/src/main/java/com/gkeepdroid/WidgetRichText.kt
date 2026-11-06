package com.gkeepdroid

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle

/** The style of a text in a widget with the bold, italic, underline, strike and heading of [flags] added. */
internal fun TextStyle.withFlags(flags: Int, sizeSp: Int): TextStyle {
    if (flags == 0) return this
    val decoration = listOfNotNull(
        TextDecoration.Underline.takeIf { flags and RichNotes.UNDERLINE != 0 },
        TextDecoration.LineThrough.takeIf { flags and RichNotes.STRIKE != 0 },
    )
    return copy(
        fontWeight = if (flags and (RichNotes.BOLD or RichNotes.HEADING) != 0) FontWeight.Bold else fontWeight,
        fontStyle = if (flags and RichNotes.ITALIC != 0) FontStyle.Italic else fontStyle,
        textDecoration = if (decoration.isEmpty()) textDecoration else TextDecoration.combine(decoration),
        fontSize = if (flags and RichNotes.HEADING != 0) (sizeSp * 1.25f).sp else fontSize,
    )
}

/**
 * The body of a note in a widget. A widget's text has no styles inside a line, so lines that are bold, italic, underlined,
 * struck through or headings as a whole are shown so, and neighboring lines of one style share a text.
 */
@Composable
internal fun RichBody(note: KeepNote, account: String, base: TextStyle, sizeSp: Int, maxLines: Int, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val doc = RichNotes.of(context, account, note.id)?.docs?.values?.firstOrNull()
    val lines = note.text.split("\n")
    var start = 0
    val groups = mutableListOf<Pair<String, Int>>()
    lines.forEach { line ->
        val flags = RichNotes.uniformFlags(doc, note.text, start, start + line.length)
        start += line.length + 1
        val last = groups.lastOrNull()
        if (last != null && last.second == flags) groups[groups.lastIndex] = (last.first + "\n" + line) to flags else groups += line to flags
    }
    var left = if (maxLines <= 0) Int.MAX_VALUE else maxLines
    groups.forEach { (text, flags) ->
        if (left <= 0) return@forEach
        Text(text, maxLines = left, style = base.withFlags(flags, sizeSp), modifier = modifier)
        left -= text.count { it == '\n' } + 1
    }
}
