package com.gkeepdroid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The note editor in small, as the layout would make it: the real bars and the real buttons with an example note between them,
 * placed by the position setting. Used by the preview of the editor's button settings; nothing in it does anything.
 */
@Composable
internal fun EditorPreview(state: ButtonPreviewState, align: Int) {
    val byId = EDITOR_ACTIONS.associateBy { it.id }
    val states = EDITOR_ACTIONS.associate { spec -> spec.id to EditorActionState(spec.icon, stringResource(spec.label), {}) }
    fun inZone(zone: Int) = state.items.filter { it.zone == zone }.mapNotNull { byId[it.id] }
    val noteZone = inZone(ZONE_NOTE)
    // The color and the label section are shown as a block with their name, as the sketch of what they would show.
    val inline: Map<String, @Composable (Boolean) -> Unit> = NOTE_INLINE.associateWith { id -> { hl -> InlinePlaceholder(byId.getValue(id), hl) } }
    val noteAt = noteZone.indexOfFirst { it.id == NOTE_ID }.coerceAtLeast(0)
    val highlighted = state.items.firstOrNull { it.id == state.highlightId }
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxSize().clip(shape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape).background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            EditorBar(inZone(ZONE_TOP_LEFT), inZone(ZONE_TOP_RIGHT), states, highlightId = state.highlightId)
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = when (align) {
                    EDITOR_ALIGN_TOP -> Arrangement.spacedBy(8.dp, Alignment.Top)
                    EDITOR_ALIGN_BOTTOM -> Arrangement.spacedBy(8.dp, Alignment.Bottom)
                    else -> Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                },
            ) {
                NoteActionButtons(noteZone.take(noteAt), states, state.highlightId, inline)
                ExampleNote(state.highlightId == NOTE_ID)
                NoteActionButtons(noteZone.drop(noteAt + 1), states, state.highlightId, inline)
            }
            EditorBar(inZone(ZONE_BOTTOM_LEFT), inZone(ZONE_BOTTOM_RIGHT), states, highlightId = state.highlightId)
        }
        // A button of the menu is shown in the menu, as it would be opened from the top bar.
        if (highlighted?.zone == ZONE_MENU) Column(
            Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 12.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(vertical = 6.dp),
        ) {
            inZone(ZONE_MENU).forEach { spec ->
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 5.dp).then(
                        if (spec.id == state.highlightId) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).padding(horizontal = 4.dp) else Modifier,
                    ),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(painterResource(spec.icon), null, Modifier.size(18.dp))
                    Text(stringResource(spec.label), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (highlighted?.zone == ZONE_HIDDEN) Text(
            stringResource(R.string.editor_zone_hidden), Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** A note as a sketch: the title field and a few lines, so that one sees how much room it takes. */
@Composable
private fun ExampleNote(highlighted: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().then(if (highlighted) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).padding(4.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(44.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape), contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.title_hint), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        listOf(0.9f, 0.6f, 0.75f).forEach { w ->
            Box(Modifier.fillMaxWidth().height(34.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)) {
                Box(Modifier.fillMaxWidth(w).padding(10.dp).height(10.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp)))
            }
        }
    }
}

/** A section that shows itself beside the note, as a block with its icon and name. */
@Composable
private fun InlinePlaceholder(spec: EditorActionSpec, highlighted: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .border(
                if (highlighted) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(12.dp),
            ).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(spec.icon), null, Modifier.size(20.dp))
        Text(stringResource(spec.label), style = MaterialTheme.typography.bodyMedium)
    }
}
