package com.gkeepdroid

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
    /** What holding the button does, if anything (adding an item: the menu of categories). */
    val onLongClick: (() -> Unit)? = null,
)

/**
 * Holding the element calls [onLongPress]; the tap that the element itself handles is then left out. The press is watched before
 * the element sees it, so buttons keep their own tap.
 */
internal fun Modifier.longPressFirst(onLongPress: (() -> Unit)?): Modifier = if (onLongPress == null) this else pointerInput(onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") true
        }
        if (released == null) {
            onLongPress()
            // The rest of the press is taken, so the button does not also count it as a tap.
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }
}

/**
 * One bar of the editor: the actions of its left zone at the left edge, those of its right zone at the right edge. When they
 * do not fit in one line, the buttons move closer and get narrower; when they would get too small to hit, the bar keeps them
 * at their size and scrolls sideways instead, so it always stays one line.
 */
@Composable
internal fun EditorBar(
    left: List<ButtonSpec>, right: List<ButtonSpec>, states: Map<String, EditorActionState>, sidePadding: Dp = 12.dp,
    highlightId: String? = null, snapBack: Boolean = true,
    /** The title field, for when the layout puts the title in this bar: it takes the room the buttons leave. */
    titleField: (@Composable (Modifier) -> Unit)? = null,
) {
    val hasTitle = titleField != null && (left + right).any { it.id == TITLE_ID }
    val shown = (left + right).count { it.id != TITLE_ID && states[it.id]?.visible == true }
    if (shown == 0 && !hasTitle) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A button is 40 dp wide with 2 dp between (the save button a little more); squeezed, down to 32 dp with nothing between.
        // A title field in the bar is given at least 120 dp before the buttons share what is left.
        val slot = (maxWidth - sidePadding * 2 - (if (hasTitle) 120.dp else 0.dp)) / shown.coerceAtLeast(1)
        if (slot >= 32.dp) {
            val width = if (slot >= 44.dp) 40.dp else slot
            val gap = if (slot >= 44.dp) 2.dp else 0.dp
            Row(
                Modifier.fillMaxWidth().padding(horizontal = sidePadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                left.forEach { if (it.id == TITLE_ID) titleField?.invoke(Modifier.weight(1f).padding(horizontal = 4.dp)) else EditorActionButton(it, states[it.id], it.id == highlightId, width) }
                if (!hasTitle) Spacer(Modifier.weight(1f))
                right.forEach { if (it.id == TITLE_ID) titleField?.invoke(Modifier.weight(1f).padding(horizontal = 4.dp)) else EditorActionButton(it, states[it.id], it.id == highlightId, width) }
            }
        } else {
            // Too many for one line: the left and the right ones in a row that is pushed sideways, a gap between the two sides.
            // It rests where the two sides meet: a side that fits stays at its edge and the full side takes the rest, its
            // surplus out of sight at its outer edge; when both are full they meet in the middle. Where more buttons wait, the
            // row fades out and a small arrow points there. A tap on a button brings it back to rest (unless switched off).
            val scroll = rememberScrollState()
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current
            var leftWidth by remember { mutableIntStateOf(0) }
            val gapWidth = 16.dp
            val rest = with(density) { (sidePadding + gapWidth / 2 - maxWidth / 2).toPx() }.toInt() + leftWidth
            val restAt = rest.coerceIn(0, scroll.maxValue)
            LaunchedEffect(restAt) { scroll.scrollTo(restAt) }
            fun withReturn(state: EditorActionState?): EditorActionState? = if (state == null || !snapBack) state else EditorActionState(
                state.icon, state.label, { state.onClick(); scope.launch { scroll.animateScrollTo(restAt) } },
                visible = state.visible, enabled = state.enabled, checked = state.checked, dimmed = state.dimmed, tint = state.tint, popup = state.popup,
            )
            val fade = 32.dp
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val edge = fade.toPx()
                            if (scroll.canScrollBackward) drawRect(
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = edge), blendMode = BlendMode.DstIn,
                            )
                            if (scroll.canScrollForward) drawRect(
                                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - edge, endX = size.width),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                        .horizontalScroll(scroll).padding(horizontal = sidePadding, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.onSizeChanged { leftWidth = it.width },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) { left.forEach { if (it.id == TITLE_ID) titleField?.invoke(Modifier.width(160.dp)) else EditorActionButton(it, withReturn(states[it.id]), it.id == highlightId) } }
                    Spacer(Modifier.width(gapWidth))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        right.forEach { if (it.id == TITLE_ID) titleField?.invoke(Modifier.width(160.dp)) else EditorActionButton(it, withReturn(states[it.id]), it.id == highlightId) }
                    }
                }
                val hint = MaterialTheme.colorScheme.onSurfaceVariant
                if (scroll.canScrollBackward) Icon(
                    painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = hint,
                    modifier = Modifier.align(Alignment.CenterStart).size(16.dp).graphicsLayer { rotationZ = 180f },
                )
                if (scroll.canScrollForward) Icon(
                    painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = hint,
                    modifier = Modifier.align(Alignment.CenterEnd).size(16.dp),
                )
            }
        }
    }
}

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
    Box((if (highlighted) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier).longPressFirst(state.onLongClick)) {
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
    closeMenu: () -> Unit, formatEntries: List<MenuEntry> = emptyList(),
): List<List<MenuEntry>> {
    val shown = layout.inZone(ZONE_MENU).filter { states[it.id]?.visible == true }
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
    var lastGroup = -1
    var toolsAdded = false
    shown.forEach { spec ->
        val entry = if (isListTool(spec.id)) {
            if (toolsAdded) return@forEach
            toolsAdded = true
            MenuEntry(listToolsLabel, R.drawable.ic_list_tools, trailing = forwardIcon) { toPage(1) }
        } else entry(spec)
        if (spec.group != lastGroup || groups.isEmpty()) groups.add(mutableListOf())
        lastGroup = spec.group
        groups.last().add(entry)
    }
    return groups
}

