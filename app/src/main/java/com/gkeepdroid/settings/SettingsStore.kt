package com.gkeepdroid

import android.content.Context
import androidx.core.content.edit
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

const val PLAIN_TRANSPARENT = 0
const val PLAIN_GRAY = 1
const val PLAIN_BORDER = 2

const val LAYOUT_GRID = 0
const val LAYOUT_LIST = 1

const val SORT_KEEP = 0
const val SORT_EDITED = 1
const val SORT_CREATED = 2
const val SORT_TITLE = 3
const val SORT_MANUAL = 4

/** The account for notes that only exist on this device and are never synced. */
const val LOCAL_ACCOUNT = "local"

const val POS_TOP = 0
const val POS_BOTTOM = 1
const val POS_LEFT = 2
const val POS_RIGHT = 3

const val BTN_REFRESH = "refresh"
const val BTN_ADD_ITEM = "add_item"
const val BTN_NEW_NOTE = "new_note"
const val BTN_NEW_LIST = "new_list"
const val BTN_SORT = "sort"
const val BTN_SEARCH = "search"
const val BTN_APP = "app"
const val BTN_KEEP = "keep"
const val BTN_REMINDER = "reminder"
const val BTN_SETTINGS = "settings"
const val BTN_FOLD = "fold"

const val ALIGN_START = 1
const val ALIGN_CENTER = 2
const val ALIGN_END = 3

/** A button's place in a bar: the edge it sits on and where along it (start, middle or end). Zone -1 is "not shown". */
const val BAR_HIDDEN = -1
fun barZone(side: Int, slot: Int) = side * 10 + slot
fun barSide(zone: Int) = zone / 10
fun barSlot(zone: Int) = zone % 10

private fun barButton(id: String, @androidx.annotation.DrawableRes icon: Int, @androidx.annotation.StringRes label: Int, side: Int, slot: Int) =
    ButtonSpec(id, icon, label, defaultZone = barZone(side, slot))

/**
 * The buttons of the widget's bar and where they stand by default (the order here is the default order): the main actions on the
 * top, creating and searching at the bottom. Which of them a widget has depends on its kind, so the settings page and the widget
 * pick from these.
 */
val WIDGET_CATALOG = ButtonCatalog(
    specs = listOf(
        barButton(BTN_ADD_ITEM, R.drawable.ic_add, R.string.btn_add_item, POS_TOP, ALIGN_CENTER),
        barButton(BTN_REMINDER, R.drawable.ic_alarm, R.string.reminders_title, POS_TOP, ALIGN_START),
        barButton(BTN_KEEP, R.drawable.ic_open_new, R.string.btn_keep, POS_TOP, ALIGN_START),
        barButton(BTN_NEW_NOTE, R.drawable.ic_add, R.string.btn_new_note, POS_BOTTOM, ALIGN_START),
        barButton(BTN_NEW_LIST, R.drawable.ic_checklist, R.string.btn_new_list, POS_BOTTOM, ALIGN_START),
        barButton(BTN_REFRESH, R.drawable.ic_refresh, R.string.btn_refresh, POS_TOP, ALIGN_END),
        barButton(BTN_SETTINGS, R.drawable.ic_settings, R.string.btn_settings, POS_TOP, ALIGN_END),
        barButton(BTN_SEARCH, R.drawable.ic_search, R.string.btn_search, POS_BOTTOM, ALIGN_END),
        barButton(BTN_APP, R.drawable.ic_apps, R.string.btn_app, POS_BOTTOM, ALIGN_END),
        barButton(BTN_SORT, R.drawable.ic_sort, R.string.btn_sort, POS_TOP, ALIGN_START),
        barButton(BTN_FOLD, R.drawable.ic_fold, R.string.btn_fold, POS_TOP, ALIGN_START),
    ),
    hiddenZone = BAR_HIDDEN,
    validZone = { it == BAR_HIDDEN || (barSide(it) in POS_TOP..POS_RIGHT && barSlot(it) in ALIGN_START..ALIGN_END) },
)

const val CB_SQUARE = 0
const val CB_ROUNDED = 1
const val CB_CIRCLE = 2
const val CB_CLASSIC = 3

