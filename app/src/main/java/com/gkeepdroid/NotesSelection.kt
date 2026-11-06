package com.gkeepdroid

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import org.json.JSONArray

/** The last searches in the app, newest first. */
object SearchHistory {
    private const val MAX = 8

    private fun prefs(context: Context) = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    fun load(context: Context): List<String> = runCatching {
        JSONArray(prefs(context).getString("list", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
    }.getOrDefault(emptyList())

    private fun save(context: Context, list: List<String>) {
        prefs(context).edit().putString("list", JSONArray(list).toString()).apply()
    }

    /** Remembers a search that was used; one letter is not worth keeping. */
    fun add(context: Context, query: String): List<String> {
        val text = query.trim()
        val list = load(context)
        if (text.length < 2) return list
        return (listOf(text) + list.filter { !it.equals(text, ignoreCase = true) }).take(MAX).also { save(context, it) }
    }

    fun clear(context: Context) = save(context, emptyList())
}

/** The words of [query] marked in [text], the same words that [matches] looks for. */
fun highlightMatches(text: AnnotatedString, query: String, color: Color): AnnotatedString {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return text
    val lower = text.text.lowercase()
    // Some characters change their length when lowercased; marks would then sit in the wrong place.
    if (lower.length != text.text.length) return text
    return buildAnnotatedString {
        append(text)
        words.forEach { word ->
            var at = lower.indexOf(word)
            while (at >= 0) {
                addStyle(SpanStyle(background = color), at, at + word.length)
                at = lower.indexOf(word, at + word.length)
            }
        }
    }
}

/** The color of a note chosen for all selected notes. */
@Composable
fun ColorChoiceDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color)) },
        text = {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteColors.names.forEach { name ->
                    val argb = NoteColors.argb(name, LocalDarkTheme.current)
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (argb != null) Color(argb) else Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { onPick(name) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (argb == null) Icon(painterResource(R.drawable.ic_color_none), contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/**
 * Labels for all selected notes: a label is checked when every selected note has it. [onApply] gets the labels that were
 * checked and the ones that were unchecked.
 */
@Composable
fun LabelChoiceDialog(labels: List<String>, allHave: Set<String>, onApply: (Set<String>, Set<String>) -> Unit, onDismiss: () -> Unit) {
    var checked by remember { mutableStateOf(allHave) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.labels_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (labels.isEmpty()) Text(stringResource(R.string.no_labels_yet))
                labels.forEach { label ->
                    Row(
                        Modifier.fillMaxWidth().clickable { checked = if (label in checked) checked - label else checked + label },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = label in checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(checked - allHave, allHave - checked) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } },
    )
}
