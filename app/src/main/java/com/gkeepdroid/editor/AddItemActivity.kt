package com.gkeepdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * A small dialog that adds items to a checklist, one after the other, without opening the note.
 * Without a connection the item is kept and sent later.
 */
class AddItemActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val account = CredentialStore.resolve(this, intent.getStringExtra(EditNoteActivity.EXTRA_ACCOUNT).orEmpty())
        val noteId = intent.getStringExtra(EditNoteActivity.EXTRA_NOTE_ID)
        if (account == null || noteId == null) {
            finish()
            return
        }
        val themeMode = SettingsStore.loadGlobal(this).themeMode
        // The categories of the checklist (bold words at the start of its items); the "+" of a category's header chooses one.
        val note = NotesCache.read(this, account).firstOrNull { it.id == noteId }
        val categories = note?.let { Categories.names(it.items, RichNotes.of(this, account, noteId)) }.orEmpty()
        val preset = intent.getStringExtra(CategoryKey.name)
        setContent { KeepTheme(themeMode, opaque = false) { AddItemDialog(account, noteId, categories, preset) } }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun AddItemDialog(account: String, noteId: String, categories: List<String>, preset: String?) {
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }
        var text by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var added by remember { mutableStateOf(0) }
        // The category the next item goes into (null: none), the ones to choose from, and the name of a new one being typed.
        var category by remember { mutableStateOf(preset) }
        val known = remember { mutableStateListOf<String>().apply { addAll(categories); if (preset != null && preset !in this) add(preset) } }
        var newCategory by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) { focus.requestFocus() }

        fun add() {
            val item = text.trim()
            if (item.isEmpty() || busy) return
            busy = true
            val name = (newCategory?.trim()?.takeIf { it.isNotEmpty() } ?: category)
            if (newCategory != null && name != null) {
                if (name !in known) known.add(name)
                category = name
                newCategory = null
            }
            // In a category the item starts with the category's name in bold, the way Keep shows it too.
            val (full, boldLength) = if (name != null) Categories.prefixed(name, item) else item to 0
            // Stored and sent in the background, so the dialog is ready for the next item at once.
            KeepRepository.queueEdit(
                this@AddItemActivity,
                PendingEdit(account, "add_list_item", if (boldLength > 0) listOf(noteId, full, boldLength.toString()) else listOf(noteId, full)),
            )
            text = ""
            added++
            busy = false
            focus.requestFocus()
        }

        // A real dialog window: tapping next to it or Back closes the activity.
        Dialog(onDismissRequest = { finish() }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.add_item), style = MaterialTheme.typography.titleLarge)
                    // Where the item goes: no category, one of the list's, or a new one.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = category == null && newCategory == null, onClick = { category = null; newCategory = null },
                            label = { Text(stringResource(R.string.category_none)) },
                        )
                        known.forEach { name ->
                            FilterChip(
                                selected = category == name && newCategory == null, onClick = { category = name; newCategory = null },
                                label = { Text(name) },
                            )
                        }
                        FilterChip(
                            selected = newCategory != null, onClick = { newCategory = newCategory ?: "" },
                            label = { Text(stringResource(R.string.category_new)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                        )
                    }
                    if (newCategory != null) OutlinedTextField(
                        value = newCategory.orEmpty(), onValueChange = { newCategory = it },
                        placeholder = { Text(stringResource(R.string.category_name)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(stringResource(R.string.item_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (added > 0) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_done), contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text("$added", color = MaterialTheme.colorScheme.primary)
                        }
                        else Spacer(Modifier.weight(1f))
                        // Items are added at once, so this only closes the dialog: "Cancel" until something
                        // was added, "Done" after that.
                        TextButton(onClick = { finish() }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(if (added == 0) android.R.string.cancel else R.string.done))
                        }
                        Button(onClick = { add() }, enabled = text.isNotBlank() && !busy, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.add_item))
                        }
                    }
                }
            }
        }
    }
}
