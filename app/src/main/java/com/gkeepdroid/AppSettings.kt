package com.gkeepdroid

import android.content.Context
import androidx.core.content.edit

/** Settings of the full-screen app view; they are separate from the widgets' settings. */
data class AppSettings(
    val layout: Int = LAYOUT_GRID,
    /** One of SORT_KEEP, SORT_EDITED, SORT_CREATED, SORT_TITLE. */
    val sortMode: Int = SORT_KEEP,
    val pinnedSection: Boolean = true,
    val showImages: Boolean = true,
    val showNoteColors: Boolean = true,
    val plainCardStyle: Int = PLAIN_BORDER,
    /** Lines of a note (or items of a checklist) shown on its card; 0 = all. */
    val previewLines: Int = 10,
    val showEditedTime: Boolean = true,
    /** Shows the date of the last edit instead of "2 days ago". */
    val editedAsDate: Boolean = false,
    val showLabels: Boolean = true,
    val showProgress: Boolean = true,
    /** Private notes ask for the device lock (PIN, pattern, fingerprint) before they open, and hide their content on the cards. */
    val lockPrivateNotes: Boolean = true,
    /** The whole app asks for fingerprint, PIN or password. */
    val lockApp: Boolean = false,
    /** Notes can be read and opened freely; changing them (and archiving, deleting) asks for the lock. */
    val lockEdit: Boolean = false,
    /** Seconds in the background during which the app opens again without asking. */
    val lockGraceSec: Int = 10,
    /** Reminders on the cards. */
    val showReminders: Boolean = true,
    /** Checklists on the cards: checked items last, optionally hidden, and how the boxes look. */
    val listCheckedToEnd: Boolean = true,
    val listCollapseChecked: Boolean = false,
    // The rounded box is the checkbox of Material 3 Expressive.
    val listCheckboxStyle: Int = CB_ROUNDED,
    val listCheckboxSizeDp: Int = 18,
    val listSpacingDp: Int = 2,
    /** Tap a box on a card to tick the item; with [listDeleteChecked] the ticked item is removed instead. */
    val listCheckInApp: Boolean = true,
    val listDeleteChecked: Boolean = false,
    /** What a checklist turns into when it becomes a note: no prefix, or "- ", "* ", "[ ] / [x] ", "1. ". */
    val convertPrefix: Int = PREFIX_NONE,
    /** Numbered lists count again in the order cards and widgets show (checked items last); off shows the saved numbers. */
    val renumberDisplay: Boolean = false,
    /** Ticking in a widget with the edit lock on: seconds during which the lock is not asked again. */
    val lockWidgetGraceSec: Int = 10,
    /** While the whole app is locked, widgets show only the titles. */
    val widgetTitlesOnly: Boolean = false,
    /** Ticking in a widget asks for the lock (with its own time), independent of the other locks. */
    val lockWidgetTick: Boolean = false,
    /** What swiping a card on the notes screen does: one of SWIPE_NONE, SWIPE_ARCHIVE, SWIPE_TRASH. */
    val swipeRight: Int = SWIPE_ARCHIVE,
    val swipeLeft: Int = SWIPE_TRASH,
    /** An arrow on each card folds the note to its title. */
    val foldNotes: Boolean = true,
    /** The row of filter buttons above the notes, and which of them it shows (bits of FILTER_*). */
    val filterBar: Boolean = true,
    val filterChips: Int = FILTER_NOTES or FILTER_LISTS or FILTER_REMINDER or FILTER_RECENT or FILTER_LABELS,
    val pullToRefresh: Boolean = true,
    /** Where the buttons of the note editor stand: the two bars, the menu or nowhere. */
    val editorLayout: EditorLayout = EditorLayout(),
)

const val FILTER_NOTES = 1
const val FILTER_LISTS = 2
const val FILTER_REMINDER = 4
const val FILTER_RECENT = 8
const val FILTER_IMAGE = 16
const val FILTER_PINNED = 32
const val FILTER_LABELS = 64

const val PREFIX_NONE = 0
const val PREFIX_DASH = 1
const val PREFIX_STAR = 2
const val PREFIX_BOX = 3
const val PREFIX_NUMBER = 4

const val SWIPE_NONE = 0
const val SWIPE_ARCHIVE = 1
const val SWIPE_TRASH = 2
const val SWIPE_PIN = 3
const val SWIPE_REMINDER = 4
const val SWIPE_SHARE = 5
const val SWIPE_HIDE = 6

