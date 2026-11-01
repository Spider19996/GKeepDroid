package com.gkeepdroid

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/** An item of a checklist; [indent] puts it under the item above, as a sub-item (Keep knows one level). */
data class KeepItem(val id: String, val text: String, val checked: Boolean, val indent: Boolean = false)

data class KeepNote(
    val id: String,
    /** Keep's own id of the note, used to link to it in the Keep app or on keep.google.com. */
    val serverId: String = "",
    val title: String,
    val text: String,
    val isList: Boolean,
    val items: List<KeepItem>,
    val color: String,
    val pinned: Boolean = false,
    /** File names of the note's downloaded images and drawings. */
    val images: List<String> = emptyList(),
    /** Keep's own sort value: higher means further up in the Keep app. */
    val sort: Long = 0,
    val updated: Long = 0,
    val created: Long = 0,
    /** "active", or "archived" / "trashed" for notes that are only listed in the app. */
    val state: String = "active",
    val labels: List<String> = emptyList(),
)

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

const val ALIGN_START = 1
const val ALIGN_CENTER = 2
const val ALIGN_END = 3

/** The standard button bar: the main actions on the top, creating and searching at the bottom. */
val DEFAULT_SIDES = mapOf(
    BTN_ADD_ITEM to POS_TOP, BTN_REMINDER to POS_TOP, BTN_REFRESH to POS_TOP, BTN_SORT to POS_TOP, BTN_KEEP to POS_TOP, BTN_SETTINGS to POS_TOP,
    BTN_NEW_NOTE to POS_BOTTOM, BTN_NEW_LIST to POS_BOTTOM, BTN_SEARCH to POS_BOTTOM, BTN_APP to POS_BOTTOM,
)
val DEFAULT_ORDER = listOf(
    BTN_ADD_ITEM, BTN_REMINDER, BTN_KEEP, BTN_NEW_NOTE, BTN_NEW_LIST, BTN_REFRESH, BTN_SETTINGS, BTN_SEARCH, BTN_APP, BTN_SORT,
)
val DEFAULT_SLOTS = mapOf(
    BTN_NEW_NOTE to ALIGN_START, BTN_REFRESH to ALIGN_END, BTN_SETTINGS to ALIGN_END, BTN_NEW_LIST to ALIGN_START,
    BTN_SORT to ALIGN_START, BTN_SEARCH to ALIGN_END, BTN_APP to ALIGN_END, BTN_KEEP to ALIGN_START,
    BTN_REMINDER to ALIGN_START,
)

val ALL_BUTTONS = listOf(BTN_REFRESH, BTN_ADD_ITEM, BTN_REMINDER, BTN_NEW_NOTE, BTN_NEW_LIST, BTN_SORT, BTN_SEARCH, BTN_APP, BTN_KEEP, BTN_SETTINGS)

fun idsFromJson(json: String?): List<String> = try {
    val a = JSONArray(json ?: "[]")
    (0 until a.length()).map { a.getString(it) }
} catch (e: JSONException) {
    emptyList()
}

fun sidesToJson(sides: Map<String, Int>): String = JSONObject().also { o -> sides.forEach { (k, v) -> o.put(k, v) } }.toString()

