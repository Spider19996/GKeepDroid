package com.gkeepdroid

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

const val ZONE_TOP_LEFT = 0
const val ZONE_TOP_RIGHT = 1
const val ZONE_BOTTOM_LEFT = 2
const val ZONE_BOTTOM_RIGHT = 3
const val ZONE_MENU = 4
const val ZONE_HIDDEN = 5
// The note's own place: the note is one of its buttons (it cannot leave), the buttons before it show above the note's fields,
// those after it below them, as buttons with a name. Numbered after the older zones, so saved layouts keep their meaning.
const val ZONE_NOTE = 6

/** Where a note that is shorter than the screen sits between the bars. */
const val EDITOR_ALIGN_CENTER = 0
const val EDITOR_ALIGN_TOP = 1
const val EDITOR_ALIGN_BOTTOM = 2

/** One thing the editor can do with a button: where it stands by default, and which group it belongs to in the menu. */
class EditorActionSpec(
    val id: String, @DrawableRes val icon: Int, @StringRes val label: Int, val group: Int, val defaultZone: Int,
)

/** Every action of the editor, in the order of the default layout. The menu is a place for them like the two bars are. */
val EDITOR_ACTIONS = listOf(
    EditorActionSpec("close", R.drawable.ic_close, R.string.discard_action, 3, ZONE_TOP_LEFT),
    EditorActionSpec("pin", R.drawable.ic_push_pin_outline, R.string.pin_note, 3, ZONE_TOP_RIGHT),
    EditorActionSpec("pin_bottom", R.drawable.ic_pin_bottom, R.string.pin_to_bottom, 3, ZONE_TOP_RIGHT),
    EditorActionSpec("reminder", R.drawable.ic_alarm, R.string.reminders_title, 3, ZONE_TOP_RIGHT),
    EditorActionSpec("save", R.drawable.ic_done, R.string.save, 3, ZONE_TOP_RIGHT),
    EditorActionSpec("more", R.drawable.ic_more_vert, R.string.more_actions, 3, ZONE_TOP_RIGHT),
    EditorActionSpec("palette", R.drawable.ic_palette, R.string.color_and_background, 3, ZONE_BOTTOM_LEFT),
    EditorActionSpec("labels", R.drawable.ic_label, R.string.labels_title, 3, ZONE_BOTTOM_LEFT),
    EditorActionSpec("image", R.drawable.ic_image, R.string.image_add_keep, 3, ZONE_BOTTOM_LEFT),
    EditorActionSpec("undo", R.drawable.ic_undo, R.string.undo, 3, ZONE_BOTTOM_RIGHT),
    EditorActionSpec("redo", R.drawable.ic_redo, R.string.redo, 3, ZONE_BOTTOM_RIGHT),
    EditorActionSpec("convert", R.drawable.ic_checklist, R.string.editor_convert, 0, ZONE_MENU),
    EditorActionSpec("list_number", R.drawable.ic_checklist, R.string.list_number, 0, ZONE_MENU),
    EditorActionSpec("list_check_all", R.drawable.ic_done, R.string.list_check_all, 0, ZONE_MENU),
    EditorActionSpec("list_uncheck_all", R.drawable.ic_close, R.string.list_uncheck_all, 0, ZONE_MENU),
    EditorActionSpec("list_sort", R.drawable.ic_sort, R.string.list_sort_alpha, 0, ZONE_MENU),
    EditorActionSpec("list_delete_checked", R.drawable.ic_delete, R.string.list_delete_checked, 0, ZONE_MENU),
    EditorActionSpec("open_in_keep", R.drawable.ic_open_new, R.string.open_in_keep, 1, ZONE_MENU),
    EditorActionSpec("share", R.drawable.ic_share, R.string.share_note, 1, ZONE_MENU),
    EditorActionSpec("hide_content", R.drawable.ic_lock, R.string.hide_content, 1, ZONE_MENU),
    EditorActionSpec("archive", R.drawable.ic_archive, R.string.archive_note, 2, ZONE_MENU),
    EditorActionSpec("delete", R.drawable.ic_delete, R.string.move_to_trash, 2, ZONE_MENU),
    // A shortcut to the settings of these buttons; the last entry of the menu, apart from the others.
    EditorActionSpec("editor_settings", R.drawable.ic_settings, R.string.editor_buttons_title, 5, ZONE_MENU),
    EditorActionSpec("note", R.drawable.ic_notes, R.string.editor_buttons_note, 4, ZONE_NOTE),
)

