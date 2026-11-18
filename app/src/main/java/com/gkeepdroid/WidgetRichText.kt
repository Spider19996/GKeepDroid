package com.gkeepdroid

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxWidth
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
 * A text of a widget with styles inside it, as a text view of its own (Glance's text can only style a whole text). [alpha] is
 * the strength of the color, [maxLines] 0 for no limit.
 */
@Composable
internal fun StyledText(
    text: CharSequence, palette: TextPalette, sizeSp: Float, alpha: Float = 1f, maxLines: Int = 0, modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val views = android.widget.RemoteViews(context.packageName, R.layout.widget_rich_text).apply {
        setTextViewText(R.id.rich_text, text)
        setTextViewTextSize(R.id.rich_text, android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setInt(R.id.rich_text, "setMaxLines", if (maxLines > 0) maxLines else Int.MAX_VALUE)
        palette.applyTo(this, R.id.rich_text, context, alpha)
    }
    Box(modifier) { AndroidRemoteViews(views, GlanceModifier.fillMaxWidth()) }
}

/**
 * The body of a note in a widget. With [palette] and Keep's formatting at hand the styles show inside the lines too (a bold word);
 * otherwise lines that are bold, italic, underlined, struck through or headings as a whole are shown so, and neighboring lines of
 * one style share a text.
 */
@Composable
internal fun RichBody(
    note: KeepNote, account: String, base: TextStyle, sizeSp: Int, maxLines: Int, modifier: GlanceModifier = GlanceModifier,
    palette: TextPalette? = null,
) {
    val context = LocalContext.current
    val doc = RichNotes.of(context, account, note.id)?.docs?.values?.firstOrNull()
    val spanned = if (palette != null) RichNotes.spanned(note.text, doc) else null
    if (spanned != null) {
        StyledText(spanned, palette!!, sizeSp.toFloat(), maxLines = maxLines, modifier = modifier)
        return
    }
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
