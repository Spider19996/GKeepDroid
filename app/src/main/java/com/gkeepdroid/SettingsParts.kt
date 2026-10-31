package com.gkeepdroid

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal const val MASTER_TOKEN_HELP_URL = "https://gkeepapi.readthedocs.io/en/latest/#obtaining-a-master-token"

internal val TEXT_COLORS = listOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFD0D0D0.toInt(), 0xFFFFD54F.toInt(), 0xFFFFB74D.toInt(),
    0xFFEF5350.toInt(), 0xFFF48FB1.toInt(), 0xFFCE93D8.toInt(), 0xFF81D4FA.toInt(), 0xFF80CBC4.toInt(),
    0xFFA5D6A7.toInt(),
)


@Composable
internal fun intervalLabel(minutes: Int): String = when {
    minutes == 0 -> stringResource(R.string.sync_manual)
    minutes < 60 -> stringResource(R.string.interval_min, minutes)
    else -> stringResource(R.string.interval_hour, minutes / 60)
}

@Composable
internal fun themeLabel(mode: Int): String = stringResource(
    when (mode) {
        THEME_LIGHT -> R.string.theme_light
        THEME_DARK -> R.string.theme_dark
        THEME_OLED -> R.string.theme_oled
        else -> R.string.theme_system
    }
)

/** Says that the first sync takes a while; OK works after a short countdown, so the note is not tapped away unread. */
@Composable
internal fun FirstSyncDialog(onConfirm: () -> Unit) {
    var left by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    AlertDialog(
        onDismissRequest = { if (left == 0) onConfirm() },
        title = { Text(stringResource(R.string.sync_section)) },
        text = { Text(stringResource(R.string.first_sync_hint)) },
        confirmButton = {
            TextButton(enabled = left == 0, onClick = onConfirm) {
                Text(if (left > 0) "${stringResource(android.R.string.ok)} ($left)" else stringResource(android.R.string.ok))
            }
        },
    )
}

/** A whole number typed in, such as minutes between two syncs: the name on the left, a small field on the right. */
@Composable
internal fun NumberRow(label: Int, value: Int, unit: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { typed -> onChange(typed.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0) },
            suffix = { Text(unit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
    }
}

/** A small explanation under a setting. */
@Composable
internal fun Hint(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One entry of the main settings list: title, optional summary and a chevron. */
@Composable
internal fun MenuRow(title: String, summary: String?, chevron: String = "›", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(chevron, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The checkbox as the widget draws it, in the settings colors. */
@Composable
internal fun CheckboxPreview(style: Int, checked: Boolean, sizeDp: Int) {
    val resource = when (style) {
        CB_SQUARE -> if (checked) R.drawable.cb_square_on else R.drawable.cb_square_off
        CB_ROUNDED -> if (checked) R.drawable.cb_rounded_on else R.drawable.cb_rounded_off
        CB_CIRCLE -> if (checked) R.drawable.cb_circle_on else R.drawable.cb_circle_off
        else -> null
    }
    if (resource != null) {
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.size(sizeDp.dp),
        )
    } else {
        Text(if (checked) "☑" else "☐", fontSize = (sizeDp * 0.85f).sp)
    }
}

/** A setting with one choice: tapping the row opens a popup menu instead of a page. */
@Composable
internal fun PopupRow(
    open: Boolean,
    onOpened: () -> Unit,
    title: String,
    summary: String?,
    options: List<Pair<String, () -> Unit>>,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            expanded = true
            onOpened()
        }
    }
    Box {
        MenuRow(title, summary, chevron = "▾") { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; action() })
            }
        }
    }
}




/** A labeled on/off switch. */
@Composable
internal fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** The pages that only exist for a widget. */


/** The heading of a group of settings in the main list. */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