object AppSettingsStore {
    private fun prefs(context: Context) = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun load(context: Context): AppSettings {
        val d = AppSettings()
        val p = prefs(context)
        return AppSettings(
            layout = p.getInt("layout", d.layout),
            sortMode = p.getInt("sort_mode", d.sortMode),
            pinnedSection = p.getBoolean("pinned_section", d.pinnedSection),
            showImages = p.getBoolean("show_images", d.showImages),
            showNoteColors = p.getBoolean("show_note_colors", d.showNoteColors),
            plainCardStyle = p.getInt("plain_card_style", d.plainCardStyle),
            previewLines = p.getInt("preview_lines", d.previewLines),
            showEditedTime = p.getBoolean("show_edited_time", d.showEditedTime),
            editedAsDate = p.getBoolean("edited_as_date", d.editedAsDate),
            showLabels = p.getBoolean("show_labels", d.showLabels),
            showProgress = p.getBoolean("show_progress", d.showProgress),
            lockPrivateNotes = p.getBoolean("lock_private_notes", d.lockPrivateNotes),
            lockApp = p.getBoolean("lock_app", d.lockApp),
            lockEdit = p.getBoolean("lock_edit", d.lockEdit),
            lockGraceSec = p.getInt("lock_grace_sec", d.lockGraceSec),
            showReminders = p.getBoolean("show_reminders", d.showReminders),
            listCheckedToEnd = p.getBoolean("list_checked_to_end", d.listCheckedToEnd),
            listCollapseChecked = p.getBoolean("list_collapse_checked", d.listCollapseChecked),
            listCheckboxStyle = p.getInt("list_checkbox_style", d.listCheckboxStyle),
            listCheckboxSizeDp = p.getInt("list_checkbox_size_dp", d.listCheckboxSizeDp),
            listSpacingDp = p.getInt("list_spacing_dp", d.listSpacingDp),
            listCheckInApp = p.getBoolean("list_check_in_app", d.listCheckInApp),
            listDeleteChecked = p.getBoolean("list_delete_checked", d.listDeleteChecked),
            convertPrefix = p.getInt("convert_prefix", d.convertPrefix),
            renumberDisplay = p.getBoolean("renumber_display", d.renumberDisplay),
            lockWidgetGraceSec = p.getInt("lock_widget_grace_sec", d.lockWidgetGraceSec),
            widgetTitlesOnly = p.getBoolean("widget_titles_only", d.widgetTitlesOnly),
            lockWidgetTick = p.getBoolean("lock_widget_tick", d.lockWidgetTick),
            swipeRight = p.getInt("swipe_right", d.swipeRight),
            swipeLeft = p.getInt("swipe_left", d.swipeLeft),
            foldNotes = p.getBoolean("fold_notes", d.foldNotes),
            filterBar = p.getBoolean("filter_bar", d.filterBar),
            filterChips = p.getInt("filter_chips", d.filterChips),
            pullToRefresh = p.getBoolean("pull_to_refresh", d.pullToRefresh),
            editorLayout = if (p.contains("editor_layout")) EditorLayout.fromJson(p.getString("editor_layout", null)) else d.editorLayout,
        )
    }

    /** All app settings as they are stored, for the backup file (the editor layout is among them). */
    fun export(context: Context): org.json.JSONObject {
        save(context, load(context))
        val out = org.json.JSONObject()
        prefs(context).all.forEach { (key, value) -> if (value is Boolean || value is Int || value is String) out.put(key, value) }
        return out
    }

    /** Takes the settings of a backup file over: only keys this app knows, and only values of the right kind. */
    fun import(context: Context, json: org.json.JSONObject): AppSettings {
        save(context, load(context))
        val known = prefs(context).all
        prefs(context).edit {
            json.keys().forEach { key ->
                val value = json.opt(key)
                when (known[key]) {
                    is Boolean -> if (value is Boolean) putBoolean(key, value)
                    is Int -> if (value is Int) putInt(key, value)
                    is String -> if (value is String) putString(key, value)
                }
            }
        }
        return load(context)
    }

    fun save(context: Context, s: AppSettings) {
        prefs(context).edit {
            putInt("layout", s.layout)
            putInt("sort_mode", s.sortMode)
            putBoolean("pinned_section", s.pinnedSection)
            putBoolean("show_images", s.showImages)
            putBoolean("show_note_colors", s.showNoteColors)
            putInt("plain_card_style", s.plainCardStyle)
            putInt("preview_lines", s.previewLines)
            putBoolean("show_edited_time", s.showEditedTime)
            putBoolean("edited_as_date", s.editedAsDate)
            putBoolean("show_labels", s.showLabels)
            putBoolean("show_progress", s.showProgress)
            putBoolean("lock_private_notes", s.lockPrivateNotes)
            putBoolean("lock_app", s.lockApp)
            putBoolean("lock_edit", s.lockEdit)
            putInt("lock_grace_sec", s.lockGraceSec)
            putBoolean("show_reminders", s.showReminders)
            putBoolean("list_checked_to_end", s.listCheckedToEnd)
            putBoolean("list_collapse_checked", s.listCollapseChecked)
            putInt("list_checkbox_style", s.listCheckboxStyle)
            putInt("list_checkbox_size_dp", s.listCheckboxSizeDp)
            putInt("list_spacing_dp", s.listSpacingDp)
            putBoolean("list_check_in_app", s.listCheckInApp)
            putBoolean("list_delete_checked", s.listDeleteChecked)
            putInt("convert_prefix", s.convertPrefix)
            putBoolean("renumber_display", s.renumberDisplay)
            putInt("lock_widget_grace_sec", s.lockWidgetGraceSec)
            putBoolean("widget_titles_only", s.widgetTitlesOnly)
            putBoolean("lock_widget_tick", s.lockWidgetTick)
            putInt("swipe_right", s.swipeRight)
            putInt("swipe_left", s.swipeLeft)
            putBoolean("fold_notes", s.foldNotes)
            putBoolean("filter_bar", s.filterBar)
            putInt("filter_chips", s.filterChips)
            putBoolean("pull_to_refresh", s.pullToRefresh)
            putString("editor_layout", s.editorLayout.toJson())
        }
    }
}
