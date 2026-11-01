package com.gkeepdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
        setContent { KeepTheme(themeMode, opaque = false) { AddItemDialog(account, noteId) } }
    }

    @Composable
    private fun AddItemDialog(account: String, noteId: String) {
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }
        var text by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var added by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) { focus.requestFocus() }

        fun add() {
            val item = text.trim()
            if (item.isEmpty() || busy) return
            busy = true
            // Stored and sent in the background, so the dialog is ready for the next item at once.
            KeepRepository.queueEdit(this@AddItemActivity, PendingEdit(account, "add_list_item", listOf(noteId, item)))
            text = ""
            added++
            busy = false
            focus.requestFocus()
        }

        // Tapping next to the dialog closes it.
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                modifier = Modifier.padding(24.dp).fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.add_item), style = MaterialTheme.typography.titleLarge)
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
                        if (added > 0) Text("✓ $added", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                        else Box(Modifier.weight(1f)) {}
                        // Items are added at once, so this only closes the dialog: "Cancel" until something
                        // was added, "Done" after that.
                        TextButton(onClick = { finish() }) {
                            Text(stringResource(if (added == 0) android.R.string.cancel else R.string.done))
                        }
                        Button(onClick = { add() }, enabled = text.isNotBlank() && !busy) {
                            Text(stringResource(R.string.add_item))
                        }
                    }
                }
            }
        }
    }
}
