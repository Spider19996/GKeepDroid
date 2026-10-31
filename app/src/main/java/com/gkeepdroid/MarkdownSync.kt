package com.gkeepdroid

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Keeps every note as its own Markdown file in a folder the user chose (through Android's folder
 * picker, so other apps can edit the same files) and syncs in both directions.
 *
 * What was last in sync is remembered per note (a hash of the file and of the note). On each run
 * the side that changed since then wins; if both changed, the newer one wins and the other is
 * kept as a ".conflict" file. A file without this app's header becomes a new note. Deleting a file
 * never deletes a note, and a note that disappeared moves its (unchanged) file to "_removed".
 */
object MarkdownSync {
    private val mutex = Mutex()
    private var observer: ContentObserver? = null

    private data class Entry(val docId: String, val name: String, val modified: Long, val isDir: Boolean)
    private data class FileState(val docId: String, val name: String, val hash: Int, val sig: Int)

    private fun prefs(context: Context) = context.getSharedPreferences("md_sync", Context.MODE_PRIVATE)

    fun treeUri(context: Context): Uri? = prefs(context).getString("tree", null)?.let { Uri.parse(it) }

    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", false) && treeUri(context) != null

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("enabled", on).apply()
        if (on) {
            startObserving(context)
            request(context)
        } else {
            stopObserving(context)
        }
    }

    /** A new folder starts from scratch: what was in sync with the old one means nothing here. */
    fun setFolder(context: Context, tree: Uri) {
        val editor = prefs(context).edit().putString("tree", tree.toString()).putBoolean("enabled", true)
        prefs(context).all.keys.filter { it.startsWith("state_") }.forEach { editor.remove(it) }
        editor.apply()
        startObserving(context)
        request(context)
    }

    /** Human-readable name of a chosen folder, such as "Documents/Notes". */
    fun folderLabel(tree: Uri): String =
        Uri.decode(DocumentsContract.getTreeDocumentId(tree)).substringAfter(':').ifEmpty { "/" }

    /** Runs a sync soon; calls within a few seconds are merged into one. */
    fun request(context: Context) {
        if (isEnabled(context)) MarkdownSyncWorker.schedule(context.applicationContext)
    }

    /** Notices changes made to the files by other apps, as long as the app's process is alive. */
    fun startObserving(context: Context) {
        val app = context.applicationContext
        val tree = treeUri(app) ?: return
        stopObserving(app)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val created = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = request(app)
        }
        app.contentResolver.registerContentObserver(children, true, created)
        observer = created
    }

    private fun stopObserving(context: Context) {
        observer?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    // ---- storage access (Android's document provider API) ---------------------------------

    private fun docUri(tree: Uri, docId: String) = DocumentsContract.buildDocumentUriUsingTree(tree, docId)

    private fun children(context: Context, tree: Uri, parentDocId: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_MIME_TYPE,
        )
        return context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(Entry(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3) == Document.MIME_TYPE_DIR))
                }
            }
        }.orEmpty()
    }

    private fun ensureDir(context: Context, tree: Uri, parentDocId: String, name: String): String? {
        children(context, tree, parentDocId).firstOrNull { it.isDir && it.name == name }?.let { return it.docId }
        return DocumentsContract.createDocument(context.contentResolver, docUri(tree, parentDocId), Document.MIME_TYPE_DIR, name)
            ?.let { DocumentsContract.getDocumentId(it) }
    }

    private fun read(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()

    private fun write(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Cannot write")
    }

    // ---- remembered state ------------------------------------------------------------------

    private fun loadState(context: Context, folder: String): MutableMap<String, FileState> {
        val raw = prefs(context).getString("state_$folder", null) ?: return mutableMapOf()
        return try {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith {
                val o = json.getJSONObject(it)
                FileState(o.getString("doc"), o.getString("name"), o.getInt("hash"), o.getInt("sig"))
            }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveState(context: Context, folder: String, state: Map<String, FileState>) {
        val json = JSONObject()
        state.forEach { (id, s) ->
            json.put(id, JSONObject().put("doc", s.docId).put("name", s.name).put("hash", s.hash).put("sig", s.sig))
        }
        prefs(context).edit().putString("state_$folder", json.toString()).apply()
    }

    // ---- the sync itself ---------------------------------------------------------------------

    /** Syncs every account; returns how many files or notes were changed. */
    suspend fun syncNow(context: Context): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val tree = treeUri(app) ?: return@withContext 0
            val rootDocId = DocumentsContract.getTreeDocumentId(tree)
            var changes = 0
            CredentialStore.accounts(app).forEach { account ->
                changes += syncAccount(app, tree, rootDocId, account)
            }
            changes
        }
    }

    private fun folderName(account: String) =
        if (CredentialStore.isLocal(account)) "local" else account.replace(Regex("[^A-Za-z0-9@._-]"), "_")

    private suspend fun syncAccount(context: Context, tree: Uri, rootDocId: String, account: String): Int {
        val folder = folderName(account)
        val dirDocId = ensureDir(context, tree, rootDocId, folder) ?: return 0
        var notes = NotesCache.read(context, account)
        val state = loadState(context, folder)
        val files = children(context, tree, dirDocId).filter { !it.isDir && it.name.endsWith(".md", ignoreCase = true) }
        val taken = files.map { it.name }.toMutableSet()
        val imageNames = copyImages(context, tree, dirDocId, account, notes)
        val seen = HashSet<String>()
        var changes = 0

        for (file in files) {
            val uri = docUri(tree, file.docId)
            val text = read(context, uri)
            val parsed = MarkdownNote.parse(text, file.name.removeSuffix(".md"))

            if (parsed.id == null) {
                // A file from another app: it becomes a new note, and the file gets the note's header.
                if (!CredentialStore.isUsable(context, account)) continue
                val before = notes.map { it.id }.toSet()
                val items = parsed.items.map { KeepItem("", it.first, it.second) }
                try {
                    KeepRepository.run(
                        context, account, "create_note",
                        KeepRepository.createNoteArgs(parsed.title, parsed.text, parsed.isChecklist, items, parsed.color ?: NoteColors.DEFAULT, parsed.pinned ?: false, parsed.labels.orEmpty()),
                    )
                } catch (e: Exception) {
                    continue // Probably no connection: try again next time.
                }
                notes = NotesCache.read(context, account)
                val created = notes.firstOrNull { it.id !in before } ?: continue
                val rendered = MarkdownNote.render(created, imageNames)
                write(context, uri, rendered.toByteArray())
                state[created.id] = FileState(file.docId, file.name, rendered.hashCode(), MarkdownNote.signature(created))
                seen += created.id
                changes++
                continue
            }

            val note = notes.firstOrNull { it.id == parsed.id }
            if (note == null) {
                // The note is gone (deleted, archived, or of another account). An untouched file is put aside.
                val known = state[parsed.id]
                if (notes.isNotEmpty() && known != null && known.hash == text.hashCode()) {
                    moveAside(context, tree, dirDocId, file)
                    state.remove(parsed.id)
                    changes++
                }
                continue
            }
            seen += note.id

            val fileHash = text.hashCode()
            val noteSig = MarkdownNote.signature(note)
            val known = state[note.id]
            val fileChanged = known == null || known.hash != fileHash
            val noteChanged = known == null || known.sig != noteSig
            if (!fileChanged && !noteChanged) continue

            if (MarkdownNote.sameContent(parsed, note)) {
                state[note.id] = FileState(file.docId, file.name, fileHash, noteSig)
                continue
            }

            val fileWins = fileChanged && (!noteChanged || file.modified > note.updated)
            if (fileChanged && noteChanged) keepConflictCopy(context, tree, dirDocId, file, if (fileWins) MarkdownNote.render(note, imageNames) else text)

            if (fileWins) {
                // The file changed: send its content to the note.
                val used = mutableSetOf<String>()
                val items = parsed.items.map { (itemText, checked) ->
                    val same = note.items.firstOrNull { it.id !in used && it.text.trim() == itemText.trim() }
                    same?.let { used += it.id }
                    KeepItem(same?.id.orEmpty(), itemText, checked, same?.indent ?: false)
                }
                try {
                    KeepRepository.run(
                        context, account, "update_note",
                        KeepRepository.updateNoteArgs(note.id, parsed.title, parsed.text, items, parsed.color ?: NoteColors.DEFAULT, parsed.pinned ?: false, emptyList(), parsed.labels ?: note.labels),
                    )
                } catch (e: Exception) {
                    continue // Probably no connection: the file stays as it is and is tried again.
                }
                val updated = NotesCache.read(context, account).firstOrNull { it.id == note.id } ?: note
                state[note.id] = FileState(file.docId, file.name, fileHash, MarkdownNote.signature(updated))
            } else {
                // The note changed: rewrite the file, renaming it if the title changed.
                val rendered = MarkdownNote.render(note, imageNames)
                write(context, uri, rendered.toByteArray())
                var docId = file.docId
                var name = file.name
                val wanted = MarkdownNote.fileName(note, taken - file.name)
                if (wanted != file.name) {
                    DocumentsContract.renameDocument(context.contentResolver, uri, wanted)?.let {
                        taken -= file.name
                        taken += wanted
                        docId = DocumentsContract.getDocumentId(it)
                        name = wanted
                    }
                }
                state[note.id] = FileState(docId, name, rendered.hashCode(), noteSig)
            }
            changes++
        }

        // Notes without a file get one.
        for (note in notes.filter { it.id !in seen }) {
            val name = MarkdownNote.fileName(note, taken)
            val rendered = MarkdownNote.render(note, imageNames)
            val created = DocumentsContract.createDocument(context.contentResolver, docUri(tree, dirDocId), "text/markdown", name) ?: continue
            write(context, created, rendered.toByteArray())
            taken += name
            state[note.id] = FileState(DocumentsContract.getDocumentId(created), name, rendered.hashCode(), MarkdownNote.signature(note))
            changes++
        }

        saveState(context, folder, state)
        return changes
    }

    /** Copies the images of the notes next to the files; returns image id → file name. */
    private fun copyImages(context: Context, tree: Uri, dirDocId: String, account: String, notes: List<KeepNote>): Map<String, String> {
        val ids = notes.flatMap { it.images }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val imagesDocId = ensureDir(context, tree, dirDocId, "images") ?: return emptyMap()
        val existing = children(context, tree, imagesDocId).map { it.name }.toSet()
        val names = HashMap<String, String>()
        for (id in ids) {
            val file = NoteImages.exportForSharing(context, account, id) ?: continue
            names[id] = file.name
            if (file.name in existing) continue
            val mime = if (file.extension == "png") "image/png" else "image/jpeg"
            DocumentsContract.createDocument(context.contentResolver, docUri(tree, imagesDocId), mime, file.name)
                ?.let { write(context, it, file.readBytes()) }
        }
        return names
    }

    /** Moves the file of a vanished note to the "_removed" subfolder instead of deleting it. */
    private fun moveAside(context: Context, tree: Uri, dirDocId: String, file: Entry) {
        val target = ensureDir(context, tree, dirDocId, "_removed") ?: return
        try {
            DocumentsContract.moveDocument(context.contentResolver, docUri(tree, file.docId), docUri(tree, dirDocId), docUri(tree, target))
        } catch (e: Exception) {
            // The provider cannot move files; leave it where it is.
        }
    }

    /** The side that lost a conflict stays next to the file, with an extension other apps ignore. */
    private fun keepConflictCopy(context: Context, tree: Uri, dirDocId: String, file: Entry, loserText: String) {
        DocumentsContract.createDocument(
            context.contentResolver, docUri(tree, dirDocId), "application/octet-stream", "${file.name}.conflict",
        )?.let { write(context, it, loserText.toByteArray()) }
    }
}

/** Runs the Markdown sync a few seconds after the last request, so bursts of changes cost one run. */
class MarkdownSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!MarkdownSync.isEnabled(applicationContext)) return Result.success()
        return try {
            MarkdownSync.syncNow(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<MarkdownSyncWorker>().setInitialDelay(4, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("md_sync", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