/** Appearance settings of a single widget instance. */
data class WidgetSettings(
    val backgroundAlpha: Float = 0f,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textSizeSp: Int = 14,
    val showNoteColors: Boolean = true,
    val noteAlpha: Float = 0.85f,
    /** How cards of notes without a color look: one of the PLAIN_* constants. */
    val plainCardStyle: Int = PLAIN_TRANSPARENT,
    /** E-mail of the account shown by the widget; empty means the first account. */
    val account: String = "",
    val headerSizeSp: Int = 22,
    /** Where each button of the bar stands (edge and place along it, see [barZone]) or that it is not shown, and their order. */
    val buttons: ButtonLayout = WIDGET_CATALOG.layout(),
    /** Lets the user tick checklist items directly in the widget. */
    val checkable: Boolean = false,
    /** Shows checked items after the unchecked ones. */
    val checkedToEnd: Boolean = true,
    /** Default for whether the checked items of a checklist are collapsed (needs checkedToEnd). */
    val collapseChecked: Boolean = false,
    /** Vertical spacing between checklist items in dp; the checkbox grows with it. */
    val listSpacingDp: Int = 4,
    /** How the notes are ordered: one of the SORT_* constants. */
    val sortMode: Int = SORT_KEEP,
    val showImages: Boolean = true,
    /** Note shown by a single-note widget; unused by the list widget. */
    val noteId: String = "",
    /** Notes of the list widget that are hidden or shown at its bottom, and the manual order. */
    val hiddenNotes: Set<String> = emptySet(),
    val pinBottom: Set<String> = emptySet(),
    val manualOrder: List<String> = emptyList(),
    /** Look of the checklist checkboxes: one of the CB_* constants. */
    val checkboxStyle: Int = CB_ROUNDED,
    /** Size of the checkbox in dp, independent of the text size. */
    val checkboxSizeDp: Int = 28,
    /** Ticks an item when its whole row is tapped, not only the checkbox. */
    val tapWholeRow: Boolean = false,
    /** Deletes an item from the checklist as soon as it is ticked in the widget. */
    val deleteChecked: Boolean = false,
    /** Maximum lines (text) or open items (checklist) per note in the list widget; 0 = unlimited. */
    val maxLines: Int = 8,
    /** Dark text in light mode and light text in dark mode, instead of the chosen text color. */
    val textColorAuto: Boolean = false,
    /** Shows how long ago a note was last edited. */
    val showEditedTime: Boolean = false,
    /** Shows the labels of a note on its card. */
    val showLabels: Boolean = true,
    /** Shows a note's next reminder on its card. */
    val showReminders: Boolean = true,
    /** The list widget shows only notes with this label; empty means all notes. */
    val labelFilter: String = "",
    /** Shows how many checklist items are ticked, such as 3/7, in the title line. */
    val showProgress: Boolean = true,
    /** A small arrow on each note folds it to its title. */
    val foldable: Boolean = false,
)

const val THEME_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2
const val THEME_OLED = 3

/** Settings that apply to the whole app rather than to one widget. */
data class GlobalSettings(
    val themeMode: Int = THEME_SYSTEM,
    /** Minutes between syncs while the display is on; 0 = no timer. */
    val syncIntervalMin: Int = 15,
    /** Sync when the phone is unlocked. */
    val syncOnUnlock: Boolean = true,
    /** Sync when the app is opened. */
    val syncOnOpen: Boolean = true,
    /** Automatic syncs are at least this many seconds apart, so frequent unlocking does not flood Google. */
    val minGapSec: Int = 60,
    /** Sync while the display is off. */
    val backgroundSync: Boolean = false,
    val backgroundIntervalMin: Int = 60,
    /** No automatic sync on mobile data. */
    val wifiOnly: Boolean = false,
    /** Shows sample notes instead of the real ones, for screenshots. */
    val demoMode: Boolean = false,
    /** Language tag such as "de"; empty follows the system language. */
    val language: String = "",
    /** Defaults for reminders; each reminder may set its own. A sound is "" (system), "none" or a ringtone URI. */
    val reminderSound: String = "",
    /** Vibrates when the phone is silent or on vibrate. */
    val reminderVibrate: Boolean = true,
    /** Sounds even when the phone is silent or on vibrate (at alarm volume). */
    val reminderOverride: Boolean = false,
    /** Minutes a reminder is put off by "Snooze" in its notification. */
    val reminderSnoozeMin: Int = 10,
    /** The quick times offered for a reminder: "r:60" is in 60 minutes, "t:18:00" the next 18:00. */
    val reminderPresets: String = "r:60,t:18:00,t:08:00",
)

object SettingsStore {
    // Global settings and the template for new widgets share one file.
    private const val GLOBAL_FILE = "widget_settings"