/**
 * What the note's place holds, in the order of the layout: the buttons and blocks between, and the [title] field and the [body] (the
 * text or the items) where the layout puts them. The title may be missing here: it can be in a bar.
 */
@Composable
internal fun NoteZoneContent(
    specs: List<ButtonSpec>, states: Map<String, EditorActionState>, highlightId: String? = null,
    inline: Map<String, @Composable (Boolean) -> Unit> = emptyMap(), title: @Composable () -> Unit, body: @Composable () -> Unit,
) {
    val run = mutableListOf<ButtonSpec>()
    fun flushNeeded() = run.isNotEmpty()
    specs.forEach { spec ->
        when (spec.id) {
            TITLE_ID, NOTE_ID -> {
                if (flushNeeded()) { NoteActionButtons(run.toList(), states, highlightId, inline); run.clear() }
                if (spec.id == TITLE_ID) title() else body()
            }
            else -> run += spec
        }
    }
    if (flushNeeded()) NoteActionButtons(run.toList(), states, highlightId, inline)
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
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowSpecs.forEach { spec ->
                                val state = states.getValue(spec.id)
                                val content: @Composable RowScope.() -> Unit = {
                                    Icon(painterResource(state.icon), contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                    // A name of two words always breaks between them, so the buttons of a row look alike.
                                    Text(state.label.split(" ").let { if (it.size == 2) it.joinToString("\n") else state.label }, maxLines = 2, textAlign = TextAlign.Start)
                                }
                                val tight = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                val mark = Modifier.fillMaxSize().then(
                                    if (spec.id == highlightId) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier,
                                )
                                // All buttons of the note's place have the outline the add button always had.
                                val tint = noteButtonTint(spec.id)
                                // The box is what the row divides; a menu of the action (the one of "more") hangs on it.
                                Box(Modifier.weight(1f).fillMaxHeight().longPressFirst(state.onLongClick)) {
                                    OutlinedButton(
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
        "convert", "list_number", "list_sort", "list_categories" -> tone(0xFF90CAF9, 0xFF1565C0)
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
