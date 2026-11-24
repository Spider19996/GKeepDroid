package com.gkeepdroid

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
 * The number of an item in the editor: a tonal pill ("2×") that opens into − and + when tapped, and closes again after a
 * moment without a tap. [count] 1 with [offer] shows a quiet "1×" to start counting.
 */
@Composable
internal fun QuantityPill(count: Int, offer: Boolean, open: Boolean, onOpen: (Boolean) -> Unit, onChange: (Int) -> Unit) {
    var lastTap by remember { mutableStateOf(0L) }
    LaunchedEffect(open, lastTap) {
        if (open) { delay(3000); onOpen(false) }
    }
    val quiet = count <= 1 && offer
    Surface(
        shape = CircleShape,
        color = if (quiet) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer,
        border = if (quiet) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = Modifier.height(32.dp),
        onClick = { if (!open) { onOpen(true); lastTap = System.currentTimeMillis() } },
    ) {
        // A plain switch that grows and shrinks: the numbers and taps always belong to the count shown now.
        Box(Modifier.animateContentSize(), contentAlignment = Alignment.Center) {
            if (!open) {
                Box(Modifier.defaultMinSize(minWidth = 40.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("$count×", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { lastTap = System.currentTimeMillis(); onChange(count - 1); if (count - 1 <= 1) onOpen(false) }, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.quantity_less), modifier = Modifier.size(18.dp))
                    }
                    Text("$count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { lastTap = System.currentTimeMillis(); onChange(count + 1) }, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.quantity_more), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** The number of an item on a card: a small tonal pill after its text, not to be tapped. */
@Composable
internal fun QuantityBadge(count: Int, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), contentColor = color, modifier = Modifier.padding(start = 6.dp)) {
        Text("$count×", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp))
    }
}