/** The checklist tools; in the menu they share one entry that opens a page of their own. */
private val LIST_TOOLS = setOf("list_number", "list_check_all", "list_uncheck_all", "list_sort", "list_delete_checked")

fun isListTool(id: String) = id in LIST_TOOLS

const val NOTE_ID = "note"

/** What the sheets of the bottom bar hold; set in the note's place, each shows itself there instead of opening a sheet. */
val NOTE_INLINE = setOf("palette", "labels")

/**
 * What can be set beside the note: the actions of the menu, which show as buttons with a name, the picture button, and the
 * color and label sections, which show themselves. The symbols of the bars that only do something (save, close, pin...) cannot.
 */
fun canSitInNote(id: String): Boolean =
    id == NOTE_ID || id == "image" || id in NOTE_INLINE || EDITOR_ACTIONS.firstOrNull { it.id == id }?.defaultZone == ZONE_MENU

/** Where each action stands: its zone, and the order of all actions (the order inside a zone is the order here). */
data class EditorLayout(
    val zones: Map<String, Int> = emptyMap(), val order: List<String> = EDITOR_ACTIONS.map { it.id }, val align: Int = EDITOR_ALIGN_CENTER,
) {
    private val byId = EDITOR_ACTIONS.associateBy { it.id }

    /**
     * Only what has a name to show can stand in the note's place: the actions of the menu, not the symbols of the bars. A zone
     * that is not known (any more) or not allowed for the action means its default place; the note itself never leaves its place.
     */
    fun zoneOf(id: String): Int {
        if (id == NOTE_ID) return ZONE_NOTE
        val default = byId[id]?.defaultZone ?: ZONE_HIDDEN
        val zone = zones[id] ?: return default
        return if (zone !in ZONE_TOP_LEFT..ZONE_NOTE || allowedZones(id)?.let { zone !in it } == true) default else zone
    }

    /**
     * Where the button may be moved to: the note only within its own place, the symbols of the bars anywhere but there, "more"
     * not into the menu it opens, the actions of the menu anywhere.
     */
    fun allowedZones(id: String): Set<Int>? = when {
        id == NOTE_ID -> setOf(ZONE_NOTE)
        id == "more" -> setOf(ZONE_TOP_LEFT, ZONE_TOP_RIGHT, ZONE_BOTTOM_LEFT, ZONE_BOTTOM_RIGHT, ZONE_HIDDEN)
        !canSitInNote(id) -> setOf(ZONE_TOP_LEFT, ZONE_TOP_RIGHT, ZONE_BOTTOM_LEFT, ZONE_BOTTOM_RIGHT, ZONE_MENU, ZONE_HIDDEN)
        else -> null
    }

    /** The actions of a zone in their order; actions that are not known (any more) or new ones are handled here. */
    fun inZone(zone: Int): List<EditorActionSpec> =
        (order + EDITOR_ACTIONS.map { it.id }).distinct().mapNotNull { byId[it] }.filter { zoneOf(it.id) == zone }

    /** [id] moved to [zone], in front of [beforeId] (the end of the zone if that is null). */
    fun moved(id: String, zone: Int, beforeId: String?): EditorLayout {
        if (allowedZones(id)?.let { zone !in it } == true) return this
        val ids = (order + EDITOR_ACTIONS.map { it.id }).distinct().filter { it != id && it in byId }
        val at = beforeId?.let { ids.indexOf(it) }?.takeIf { it >= 0 }
        val next = if (at != null) ids.take(at) + id + ids.drop(at) else {
            // The end of a zone: right after the last action that is in it, so it does not jump to the end of everything.
            val last = ids.indexOfLast { zoneOf(it) == zone }
            if (last >= 0) ids.take(last + 1) + id + ids.drop(last + 1) else ids + id
        }
        return EditorLayout(zones + (id to zone), next, align)
    }

    fun toJson(): String = JSONObject()
        .put("order", JSONArray(order))
        .put("align", align)
        .put("zones", JSONObject().also { o -> zones.forEach { (k, v) -> o.put(k, v) } })
        .toString()

    /** The layout as a file of its own for saving it; the marker tells it from other files. */
    fun toFileJson(): String = JSONObject(toJson()).put("editorLayout", 1).toString(2)

    companion object {
        /** A layout read from a file; throws if the text is not one. */
        fun fromFileJson(text: String): EditorLayout {
            require(JSONObject(text).has("editorLayout")) { "Not an editor layout file" }
            return fromJson(text)
        }

        fun fromJson(json: String?): EditorLayout = runCatching {
            val o = JSONObject(json ?: return EditorLayout())
            val order = o.getJSONArray("order").let { a -> (0 until a.length()).map { a.getString(it) } }
            val z = o.getJSONObject("zones")
            EditorLayout(z.keys().asSequence().associateWith { z.getInt(it) }, order, o.optInt("align", EDITOR_ALIGN_CENTER).coerceIn(EDITOR_ALIGN_CENTER, EDITOR_ALIGN_BOTTOM))
        }.getOrDefault(EditorLayout())
    }
}

