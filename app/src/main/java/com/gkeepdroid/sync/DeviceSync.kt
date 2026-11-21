package com.gkeepdroid

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Keeps what Keep has no place for in step on all of the user's phones: private notes, reminders (if wanted), a note's own text
 * color and photo, folded notes, folded categories and quote blocks, and the categories a list once had.
 *
 * Every phone writes one small file of its own into a shared place (see [SyncStorage]) and reads the files of the others, so
 * two phones never write the same file and nothing conflicts. A file holds everything its phone knows, each value with the time
 * it was last changed; the newest change wins ([DeviceSyncMerge]). Photos are separate files named after their content.
 *
 * A value is named "account|note|field". The fields are private, fold, look.text, look.dark, bg (the photo's hash),
 * rem:<id>, cfold:<category>, qfold:<block> and chist:<category>.
 */
object DeviceSync {
    private const val PREFIX = "gkd-sync-"
    private const val DAY = 24L * 60 * 60 * 1000
    private val mutex = Mutex()

    /** True while values of other phones are written into this phone's stores, which must not count as changes made here. */
    @Volatile private var applying = false

    private fun prefs(context: Context) = context.getSharedPreferences("device_sync", Context.MODE_PRIVATE)

    // ---- settings ------------------------------------------------------------------------------

    fun treeUri(context: Context): Uri? = prefs(context).getString("tree", null)?.toUri()

    const val MODE_DRIVE = "drive"
    const val MODE_FOLDER = "folder"

    /** Where the phones meet: the hidden app folder of Google Drive, or a folder the user syncs with another app. Drive unless a folder was chosen before. */
    fun mode(context: Context): String = prefs(context).getString("mode", null) ?: if (treeUri(context) != null) MODE_FOLDER else MODE_DRIVE

    fun setMode(context: Context, mode: String) {
        prefs(context).edit { putString("mode", mode) }
        request(context)
    }

    /** The account whose Drive is used: the first signed-in Google account. */
    fun driveAccount(context: Context): String? = syncedAccounts(context).firstOrNull { CredentialStore.token(context, it) != null }

    fun isEnabled(context: Context) =
        prefs(context).getBoolean("enabled", false) && (if (mode(context) == MODE_DRIVE) driveAccount(context) != null else treeUri(context) != null)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("enabled", on) }
        if (on) request(context)
    }

    fun setFolder(context: Context, tree: Uri) {
        prefs(context).edit { putString("tree", tree.toString()).putString("mode", MODE_FOLDER).putBoolean("enabled", true) }
        request(context)
    }

    fun folderLabel(tree: Uri): String = Uri.decode(DocumentsContract.getTreeDocumentId(tree)).substringAfter(':').ifEmpty { "/" }

    /** Whether reminders of other phones are taken over (and this phone's are given to them). Off unless the user wants it. */
    fun remindersEnabled(context: Context) = prefs(context).getBoolean("reminders", false)

    fun setRemindersEnabled(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("reminders", on) }
        request(context)
    }

    private fun storage(context: Context): SyncStorage? =
        if (mode(context) == MODE_DRIVE) driveAccount(context)?.let { DriveSyncStorage(context, it) } else treeUri(context)?.let { FolderSyncStorage(context, it) }

    private fun deviceId(context: Context): String =
        prefs(context).getString("device", null) ?: UUID.randomUUID().toString().take(8).also { id -> prefs(context).edit { putString("device", id) } }

    /** Runs a sync soon; calls within a few seconds are merged into one. */
    fun request(context: Context) {
        if (isEnabled(context)) DeviceSyncWorker.schedule(context.applicationContext)
    }

    /** The stores call this after every change made on this phone. */
    fun changed(context: Context) {
        if (!applying) request(context)
    }

    // ---- what this phone has now --------------------------------------------------------------

    private fun syncedAccounts(context: Context) = CredentialStore.accounts(context).filter { !CredentialStore.isLocal(it) }.toSet()

    private val photoHashes = HashMap<String, Triple<Long, Long, String>>()

    private fun sha1(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun photoHash(context: Context, account: String, noteId: String): String? {
        val file = CustomBackgrounds.fileOf(context, account, noteId).takeIf { it.exists() } ?: return null
        synchronized(photoHashes) {
            photoHashes[file.path]?.let { (modified, length, hash) -> if (modified == file.lastModified() && length == file.length()) return hash }
            val hash = sha1(file.readBytes())
            photoHashes[file.path] = Triple(file.lastModified(), file.length(), hash)
            return hash
        }
    }

    /** All values of this phone that are synced, by name. */
    private fun collect(context: Context): Map<String, String> {
        val accounts = syncedAccounts(context)
        val out = HashMap<String, String>()
        fun mine(noteKey: String) = noteKey.substringBefore('|') in accounts
        /** "account|note|rest" → "account|note|prefix:rest". */
        fun add(key: String, prefix: String) {
            val parts = key.split("|", limit = 3)
            if (parts.size == 3 && parts[0] in accounts) out["${parts[0]}|${parts[1]}|$prefix${parts[2]}"] = "1"
        }
        PrivateNotes.load(context).filter(::mine).forEach { out["$it|private"] = "1" }
        FoldStore.load(context).filter(::mine).forEach { out["$it|fold"] = "1" }
        NoteLook.all(context).forEach { (key, value) -> if (mine(key)) out[key] = value }
        CategoryFolds.load(context).forEach { add(it, "cfold:") }
        QuoteBlocks.load(context).forEach { add(it, "qfold:") }
        CategoryHistory.all(context).forEach { (noteKey, names) -> if (mine(noteKey)) names.forEach { out["$noteKey|chist:$it"] = "1" } }
        if (remindersEnabled(context)) Reminders.all(context).forEach { (key, value) -> if (mine(key)) out[key] = value }
        accounts.forEach { account ->
            NotesCache.readAll(context, account).forEach { note -> photoHash(context, account, note.id)?.let { out["$account|${note.id}|bg"] = it } }
        }
        return out
    }

    // ---- taking over what other phones know ----------------------------------------------------

    /** Writes one value into the matching store; false if it cannot be done now (a photo that has not arrived yet). */
    private fun apply(context: Context, storage: SyncStorage, key: String, value: String?, accounts: Set<String>): Boolean {
        val parts = key.split("|", limit = 3)
        if (parts.size != 3 || parts[0] !in accounts) return true
        val (account, note, field) = parts
        val on = value != null
        when {
            field == "private" -> PrivateNotes.set(context, account, note, on)
            field == "fold" -> FoldStore.set(context, account, note, on)
            field.startsWith("look.") -> NoteLook.setField(context, account, note, field.removePrefix("look."), value)
            field.startsWith("rem:") -> {
                if (value == null) Reminders.remove(context, account, note, field.removePrefix("rem:"))
                else Reminders.put(context, account, note, Reminder.fromJson(JSONObject(value)))
            }
            field.startsWith("cfold:") -> CategoryFolds.set(context, account, note, field.removePrefix("cfold:"), on)
            field.startsWith("qfold:") -> QuoteBlocks.set(context, account, note, field.removePrefix("qfold:"), on)
            field.startsWith("chist:") -> {
                val name = field.removePrefix("chist:")
                if (on) CategoryHistory.add(context, account, note, listOf(name)) else CategoryHistory.remove(context, account, note, name)
            }
            field == "bg" -> {
                if (value == null) {
                    CustomBackgrounds.remove(context, account, note)
                } else {
                    val bytes = storage.read(photoName(value))?.takeIf { sha1(it) == value } ?: return false
                    CustomBackgrounds.store(context, account, note, bytes)
                }
            }
        }
        return true
    }

    private fun photoName(hash: String) = "${PREFIX}photo-$hash.jpg"

    // ---- state of this phone -------------------------------------------------------------------

    private fun loadState(context: Context): MutableMap<String, SyncEntry> {
        val raw = prefs(context).getString("state", null) ?: return mutableMapOf()
        return runCatching { parseEntries(JSONObject(raw)) }.getOrDefault(mutableMapOf())
    }

    private fun loadLast(context: Context): Map<String, String>? {
        val raw = prefs(context).getString("last", null) ?: return null
        return runCatching { JSONObject(raw).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }.getOrNull()
    }

    private fun parseEntries(o: JSONObject): MutableMap<String, SyncEntry> {
        val map = mutableMapOf<String, SyncEntry>()
        o.keys().forEach { key ->
            val e = o.getJSONObject(key)
            map[key] = SyncEntry(if (e.has("v")) e.getString("v") else null, e.getLong("t"))
        }
        return map
    }

    private fun entriesJson(state: Map<String, SyncEntry>) = JSONObject().also { o ->
        state.toSortedMap().forEach { (key, e) -> o.put(key, JSONObject().put("t", e.time).also { if (e.value != null) it.put("v", e.value) }) }
    }

    // ---- the sync itself -------------------------------------------------------------------------

    /** Syncs once; returns how many values of other phones were taken over. */
    suspend fun syncNow(context: Context): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val storage = storage(app) ?: return@withContext 0
            val now = System.currentTimeMillis()
            val device = deviceId(app)
            val ownName = "${PREFIX}device-$device.json"

            val last = loadLast(app)
            val state = loadState(app)
            DeviceSyncMerge.stampLocalChanges(last.orEmpty(), collect(app), state, now, first = last == null)

            val files = storage.list()
            for (file in files) {
                if (!file.name.startsWith("${PREFIX}device-") || !file.name.endsWith(".json") || file.name == ownName) continue
                val remote = runCatching {
                    val o = JSONObject(storage.read(file.name)?.decodeToString() ?: return@runCatching null)
                    if (o.optInt("version") != 1) null else parseEntries(o.getJSONObject("entries"))
                }.getOrNull() ?: continue
                DeviceSyncMerge.mergeRemote(state, remote)
            }
            DeviceSyncMerge.prune(state, now - 60 * DAY)

            // What the others know and this phone does not: written into the stores, without counting as changes made here.
            val accounts = syncedAccounts(app)
            var taken = 0
            val todo = DeviceSyncMerge.toApply(state, collect(app)).filter { (key, _) ->
                val parts = key.split("|", limit = 3)
                parts.size == 3 && parts[0] in accounts && (remindersEnabled(app) || !parts[2].startsWith("rem:"))
            }
            if (todo.isNotEmpty()) {
                applying = true
                try {
                    todo.forEach { (key, value) -> if (apply(app, storage, key, value, accounts)) taken++ }
                } finally {
                    applying = false
                }
                WidgetRefresh.all(app)
            }

            // The state first, the photos after it: a phone that sees a photo in a state file can wait for it, but not the other way round.
            val content = JSONObject().put("version", 1).put("device", device).put("time", now).put("entries", entriesJson(state)).toString()
            val digest = sha1(content.substringAfter("\"entries\"").toByteArray())
            if (prefs(app).getString("written", null) != digest || files.none { it.name == ownName }) {
                storage.write(ownName, content.toByteArray())
                prefs(app).edit { putString("written", digest) }
            }

            val after = collect(app)
            val have = files.map { it.name }.toSet()
            accounts.forEach { account ->
                NotesCache.readAll(app, account).forEach { note ->
                    val hash = after["$account|${note.id}|bg"] ?: return@forEach
                    if (photoName(hash) !in have) storage.write(photoName(hash), CustomBackgrounds.fileOf(app, account, note.id).readBytes())
                }
            }
            // Photos no value points to any more go after a day, long enough for a phone that wrote its state a moment ago.
            val wanted = state.filter { it.key.endsWith("|bg") }.mapNotNull { it.value.value }.map(::photoName).toSet()
            files.filter { it.name.startsWith("${PREFIX}photo-") && it.name !in wanted && it.modified < now - DAY }.forEach { storage.delete(it.name) }

            prefs(app).edit {
                putString("state", entriesJson(state).toString())
                putString("last", JSONObject(after as Map<*, *>).toString())
            }
            taken
        }
    }
}

/** Runs the sync a few seconds after the last change, so bursts of changes cost one run. */
class DeviceSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!DeviceSync.isEnabled(applicationContext)) return Result.success()
        return try {
            DeviceSync.syncNow(applicationContext)
            Result.success()
        } catch (e: Exception) {
            AppLog.e(applicationContext, "devicesync", "sync failed: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val builder = OneTimeWorkRequestBuilder<DeviceSyncWorker>().setInitialDelay(4, TimeUnit.SECONDS)
            // Drive needs a connection; a folder does not.
            if (DeviceSync.mode(context) == DeviceSync.MODE_DRIVE) builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            val request = builder.build()
            WorkManager.getInstance(context).enqueueUniqueWork("device_sync", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
