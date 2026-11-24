package com.gkeepdroid

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Leaves the quantity out of what an item's field shows (it stands in the counter on the frame), on top of [base]. The cursor
 * cannot go into the number: a place inside it counts as the end of it, so typing goes into the name.
 */
internal class QuantityTransformation(private val base: VisualTransformation) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // Read from the text as it is now, so the mapping always fits it (the field can be a step ahead of the item).
        val styled = base.filter(text).text
        val found = Quantities.find(text.text)?.takeIf { styled.length == text.length } ?: return base.filter(text)
        val start = found.start
        val end = found.end
        val cut = end - start
        val shown = styled.subSequence(0, start) + styled.subSequence(end, styled.length)
        return TransformedText(shown, object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = when {
                offset <= start -> if (start == 0) 0 else offset
                offset >= end -> offset - cut
                else -> start
            }
            // A prefix: the visible start is the start of the name. A suffix: the visible end is the end of the name.
            override fun transformedToOriginal(offset: Int) = if (start == 0) (offset + cut).coerceAtMost(text.length) else offset
        })
    }
}
