package com.gkeepdroid

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
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
 * The body of a note in a widget. Lines that start with "> " fold into their first line, which then toggles its block; the other
 * stretches of the text get [modifier].
 */
@Composable
internal fun RichBody(
    note: KeepNote, account: String, base: TextStyle, sizeSp: Int, maxLines: Int, modifier: GlanceModifier = GlanceModifier,
    palette: TextPalette? = null,
) {
    val context = LocalContext.current
    val doc = RichNotes.of(context, account, note.id)?.docs?.values?.firstOrNull()
    val blocks = QuoteBlocks.find(note.text)
    if (blocks.isEmpty()) {
        RichText(note.text, doc, base, sizeSp, maxLines, modifier, palette)
        return
    }
    val folds = QuoteBlocks.load(context)
    var left = if (maxLines <= 0) Int.MAX_VALUE else maxLines
    QuoteBlocks.parts(note.text, blocks) { QuoteBlocks.key(account, note.id, it.key) in folds }.forEach { part ->
        if (left <= 0) return@forEach
        when (part) {
            is QuoteBlocks.Part.Plain -> {
                RichText(part.text.text, part.text.doc(doc), base, sizeSp, left, modifier, palette)
                left -= part.text.text.count { it == '\n' } + 1
            }
            is QuoteBlocks.Part.Quote -> {
                val toggle = actionRunCallback<ToggleQuoteAction>(
                    actionParametersOf(NoteIdKey to note.id, AccountKey to account, QuoteKey to part.block.key, CollapsedKey to !part.folded)
                )
                // The line at the left runs the height of the block and ends in the rounded turn of the frames' corners.
                // The first line stands on top in a link's color, underlined, a finger high, with an arrow that shows what a tap does.
                val link = android.text.SpannableString(part.head.text).apply {
                    setSpan(android.text.style.UnderlineSpan(), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                Column(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(GlanceModifier.fillMaxWidth().clickable(toggle), verticalAlignment = Alignment.CenterVertically) {
                        if (palette != null) StyledText(link, palette.link(), sizeSp.toFloat(), modifier = GlanceModifier.defaultWeight().padding(vertical = 11.dp))
                        else RichText(part.head.text, part.head.doc(doc), base, sizeSp, 0, GlanceModifier.defaultWeight().padding(vertical = 11.dp), palette)
                        Image(
                            ImageProvider(if (part.folded) R.drawable.ic_arrow_left else R.drawable.ic_arrow_down),
                            contentDescription = context.getString(if (part.folded) R.string.unfold_all else R.string.fold_all),
                            colorFilter = ColorFilter.tint(palette?.provider(0.7f) ?: ColorProvider(Color.Gray)),
                            modifier = GlanceModifier.padding(horizontal = 8.dp).size(32.dp),
                        )
                    }
                    // The other lines of an open block have the line at their left, which is the background of them (so it is as high as
                    // they are) and ends in a rounded foot, as the corners of the frames do.
                    part.body?.let {
                        Column(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.quote_bar))) {
                            RichText(it.text, it.doc(doc), base, sizeSp, (left - 1).coerceAtLeast(1), modifier.padding(start = 12.dp, bottom = 6.dp), palette)
                            // The foot is not indented: it carries on from the line at the left edge.
                            Image(ImageProvider(R.drawable.quote_foot), contentDescription = null, modifier = GlanceModifier.size(width = 12.dp, height = 11.dp))
                        }
                    }
                }
                left -= 1 + (part.body?.text?.count { it == '\n' }?.plus(1) ?: 0)
            }
        }
    }
}

/** One text of a note body with Keep's styles: see [RichBody]. */
@Composable
private fun RichText(
    text: String, doc: RichDoc?, base: TextStyle, sizeSp: Int, maxLines: Int, modifier: GlanceModifier, palette: TextPalette?,
) {
    val spanned = if (palette != null) RichNotes.spanned(text, doc) else null
    if (spanned != null) {
        StyledText(spanned, palette!!, sizeSp.toFloat(), maxLines = maxLines, modifier = modifier)
        return
    }
    val lines = text.split("\n")
    var start = 0
    val groups = mutableListOf<Pair<String, Int>>()
    lines.forEach { line ->
        val flags = RichNotes.uniformFlags(doc, text, start, start + line.length)
        start += line.length + 1
        val last = groups.lastOrNull()
        if (last != null && last.second == flags) groups[groups.lastIndex] = (last.first + "\n" + line) to flags else groups += line to flags
    }
    var left = if (maxLines <= 0) Int.MAX_VALUE else maxLines
    groups.forEach { (t, flags) ->
        if (left <= 0) return@forEach
        Text(t, maxLines = left, style = base.withFlags(flags, sizeSp), modifier = modifier)
        left -= t.count { it == '\n' } + 1
    }
}
