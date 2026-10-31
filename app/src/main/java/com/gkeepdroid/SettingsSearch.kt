package com.gkeepdroid

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp

/** The pages of the settings: a main list like the Android settings, each entry opens one page. */
internal enum class Page(val title: Int) {
    Main(R.string.app_name),
    Accounts(R.string.tab_accounts),
    Appearance(R.string.appearance),
    Notes(R.string.section_notes),
    ManageNotes(R.string.manage_notes),
    Checklists(R.string.list_section),
    ButtonBar(R.string.section_button_bar),
    AppView(R.string.app_view),
    Backup(R.string.tab_backup),
    Sync(R.string.sync_section),
    Reminders(R.string.reminders_title),
}

/** One searchable setting: its label, the page it lives on, and the popup it opens (if any). */
internal data class SettingEntry(val label: Int, val page: Page, val popup: String? = null) {
    val pageTitle: Int get() = if (popup != null) label else page.title
}

/** Strings with a value ("Text size: 14 sp") are searched and shown by the part before the colon. */
internal fun android.content.Context.settingLabel(res: Int): String =
    getString(res, 0).substringBefore(':').substringBefore('：').trim()

internal fun settingEntries(isSingleNote: Boolean): List<SettingEntry> = buildList {
    add(SettingEntry(R.string.tab_accounts, Page.Accounts))
    add(SettingEntry(R.string.app_view, Page.AppView))
    add(SettingEntry(R.string.layout_label, Page.AppView))
    add(SettingEntry(R.string.pinned_section_switch, Page.AppView))
    add(SettingEntry(R.string.preview_lines, Page.AppView))
    add(SettingEntry(R.string.add_account, Page.Accounts))
    add(SettingEntry(R.string.account_for_widget, Page.Accounts))
    add(SettingEntry(R.string.token_help, Page.Accounts))
    if (isSingleNote) add(SettingEntry(R.string.select_note, Page.Main, popup = "note"))

    add(SettingEntry(R.string.appearance, Page.Appearance))
    listOf(
        R.string.bg_opacity, R.string.text_size, R.string.text_color, R.string.text_color_auto, R.string.show_note_colors,
        R.string.note_opacity, R.string.plain_style,
    ).forEach { add(SettingEntry(it, Page.Appearance)) }

    add(SettingEntry(R.string.section_notes, Page.Notes))
    add(SettingEntry(R.string.show_images, Page.Notes))
    add(SettingEntry(R.string.show_edited_time, Page.Notes))
    add(SettingEntry(R.string.show_labels, Page.Notes))
    add(SettingEntry(R.string.reminder_show_widget, Page.Notes))
    if (!isSingleNote) add(SettingEntry(R.string.label_filter, Page.Notes, popup = "labelfilter"))
    add(SettingEntry(R.string.show_progress, Page.Notes))
    add(SettingEntry(R.string.lock_private, Page.AppView))
    if (!isSingleNote) add(SettingEntry(R.string.max_lines, Page.Notes))
    if (isSingleNote) add(SettingEntry(R.string.show_add_item_button, Page.ButtonBar))
    if (!isSingleNote) {
        listOf(
            R.string.sort_by, R.string.sort_keep, R.string.sort_edited,
            R.string.sort_created, R.string.sort_title, R.string.sort_manual,
        ).forEach { add(SettingEntry(it, Page.Notes)) }
        add(SettingEntry(R.string.manage_notes, Page.ManageNotes))
    }

    add(SettingEntry(R.string.list_section, Page.Checklists))
    listOf(
        R.string.check_in_widget, R.string.checked_to_end, R.string.collapse_checked, R.string.delete_checked,
        R.string.list_spacing, R.string.checkbox_style, R.string.checkbox_size, R.string.tap_whole_row,
        R.string.cb_style_square, R.string.cb_style_rounded, R.string.cb_style_circle, R.string.cb_style_classic,
    ).forEach { add(SettingEntry(it, Page.Checklists)) }

    add(SettingEntry(R.string.section_button_bar, Page.ButtonBar))
    listOf(R.string.header_position, R.string.header_size, R.string.show_keep_button,
        R.string.show_refresh_button, R.string.show_settings_button,
    ).forEach {
        add(SettingEntry(it, Page.ButtonBar))
    }
    if (!isSingleNote) {
        listOf(R.string.show_new_note_button, R.string.show_new_list_button, R.string.show_sort_button, R.string.show_search_bar)
            .forEach { add(SettingEntry(it, Page.ButtonBar)) }
    }

    add(SettingEntry(R.string.reminders_title, Page.Reminders))
    add(SettingEntry(R.string.reminder_sound, Page.Reminders))
    add(SettingEntry(R.string.reminder_vibrate, Page.Reminders))
    add(SettingEntry(R.string.reminder_override, Page.Reminders))
    add(SettingEntry(R.string.reminder_snooze, Page.Reminders))
    add(SettingEntry(R.string.reminder_presets, Page.Reminders))
    add(SettingEntry(R.string.sync_section, Page.Sync))
    add(SettingEntry(R.string.sync_group_display, Page.Sync))
    add(SettingEntry(R.string.sync_every, Page.Sync))
    add(SettingEntry(R.string.sync_on_open, Page.Sync))
    add(SettingEntry(R.string.sync_on_unlock, Page.Sync))
    add(SettingEntry(R.string.sync_min_gap, Page.Sync))
    add(SettingEntry(R.string.sync_background, Page.Sync))
    add(SettingEntry(R.string.sync_wifi_only, Page.Sync))
    add(SettingEntry(R.string.theme_section, Page.Main, popup = "theme"))
    listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark, R.string.theme_oled).forEach {
        add(SettingEntry(it, Page.Main, popup = "theme"))
    }

    add(SettingEntry(R.string.md_sync_title, Page.Backup))
    add(SettingEntry(R.string.md_sync_enable, Page.Backup))
    add(SettingEntry(R.string.md_sync_choose, Page.Backup))
    add(SettingEntry(R.string.md_sync_now, Page.Backup))
    add(SettingEntry(R.string.demo_mode, Page.Main))
    add(SettingEntry(R.string.language, Page.Main, popup = "language"))
    add(SettingEntry(R.string.tab_backup, Page.Backup))
    add(SettingEntry(R.string.export_settings, Page.Backup))
    add(SettingEntry(R.string.import_settings, Page.Backup))
}

internal val WIDGET_PAGES = setOf(Page.Appearance, Page.Notes, Page.ManageNotes, Page.Checklists, Page.ButtonBar)
