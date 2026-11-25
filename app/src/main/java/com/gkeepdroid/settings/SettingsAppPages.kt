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
        Group(stringResource(R.string.group_order)) {
            item { ChoiceItem(R.string.layout_label, listOf(LAYOUT_GRID to stringResource(R.string.layout_grid), LAYOUT_LIST to stringResource(R.string.layout_list)), appLayout, it) { v -> appLayout = v } }
            item {
                ChoiceItem(
                    R.string.sort_by,
                    listOf(
                        SORT_KEEP to stringResource(R.string.sort_keep), SORT_EDITED to stringResource(R.string.sort_edited),
                        SORT_CREATED to stringResource(R.string.sort_created), SORT_TITLE to stringResource(R.string.sort_title),
                    ),
                    appSort, it,
                ) { v -> appSort = v }
            }
            item { SwitchItem(R.string.pinned_section_switch, appPinnedSection, it) { v -> appPinnedSection = v } }
            item { SwitchItem(R.string.widget_order_numbers_switch, appWidgetOrderNumbers, it) { v -> appWidgetOrderNumbers = v } }
            item { SwitchItem(R.string.pull_to_refresh_switch, appPullToRefresh, it) { v -> appPullToRefresh = v } }
        }
        Group(stringResource(R.string.group_look)) {
            item { ChoiceItem(R.string.plain_style, plainStyles(), appPlainStyle, it) { v -> appPlainStyle = v } }
            // 0 shows every line of a note and every item of a checklist.
            item {
                SliderItem(
                    if (appPreviewLines == 0) stringResource(R.string.max_lines_unlimited)
                    else pluralStringResource(R.plurals.preview_lines, appPreviewLines, appPreviewLines),
                    R.string.preview_length, appPreviewLines, 0..30, it, steps = 29,
                ) { v -> appPreviewLines = v }
            }
        }
    }
}

/** What a card shows. */
@Composable
internal fun ColumnScope.AppCardsPage(s: SettingsState) {
    with(s) {
        Group(stringResource(R.string.group_card_content)) {
            item { SwitchItem(R.string.show_images, appShowImages, it) { v -> appShowImages = v } }
            item { SwitchItem(R.string.show_note_colors, appShowColors, it) { v -> appShowColors = v } }
            item { SwitchItem(R.string.show_labels, appShowLabels, it) { v -> appShowLabels = v } }
            item { SwitchItem(R.string.show_progress, appShowProgress, it) { v -> appShowProgress = v } }
            item { SwitchItem(R.string.reminder_show_widget, appShowReminders, it) { v -> appShowReminders = v } }
        }
        Group(stringResource(R.string.group_card_details)) {
            item { SwitchItem(R.string.show_edited_time, appShowEdited, it) { v -> appShowEdited = v } }
            if (appShowEdited) item { SwitchItem(R.string.edited_as_date, appEditedAsDate, it) { v -> appEditedAsDate = v } }
            item { SwitchItem(R.string.fold_notes_app, appFoldNotes, it) { v -> appFoldNotes = v } }
        }
    }
}

/** What swiping a card sideways does on the notes screen. */
@Composable
internal fun ColumnScope.AppSwipePage(s: SettingsState) {
    with(s) {
        val choices = swipeChoices.map { (action, name) -> action to stringResource(name) }
        Group {
            item { ChoiceItem(R.string.swipe_right, choices, appSwipeRight, it) { v -> appSwipeRight = v } }
            item { ChoiceItem(R.string.swipe_left, choices, appSwipeLeft, it, stringResource(R.string.swipe_hint)) { v -> appSwipeLeft = v } }
        }
    }
}

@Composable
internal fun ColumnScope.FilterBarOptions(bar: Boolean, chips: Int, onBar: (Boolean) -> Unit, onChips: (Int) -> Unit) {
    Group {
        item { SwitchItem(R.string.filter_bar_show, bar, it, onChange = onBar) }
    }
    if (bar) Group(stringResource(R.string.group_filter_chips)) {
        filterChoices.forEach { (bit, label) ->
            item { SwitchItem(label, chips and bit != 0, it) { on -> onChips(if (on) chips or bit else chips and bit.inv()) } }
        }
    }
}

@Composable
internal fun ColumnScope.AppFiltersPage(s: SettingsState) {
    with(s) {
        FilterBarOptions(appFilterBar, appFilterChips, { appFilterBar = it }) { appFilterChips = it }
    }
}

