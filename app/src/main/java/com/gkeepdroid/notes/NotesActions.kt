package com.gkeepdroid

import android.content.Intent
import androidx.activity.ComponentActivity

/**
 * What the notes screen does with notes: the actions on the chosen notes and on a swiped card. [act] runs a change like an edit in
 * the editor (it asks for the lock when that is on), [unlock] asks for the lock for the rest.
 */
internal class NotesActions(
    private val activity: ComponentActivity,
    private val model: NotesModel,
    private val selectedEntries: () -> List<Pair<KeepNote, String>>,
    private val act: (suspend () -> Unit) -> Unit,
    private val unlock: (Int, (Boolean) -> Unit) -> Unit,
) {
    private val context = activity

    /** Changes every chosen note like an edit in the editor would: stored at once, sent in the background. */
    fun editSelected(change: (KeepNote, String) -> KeepNote) {
        val targets = selectedEntries()
        model.selected = emptySet()
        act {
            val privateKeys = PrivateNotes.load(context)
            targets.forEach { (note, account) ->
                val n = change(note, account)
                val args = KeepRepository.updateNoteArgs(n.id, n.title, n.text, n.items, n.color, n.pinned, emptyList(), n.labels)
                // A note that still carries the old mark in Keep becomes private on this phone before the edit takes the mark off.
                if (note.hidden && PrivateNotes.isPrivate(note, account, privateKeys)) PrivateNotes.set(context, account, n.id, true)
                KeepRepository.queueEdit(context, PendingEdit(account, "update_note", args))
            }
        }
    }
    fun moveSelected(block: suspend (KeepNote, String) -> Unit) {
        val targets = selectedEntries()
        model.selected = emptySet()
        act { targets.forEach { (note, account) -> block(note, account) } }
    }
    fun duplicateSelected() {
        val targets = selectedEntries()
        model.selected = emptySet()
        act {
            val privateKeys = PrivateNotes.load(context)
            targets.forEach { (n, account) ->
                val args = KeepRepository.createNoteArgs(n.title, n.text, n.isList, n.items, n.color, n.pinned, n.labels)
                val edit = PendingEdit(account, "create_note", args, hidden = PrivateNotes.isPrivate(n, account, privateKeys))
                if (CredentialStore.isLocal(account)) {
                    val before = Reminders.noteIds(context, account)
                    KeepRepository.run(context, account, "create_note", args)
                    Reminders.attachToNew(context, edit, before)
                } else {
                    KeepRepository.queueEdit(context, edit)
                }
            }
        }
    }
    fun trashSelected() {
        // Local notes have no trash: those are deleted, after asking.
        val (local, remote) = selectedEntries().partition { CredentialStore.isLocal(it.second) }
        model.selected = emptySet()
        if (local.isNotEmpty()) model.deleteSelected = local
        if (remote.isNotEmpty()) act { remote.forEach { (note, account) -> KeepRepository.trashNote(context, account, note.id) } }
    }

    /** A card was swiped (and let go): the action chosen in the settings, with "Undo" where there is one. */
    fun swiped(note: KeepNote, account: String, action: Int) {
        /** Changes of the note itself (pin, hide) are edits like any other: they ask for the lock when it is on. */
        fun editNote(pinned: Boolean, hide: Boolean) {
            act {
                val args = KeepRepository.updateNoteArgs(
                    note.id, note.title, note.text, note.items, note.color, pinned, emptyList(), note.labels,
                )
                PrivateNotes.set(context, account, note.id, hide)
                KeepRepository.queueEdit(context, PendingEdit(account, "update_note", args))
            }
        }
        when {
            action == SWIPE_ARCHIVE && !CredentialStore.isLocal(account) -> act {
                KeepRepository.archiveNote(context, account, note.id)
                UndoStore.set(account, note.id, UndoStore.Kind.ARCHIVE)
                model.undoKey++
            }
            // Local notes have no trash: deleting them asks first.
            action == SWIPE_TRASH && CredentialStore.isLocal(account) -> model.deleteTarget = note to account
            action == SWIPE_TRASH -> act {
                KeepRepository.trashNote(context, account, note.id)
                UndoStore.set(account, note.id, UndoStore.Kind.TRASH)
                model.undoKey++
            }
            action == SWIPE_PIN -> editNote(!note.pinned, PrivateNotes.isPrivate(note, account, PrivateNotes.load(context)))
            action == SWIPE_HIDE -> editNote(note.pinned, !PrivateNotes.isPrivate(note, account, PrivateNotes.load(context)))
            action == SWIPE_REMINDER -> {
                val open = {
                    activity.startActivity(
                        Intent(context, RemindersActivity::class.java)
                            .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account)
                            .putExtra(EditNoteActivity.EXTRA_NOTE_ID, note.id)
                    )
                }
                if (AppLock.editAllowed(activity)) open() else unlock(R.string.lock_prompt_edit) { ok -> if (ok) open() }
            }
            action == SWIPE_SHARE && !PrivateNotes.isPrivate(note, account, PrivateNotes.load(context)) -> {
                val body = if (note.isList) note.items.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text } else note.text
                activity.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, note.title).putExtra(Intent.EXTRA_TEXT, body),
                        null,
                    )
                )
            }
        }
    }
}
