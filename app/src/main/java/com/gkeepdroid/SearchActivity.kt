package com.gkeepdroid

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Searches the notes of the widget's account. A widget cannot contain a text field, so its search
 * bar opens this screen; a tap on a result opens the note in the editor.
 */
class SearchActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val account = CredentialStore.resolve(this, intent.getStringExtra(EditNoteActivity.EXTRA_ACCOUNT).orEmpty())
        if (account == null) {
            finish()
            return
        }
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val themeMode = SettingsStore.loadGlobal(this).themeMode
        setContent { KeepTheme(themeMode) { SearchScreen(account, widgetId) } }
    }

    @Composable
    private fun SearchScreen(account: String, widgetId: Int) {
        var query by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        // All notes of the account, in the widget's order, including ones hidden in the widget.
        val notes = remember {
            sortNotes(NotesCache.read(this, account), SettingsStore.loadWidget(this, widgetId), includeHidden = true)
        }
        val results = remember(query) { notes.filter { it.matches(query) } }
        val untitled = stringResource(R.string.untitled_note)

        LaunchedEffect(Unit) { focus.requestFocus() }

        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton { finish() }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_notes_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("✕") }
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }

            if (results.isEmpty()) {
                Text(
                    stringResource(R.string.search_no_results),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.id }) { note ->
                    ResultCard(note, query, untitled) {
                        startActivity(
                            Intent(this@SearchActivity, EditNoteActivity::class.java)
                                .putExtra(EditNoteActivity.EXTRA_NOTE_ID, note.id)
                                .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        )
                        finish()
                    }
                }
            }
        }
    }

    @Composable
    private fun ResultCard(note: KeepNote, query: String, untitled: String, onClick: () -> Unit) {
        val noteColor = NoteColors.argb(note.color)
        val textColor = if (noteColor != null) DarkNoteText else MaterialTheme.colorScheme.onSurface
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(noteColor?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(12.dp),
        ) {
            Text(
                noteLabel(note).ifBlank { untitled },
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val snippet = snippetFor(note, query)
            if (snippet.isNotBlank()) {
                Text(
                    highlight(snippet, query),
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    /** The first line (of the text or a list item) that contains a search word, else the first line. */
    private fun snippetFor(note: KeepNote, query: String): String {
        val lines = if (note.isList) note.items.map { it.text } else note.text.lines()
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return lines.firstOrNull { line -> words.any { it in line.lowercase() } }
            ?: lines.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun highlight(text: String, query: String) = buildAnnotatedString {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lower = text.lowercase()
        var index = 0
        while (index < text.length) {
            val hit = words.mapNotNull { w -> lower.indexOf(w, index).takeIf { it >= 0 }?.let { it to w.length } }
                .minByOrNull { it.first }
            if (hit == null) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, hit.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(hit.first, hit.first + hit.second)) }
            index = hit.first + hit.second
        }
    }
}