/** What an action does and looks like in this moment of the editor (the screen builds one for each action). */
class EditorActionState(
    @DrawableRes val icon: Int,
    val label: String,
    val onClick: () -> Unit,
    val visible: Boolean = true,
    val enabled: Boolean = true,
    /** Not null for an action that is on or off: a toggle in a bar, a check in the menu. */
    val checked: Boolean? = null,
    val dimmed: Boolean = false,
    val tint: Color? = null,
    /** Drawn next to the button; the overflow menu hangs here. */
    val popup: (@Composable () -> Unit)? = null,
)

/**
 * One bar of the editor: the actions of its left zone at the left edge, those of its right zone at the right edge. When they
 * do not fit in one line, the bar grows: the left ones stay in the first line, the right ones follow below at the right edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorBar(
    left: List<EditorActionSpec>, right: List<EditorActionSpec>, states: Map<String, EditorActionState>, sidePadding: Dp = 12.dp,
    highlightId: String? = null,
) {
    val shown = (left + right).count { states[it.id]?.visible == true }
    if (shown == 0) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A button is 40 dp wide with 2 dp between (the save button a little more).
        val fits = (maxWidth - sidePadding * 2) / 44.dp >= shown
        val padding = Modifier.fillMaxWidth().padding(horizontal = sidePadding, vertical = 4.dp)
        if (fits) Row(padding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            left.forEach { EditorActionButton(it, states[it.id], it.id == highlightId) }
            Spacer(Modifier.weight(1f))
            right.forEach { EditorActionButton(it, states[it.id], it.id == highlightId) }
        } else Column(padding) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                left.forEach { EditorActionButton(it, states[it.id], it.id == highlightId) }
            }
            FlowRow(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(2.dp),
            ) { right.forEach { EditorActionButton(it, states[it.id], it.id == highlightId) } }
        }
    }
}

@Composable
private fun EditorActionButton(spec: EditorActionSpec, state: EditorActionState?, highlighted: Boolean = false) {
    if (state == null || !state.visible) return
    val modifier = Modifier.size(40.dp)
    val icon: @Composable () -> Unit = {
        // Read inside the button: its content color is the grayed one while it is disabled.
        val tint = state.tint ?: LocalContentColor.current
        Icon(
            painterResource(state.icon), contentDescription = state.label,
            tint = if (state.dimmed) tint.copy(alpha = 0.38f) else tint,
        )
    }
    Box(if (highlighted) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier) {
        when {
            spec.id == "save" -> FilledIconButton(
                onClick = state.onClick, enabled = state.enabled, shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(horizontal = 4.dp).size(40.dp),
            ) { Icon(painterResource(state.icon), contentDescription = state.label) }
            state.checked != null -> FilledTonalIconToggleButton(
                checked = state.checked, onCheckedChange = { state.onClick() }, enabled = state.enabled, shapes = IconButtonDefaults.toggleableShapes(),
                colors = if (spec.id == "reminder") quietToggleColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) else quietToggleColors(),
                modifier = modifier,
            ) { icon() }
            else -> IconButton(onClick = state.onClick, enabled = state.enabled, shapes = IconButtonDefaults.shapes(), modifier = modifier) { icon() }
        }
        state.popup?.invoke()
    }
}

/** The groups of the overflow menu: the actions of the menu zone that are possible now, checklist tools under one entry. */
internal fun menuGroups(
    layout: EditorLayout, states: Map<String, EditorActionState>, page: Int, toPage: (Int) -> Unit, listToolsLabel: String, backIcon: Int, forwardIcon: @Composable () -> Unit,
    closeMenu: () -> Unit,
): List<List<MenuEntry>> {
    val shown = layout.inZone(ZONE_MENU).filter { states[it.id]?.visible == true }
    fun entry(spec: EditorActionSpec): MenuEntry {
        val state = states.getValue(spec.id)
        return MenuEntry(state.label, state.icon, checked = state.checked, onClick = { closeMenu(); state.onClick() })
    }
    if (page == 1) {
        val tools = shown.filter { isListTool(it.id) }.map(::entry)
        return listOf(listOf(MenuEntry(listToolsLabel, backIcon) { toPage(0) }), tools)
    }
    val groups = mutableListOf<MutableList<MenuEntry>>()
    var lastGroup = -1
    var toolsAdded = false
    shown.forEach { spec ->
        val entry = if (isListTool(spec.id)) {
            if (toolsAdded) return@forEach
            toolsAdded = true
            MenuEntry(listToolsLabel, R.drawable.ic_done, trailing = forwardIcon) { toPage(1) }
        } else entry(spec)
        if (spec.group != lastGroup || groups.isEmpty()) groups.add(mutableListOf())
        lastGroup = spec.group
        groups.last().add(entry)
    }
    return groups
}

