package com.gkeepdroid

import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// The settings pages of the notes screen of the app and of the editor (the pages that are about the app, not the account).

/** Layout, order and the look of the cards. */
@Composable
internal fun ColumnScope.AppLayoutPage(s: SettingsState) {
    with(s) {
        Text(stringResource(R.string.layout_label), Modifier.searchFlash(R.string.layout_label), style = MaterialTheme.typography.titleMedium)
        ChoiceGroup(
            listOf(LAYOUT_GRID to stringResource(R.string.layout_grid), LAYOUT_LIST to stringResource(R.string.layout_list)),
            appLayout,
        ) { appLayout = it }

        Text(stringResource(R.string.sort_by), Modifier.searchFlash(R.string.sort_by), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SORT_KEEP to R.string.sort_keep,
                SORT_EDITED to R.string.sort_edited,
                SORT_CREATED to R.string.sort_created,
                SORT_TITLE to R.string.sort_title,
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = appSort == mode,
                    onClick = { appSort = mode },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        SwitchRow(R.string.pinned_section_switch, appPinnedSection) { appPinnedSection = it }
        SwitchRow(R.string.pull_to_refresh_switch, appPullToRefresh) { appPullToRefresh = it }

        Text(stringResource(R.string.plain_style), Modifier.searchFlash(R.string.plain_style))
        ChoiceGroup(plainStyles(), appPlainStyle) { appPlainStyle = it }

        // 0 shows every line of a note and every item of a checklist.
        Text(
            if (appPreviewLines == 0) stringResource(R.string.max_lines_unlimited)
            else pluralStringResource(R.plurals.preview_lines, appPreviewLines, appPreviewLines),
            Modifier.searchFlash(R.string.preview_length),
        )
        ValueSlider(
            value = appPreviewLines.toFloat(), onValueChange = { appPreviewLines = it.toInt() },
            valueRange = 0f..30f, steps = 29,
        )
    }
}

/** What a card shows. */
@Composable
internal fun ColumnScope.AppCardsPage(s: SettingsState) {
    with(s) {
        SwitchRow(R.string.show_images, appShowImages) { appShowImages = it }
        SwitchRow(R.string.show_note_colors, appShowColors) { appShowColors = it }
        SwitchRow(R.string.show_edited_time, appShowEdited) { appShowEdited = it }
        if (appShowEdited) SwitchRow(R.string.edited_as_date, appEditedAsDate) { appEditedAsDate = it }
        SwitchRow(R.string.show_labels, appShowLabels) { appShowLabels = it }
        SwitchRow(R.string.show_progress, appShowProgress) { appShowProgress = it }
        SwitchRow(R.string.reminder_show_widget, appShowReminders) { appShowReminders = it }
        SwitchRow(R.string.fold_notes_app, appFoldNotes) { appFoldNotes = it }
    }
}

/** What swiping a card sideways does on the notes screen. */
@Composable
internal fun ColumnScope.AppSwipePage(s: SettingsState) {
    with(s) {
        listOf(R.string.swipe_right to true, R.string.swipe_left to false).forEach { (label, right) ->
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                swipeChoices.forEach { (action, name) ->
                    FilterChip(
                        selected = (if (right) appSwipeRight else appSwipeLeft) == action,
                        onClick = { if (right) appSwipeRight = action else appSwipeLeft = action },
                        label = { Text(stringResource(name)) },
                    )
                }
            }
        }
        Hint(R.string.swipe_hint)
    }
}

@Composable
internal fun ColumnScope.FilterBarOptions(bar: Boolean, chips: Int, onBar: (Boolean) -> Unit, onChips: (Int) -> Unit) {
    SwitchRow(R.string.filter_bar_show, bar, onBar)
    if (bar) filterChoices.forEach { (bit, label) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = chips and bit != 0, onCheckedChange = { on -> onChips(if (on) chips or bit else chips and bit.inv()) })
            Text(stringResource(label))
        }
    }
}

@Composable
internal fun ColumnScope.AppFiltersPage(s: SettingsState) {
    with(s) {
        FilterBarOptions(appFilterBar, appFilterChips, { appFilterBar = it }) { appFilterChips = it }
    }
}