/** Checklists: on the cards, in the editor, their quantities and what they turn into. */
@Composable
internal fun ColumnScope.AppChecklistsPage(s: SettingsState) {
    with(s) {
        Group(stringResource(R.string.group_on_cards)) {
            item { SwitchItem(R.string.check_on_card, appListCheckInApp, it) { v -> appListCheckInApp = v } }
            if (appListCheckInApp) item { SwitchItem(R.string.delete_checked, appListDeleteChecked, it) { v -> appListDeleteChecked = v } }
            item { SwitchItem(R.string.checked_to_end, appListCheckedToEnd, it) { v -> appListCheckedToEnd = v } }
            if (appListCheckedToEnd) item { SwitchItem(R.string.collapse_checked, appListCollapseChecked, it) { v -> appListCollapseChecked = v } }
            item { SwitchItem(R.string.renumber_display, appRenumberDisplay, it, stringResource(R.string.renumber_display_hint)) { v -> appRenumberDisplay = v } }
            item { MenuRow(stringResource(R.string.checkbox_style), null, shapes = it) { page = Page.AppCheckbox } }
        }
        // What swiping an item in the editor does (and a category's header, for all its items).
        val itemChoices = listOf(
            ITEM_SWIPE_NONE to stringResource(R.string.swipe_none), ITEM_SWIPE_TICK to stringResource(R.string.item_swipe_tick),
            ITEM_SWIPE_REMOVE to stringResource(R.string.item_swipe_remove), ITEM_SWIPE_CATEGORY to stringResource(R.string.category_move),
        )
        Group(stringResource(R.string.group_in_editor)) {
            item { ChoiceItem(R.string.item_swipe_right, itemChoices, appItemSwipeRight, it) { v -> appItemSwipeRight = v } }
            item { ChoiceItem(R.string.item_swipe_left, itemChoices, appItemSwipeLeft, it, stringResource(R.string.item_swipe_hint)) { v -> appItemSwipeLeft = v } }
            item { SwitchItem(R.string.trim_empty_edges, appTrimEmptyEdges, it, stringResource(R.string.trim_empty_edges_hint)) { v -> appTrimEmptyEdges = v } }
        }
        Group(stringResource(R.string.group_quantities)) {
            item { SwitchItem(R.string.list_quantities, appListQuantities, it, stringResource(R.string.list_quantities_hint)) { v -> appListQuantities = v } }
            if (appListQuantities) item { SwitchItem(R.string.list_quantity_one, appListQuantityOne, it) { v -> appListQuantityOne = v } }
        }
        // What the lines of a checklist start with when it is turned into a note.
        Group(stringResource(R.string.group_convert)) {
            item {
                ChoiceItem(
                    R.string.convert_prefix_title,
                    listOf(
                        PREFIX_NONE to stringResource(R.string.prefix_none), PREFIX_DASH to stringResource(R.string.prefix_dash),
                        PREFIX_STAR to stringResource(R.string.prefix_star), PREFIX_BOX to stringResource(R.string.prefix_box),
                        PREFIX_NUMBER to stringResource(R.string.prefix_number),
                    ),
                    appConvertPrefix, it, stringResource(R.string.convert_prefix_hint),
                ) { v -> appConvertPrefix = v }
            }
        }
    }
}

/** How the boxes of a checklist look on the cards: style, size and spacing, with a preview. */
@Composable
internal fun ColumnScope.AppCheckboxPage(s: SettingsState) {
    with(s) {
        Group {
            item { ChoiceItem(R.string.checkbox_style, checkboxStyles(), appListCheckboxStyle, it) { v -> appListCheckboxStyle = v } }
            item { SliderItem(stringResource(R.string.checkbox_size, appListCheckboxSize), R.string.checkbox_size, appListCheckboxSize, 12..40, it) { v -> appListCheckboxSize = v } }
            item { SliderItem("${stringResource(R.string.list_spacing)}: $appListSpacing dp", R.string.list_spacing, appListSpacing, 0..24, it) { v -> appListSpacing = v } }
            // Live preview of both states with the chosen style, size and spacing.
            item {
                PlainItem(it) {
                    listOf(false, true).forEach { checked ->
                        Row(Modifier.padding(vertical = (appListSpacing / 2).dp), verticalAlignment = Alignment.CenterVertically) {
                            CheckboxPreview(appListCheckboxStyle, checked, appListCheckboxSize)
                            Text(stringResource(R.string.item_hint), Modifier.padding(start = 8.dp))
                        }
                    }
                }
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
        Group {
            item { MenuRow(stringResource(R.string.editor_buttons_title), null, shapes = it) { page = Page.EditorButtons } }
            item { SwitchItem(R.string.editor_in_recents, appEditorInRecents, it, stringResource(R.string.editor_in_recents_hint)) { v -> appEditorInRecents = v } }
            item { SwitchItem(R.string.editor_bar_snap_back, appEditorBarSnapBack, it, stringResource(R.string.editor_bar_snap_back_hint)) { v -> appEditorBarSnapBack = v } }
        }
    }
}
