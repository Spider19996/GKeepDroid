package com.gkeepdroid

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    // The text styles of the selected text (or of what is typed next): one button with a list of them, off until it is placed;
    // set in the note's place it is a bar of its own.
    ButtonSpec("format", R.drawable.ic_text_format, R.string.format_title, 3, ZONE_HIDDEN),
    // Next to undo: a delete that was not meant can be taken back at once.
    ButtonSpec("list_delete_checked", R.drawable.ic_delete_sweep, R.string.list_delete_checked, 3, ZONE_BOTTOM_RIGHT),
    ButtonSpec("undo", R.drawable.ic_undo, R.string.undo, 3, ZONE_BOTTOM_RIGHT),
    ButtonSpec("redo", R.drawable.ic_redo, R.string.redo, 3, ZONE_BOTTOM_RIGHT),
    ButtonSpec("convert", R.drawable.ic_checklist, R.string.editor_convert, 0, ZONE_MENU),
    ButtonSpec("list_number", R.drawable.ic_list_numbered, R.string.list_number, 0, ZONE_MENU),
    ButtonSpec("list_check_all", R.drawable.ic_done_all, R.string.list_check_all, 0, ZONE_MENU),
    ButtonSpec("list_uncheck_all", R.drawable.ic_remove_done, R.string.list_uncheck_all, 0, ZONE_MENU),
    ButtonSpec("list_sort", R.drawable.ic_sort, R.string.list_sort_alpha, 0, ZONE_MENU),
    ButtonSpec("open_in_keep", R.drawable.ic_open_new, R.string.open_in_keep, 1, ZONE_MENU),
    ButtonSpec("share", R.drawable.ic_share, R.string.share_note, 1, ZONE_MENU),
    ButtonSpec("hide_content", R.drawable.ic_visibility_off, R.string.hide_content, 1, ZONE_MENU),
    ButtonSpec("archive", R.drawable.ic_archive, R.string.archive_note, 2, ZONE_MENU),
    ButtonSpec("delete", R.drawable.ic_delete, R.string.move_to_trash, 2, ZONE_MENU),
    // A shortcut to the settings of these buttons; the last entry of the menu, apart from the others.
    ButtonSpec("editor_settings", R.drawable.ic_settings, R.string.editor_buttons_title, 5, ZONE_MENU),
    ButtonSpec("note", R.drawable.ic_notes, R.string.editor_buttons_note, 4, ZONE_NOTE),
    // Under the items of a checklist, as it always was; it can also be a symbol in a bar or an entry of the menu.
    ButtonSpec("add_item", R.drawable.ic_add, R.string.add_item, 0, ZONE_NOTE),
    // Beside the add button, under the items.
    ButtonSpec("add_divider", R.drawable.ic_divider, R.string.add_divider, 0, ZONE_NOTE),
)

/** The checklist tools; in the menu they share one entry that opens a page of their own. */
private val LIST_TOOLS = setOf("list_number", "list_check_all", "list_uncheck_all", "list_sort", "list_delete_checked", "add_item", "add_divider")

fun isListTool(id: String) = id in LIST_TOOLS

const val NOTE_ID = "note"

/** Tells a saved layout file of the editor from the files of other editors. */
const val EDITOR_LAYOUT_MARKER = "editorLayout"

/** What the sheets of the bottom bar (and the list of text styles) hold; set in the note's place, each shows itself there instead of opening. */
val NOTE_INLINE = setOf("palette", "labels", "format")

/** Where an action of the editor may be moved to: the note only within its own place, every other action anywhere (the note's place included, as a button with a name). */
private fun editorAllowedZones(id: String): Set<Int>? = if (id == NOTE_ID) setOf(ZONE_NOTE) else null

