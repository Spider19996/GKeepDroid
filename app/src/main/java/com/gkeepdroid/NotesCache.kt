package com.gkeepdroid

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/** Per-account cache of the last synced notes, so widgets render without network. */
object NotesCache {
    private fun key(email: String) = email.lowercase().replace(Regex("[^a-z0-9]"), "_")

    private fun file(context: Context, email: String) = File(context.filesDir, "notes_${key(email)}.json")

    /** Where the Python bridge stores the downloaded images of an account. */
    fun imageDir(context: Context, email: String) = File(context.filesDir, "keep_state_${key(email)}_images")

    fun stateFile(context: Context, email: String) = File(context.filesDir, "keep_state_${key(email)}.json")

    /**
     * Writes the cache; returns false (and writes nothing) when it is unchanged. The JSON is
     * normalized first so that the same content never looks "changed" because of formatting, and
     * [pending] checks that were not sent to Keep yet are applied on top of what Keep returned.
     */
    /** Counts every change of the cached notes, so an open screen shows the change at once. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun write(
        context: Context, email: String, json: String, pending: List<PendingToggle> = emptyList(),
        edits: List<PendingEdit> = emptyList(),
    ): Boolean {
        var array = JSONArray(json)
        PendingOverlay.editNotes(array, edits)
        val moved = PendingOverlay.moveNotes(context, email, array, edits)
        array = moved.first
        if (pending.isNotEmpty()) {
            for (i in 0 until array.length()) {
                val note = array.getJSONObject(i)
                val items = note.getJSONArray("items")
                val kept = JSONArray()
                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    val op = pending.lastOrNull { it.itemId == item.optString("id") }
                    if (op != null && op.delete) continue
                    if (op != null) item.put("checked", op.checked)
                    kept.put(item)
                }
                note.put("items", kept)
            }
        }
        val normalized = array.toString()
        val f = file(context, email)
        if (f.exists() && f.readText() == normalized) {
            if (moved.second) version.intValue++
            return moved.second
        }
        f.writeText(normalized)
        version.intValue++
        return true
    }

    /** Puts the edits that were not sent to Keep yet on top of the cached notes; false when nothing changed. */
    fun reapplyEdits(context: Context, email: String): Boolean {
        val f = file(context, email)
        if (!f.exists() || DemoNotes.enabled(context)) return false
        return try {
            write(
                context, email, f.readText(), PendingToggles.list(context, email),
                PendingEdits.all(context).filter { it.account == email },
            )
        } catch (e: JSONException) {
            false
        }
    }

    /** Flips one checklist item in the cache so the widget can react before the network call ends. */
    fun setChecked(context: Context, email: String, noteId: String, itemId: String, checked: Boolean): Boolean {
        val f = file(context, email)
        if (!f.exists()) return false
        return try {
            val array = JSONArray(f.readText())
            var changed = false
            for (i in 0 until array.length()) {
                val note = array.getJSONObject(i)
                if (note.getString("id") != noteId) continue
                val items = note.getJSONArray("items")
                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    if (item.optString("id") == itemId) {
                        item.put("checked", checked)
                        changed = true
                    }
                }
            }
            if (changed) f.writeText(array.toString())
            changed
        } catch (e: JSONException) {
            false
        }
    }

    /** Removes one checklist item from the cache, like Keep will once the deletion is sent. */
    fun removeItem(context: Context, email: String, noteId: String, itemId: String): Boolean {
        val f = file(context, email)
        if (!f.exists()) return false
        return try {
            val array = JSONArray(f.readText())
            var changed = false
            for (i in 0 until array.length()) {
                val note = array.getJSONObject(i)
                if (note.getString("id") != noteId) continue
                val items = note.getJSONArray("items")
                val kept = JSONArray()
                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    if (item.optString("id") == itemId) changed = true else kept.put(item)
                }
                note.put("items", kept)
            }
            if (changed) f.writeText(array.toString())
            changed
        } catch (e: JSONException) {
            false
        }
    }

    internal fun otherFile(context: Context, email: String) = File(context.filesDir, "keep_state_${key(email)}_other.json")

    /** Archived and trashed notes of an account, as last synced. */
    fun readOther(context: Context, email: String?): List<KeepNote> {
        if (email == null) return emptyList()
        if (DemoNotes.enabled(context)) return emptyList()
        val f = otherFile(context, email)
        if (!f.exists()) return emptyList()
        return try {
            val array = JSONArray(f.readText())
            (0 until array.length()).map { parse(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** Formatting and backgrounds of the notes, which the Python bridge writes next to the sync state. */
    fun richFile(context: Context, email: String) = File(context.filesDir, "keep_state_${key(email)}_rich.json")

    fun labelsFile(context: Context, email: String) = File(context.filesDir, "keep_state_${key(email)}_labels.json")

    /** The label names stored for an account, also unused ones. */
    fun labelsOnly(context: Context, email: String?): List<String> {
        if (email == null) return emptyList()
        return try {
            val f = labelsFile(context, email)
            if (f.exists()) JSONArray(f.readText()).let { a -> (0 until a.length()).map { a.getString(it) } }.filter { it != HIDDEN_LABEL } else emptyList()
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** The labels an account has (also unused ones) plus the ones its notes carry. */
    fun labels(context: Context, email: String?): List<String> {
        if (email == null) return emptyList()
        return (labelsOnly(context, email) + readAll(context, email).flatMap { it.labels }).distinct().sortedBy { it.lowercase() }
    }

    /** Every note of an account, active ones first. */
    fun readAll(context: Context, email: String?): List<KeepNote> = read(context, email) + readOther(context, email)

    fun delete(context: Context, email: String) {
        otherFile(context, email).delete()
        file(context, email).delete()
        stateFile(context, email).delete()
        richFile(context, email).delete()
        File(richFile(context, email).path.removeSuffix(".json") + "model.json").delete()
        imageDir(context, email).deleteRecursively()
    }

    fun read(context: Context, email: String?): List<KeepNote> {
        if (email == null) return emptyList()
        if (DemoNotes.enabled(context)) {
            return if (email == CredentialStore.accounts(context).firstOrNull()) DemoNotes.notes() else emptyList()
        }
        val f = file(context, email)
        if (!f.exists()) return emptyList()
        // A damaged cache file falls back to empty and the next sync fills it again.
        return try {
            val array = JSONArray(f.readText())
            (0 until array.length()).map { parse(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun parse(o: JSONObject): KeepNote {
        val items = o.getJSONArray("items")
        return KeepNote(
            id = o.getString("id"),
            serverId = o.optString("serverId"),
            title = o.getString("title"),
            text = o.getString("text"),
            isList = o.getBoolean("isList"),
            items = (0 until items.length()).map {
                val item = items.getJSONObject(it)
                KeepItem(item.optString("id"), item.optString("text"), item.optBoolean("checked"), item.optBoolean("indent"))
            },
            color = o.optString("color", NoteColors.DEFAULT),
            pinned = o.optBoolean("pinned"),
            images = o.optJSONArray("images")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            sort = o.optLong("sort"),
            updated = o.optLong("updated"),
            created = o.optLong("created"),
            state = o.optString("state", "active"),
            labels = o.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                .filter { it != HIDDEN_LABEL },
            hidden = o.optJSONArray("labels")?.let { a -> (0 until a.length()).any { a.getString(it) == HIDDEN_LABEL } } ?: false,
        )
    }
}
