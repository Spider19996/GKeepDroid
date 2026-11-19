package com.gkeepdroid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Shown at the end of a checklist while an item is held: letting it go here puts it into a new category. */
@Composable
internal fun NewCategoryDropZone(active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.medium)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .border(if (active) 2.dp else 1.dp, color, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = color)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.category_new), color = color, style = MaterialTheme.typography.titleSmall)
    }
}

/** A category to choose; held, [onLongClick] runs (an earlier category turns red, held again it is forgotten). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CategoryChip(name: String, armed: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val color = if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    Box(
        Modifier.clip(MaterialTheme.shapes.small)
            .background(if (armed) MaterialTheme.colorScheme.errorContainer else Color.Transparent)
            .border(1.dp, color, MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = if (armed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
    }
}

/** A place to drop a held item into a category that has nothing else in it; [label] names it where no header does. */
@Composable
internal fun CategoryDropSlot(label: String?, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier.fillMaxWidth().padding(top = 4.dp).height(56.dp).clip(MaterialTheme.shapes.medium)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .border(if (active) 2.dp else 1.dp, color, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) Text(label, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
    }
}

/** Where a held category would be put: a line in the primary color. */
@Composable
internal fun DropLine() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
}