fun sidesFromJson(json: String?): Map<String, Int> = try {
    val o = JSONObject(json ?: "{}")
    o.keys().asSequence().associateWith { o.getInt(it) }
} catch (e: JSONException) {
    emptyMap()
}

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
    /** Side of each shown button (BTN_* id to POS_*); a button without an entry is not shown. */
    val buttonSides: Map<String, Int> = DEFAULT_SIDES,
    /** Order of the buttons inside their bars: BTN_* ids, first one first; missing ones follow in default order. */
    val buttonOrder: List<String> = DEFAULT_ORDER,
    /** Where a button sits along its bar (ALIGN_START, ALIGN_CENTER or ALIGN_END); missing means the middle. */
    val buttonSlots: Map<String, Int> = DEFAULT_SLOTS,
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
    val checkboxStyle: Int = CB_SQUARE,
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
    // Global settings and the template for new widgets share one file, as in earlier versions.
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
        globalPrefs(context).edit()
            .putInt("theme_mode", s.themeMode)
            .putInt("sync_interval_min", s.syncIntervalMin)
            .putBoolean("sync_on_unlock", s.syncOnUnlock)
            .putBoolean("sync_on_open", s.syncOnOpen)
            .putInt("min_gap_sec", s.minGapSec)
            .putBoolean("background_sync", s.backgroundSync)
            .putInt("background_interval_min", s.backgroundIntervalMin)
            .putBoolean("wifi_only", s.wifiOnly)
            .putBoolean("demo_mode", s.demoMode)
            .putString("language", s.language)
            .putString("reminder_sound", s.reminderSound)
            .putBoolean("reminder_vibrate", s.reminderVibrate)
            .putBoolean("reminder_override", s.reminderOverride)
            .putInt("reminder_snooze_min", s.reminderSnoozeMin)
            .putString("reminder_presets", s.reminderPresets)
            .apply()
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
        widgetPrefs(context, appWidgetId).edit().putBoolean("reorder_mode", on).apply()
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
            buttonSides = if (p.contains("button_sides")) sidesFromJson(p.getString("button_sides", null)) else d.buttonSides,
            buttonOrder = if (p.contains("button_order")) idsFromJson(p.getString("button_order", null)) else d.buttonOrder,
            buttonSlots = if (p.contains("button_slots")) sidesFromJson(p.getString("button_slots", null)) else d.buttonSlots,
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
        )
    }

    private fun write(p: SharedPreferences, s: WidgetSettings) {
        p.edit()
            .putFloat("background_alpha", s.backgroundAlpha)
            .putInt("text_color", s.textColor)
            .putInt("text_size_sp", s.textSizeSp)
            .putBoolean("show_note_colors", s.showNoteColors)
            .putFloat("note_alpha", s.noteAlpha)
            .putInt("plain_card_style", s.plainCardStyle)
            .putString("account", s.account)
            .putInt("header_size_sp", s.headerSizeSp)
            .putString("button_sides", sidesToJson(s.buttonSides))
            .putString("button_order", JSONArray(s.buttonOrder).toString())
            .putString("button_slots", sidesToJson(s.buttonSlots))
            .putBoolean("checkable", s.checkable)
            .putBoolean("checked_to_end", s.checkedToEnd)
            .putBoolean("collapse_checked", s.collapseChecked)
            .putInt("list_spacing_dp", s.listSpacingDp)
            .putInt("sort_mode", s.sortMode)
            .putBoolean("show_images", s.showImages)
            .putString("note_id", s.noteId)
            .putStringSet("hidden_notes", s.hiddenNotes)
            .putStringSet("pin_bottom", s.pinBottom)
            .putString("manual_order", JSONArray(s.manualOrder).toString())
            .putInt("checkbox_style", s.checkboxStyle)
            .putInt("checkbox_size_dp", s.checkboxSizeDp)
            .putBoolean("tap_whole_row", s.tapWholeRow)
            .putBoolean("delete_checked", s.deleteChecked)
            .putInt("max_lines", s.maxLines)
            .putBoolean("text_color_auto", s.textColorAuto)
            .putBoolean("show_edited_time", s.showEditedTime)
            .putBoolean("show_labels", s.showLabels)
            .putBoolean("show_reminders", s.showReminders)
            .putString("label_filter", s.labelFilter)
            .putBoolean("show_progress", s.showProgress)
            .apply()
    }
}

/**
 * Accounts and their master tokens, kept in encrypted preferences. Nothing is hard-coded and
 * tokens never leave the device (they are not part of the settings export).
 */
