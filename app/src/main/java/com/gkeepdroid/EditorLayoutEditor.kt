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
    val items = (ZONE_TOP_LEFT..ZONE_NOTE).flatMap { zone -> layout.inZone(zone).map { ButtonItem(it.id, it.icon, it.label, zone, layout.allowedZones(it.id)) } }
    ButtonEditor(items, { id, zone, before -> onChange(layout.moved(id, zone, before)) }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Zone(ZONE_TOP_LEFT, Modifier.weight(1f))
                Zone(ZONE_TOP_RIGHT, Modifier.weight(2f))
            }
            // The note is a button of its own that stays here; buttons with a name set before or after it show above or below the note.
            Zone(ZONE_NOTE, Modifier.fillMaxWidth(), vertical = true, labeled = true)
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
