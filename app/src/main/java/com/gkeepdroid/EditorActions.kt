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

/** Every action of the editor, in the order of the default layout. The menu is a place for them like the two bars are. */
val EDITOR_ACTIONS = listOf(
    ButtonSpec("close", R.drawable.ic_close, R.string.discard_action, 3, ZONE_TOP_LEFT),
    ButtonSpec("pin", R.drawable.ic_push_pin_outline, R.string.pin_note, 3, ZONE_TOP_RIGHT),
    ButtonSpec("pin_bottom", R.drawable.ic_pin_bottom, R.string.pin_to_bottom, 3, ZONE_TOP_RIGHT),
    ButtonSpec("reminder", R.drawable.ic_alarm, R.string.reminders_title, 3, ZONE_TOP_RIGHT),
    ButtonSpec("save", R.drawable.ic_done, R.string.save, 3, ZONE_TOP_RIGHT),
    ButtonSpec("more", R.drawable.ic_more_vert, R.string.more_actions, 3, ZONE_TOP_RIGHT),
    ButtonSpec("palette", R.drawable.ic_palette, R.string.color_and_background, 3, ZONE_BOTTOM_LEFT),
    ButtonSpec("labels", R.drawable.ic_label, R.string.labels_title, 3, ZONE_BOTTOM_LEFT),
    ButtonSpec("image", R.drawable.ic_image, R.string.image_add_keep, 3, ZONE_BOTTOM_LEFT),
    ButtonSpec("undo", R.drawable.ic_undo, R.string.undo, 3, ZONE_BOTTOM_RIGHT),
    ButtonSpec("redo", R.drawable.ic_redo, R.string.redo, 3, ZONE_BOTTOM_RIGHT),
    ButtonSpec("convert", R.drawable.ic_checklist, R.string.editor_convert, 0, ZONE_MENU),
    ButtonSpec("list_number", R.drawable.ic_checklist, R.string.list_number, 0, ZONE_MENU),
    ButtonSpec("list_check_all", R.drawable.ic_done, R.string.list_check_all, 0, ZONE_MENU),
    ButtonSpec("list_uncheck_all", R.drawable.ic_close, R.string.list_uncheck_all, 0, ZONE_MENU),
    ButtonSpec("list_sort", R.drawable.ic_sort, R.string.list_sort_alpha, 0, ZONE_MENU),
    ButtonSpec("list_delete_checked", R.drawable.ic_delete, R.string.list_delete_checked, 0, ZONE_MENU),
    ButtonSpec("open_in_keep", R.drawable.ic_open_new, R.string.open_in_keep, 1, ZONE_MENU),
    ButtonSpec("share", R.drawable.ic_share, R.string.share_note, 1, ZONE_MENU),
    ButtonSpec("hide_content", R.drawable.ic_lock, R.string.hide_content, 1, ZONE_MENU),
    ButtonSpec("archive", R.drawable.ic_archive, R.string.archive_note, 2, ZONE_MENU),
    ButtonSpec("delete", R.drawable.ic_delete, R.string.move_to_trash, 2, ZONE_MENU),
    // A shortcut to the settings of these buttons; the last entry of the menu, apart from the others.
    ButtonSpec("editor_settings", R.drawable.ic_settings, R.string.editor_buttons_title, 5, ZONE_MENU),
    ButtonSpec("note", R.drawable.ic_notes, R.string.editor_buttons_note, 4, ZONE_NOTE),
)

/** The checklist tools; in the menu they share one entry that opens a page of their own. */
private val LIST_TOOLS = setOf("list_number", "list_check_all", "list_uncheck_all", "list_sort", "list_delete_checked")

fun isListTool(id: String) = id in LIST_TOOLS

const val NOTE_ID = "note"

/** Tells a saved layout file of the editor from the files of other editors. */
const val EDITOR_LAYOUT_MARKER = "editorLayout"

/** What the sheets of the bottom bar hold; set in the note's place, each shows itself there instead of opening a sheet. */
val NOTE_INLINE = setOf("palette", "labels")

/**
 * What can be set beside the note: the actions of the menu, which show as buttons with a name, the picture button, and the
 * color and label sections, which show themselves. The symbols of the bars that only do something (save, close, pin...) cannot.
 */
fun canSitInNote(id: String): Boolean =
    id == NOTE_ID || id == "image" || id in NOTE_INLINE || EDITOR_ACTIONS.firstOrNull { it.id == id }?.defaultZone == ZONE_MENU

/**
 * Where an action of the editor may be moved to: the note only within its own place, the symbols of the bars anywhere but there,
 * "more" not into the menu it opens, the actions of the menu anywhere.
 */
private fun editorAllowedZones(id: String): Set<Int>? = when {
    id == NOTE_ID -> setOf(ZONE_NOTE)
    id == "more" -> setOf(ZONE_TOP_LEFT, ZONE_TOP_RIGHT, ZONE_BOTTOM_LEFT, ZONE_BOTTOM_RIGHT, ZONE_HIDDEN)
    !canSitInNote(id) -> setOf(ZONE_TOP_LEFT, ZONE_TOP_RIGHT, ZONE_BOTTOM_LEFT, ZONE_BOTTOM_RIGHT, ZONE_MENU, ZONE_HIDDEN)
    else -> null
}

/** The editor's buttons and its rules; the layout of them is a [ButtonLayout] of this catalog. */
val EDITOR_CATALOG = ButtonCatalog(
    EDITOR_ACTIONS, hiddenZone = ZONE_HIDDEN, validZone = { it in ZONE_TOP_LEFT..ZONE_NOTE }, allowedFor = ::editorAllowedZones,
    optionRange = EDITOR_ALIGN_CENTER..EDITOR_ALIGN_BOTTOM,
)

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
    left: List<ButtonSpec>, right: List<ButtonSpec>, states: Map<String, EditorActionState>, sidePadding: Dp = 12.dp,
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
private fun EditorActionButton(spec: ButtonSpec, state: EditorActionState?, highlighted: Boolean = false) {
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
    layout: ButtonLayout, states: Map<String, EditorActionState>, page: Int, toPage: (Int) -> Unit, listToolsLabel: String, backIcon: Int, forwardIcon: @Composable () -> Unit,
    closeMenu: () -> Unit,
): List<List<MenuEntry>> {
    val shown = layout.inZone(ZONE_MENU).filter { states[it.id]?.visible == true }
    fun entry(spec: ButtonSpec): MenuEntry {
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
    specs: List<ButtonSpec>, states: Map<String, EditorActionState>, highlightId: String? = null,
    inline: Map<String, @Composable (Boolean) -> Unit> = emptyMap(),
) {
    val shown = specs.filter { states[it.id]?.visible == true }
    if (shown.isEmpty()) return
    // Runs of buttons share a row; a block that shows itself breaks the run.
    val runs = mutableListOf<List<ButtonSpec>>()
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