/**
 * The actions of the note's place: buttons with a name side by side as far as they fit, and what is in [inline] (the color and the
 * label section) as a block of its own, in the order of the layout.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NoteActionButtons(
    specs: List<EditorActionSpec>, states: Map<String, EditorActionState>, highlightId: String? = null,
    inline: Map<String, @Composable (Boolean) -> Unit> = emptyMap(),
) {
    val shown = specs.filter { states[it.id]?.visible == true }
    if (shown.isEmpty()) return
    // Runs of buttons share a row; a block that shows itself breaks the run.
    val runs = mutableListOf<List<EditorActionSpec>>()
    shown.forEach { spec ->
        if (spec.id in inline) runs.add(listOf(spec))
        else if (runs.isNotEmpty() && runs.last().first().id !in inline) runs[runs.lastIndex] = runs.last() + spec
        else runs.add(listOf(spec))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        runs.forEach { run ->
            val block = inline[run.first().id]
            if (block != null) block(run.first().id == highlightId)
            else FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                run.forEach { spec ->
                    val state = states.getValue(spec.id)
                    TextButton(
                        onClick = state.onClick, enabled = state.enabled, shapes = ButtonDefaults.shapes(),
                        modifier = if (spec.id == highlightId) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier,
                    ) {
                        Icon(painterResource(state.icon), contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(state.label)
                    }
                }
            }
        }
    }
}
