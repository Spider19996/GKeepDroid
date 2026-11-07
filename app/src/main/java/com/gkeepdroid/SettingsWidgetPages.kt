@file:OptIn(ExperimentalLayoutApi::class)

package com.gkeepdroid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun ColumnScope.AppearancePage(s: SettingsState) {
    with(s) {
        Text(stringResource(R.string.bg_opacity, (backgroundAlpha * 100).toInt()))
        ValueSlider(value = backgroundAlpha, onValueChange = { backgroundAlpha = it })

        Text(stringResource(R.string.text_size, textSize))
        ValueSlider(
            value = textSize.toFloat(), onValueChange = { textSize = it.toInt() },
            valueRange = 10f..24f,
        )

        SwitchRow(R.string.text_color_auto, textColorAuto) { textColorAuto = it }
        if (!textColorAuto) {
        Text(stringResource(R.string.text_color))
        // Every circle sits in a 48 dp touch area, which also sets the distance between the circles.
        FlowRow {
            TEXT_COLORS.forEach { color ->
                RoundTouchTarget(onClick = { textColor = color }) {
                    Box(
                        Modifier.size(if (color == textColor) 44.dp else 36.dp)
                            .clip(CircleShape).background(Color(color))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                }
            }
            // Opens the color picker; shows the current color when it is a custom one.
            val isCustom = textColor !in TEXT_COLORS
            RoundTouchTarget(onClick = { showColorPicker = true }) {
                Box(
                    Modifier.size(if (isCustom) 44.dp else 36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCustom) Brush.linearGradient(listOf(Color(textColor), Color(textColor)))
                            else Brush.sweepGradient(
                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                            )
                        )
                        .border(
                            if (isCustom) 3.dp else 1.dp,
                            if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) { if (!isCustom) Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = Color.White) }
            }
        }

        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.show_note_colors), Modifier.weight(1f))
            Switch(checked = showNoteColors, onCheckedChange = { showNoteColors = it })
        }
        Text(stringResource(R.string.note_opacity, (noteAlpha * 100).toInt()))
        ValueSlider(value = noteAlpha, onValueChange = { noteAlpha = it })

        Text(stringResource(R.string.plain_style))
        ChoiceGroup(plainStyles(), plainStyle) { plainStyle = it }
    }
}

