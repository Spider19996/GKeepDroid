package com.gkeepdroid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Leaves the quantity out of what an item's field shows (it stands in the pill beside it), on top of [base]. The cursor
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

/**
 * The number of an item in the editor, pinned to the top right corner of its button (as a count on an app symbol): small, in the
 * accent color, with the number alone. A tap on it opens the [QuantityStepper].
 */
@Composable
internal fun QuantityTag(count: Int, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp),
    ) {
        Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** − count + in a floating pill: counts an item up and down; 1 takes the number out of the text again. */
@Composable
internal fun QuantityStepper(count: Int, onChange: (Int) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(count - 1) }, enabled = count > 1) {
                Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.quantity_less))
            }
            Text(
                "$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.defaultMinSize(minWidth = 28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { onChange(count + 1) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.quantity_more))
            }
        }
    }
}