/** The checklists on the cards of the app: order, hidden checked items, box style and spacing. */
@Composable
internal fun ColumnScope.AppChecklistsPage(s: SettingsState) {
    with(s) {
        SwitchRow(R.string.check_on_card, appListCheckInApp) { appListCheckInApp = it }
        if (appListCheckInApp) SwitchRow(R.string.delete_checked, appListDeleteChecked) { appListDeleteChecked = it }
        SwitchRow(R.string.checked_to_end, appListCheckedToEnd) { appListCheckedToEnd = it }
        if (appListCheckedToEnd) SwitchRow(R.string.collapse_checked, appListCollapseChecked) { appListCollapseChecked = it }
        // What the lines of a checklist start with when it is turned into a note.
        Text(stringResource(R.string.convert_prefix_title), Modifier.searchFlash(R.string.convert_prefix_title), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PREFIX_NONE to R.string.prefix_none, PREFIX_DASH to R.string.prefix_dash, PREFIX_STAR to R.string.prefix_star,
                PREFIX_BOX to R.string.prefix_box, PREFIX_NUMBER to R.string.prefix_number,
            ).forEach { (prefix, label) ->
                FilterChip(selected = appConvertPrefix == prefix, onClick = { appConvertPrefix = prefix }, label = { Text(stringResource(label)) })
            }
        }
        Hint(R.string.convert_prefix_hint)
        SwitchRow(R.string.renumber_display, appRenumberDisplay) { appRenumberDisplay = it }
        Hint(R.string.renumber_display_hint)
        SwitchRow(R.string.trim_empty_edges, appTrimEmptyEdges) { appTrimEmptyEdges = it }
        Hint(R.string.trim_empty_edges_hint)
        // What swiping an item in the editor does (and a category's header, for all its items).
        listOf(R.string.item_swipe_right to true, R.string.item_swipe_left to false).forEach { (label, right) ->
            Text(stringResource(label), Modifier.searchFlash(label), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ITEM_SWIPE_NONE to R.string.swipe_none, ITEM_SWIPE_TICK to R.string.item_swipe_tick,
                    ITEM_SWIPE_REMOVE to R.string.item_swipe_remove, ITEM_SWIPE_CATEGORY to R.string.category_move,
                ).forEach { (action, name) ->
                    FilterChip(
                        selected = (if (right) appItemSwipeRight else appItemSwipeLeft) == action,
                        onClick = { if (right) appItemSwipeRight = action else appItemSwipeLeft = action },
                        label = { Text(stringResource(name)) },
                    )
                }
            }
        }
        Hint(R.string.item_swipe_hint)
        MenuRow(stringResource(R.string.checkbox_style), null) { page = Page.AppCheckbox }
    }
}

/** How the boxes of a checklist look on the cards: style, size and spacing. */
@Composable
internal fun ColumnScope.AppCheckboxPage(s: SettingsState) {
    with(s) {
        Text("${stringResource(R.string.list_spacing)}: $appListSpacing dp", Modifier.searchFlash(R.string.list_spacing))
        ValueSlider(value = appListSpacing.toFloat(), onValueChange = { appListSpacing = it.toInt() }, valueRange = 0f..24f)
        Text(stringResource(R.string.checkbox_style), Modifier.searchFlash(R.string.checkbox_style), style = MaterialTheme.typography.titleMedium)
        ChoiceGroup(checkboxStyles(), appListCheckboxStyle) { appListCheckboxStyle = it }
        Text(stringResource(R.string.checkbox_size, appListCheckboxSize), Modifier.searchFlash(R.string.checkbox_size))
        ValueSlider(value = appListCheckboxSize.toFloat(), onValueChange = { appListCheckboxSize = it.toInt() }, valueRange = 12f..40f)
        // Live preview of both states with the chosen style, size and spacing.
        listOf(false, true).forEach { checked ->
            Row(Modifier.padding(vertical = (appListSpacing / 2).dp), verticalAlignment = Alignment.CenterVertically) {
                CheckboxPreview(appListCheckboxStyle, checked, appListCheckboxSize)
                Text(stringResource(R.string.item_hint), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Where the buttons of the note editor stand: in the bar above, the bar below, the menu, or nowhere. */
@Composable
internal fun ColumnScope.EditorButtonsPage(s: SettingsState) {
    with(s) {
        EditorLayoutEditor(appEditorLayout) { appEditorLayout = it }
        Text(stringResource(R.string.editor_align_title), Modifier.searchFlash(R.string.editor_align_title))
        ChoiceGroup(
            listOf(
                EDITOR_ALIGN_CENTER to stringResource(R.string.editor_align_center),
                EDITOR_ALIGN_TOP to stringResource(R.string.editor_align_top),
                EDITOR_ALIGN_BOTTOM to stringResource(R.string.editor_align_bottom),
            ),
            appEditorLayout.option,
        ) { appEditorLayout = appEditorLayout.copy(option = it) }
        LayoutFileButtons(
            filePrefix = "gkeepdroid-editor-layout",
            export = { appEditorLayout.toFileJson(EDITOR_LAYOUT_MARKER) },
            import = { appEditorLayout = ButtonLayout.fromFileJson(EDITOR_CATALOG, it, EDITOR_LAYOUT_MARKER) },
            resetLabel = stringResource(R.string.editor_buttons_reset),
            onReset = { appEditorLayout = EDITOR_CATALOG.layout() },
            onStatus = { status = it },
        )
    }
}

/** How the note editor behaves: its buttons (a page of their own) and what happens when it is left. */
@Composable
internal fun ColumnScope.EditorPage(s: SettingsState) {
    with(s) {
        MenuRow(stringResource(R.string.editor_buttons_title), null) { page = Page.EditorButtons }
        SwitchRow(R.string.editor_in_recents, appEditorInRecents) { appEditorInRecents = it }
        Hint(R.string.editor_in_recents_hint)
        SwitchRow(R.string.editor_bar_snap_back, appEditorBarSnapBack) { appEditorBarSnapBack = it }
        Hint(R.string.editor_bar_snap_back_hint)
    }
}
