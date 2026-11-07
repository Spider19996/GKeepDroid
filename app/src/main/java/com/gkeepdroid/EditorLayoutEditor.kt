package com.gkeepdroid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A miniature of the note editor: the top bar, the note, the bottom bar, the menu and a place for hidden buttons. Built on
 * [ButtonEditor].
 */
@Composable
internal fun EditorLayoutEditor(layout: EditorLayout, onChange: (EditorLayout) -> Unit) {
    val items = (ZONE_TOP_LEFT..ZONE_CONTENT_BOTTOM).flatMap { zone -> layout.inZone(zone).map { ButtonItem(it.id, it.icon, it.label, zone) } }
    ButtonEditor(items, { id, zone, before -> onChange(layout.moved(id, zone, before)) }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Zone(ZONE_TOP_LEFT, Modifier.weight(1f))
                Zone(ZONE_TOP_RIGHT, Modifier.weight(2f))
            }
            // The note with the two rows that sit in its column.
            Column(
                Modifier.fillMaxWidth().border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.medium).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.editor_zone_content_top), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Zone(ZONE_CONTENT_TOP, Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.editor_buttons_note), Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
                Text(stringResource(R.string.editor_zone_content_bottom), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Zone(ZONE_CONTENT_BOTTOM, Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Zone(ZONE_BOTTOM_LEFT, Modifier.weight(1f))
                Zone(ZONE_BOTTOM_RIGHT, Modifier.weight(1f))
            }
            Text(stringResource(R.string.editor_zone_menu), style = MaterialTheme.typography.titleSmall)
            Zone(ZONE_MENU, Modifier.fillMaxWidth(), labeled = true)
            Text(stringResource(R.string.editor_zone_hidden), style = MaterialTheme.typography.titleSmall)
            Zone(ZONE_HIDDEN, Modifier.fillMaxWidth(), labeled = true)
        }
    }
}
