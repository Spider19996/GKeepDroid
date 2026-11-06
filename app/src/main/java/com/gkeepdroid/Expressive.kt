package com.gkeepdroid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Building blocks of the Material 3 Expressive look that several screens share.
 */

/** A slider whose value lives in the caller's state (a setting), so a change from elsewhere moves it too. */
@Composable
internal fun ValueSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    state.value = value
    Slider(state = state, onValueChange = onValueChange, modifier = modifier)
}

/**
 * One choice out of a few, as a connected button group: the buttons share the width, and the chosen one turns
 * round. Labels too long for one row move the last buttons to a second one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = value == selected,
                onCheckedChange = { onSelect(value) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.weight(1f),
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/**
 * One entry of a [GroupedMenu]: [icon] is a drawable, [leading] anything else in its place. [selected] marks the
 * current one of a choice, [checked] an option that is on or off; both show a check in front.
 */
internal class MenuEntry(
    val text: String,
    val icon: Int? = null,
    val leading: (@Composable () -> Unit)? = null,
    val trailing: (@Composable () -> Unit)? = null,
    val selected: Boolean? = null,
    val checked: Boolean? = null,
    val onClick: () -> Unit,
)

/**
 * A Material 3 Expressive menu: the entries sit in rounded groups with small gaps between them. Empty groups
 * are left out, and a long menu scrolls.
 */
@Composable
internal fun GroupedMenu(expanded: Boolean, onDismissRequest: () -> Unit, groups: List<List<MenuEntry>>) {
    val shown = groups.filter { it.isNotEmpty() }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismissRequest, modifier = Modifier.verticalScroll(rememberScrollState())) {
        shown.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, shown.size)) {
                group.forEachIndexed { index, entry ->
                    val shapes = MenuDefaults.itemShape(index, group.size)
                    val leading = entry.leading ?: entry.icon?.let { icon -> { Icon(painterResource(icon), contentDescription = null) } }
                    val check: @Composable () -> Unit = { Icon(painterResource(R.drawable.ic_done), contentDescription = null) }
                    when {
                        entry.selected != null -> SelectableDropdownMenuItem(
                            selected = entry.selected, onClick = entry.onClick, text = { Text(entry.text) }, shapes = shapes,
                            leadingIcon = leading, selectedLeadingIcon = check, trailingContent = entry.trailing,
                        )
                        entry.checked != null -> CheckableDropdownMenuItem(
                            checked = entry.checked, onCheckedChange = { entry.onClick() }, text = { Text(entry.text) }, shapes = shapes,
                            leadingIcon = leading, checkedLeadingIcon = check, trailingContent = entry.trailing,
                        )
                        else -> DropdownMenuItem(
                            onClick = entry.onClick, text = { Text(entry.text) }, shape = shapes.shape,
                            leadingIcon = leading, trailingContent = entry.trailing,
                        )
                    }
                }
            }
        }
    }
}

/** Gives a small round control the 48 dp touch area Material asks for; the control inside keeps its own size. */
@Composable
internal fun RoundTouchTarget(
    onClick: () -> Unit, enabled: Boolean = true, shape: androidx.compose.ui.graphics.Shape = CircleShape, content: @Composable () -> Unit,
) {
    Box(
        Modifier.size(48.dp).clip(shape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