/** The editor's buttons and its rules; the layout of them is a [ButtonLayout] of this catalog. */
val EDITOR_CATALOG = ButtonCatalog(
    EDITOR_ACTIONS, hiddenZone = ZONE_HIDDEN, validZone = { it in ZONE_TOP_LEFT..ZONE_NOTE }, allowedFor = ::editorAllowedZones,
    optionRange = EDITOR_ALIGN_CENTER..EDITOR_ALIGN_BOTTOM,
    defaultAnchors = mapOf("list_delete_checked" to "undo"),
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

/** Buttons that always stay in their bar: closing, saving and the menu the others move into. */
private val NEVER_OVERFLOW = setOf("close", "save", "more")

/**
 * One bar of the editor: the actions of its left zone at the left edge, those of its right zone at the right edge. When they
 * do not fit in one line, the buttons move closer and get narrower; when they would get too small to hit, the ones nearest the
 * middle of the bar move into the menu (they are given to [onOverflow]), so the bar always stays one line. [withMore] is the
 * menu button, for a bar that has to show it when something moves out of it (the menu button is not in a bar of its own).
 * Without [onOverflow] (the preview of the settings) every button stays, as narrow as it has to be.
 */
@Composable
internal fun EditorBar(
    left: List<ButtonSpec>, right: List<ButtonSpec>, states: Map<String, EditorActionState>, sidePadding: Dp = 12.dp,
    highlightId: String? = null, onOverflow: ((List<String>) -> Unit)? = null, withMore: ButtonSpec? = null,
) {
    val visibleLeft = left.filter { states[it.id]?.visible == true }
    val visibleRight = right.filter { states[it.id]?.visible == true }
    val shown = visibleLeft.size + visibleRight.size
    if (shown == 0) {
        if (onOverflow != null) SideEffect { onOverflow(emptyList()) }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A button is 40 dp wide with 2 dp between (the save button a little more); squeezed, down to 32 dp with nothing between.
        val room = maxWidth - sidePadding * 2
        val fitting = (room / 32.dp).toInt().coerceAtLeast(1)
        var keepLeft = visibleLeft
        var keepRight = visibleRight
        val moved = mutableListOf<String>()
        if (onOverflow != null && shown > fitting) {
            val addMore = withMore != null && withMore !in visibleLeft && withMore !in visibleRight
            val limit = fitting - (if (addMore) 1 else 0)
            // From the middle outwards: the end of the left side first, then the start of the right side.
            val candidates = (visibleLeft.reversed() + visibleRight).filter { it.id !in NEVER_OVERFLOW }
            for (spec in candidates) {
                if (keepLeft.size + keepRight.size <= limit) break
                moved += spec.id
                keepLeft = keepLeft - spec
                keepRight = keepRight - spec
            }
            if (addMore && moved.isNotEmpty()) keepRight = keepRight + withMore!!
        }
        if (onOverflow != null) SideEffect { onOverflow(moved.toList()) }
        val count = keepLeft.size + keepRight.size
        val slot = room / count.coerceAtLeast(1)
        val width = if (slot >= 44.dp) 40.dp else slot
        val gap = if (slot >= 44.dp) 2.dp else 0.dp
        Row(
            Modifier.fillMaxWidth().padding(horizontal = sidePadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            keepLeft.forEach { EditorActionButton(it, states[it.id], it.id == highlightId, width) }
            Spacer(Modifier.weight(1f))
            keepRight.forEach { spec ->
                // The menu button can be here only because something moved into the menu.
                val state = states[spec.id]?.let { if (spec == withMore) forceVisible(it) else it }
                EditorActionButton(spec, state, spec.id == highlightId, width)
            }
        }
    }
}

private fun forceVisible(state: EditorActionState) = EditorActionState(
    state.icon, state.label, state.onClick, visible = true, enabled = state.enabled, checked = state.checked, dimmed = state.dimmed,
    tint = state.tint, popup = state.popup,
)

@Composable
private fun EditorActionButton(spec: ButtonSpec, state: EditorActionState?, highlighted: Boolean = false, width: Dp = 40.dp) {
    if (state == null || !state.visible) return
    val modifier = Modifier.size(width, 40.dp)
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
                onClick = state.onClick, enabled = state.enabled, shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.padding(horizontal = if (width < 40.dp) 0.dp else 4.dp).size(width, 40.dp),
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
    closeMenu: () -> Unit, overflow: List<String> = emptyList(), formatEntries: List<MenuEntry> = emptyList(),
): List<List<MenuEntry>> {
    val shown = layout.inZone(ZONE_MENU).filter { states[it.id]?.visible == true && it.id !in overflow }
    val formatLabel = states["format"]?.label.orEmpty()
    fun entry(spec: ButtonSpec): MenuEntry {
        val state = states.getValue(spec.id)
        // The text styles are a page of their own, as the checklist tools are.
        if (spec.id == "format") return MenuEntry(state.label, state.icon, trailing = forwardIcon) { toPage(2) }
        return MenuEntry(state.label, state.icon, checked = state.checked, onClick = { closeMenu(); state.onClick() })
    }
    if (page == 1) {
        val tools = shown.filter { isListTool(it.id) }.map(::entry)
        return listOf(listOf(MenuEntry(listToolsLabel, backIcon) { toPage(0) }), tools)
    }
    if (page == 2) return listOf(listOf(MenuEntry(formatLabel, backIcon) { toPage(0) }), formatEntries)
    val groups = mutableListOf<MutableList<MenuEntry>>()
    // What did not fit into its bar comes first, each as itself (a checklist tool too), in the order of the bars.
    val moved = overflow.mapNotNull { id -> layout.catalog.byId[id]?.takeIf { states[id]?.visible == true } }
    if (moved.isNotEmpty()) groups.add(moved.map(::entry).toMutableList())
    var lastGroup = -1
    var toolsAdded = false
    shown.forEach { spec ->
        val entry = if (isListTool(spec.id)) {
            if (toolsAdded) return@forEach
            toolsAdded = true
            MenuEntry(listToolsLabel, R.drawable.ic_list_tools, trailing = forwardIcon) { toPage(1) }
        } else entry(spec)
        if (spec.group != lastGroup || groups.size == (if (moved.isNotEmpty()) 1 else 0)) groups.add(mutableListOf())
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
            else BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Buttons of a row share its width equally (long names wrap), so two of them sit side by side on a phone;
                // a row takes as many as leave each at least 150 dp.
                val perRow = (maxWidth / 150.dp).toInt().coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    run.chunked(perRow).forEach { rowSpecs ->
                        // A row that is not full keeps the width of the full ones, so the columns line up.
                        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowSpecs.forEach { spec ->
                                val state = states.getValue(spec.id)
                                val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                                    Icon(painterResource(state.icon), contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                    // A name of two words always breaks between them, so the buttons of a row look alike.
                                    Text(state.label.split(" ").let { if (it.size == 2) it.joinToString("\n") else state.label }, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                                }
                                val tight = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                val mark = Modifier.fillMaxSize().then(
                                    if (spec.id == highlightId) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier,
                                )
                                // All buttons of the note's place have the outline the add button always had.
                                val tint = noteButtonTint(spec.id)
                                // The box is what the row divides; a menu of the action (the one of "more") hangs on it.
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = state.onClick, enabled = state.enabled, shapes = ButtonDefaults.shapes(), modifier = mark, contentPadding = tight,
                                        colors = if (tint != null) ButtonDefaults.outlinedButtonColors(contentColor = tint) else ButtonDefaults.outlinedButtonColors(),
                                        content = content,
                                    )
                                    state.popup?.invoke()
                                }
                            }
                            repeat(perRow - rowSpecs.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The color of a button in the note's place, by what it does, so the buttons differ at a glance: adding and ticking green, deleting
 * red, changing the form or the order blue, undoing and archiving amber, going elsewhere (Keep, sharing) teal, hiding purple.
 * The settings shortcut keeps the normal color.
 */
@Composable
private fun noteButtonTint(id: String): Color? {
    // Lighter tones on a dark screen, darker ones on a light one, so the text stays readable on both.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    fun tone(onDark: Long, onLight: Long) = Color(if (dark) onDark else onLight)
    return when (id) {
        "add_item", "add_divider", "list_check_all" -> tone(0xFF81C784, 0xFF2E7D32)
        "more" -> null
        "list_delete_checked", "delete" -> tone(0xFFEF9A9A, 0xFFC62828)
        "convert", "list_number", "list_sort" -> tone(0xFF90CAF9, 0xFF1565C0)
        "list_uncheck_all", "archive" -> tone(0xFFFFCC80, 0xFFE65100)
        "open_in_keep", "share" -> tone(0xFF80CBC4, 0xFF00695C)
        "hide_content" -> tone(0xFFCE93D8, 0xFF6A1B9A)
        "format" -> tone(0xFF90CAF9, 0xFF1565C0)
        "save" -> tone(0xFF81C784, 0xFF2E7D32)
        "close" -> tone(0xFFEF9A9A, 0xFFC62828)
        "undo", "redo" -> tone(0xFF90CAF9, 0xFF1565C0)
        "reminder", "pin", "pin_bottom" -> tone(0xFFFFCC80, 0xFFE65100)
        else -> null
    }
}

/** One text style of the format button: its symbol, its name, whether it is on where the cursor is, and switching it. */
internal class FormatOption(@DrawableRes val icon: Int, val label: String, val active: Boolean, val toggle: () -> Unit)

/** The text styles as a bar of their own: what the format button shows when it is set in the note's place. */
@Composable
internal fun FormatBar(options: List<FormatOption>, highlighted: Boolean = false) {
    // The same pill and outline as the buttons with a name beside it.
    val shape = RoundedCornerShape(percent = 50)
    val outline = ButtonDefaults.outlinedButtonBorder(true)
    Row(
        Modifier.fillMaxWidth()
            .then(if (highlighted) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier.border(outline, shape))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            FilledTonalIconToggleButton(
                checked = option.active, onCheckedChange = { option.toggle() }, shapes = IconButtonDefaults.toggleableShapes(),
                colors = quietToggleColors(),
            ) { Icon(painterResource(option.icon), contentDescription = option.label) }
        }
    }
}