    private fun globalPrefs(context: Context) =
        context.getSharedPreferences(GLOBAL_FILE, Context.MODE_PRIVATE)

    private fun widgetPrefs(context: Context, appWidgetId: Int) =
        context.getSharedPreferences("widget_$appWidgetId", Context.MODE_PRIVATE)

    fun loadGlobal(context: Context): GlobalSettings {
        val d = GlobalSettings()
        val p = globalPrefs(context)
        return GlobalSettings(
            themeMode = p.getInt("theme_mode", d.themeMode),
            syncIntervalMin = p.getInt("sync_interval_min", d.syncIntervalMin),
            syncOnUnlock = p.getBoolean("sync_on_unlock", d.syncOnUnlock),
            syncOnOpen = p.getBoolean("sync_on_open", d.syncOnOpen),
            minGapSec = p.getInt("min_gap_sec", d.minGapSec),
            backgroundSync = p.getBoolean("background_sync", d.backgroundSync),
            backgroundIntervalMin = p.getInt("background_interval_min", d.backgroundIntervalMin),
            wifiOnly = p.getBoolean("wifi_only", d.wifiOnly),
            demoMode = p.getBoolean("demo_mode", d.demoMode),
            language = p.getString("language", d.language).orEmpty(),
            reminderSound = p.getString("reminder_sound", d.reminderSound).orEmpty(),
            reminderVibrate = p.getBoolean("reminder_vibrate", d.reminderVibrate),
            reminderOverride = p.getBoolean("reminder_override", d.reminderOverride),
            reminderSnoozeMin = p.getInt("reminder_snooze_min", d.reminderSnoozeMin),
            reminderPresets = p.getString("reminder_presets", d.reminderPresets).orEmpty(),
        )
    }

    fun saveGlobal(context: Context, s: GlobalSettings) {
        // Screens that depend on the demo mode (the debug mark) notice a change at once.
        DemoNotes.version.intValue++
        globalPrefs(context).edit {
            putInt("theme_mode", s.themeMode)
            putInt("sync_interval_min", s.syncIntervalMin)
            putBoolean("sync_on_unlock", s.syncOnUnlock)
            putBoolean("sync_on_open", s.syncOnOpen)
            putInt("min_gap_sec", s.minGapSec)
            putBoolean("background_sync", s.backgroundSync)
            putInt("background_interval_min", s.backgroundIntervalMin)
            putBoolean("wifi_only", s.wifiOnly)
            putBoolean("demo_mode", s.demoMode)
            putString("language", s.language)
            putString("reminder_sound", s.reminderSound)
            putBoolean("reminder_vibrate", s.reminderVibrate)
            putBoolean("reminder_override", s.reminderOverride)
            putInt("reminder_snooze_min", s.reminderSnoozeMin)
            putString("reminder_presets", s.reminderPresets)
        }
    }

    /** Settings of a widget; a widget without its own settings starts from the last saved ones. */
    fun loadWidget(context: Context, appWidgetId: Int): WidgetSettings {
        val own = widgetPrefs(context, appWidgetId)
        return read(if (own.contains("text_size_sp")) own else globalPrefs(context))
    }

    fun saveWidget(context: Context, appWidgetId: Int, s: WidgetSettings) {
        write(widgetPrefs(context, appWidgetId), s)
        write(globalPrefs(context), s.copy(
            account = "", noteId = "", hiddenNotes = emptySet(), pinBottom = emptySet(),
            manualOrder = emptyList(),
        ))
    }

    /** Whether the widget currently shows the arrows for re-ordering notes (not part of the settings). */
    fun isReordering(context: Context, appWidgetId: Int): Boolean =
        widgetPrefs(context, appWidgetId).getBoolean("reorder_mode", false)

    fun setReordering(context: Context, appWidgetId: Int, on: Boolean) {
        widgetPrefs(context, appWidgetId).edit { putBoolean("reorder_mode", on) }
    }

    fun deleteWidget(context: Context, appWidgetId: Int) {
        context.deleteSharedPreferences("widget_$appWidgetId")
    }

