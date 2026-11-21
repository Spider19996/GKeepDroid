package com.gkeepdroid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shows a note's blocks of "> " lines as the app shows them elsewhere while the text field's text stays what it is: no ">", the lines
 * set in by spaces, a folded block as its first line and a mark. [flags] puts Keep's styles on the text as well.
 */
class QuoteTransformation(private val view: QuoteBlocks.View, private val flags: DocFlags?, private val linkColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val folded = view.folded
        val doc = flags?.takeIf { it.flags.size == text.length }?.let { RichDoc(folded.text, folded.runs(it.runs())) }
        val styled = if (doc != null) RichNotes.style(AnnotatedString(folded.text), doc) else AnnotatedString(folded.text)
        // The first line of a block, the one that stays when it is folded, is a link's color and underlined, as on the cards.
        val shown = buildAnnotatedString {
            append(styled)
            folded.heads.forEach {
                addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), it.start, it.end)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = folded.toFolded(offset).coerceIn(0, folded.text.length)
            override fun transformedToOriginal(offset: Int) = folded.toOriginal(offset).coerceIn(0, text.length)
        }
        return TransformedText(shown, mapping)
    }
}