object CredentialStore {
    @Volatile
    private var prefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences = prefs ?: synchronized(this) {
        prefs ?: EncryptedSharedPreferences.create(
            context.applicationContext,
            "credentials",
            MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { prefs = it }
    }

    fun accounts(context: Context): List<String> {
        val raw = prefs(context).getString("accounts", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun token(context: Context, email: String): String? = prefs(context).getString("token_$email", null)

    fun isLocal(email: String) = email == LOCAL_ACCOUNT

    /** A Keep account needs its token; the local account needs nothing. */
    fun isUsable(context: Context, email: String) = isLocal(email) || token(context, email) != null

    /** What the user sees for an account: the e-mail, or a name for the local account. */
    fun displayName(context: Context, email: String) = when {
        isLocal(email) -> context.getString(R.string.local_account_name)
        DemoNotes.enabled(context) -> DemoNotes.ACCOUNT_NAME
        else -> email
    }

    /** Adds the account or updates its token; a null token keeps an existing one. */
    fun put(context: Context, email: String, token: String?) {
        val p = prefs(context)
        val list = accounts(context).toMutableList()
        if (email !in list) list.add(email)
        val editor = p.edit().putString("accounts", JSONArray(list).toString())
        if (token != null) editor.putString("token_$email", token)
        editor.apply()
    }

    fun remove(context: Context, email: String) {
        val list = accounts(context).filter { it != email }
        prefs(context).edit()
            .putString("accounts", JSONArray(list).toString())
            .remove("token_$email")
            .apply()
        NotesCache.delete(context, email)
        if (isLocal(email)) LocalNotes.deleteAll(context)
    }

    /** The account a widget actually shows: its own choice if it still exists, else the first one. */
    fun resolve(context: Context, wanted: String): String? {
        val all = accounts(context)
        return if (wanted in all) wanted else all.firstOrNull()
    }
}

/** Per-note override of whether the checked items of a checklist are collapsed in the widget. */
object CollapseStore {
    private fun prefs(context: Context) = context.getSharedPreferences("collapsed", Context.MODE_PRIVATE)

    fun key(account: String, noteId: String) = "$account|$noteId"

    fun load(context: Context): Map<String, Boolean> =
        prefs(context).all.mapNotNull { (k, v) -> (v as? Boolean)?.let { k to it } }.toMap()

    fun set(context: Context, account: String, noteId: String, collapsed: Boolean) {
        prefs(context).edit().putBoolean(key(account, noteId), collapsed).apply()
    }
}

/** Notes whose content is hidden in the widgets (only the title shows); stays on this device. */
object PrivateNotes {
    private fun prefs(context: Context) = context.getSharedPreferences("private_notes", Context.MODE_PRIVATE)

    fun key(account: String, noteId: String) = "$account|$noteId"

    fun load(context: Context): Set<String> = prefs(context).getStringSet("keys", null)?.toSet().orEmpty()

    fun set(context: Context, account: String, noteId: String, hidden: Boolean) {
        val keys = load(context).toMutableSet()
        if (hidden) keys.add(key(account, noteId)) else keys.remove(key(account, noteId))
        prefs(context).edit().putStringSet("keys", keys).apply()
    }
}

/** Keep's note colors, as named by gkeepapi, mapped to ARGB values. */
object NoteColors {
    private val argb = linkedMapOf(
        "Red" to 0xFFF28B82.toInt(),
        "Orange" to 0xFFFBBC04.toInt(),
        "Yellow" to 0xFFFFF475.toInt(),
        "Green" to 0xFFCCFF90.toInt(),
        "Teal" to 0xFFA7FFEB.toInt(),
        "Blue" to 0xFFCBF0F8.toInt(),
        "DarkBlue" to 0xFFAECBFA.toInt(),
        "Purple" to 0xFFD7AEFB.toInt(),
        "Pink" to 0xFFFDCFE8.toInt(),
        "Brown" to 0xFFE6C9A8.toInt(),
        "Gray" to 0xFFE8EAED.toInt(),
    )

    const val DEFAULT = "White"
    val names: List<String> = listOf(DEFAULT) + argb.keys

    /** Null for the default (uncolored) note. */
    fun argb(name: String): Int? = argb[name]
}

/** Per-account cache of the last synced notes, so widgets render without network. */
object NotesCache {
    private fun key(email: String) = email.lowercase().replace(Regex("[^a-z0-9]"), "_")

    private fun file(context: Context, email: String) = File(context.filesDir, "notes_${key(email)}.json")

    /** Where the Python bridge stores the downloaded images of an account. */
    fun imageDir(context: Context, email: String) = File(context.filesDir, "keep_state_${key(email)}_images")

    fun stateFile(context: Context, email: String) = File(context.filesDir, "keep_state_${key(email)}.json")

    /**
     * Writes the cache; returns false (and writes nothing) when it is unchanged. The JSON is
     * normalized first so that the same content never looks "changed" because of formatting, and
     * [pending] checks that were not sent to Keep yet are applied on top of what Keep returned.
     */
    fun write(
        context: Context, email: String, json: String, pending: List<PendingToggle> = emptyList(),
        edits: List<PendingEdit> = emptyList(),
    ): Boolean {
        val array = JSONArray(json)
        applyEdits(array, edits)
        if (pending.isNotEmpty()) {
            for (i in 0 until array.length()) {
                val note = array.getJSONObject(i)
                val items = note.getJSONArray("items")
                val kept = JSONArray()
                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    val op = pending.lastOrNull { it.itemId == item.optString("id") }
                    if (op != null && op.delete) continue
                    if (op != null) item.put("checked", op.checked)
                    kept.put(item)
                }
                note.put("items", kept)
            }
        }
        val normalized = array.toString()
        val f = file(context, email)
        if (f.exists() && f.readText() == normalized) return false
        f.writeText(normalized)
        return true
    }

    /** Puts the edits that were not sent to Keep yet on top of the cached notes; false when nothing changed. */
    fun reapplyEdits(context: Context, email: String): Boolean {
        val f = file(context, email)
        if (!f.exists() || DemoNotes.enabled(context)) return false
        return try {
            write(
                context, email, f.readText(), PendingToggles.list(context, email),
                PendingEdits.all(context).filter { it.account == email },
            )
        } catch (e: JSONException) {
            false
        }
    }

    /** Applies changes of existing notes, the way Keep will once they are sent. A new note shows up with its sync. */
    private fun applyEdits(array: JSONArray, edits: List<PendingEdit>) {
        for (edit in edits) {
            if (edit.function != "update_note") continue
            try {
                val id = edit.args[0].toString()
                val removed = JSONArray(edit.args[6].toString()).let { a -> (0 until a.length()).map { a.getString(it) } }
                for (i in 0 until array.length()) {
                    val note = array.getJSONObject(i)
                    if (note.getString("id") != id) continue
                    note.put("title", edit.args[1].toString())
                    if (note.getBoolean("isList")) {
                        // Like the bridge, blank items are dropped.
                        val items = JSONArray(edit.args[3].toString())
                        val kept = JSONArray()
                        for (j in 0 until items.length()) {
                            if (items.getJSONObject(j).optString("text").isNotBlank()) kept.put(items.getJSONObject(j))
                        }
                        note.put("items", kept)
                    } else {
                        note.put("text", edit.args[2].toString())
                    }
                    note.put("color", edit.args[4].toString())
                    note.put("pinned", edit.args[5] as? Boolean ?: edit.args[5].toString().toBoolean())
                    note.put("labels", JSONArray(edit.args[7].toString()))
                    val images = note.optJSONArray("images") ?: JSONArray()
                    note.put("images", JSONArray((0 until images.length()).map { images.getString(it) }.filter { it !in removed }))
                }
            } catch (e: Exception) {
                // An edit that does not fit (stored by an older version) is just not previewed.
            }
        }
    }

    /** Flips one checklist item in the cache so the widget can react before the network call ends. */
    fun setChecked(context: Context, email: String, noteId: String, itemId: String, checked: Boolean): Boolean {
        val f = file(context, email)
        if (!f.exists()) return false
        return try {
            val array = JSONArray(f.readText())
            var changed = false
            for (i in 0 until array.length()) {
                val note = array.getJSONObject(i)
                if (note.getString("id") != noteId) continue
                val items = note.getJSONArray("items")
                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    if (item.optString("id") == itemId) {
                        item.put("checked", checked)
                        changed = true
                    }
                }
            }
            if (changed) f.writeText(array.toString())
            changed
        } catch (e: JSONException) {
            false
        }
    }

    /** Removes one checklist item from the cache, like Keep will once the deletion is sent. */
    fun removeItem(context: Context, email: String, noteId: String, itemId: String): Boolean {
        val f = file(context, email)
        if (!f.exists()) return false
        return try {
            val array = JSONArray(f.readText())
            var changed = false
            for (i in 0 until array.length()) {
                val note = array.getJSONObject(i)
                if (note.getString("id") != noteId) continue
                val items = note.getJSONArray("items")
                val kept = JSONArray()
                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    if (item.optString("id") == itemId) changed = true else kept.put(item)
                }
                note.put("items", kept)
            }
            if (changed) f.writeText(array.toString())
            changed
        } catch (e: JSONException) {
            false
        }
    }

