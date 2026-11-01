package com.gkeepdroid

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

/** Puts the changes that Keep does not have yet on top of the cached notes, so the app shows them at once. */
object PendingOverlay {
    private val MOVES = setOf("archive_note", "trash_note", "unarchive_note", "untrash_note", "delete_forever")

    /**
     * Moves notes between the notes, the archive and the trash (and removes deleted ones), as Keep will
     * once it has the change. Returns the new list of notes and whether the archive or trash file changed.
     */
    fun moveNotes(context: Context, email: String, array: JSONArray, edits: List<PendingEdit>): Pair<JSONArray, Boolean> {
        val moves = edits.filter { it.function in MOVES }
        if (moves.isEmpty()) return array to false
        val otherFile = NotesCache.otherFile(context, email)
        val oldOther = if (otherFile.exists()) otherFile.readText() else "[]"
        val active = (0 until array.length()).map { array.getJSONObject(it) }.toMutableList()
        val others = try {
            JSONArray(oldOther).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.toMutableList()
        } catch (e: JSONException) {
            return array to false
        }
        for (edit in moves) {
            val id = edit.args[0].toString()
            val inActive = active.firstOrNull { it.optString("id") == id }
            val inOther = others.firstOrNull { it.optString("id") == id }
            when (edit.function) {
                "archive_note", "trash_note" -> {
                    val note = inActive ?: inOther ?: continue
                    inActive?.let { active.remove(it) }
                    if (inOther == null) others.add(note)
                    note.put("state", if (edit.function == "trash_note") "trashed" else "archived")
                }
                "unarchive_note", "untrash_note" -> {
                    val note = inOther ?: continue
                    others.remove(note)
                    note.remove("state")
                    if (inActive == null) active.add(note)
                }
                else -> {
                    inActive?.let { active.remove(it) }
                    inOther?.let { others.remove(it) }
                }
            }
        }
        val newOther = JSONArray(others).toString()
        val changed = newOther != JSONArray(oldOther).toString()
        if (changed) otherFile.writeText(newOther)
        return JSONArray(active) to changed
    }

    /** Applies changes of existing notes, the way Keep will once they are sent. A new note shows up with its sync. */
    fun editNotes(array: JSONArray, edits: List<PendingEdit>) {
        for (edit in edits) {
            if (edit.function != "update_note") continue
            try {
                val id = edit.args[0].toString()
                val removed = JSONArray(edit.args[6].toString()).let { a -> (0 until a.length()).map { a.getString(it) } }
                for (i in 0 until array.length()) {
                    val note = array.getJSONObject(i)
                    if (note.getString("id") != id) continue
                    note.put("title", edit.args[1].toString())
                    if (note.getBoolean("isList")) {
                        // Like the bridge, blank items are dropped.
                        val items = JSONArray(edit.args[3].toString())
                        val kept = JSONArray()
                        for (j in 0 until items.length()) {
                            if (items.getJSONObject(j).optString("text").isNotBlank()) kept.put(items.getJSONObject(j))
                        }
                        note.put("items", kept)
                    } else {
                        note.put("text", edit.args[2].toString())
                    }
                    note.put("color", edit.args[4].toString())
                    note.put("pinned", edit.args[5] as? Boolean ?: edit.args[5].toString().toBoolean())
                    note.put("labels", JSONArray(edit.args[7].toString()))
                    val images = note.optJSONArray("images") ?: JSONArray()
                    note.put("images", JSONArray((0 until images.length()).map { images.getString(it) }.filter { it !in removed }))
                }
            } catch (e: Exception) {
                // An edit that does not fit (stored by an older version) is just not previewed.
            }
        }
    }
}
