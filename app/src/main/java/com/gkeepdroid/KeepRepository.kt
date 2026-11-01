package com.gkeepdroid

import android.content.Context
import com.chaquo.python.Python
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Runs gkeepapi calls on a background thread and refreshes cache and widgets. */
object KeepRepository {

    // Python state files are per account and must not be used by two calls at once.
    private val lock = Mutex()

    /** Syncs every account that has a token; returns false if any of them failed. */
    suspend fun syncAll(context: Context): Boolean {
        var allOk = true
        var error: String? = null
        var expired: String? = null
        CredentialStore.accounts(context).forEach { email ->
            if (CredentialStore.token(context, email) == null) return@forEach
            try {
                sync(context, email)
            } catch (e: Exception) {
                allOk = false
                if (expired == null && SyncPolicy.isAuthError(e)) expired = email
                if (error == null) error = e.message?.lineSequence()?.firstOrNull()?.take(160) ?: e.javaClass.simpleName
            }
        }
        SyncPolicy.recordSync(context, error, expired)
        Reminders.cleanup(context)
        return allOk
    }

    suspend fun sync(context: Context, email: String) = call(context, email, "fetch_notes")

    /** Sends the queued checklist ticks of an account to Keep in one go. */
    suspend fun sendToggles(context: Context, email: String, ops: List<PendingToggle>) {
        val json = JSONArray().apply {
            ops.forEach {
                put(JSONObject().put("note", it.noteId).put("item", it.itemId).put("checked", it.checked).put("delete", it.delete))
            }
        }.toString()
        call(context, email, "set_items_checked", json)
    }

    suspend fun unarchiveNote(context: Context, email: String, noteId: String) = call(context, email, "unarchive_note", noteId)

    suspend fun untrashNote(context: Context, email: String, noteId: String) = call(context, email, "untrash_note", noteId)

    suspend fun deleteForever(context: Context, email: String, noteId: String) = call(context, email, "delete_forever", noteId)

    suspend fun archiveNote(context: Context, email: String, noteId: String) = call(context, email, "archive_note", noteId)

    suspend fun trashNote(context: Context, email: String, noteId: String) = call(context, email, "trash_note", noteId)

    /** The arguments of the Python calls, also stored when a change has to wait for the network. */
    fun updateNoteArgs(
        id: String, title: String, text: String, items: List<KeepItem>, color: String, pinned: Boolean,
        removeImages: List<String>, labels: List<String>,
    ): List<Any> = listOf(
        id, title, text, itemsToJson(items), color, pinned, JSONArray(removeImages).toString(), JSONArray(labels).toString(),
    )

    fun createNoteArgs(
        title: String, text: String, isList: Boolean, items: List<KeepItem>, color: String, pinned: Boolean,
        labels: List<String>,
    ): List<Any> = listOf(title, text, isList, itemsToJson(items), color, pinned, JSONArray(labels).toString())

    // Label changes talk to Keep in the background, so the editor stays quick and closing it does not cancel them.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createLabel(context: Context, email: String, name: String) {
        backgroundScope.launch {
            runCatching { callOrQueue(context, email, "create_label", name) }
            // Offline, the label is still known on the device until Keep has it.
            if (PendingEdits.all(context).any { it.account == email && it.function == "create_label" && it.args == listOf<Any>(name) }) {
                NotesCache.labelsFile(context, email).writeText(JSONArray(NotesCache.labelsOnly(context, email) + name).toString())
            }
        }
    }

    fun deleteLabel(context: Context, email: String, name: String) {
        backgroundScope.launch {
            runCatching { callOrQueue(context, email, "delete_label", name) }
            if (PendingEdits.all(context).any { it.account == email && it.function == "delete_label" && it.args == listOf<Any>(name) }) {
                NotesCache.labelsFile(context, email).writeText(JSONArray(NotesCache.labelsOnly(context, email) - name).toString())
            }
        }
    }

    /** Moves a note in Keep's order; a higher value is further up. Without a connection it is sent later. */
    suspend fun setSort(context: Context, email: String, noteId: String, sort: Long) =
        callOrQueue(context, email, "set_sort", noteId, sort.toString())

    /** Runs a call; if there is no connection, the call is kept on the device and sent when there is one. */
    private suspend fun callOrQueue(context: Context, email: String, function: String, vararg args: Any) {
        try {
            call(context, email, function, *args)
        } catch (e: Exception) {
            if (!PendingEdits.isOffline(e)) throw e
            PendingEdits.add(context, PendingEdit(email, function, args.toList()))
            PendingEditsWorker.schedule(context)
        }
    }

    suspend fun addListItem(context: Context, email: String, noteId: String, text: String) =
        call(context, email, "add_list_item", noteId, text)

    /** Runs a stored call again, for example one that waited for the network. */
    suspend fun run(context: Context, email: String, function: String, args: List<Any>, editId: String? = null) =
        call(context, email, function, *args.toTypedArray(), ignoreEdit = editId)

    /**
     * Saves a change without making the caller wait for Keep: it is stored, shown in the cache and
     * widgets at once, and sent in the background. It stays stored until Keep has it.
     */
    fun queueEdit(context: Context, edit: PendingEdit) {
        val appContext = context.applicationContext
        PendingEdits.add(appContext, edit)
        val changed = NotesCache.reapplyEdits(appContext, edit.account)
        backgroundScope.launch {
            if (changed) {
                WidgetRefresh.all(appContext)
                MarkdownSync.request(appContext)
            }
            PendingEdits.sendNow(appContext)
        }
    }

    private suspend fun call(context: Context, email: String, function: String, vararg args: Any, ignoreEdit: String? = null) {
        val appContext = context.applicationContext
        // Demo mode never touches the real notes.
        if (DemoNotes.enabled(appContext)) return
        val json = if (CredentialStore.isLocal(email)) {
            // Local notes live on the device: no login, no network.
            lock.withLock { withContext(Dispatchers.IO) { LocalNotes.run(appContext, email, function, args.toList()) } }
        } else {
            val token = CredentialStore.token(appContext, email)
            check(token != null) { "Not signed in" }
            val statePath = NotesCache.stateFile(appContext, email).absolutePath
            lock.withLock {
                withContext(Dispatchers.IO) {
                    PythonRuntime.get(appContext).getModule("keep_bridge")
                        .callAttr(function, email, token, statePath, *args)
                        .toString()
                }
            }
        }
        // Ticks not sent yet stay visible instead of being overwritten by Keep's older state.
        val pending = PendingToggles.list(appContext, email)
        // Widgets are only redrawn when something actually changed.
        // Changes still waiting to be sent stay visible, too (the one that was just sent is in Keep's answer).
        val edits = PendingEdits.all(appContext).filter { it.account == email && it.id != ignoreEdit }
        if (NotesCache.write(appContext, email, json, pending, edits)) {
            WidgetRefresh.all(appContext)
            // The Markdown files follow every change of the notes.
            MarkdownSync.request(appContext)
        }
    }

    private fun itemsToJson(items: List<KeepItem>) = JSONArray().apply {
        items.forEach {
            put(JSONObject().put("id", it.id).put("text", it.text).put("checked", it.checked).put("indent", it.indent))
        }
    }.toString()
}
