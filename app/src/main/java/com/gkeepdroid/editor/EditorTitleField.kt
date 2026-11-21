package com.gkeepdroid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * The title of the note. In the note's place it is the rounder field of Material 3 Expressive with a thicker line than the rest, so
 * it stands out from the items below it; [compact] is the one-line field for a bar, where the layout can put it.
 */
@Composable
internal fun EditorTitleField(model: EditorModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    var title by model::title
    val canEdit = model.canEdit
    val interaction = remember { MutableInteractionSource() }
    val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    if (compact) {
        BasicTextField(
            value = title, onValueChange = { title = it }, readOnly = !canEdit, singleLine = true,
            textStyle = style, interactionSource = interaction, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = modifier,
        ) { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.medium).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (title.isEmpty()) Text(stringResource(R.string.title_hint), style = style, color = MaterialTheme.colorScheme.outline)
                inner()
            }
        }
        return
    }
    val colors = OutlinedTextFieldDefaults.colors()
    BasicTextField(
        value = title, onValueChange = { title = it }, readOnly = !canEdit, singleLine = false,
        textStyle = style, interactionSource = interaction, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth(),
    ) { inner ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = title, innerTextField = inner, enabled = true, singleLine = false,
            visualTransformation = VisualTransformation.None, interactionSource = interaction,
            label = { Text(stringResource(R.string.title_hint)) }, colors = colors,
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = true, isError = false, interactionSource = interaction, colors = colors,
                    shape = MaterialTheme.shapes.medium, focusedBorderThickness = 3.dp, unfocusedBorderThickness = 2.dp,
                )
            },
        )
    }
}