@Composable
internal fun ColumnScope.NotesPage(s: SettingsState) {
    with(s) {
        if (!isSingleNote) {
            Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    SORT_KEEP to R.string.sort_keep,
                    SORT_EDITED to R.string.sort_edited,
                    SORT_CREATED to R.string.sort_created,
                    SORT_TITLE to R.string.sort_title,
                    SORT_MANUAL to R.string.sort_manual,
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { sortMode = mode },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        SwitchRow(R.string.show_images, showImages) { showImages = it }
        SwitchRow(R.string.show_edited_time, showEditedTime) { showEditedTime = it }
        SwitchRow(R.string.show_labels, showLabels) { showLabels = it }
        SwitchRow(R.string.reminder_show_widget, showReminders) { showReminders = it }
        if (!isSingleNote) {
            // Only the notes with one label: the labels of the account this widget shows.
            val labelChoices = remember(widgetAccount, accounts.size) {
                NotesCache.labels(context, CredentialStore.resolve(context, widgetAccount))
            }
            PopupRow(
                pendingPopup == "labelfilter", { pendingPopup = null },
                stringResource(R.string.label_filter),
                labelFilter.ifEmpty { stringResource(R.string.all_notes) },
                listOf(
                    MenuEntry(stringResource(R.string.all_notes), selected = labelFilter.isEmpty()) { labelFilter = "" },
                ) + labelChoices.map { name ->
                    MenuEntry(name, selected = name == labelFilter) { labelFilter = name }
                },
            )
        }
        if (!isSingleNote) SwitchRow(R.string.show_progress, showProgress) { showProgress = it }
        if (!isSingleNote) SwitchRow(R.string.fold_notes_widget, foldable) { foldable = it }
        if (!isSingleNote) {
            // 0 means unlimited. Longer notes are cut off in the list; the editor shows everything.
            Text(
                if (maxLines == 0) stringResource(R.string.max_lines_unlimited)
                else stringResource(R.string.max_lines, maxLines),
            )
            ValueSlider(
                value = maxLines.toFloat(), onValueChange = { maxLines = it.toInt() },
                valueRange = 0f..30f, steps = 29,
            )
        }
    }
}

@Composable
internal fun ColumnScope.ManageNotesPage(s: SettingsState) {
    with(s) {
        Text(
            stringResource(R.string.manage_notes_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val allNotes = remember(widgetAccount, accounts.size) {
            NotesCache.read(context, CredentialStore.resolve(context, widgetAccount))
        }
        val untitled = stringResource(R.string.untitled_note)
        if (allNotes.isEmpty()) Text(stringResource(R.string.no_notes_to_select))
        val ordered = sortNotes(allNotes, currentWidget(), includeHidden = true)
        ordered.forEachIndexed { index, note ->
            // The note's name has a line of its own; below it the switch and the arrows share one line.
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    noteLabel(note).ifBlank { untitled },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = note.id !in hiddenNotes,
                        onCheckedChange = { shown ->
                            hiddenNotes = if (shown) hiddenNotes - note.id else hiddenNotes + note.id
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalIconButton(
                        onClick = { moveNote(ordered, index, -1) },
                        enabled = index > 0,
                        modifier = Modifier.size(52.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) { Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.move_up), Modifier.size(30.dp)) }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { moveNote(ordered, index, 1) },
                        enabled = index < ordered.lastIndex,
                        modifier = Modifier.size(52.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) { Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.move_down), Modifier.size(30.dp)) }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
internal fun ColumnScope.ChecklistsPage(s: SettingsState) {
    with(s) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.check_in_widget), Modifier.weight(1f))
            Switch(checked = checkable, onCheckedChange = { checkable = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.checked_to_end), Modifier.weight(1f))
            Switch(checked = checkedToEnd, onCheckedChange = { checkedToEnd = it })
        }
        if (checkedToEnd) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.collapse_checked), Modifier.weight(1f))
                Switch(checked = collapseChecked, onCheckedChange = { collapseChecked = it })
            }
        }
        if (checkable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.delete_checked), Modifier.weight(1f))
                Switch(checked = deleteChecked, onCheckedChange = { deleteChecked = it })
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("${stringResource(R.string.list_spacing)}: $listSpacingDp dp")
        ValueSlider(
            value = listSpacingDp.toFloat(), onValueChange = { listSpacingDp = it.toInt() },
            valueRange = 0f..24f,
        )

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(stringResource(R.string.checkbox_style), style = MaterialTheme.typography.titleMedium)
        ChoiceGroup(checkboxStyles(), checkboxStyle) { checkboxStyle = it }
        Text(stringResource(R.string.checkbox_size, checkboxSizeDp))
        ValueSlider(
            value = checkboxSizeDp.toFloat(), onValueChange = { checkboxSizeDp = it.toInt() },
            valueRange = 14f..56f,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tap_whole_row), Modifier.weight(1f))
            Switch(checked = tapWholeRow, onCheckedChange = { tapWholeRow = it })
        }
        // Live preview of both states with the chosen style, size and spacing.
        listOf(false, true).forEach { checked ->
            Row(
                Modifier.padding(vertical = listSpacingDp.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckboxPreview(checkboxStyle, checked, checkboxSizeDp)
                Text(
                    stringResource(R.string.item_hint),
                    Modifier.padding(start = 12.dp),
                    fontSize = textSize.sp,
                )
            }
        }
    }
}

@Composable
internal fun ColumnScope.ButtonBarPage(s: SettingsState) {
    with(s) {
        // Which buttons this widget has depends on its kind.
        val available = buildSet {
            add(BTN_REFRESH)
            if (isSingleNote) { add(BTN_ADD_ITEM); add(BTN_REMINDER) }
            if (!isSingleNote) {
                add(BTN_NEW_NOTE); add(BTN_NEW_LIST); add(BTN_SORT); add(BTN_SEARCH)
                // Only offered while the widget can fold its notes.
                if (foldable) add(BTN_FOLD)
            }
            add(BTN_APP); add(BTN_KEEP); add(BTN_SETTINGS)
        }
        ButtonLayoutEditor(buttonLayout, available, headerSize) { id, zone, beforeId ->
            // Without the settings button the settings are harder to reach, so a dialog asks before it is switched off.
            if (id == BTN_SETTINGS && zone == BAR_HIDDEN) confirmSettingsButtonOff = true
            else buttonLayout = buttonLayout.moved(id, zone, beforeId)
        }
        Text(stringResource(R.string.header_size, headerSize))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ValueSlider(
                value = headerSize.toFloat(), onValueChange = { headerSize = it.toInt() },
                valueRange = 16f..40f,
                modifier = Modifier.weight(1f),
            )
            // The real icon in its real size; the outline is the touch area (8 dp of padding on each side).
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size((headerSize + 16).dp)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(headerSize.dp))
                }
            }
        }
        LayoutFileButtons(
            filePrefix = "gkeepdroid-layout",
            export = { Backup.exportLayout(currentWidget()) },
            import = { applyLayout(Backup.parseLayout(it, currentWidget())) },
            resetLabel = stringResource(if (layoutResetShown) R.string.layout_reset_done else R.string.layout_reset),
            onReset = { confirmLayoutReset = true },
            onStatus = { status = it },
            resetEnabled = !layoutResetShown,
        )
    }
}