    private fun otherFile(context: Context, email: String) = File(context.filesDir, "keep_state_${key(email)}_other.json")

    /** Archived and trashed notes of an account, as last synced. */
    fun readOther(context: Context, email: String?): List<KeepNote> {
        if (email == null) return emptyList()
        if (DemoNotes.enabled(context)) return emptyList()
        val f = otherFile(context, email)
        if (!f.exists()) return emptyList()
        return try {
            val array = JSONArray(f.readText())
            (0 until array.length()).map { parse(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun labelsFile(context: Context, email: String) = File(context.filesDir, "keep_state_${key(email)}_labels.json")

    /** The label names stored for an account, also unused ones. */
    fun labelsOnly(context: Context, email: String?): List<String> {
        if (email == null) return emptyList()
        return try {
            val f = labelsFile(context, email)
            if (f.exists()) JSONArray(f.readText()).let { a -> (0 until a.length()).map { a.getString(it) } } else emptyList()
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** The labels an account has (also unused ones) plus the ones its notes carry. */
    fun labels(context: Context, email: String?): List<String> {
        if (email == null) return emptyList()
        return (labelsOnly(context, email) + readAll(context, email).flatMap { it.labels }).distinct().sortedBy { it.lowercase() }
    }

    /** Every note of an account, active ones first. */
    fun readAll(context: Context, email: String?): List<KeepNote> = read(context, email) + readOther(context, email)

    fun delete(context: Context, email: String) {
        otherFile(context, email).delete()
        file(context, email).delete()
        stateFile(context, email).delete()
        imageDir(context, email).deleteRecursively()
    }

    fun read(context: Context, email: String?): List<KeepNote> {
        if (email == null) return emptyList()
        if (DemoNotes.enabled(context)) {
            return if (email == CredentialStore.accounts(context).firstOrNull()) DemoNotes.notes() else emptyList()
        }
        val f = file(context, email)
        if (!f.exists()) return emptyList()
        // A cache written by an older version may be incomplete: fall back to empty and resync.
        return try {
            val array = JSONArray(f.readText())
            (0 until array.length()).map { parse(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun parse(o: JSONObject): KeepNote {
        val items = o.getJSONArray("items")
        return KeepNote(
            id = o.getString("id"),
            serverId = o.optString("serverId"),
            title = o.getString("title"),
            text = o.getString("text"),
            isList = o.getBoolean("isList"),
            items = (0 until items.length()).map {
                val item = items.getJSONObject(it)
                KeepItem(item.optString("id"), item.optString("text"), item.optBoolean("checked"), item.optBoolean("indent"))
            },
            color = o.optString("color", NoteColors.DEFAULT),
            pinned = o.optBoolean("pinned"),
            images = o.optJSONArray("images")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            sort = o.optLong("sort"),
            updated = o.optLong("updated"),
            created = o.optLong("created"),
            state = o.optString("state", "active"),
            labels = o.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
        )
    }
}

/**
 * Orders notes for a widget: the chosen sort with Keep's own pinned notes first (not for a manual
 * order) and the notes the user shows at the widget's bottom at the end. Hidden notes
 * are dropped unless [includeHidden] is set (the settings list shows all of them).
 */
fun sortNotes(notes: List<KeepNote>, settings: WidgetSettings, includeHidden: Boolean = false): List<KeepNote> {
    val shown = if (includeHidden) notes else notes.filter { it.id !in settings.hiddenNotes }
    val sorted = when (settings.sortMode) {
        SORT_EDITED -> shown.sortedByDescending { it.updated }
        SORT_CREATED -> shown.sortedByDescending { it.created }
        SORT_TITLE -> shown.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { noteLabel(it) })
        SORT_MANUAL -> {
            val position = settings.manualOrder.withIndex().associate { it.value to it.index }
            // Notes that are not in the manual order yet (new ones) come first, in Keep's order.
            shown.sortedByDescending { it.sort }.sortedBy { position[it.id] ?: -1 }
        }
        else -> shown.sortedByDescending { it.sort }
    }
    // The sorts are stable, so the chosen order is kept within each group.
    // Notes pinned in Keep come first, except with a manual order, where the user's order decides.
    val keepPinned = if (settings.sortMode == SORT_MANUAL) sorted else sorted.sortedByDescending { it.pinned }
    val bottom = keepPinned.filter { it.id in settings.pinBottom }
    val rest = keepPinned.filter { it.id !in settings.pinBottom }
    return rest + bottom
}

/** Title, or the first line of the text or list, for places that need a short name. */
fun noteLabel(note: KeepNote): String = note.title.ifBlank {
    if (note.isList) note.items.firstOrNull()?.text.orEmpty() else note.text.lineSequence().firstOrNull().orEmpty()
}

/** Whether a note matches a search: every word of [query] must appear in the title, text or an item. */
fun KeepNote.matches(query: String): Boolean {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = (listOf(title, text) + items.map { it.text } + labels).joinToString("\n").lowercase()
    return words.all { it in haystack }
}

/**
 * Moves a note one place up (-1) or down (+1) among the notes the widget shows. The result is a
 * manual order; hidden notes keep their place. Returns null if the note cannot move that way.
 */
fun reorderedSettings(notes: List<KeepNote>, settings: WidgetSettings, noteId: String, delta: Int): WidgetSettings? {
    val ordered = sortNotes(notes, settings, includeHidden = true).map { it.id }.toMutableList()
    val visible = ordered.filter { it !in settings.hiddenNotes }
    val from = visible.indexOf(noteId)
    val to = from + delta
    if (from < 0 || to !in visible.indices) return null
    java.util.Collections.swap(ordered, ordered.indexOf(visible[from]), ordered.indexOf(visible[to]))
    return settings.copy(sortMode = SORT_MANUAL, manualOrder = ordered)
}

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
)

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
        )
    }

    fun save(context: Context, s: AppSettings) {
        prefs(context).edit()
            .putInt("layout", s.layout)
            .putInt("sort_mode", s.sortMode)
            .putBoolean("pinned_section", s.pinnedSection)
            .putBoolean("show_images", s.showImages)
            .putBoolean("show_note_colors", s.showNoteColors)
            .putInt("plain_card_style", s.plainCardStyle)
            .putInt("preview_lines", s.previewLines)
            .putBoolean("show_edited_time", s.showEditedTime)
            .putBoolean("edited_as_date", s.editedAsDate)
            .putBoolean("show_labels", s.showLabels)
            .putBoolean("show_progress", s.showProgress)
            .putBoolean("lock_private_notes", s.lockPrivateNotes)
            .apply()
    }
}