    /** Removes the settings of widgets that no longer exist and returns how many there were. */
    fun deleteOrphans(context: Context, placedIds: Collection<Int>): Int {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        val orphans = dir.listFiles().orEmpty()
            .mapNotNull { Regex("widget_(\\d+)\\.xml").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
            .filter { it !in placedIds }
        orphans.forEach { deleteWidget(context, it) }
        return orphans.size
    }

    private fun read(p: SharedPreferences): WidgetSettings {
        val d = WidgetSettings()
        return WidgetSettings(
            backgroundAlpha = p.getFloat("background_alpha", d.backgroundAlpha),
            textColor = p.getInt("text_color", d.textColor),
            textSizeSp = p.getInt("text_size_sp", d.textSizeSp),
            showNoteColors = p.getBoolean("show_note_colors", d.showNoteColors),
            noteAlpha = p.getFloat("note_alpha", d.noteAlpha),
            plainCardStyle = p.getInt("plain_card_style", d.plainCardStyle),
            account = p.getString("account", d.account).orEmpty(),
            headerSizeSp = p.getInt("header_size_sp", d.headerSizeSp),
            buttons = if (p.contains("button_layout")) ButtonLayout.fromJson(WIDGET_CATALOG, p.getString("button_layout", null)) else d.buttons,
            checkable = p.getBoolean("checkable", d.checkable),
            checkedToEnd = p.getBoolean("checked_to_end", d.checkedToEnd),
            collapseChecked = p.getBoolean("collapse_checked", d.collapseChecked),
            listSpacingDp = p.getInt("list_spacing_dp", d.listSpacingDp),
            sortMode = p.getInt("sort_mode", d.sortMode),
            showImages = p.getBoolean("show_images", d.showImages),
            noteId = p.getString("note_id", d.noteId).orEmpty(),
            hiddenNotes = p.getStringSet("hidden_notes", null)?.toSet() ?: d.hiddenNotes,
            pinBottom = p.getStringSet("pin_bottom", null)?.toSet() ?: d.pinBottom,
            manualOrder = p.getString("manual_order", null)?.let { raw ->
                try { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } } catch (e: JSONException) { emptyList() }
            } ?: d.manualOrder,
            checkboxStyle = p.getInt("checkbox_style", d.checkboxStyle),
            checkboxSizeDp = p.getInt("checkbox_size_dp", d.checkboxSizeDp),
            tapWholeRow = p.getBoolean("tap_whole_row", d.tapWholeRow),
            deleteChecked = p.getBoolean("delete_checked", d.deleteChecked),
            maxLines = p.getInt("max_lines", d.maxLines),
            textColorAuto = p.getBoolean("text_color_auto", d.textColorAuto),
            showEditedTime = p.getBoolean("show_edited_time", d.showEditedTime),
            showLabels = p.getBoolean("show_labels", d.showLabels),
            showReminders = p.getBoolean("show_reminders", d.showReminders),
            labelFilter = p.getString("label_filter", d.labelFilter).orEmpty(),
            showProgress = p.getBoolean("show_progress", d.showProgress),
            foldable = p.getBoolean("foldable", d.foldable),
        )
    }

    private fun write(p: SharedPreferences, s: WidgetSettings) {
        p.edit {
            putFloat("background_alpha", s.backgroundAlpha)
            putInt("text_color", s.textColor)
            putInt("text_size_sp", s.textSizeSp)
            putBoolean("show_note_colors", s.showNoteColors)
            putFloat("note_alpha", s.noteAlpha)
            putInt("plain_card_style", s.plainCardStyle)
            putString("account", s.account)
            putInt("header_size_sp", s.headerSizeSp)
            putString("button_layout", s.buttons.toJson())
            putBoolean("checkable", s.checkable)
            putBoolean("checked_to_end", s.checkedToEnd)
            putBoolean("collapse_checked", s.collapseChecked)
            putInt("list_spacing_dp", s.listSpacingDp)
            putInt("sort_mode", s.sortMode)
            putBoolean("show_images", s.showImages)
            putString("note_id", s.noteId)
            putStringSet("hidden_notes", s.hiddenNotes)
            putStringSet("pin_bottom", s.pinBottom)
            putString("manual_order", JSONArray(s.manualOrder).toString())
            putInt("checkbox_style", s.checkboxStyle)
            putInt("checkbox_size_dp", s.checkboxSizeDp)
            putBoolean("tap_whole_row", s.tapWholeRow)
            putBoolean("delete_checked", s.deleteChecked)
            putInt("max_lines", s.maxLines)
            putBoolean("text_color_auto", s.textColorAuto)
            putBoolean("show_edited_time", s.showEditedTime)
            putBoolean("show_labels", s.showLabels)
            putBoolean("show_reminders", s.showReminders)
            putString("label_filter", s.labelFilter)
            putBoolean("show_progress", s.showProgress)
            putBoolean("foldable", s.foldable)
        }
    }
}
