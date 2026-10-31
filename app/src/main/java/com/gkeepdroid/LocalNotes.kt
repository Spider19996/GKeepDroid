package com.gkeepdroid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Notes that only exist on this device, for using the app without any account. It understands the
 * same calls as the Python bridge (same names, same arguments) and answers with the same JSON, so
 * the rest of the app treats it like one more account. Archiving and trashing delete the note,
 * because there is no trash to restore from.
 */
object LocalNotes {
    private fun file(context: Context) = File(context.filesDir, "local_notes.json")

    private fun load(context: Context): JSONArray {
        val f = file(context)
        return if (f.exists()) JSONArray(f.readText()) else JSONArray()
    }

    fun deleteAll(context: Context) {
        file(context).delete()
    }

    /** Runs a call and returns all local notes as JSON, in the format of the notes cache. */
    fun run(context: Context, email: String, function: String, args: List<Any>): String {
        val notes = load(context)
        when (function) {
            "create_note" -> create(notes, args)
            "update_note" -> update(notes, args)
            "add_list_item" -> addItem(notes, args[0] as String, args[1] as String)
            "set_items_checked" -> applyTicks(notes, JSONArray(args[0] as String))
            "create_label" -> saveLabels(context, email, NotesCache.labelsOnly(context, email) + (args[0] as String).trim())
            "delete_label" -> {
                val name = args[0] as String
                saveLabels(context, email, NotesCache.labelsOnly(context, email) - name)
                removeLabel(notes, name)
            }
            "set_sort" -> find(notes, args[0] as String)?.put("sort", (args[1] as String).toLong())
            "trash_note", "archive_note" -> removeNote(notes, args[0] as String)
            // "fetch_notes" and "remove_image" change nothing: there is no server and no images.
        }
        val json = notes.toString()
        file(context).writeText(json)
        return json
    }

    private fun saveLabels(context: Context, email: String, names: List<String>) {
        NotesCache.labelsFile(context, email).writeText(JSONArray(names.filter { it.isNotBlank() }.distinct()).toString())
    }

    private fun removeLabel(notes: JSONArray, name: String) {
        for (i in 0 until notes.length()) {
            val note = notes.getJSONObject(i)
            val old = note.optJSONArray("labels") ?: continue
            val kept = JSONArray()
            for (j in 0 until old.length()) if (old.getString(j) != name) kept.put(old.getString(j))
            note.put("labels", kept)
        }
    }

    private fun find(notes: JSONArray, id: String): JSONObject? =
        (0 until notes.length()).map { notes.getJSONObject(it) }.firstOrNull { it.getString("id") == id }

    private fun removeNote(notes: JSONArray, id: String) {
        for (i in 0 until notes.length()) {
            if (notes.getJSONObject(i).getString("id") == id) {
                notes.remove(i)
                return
            }
        }
    }

    /** Items as sent by the editor; new ones have an empty id and get one here. Blank items are dropped. */
    private fun itemsFrom(json: String): JSONArray {
        val source = JSONArray(json)
        val result = JSONArray()
        for (i in 0 until source.length()) {
            val item = source.getJSONObject(i)
            val text = item.optString("text").trim()
            if (text.isEmpty()) continue
            result.put(
                JSONObject()
                    .put("id", item.optString("id").ifEmpty { UUID.randomUUID().toString() })
                    .put("text", text)
                    .put("checked", item.optBoolean("checked"))
                    .put("indent", item.optBoolean("indent"))
            )
        }
        return result
    }

    /** A list of label names as JSON text, cleaned of blanks and repeats. */
    private fun labelsFrom(json: String?): JSONArray {
        val result = JSONArray()
        if (json == null) return result
        val source = JSONArray(json)
        val seen = HashSet<String>()
        for (i in 0 until source.length()) {
            val name = source.getString(i).trim()
            if (name.isNotEmpty() && seen.add(name)) result.put(name)
        }
        return result
    }

    private fun create(notes: JSONArray, args: List<Any>) {
        val title = args[0]
        val text = args[1]
        val isList = args[2]
        val itemsJson = args[3]
        val color = args[4]
        val pinned = args[5]
        val now = System.currentTimeMillis()
        // A new note goes to the top, like in Keep: a higher sort value is further up.
        val top = (0 until notes.length()).maxOfOrNull { notes.getJSONObject(it).optLong("sort") } ?: 0L
        notes.put(
            JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("serverId", "")
                .put("title", title as String)
                .put("text", if (isList as Boolean) "" else text as String)
                .put("isList", isList)
                .put("items", if (isList) itemsFrom(itemsJson as String) else JSONArray())
                .put("pinned", pinned as Boolean)
                .put("color", color as String)
                .put("images", JSONArray())
                .put("labels", labelsFrom(args.getOrNull(6) as? String))
                .put("sort", top + 1)
                .put("updated", now)
                .put("created", now)
        )
    }

    private fun update(notes: JSONArray, args: List<Any>) {
        val note = find(notes, args[0] as String) ?: return
        note.put("title", args[1] as String)
        if (note.getBoolean("isList")) note.put("items", itemsFrom(args[3] as String)) else note.put("text", args[2] as String)
        note.put("color", args[4] as String)
        note.put("pinned", args[5] as Boolean)
        (args.getOrNull(7) as? String)?.let { note.put("labels", labelsFrom(it)) }
        note.put("updated", System.currentTimeMillis())
    }

    private fun addItem(notes: JSONArray, noteId: String, text: String) {
        val note = find(notes, noteId) ?: return
        note.getJSONArray("items").put(
            JSONObject().put("id", UUID.randomUUID().toString()).put("text", text.trim()).put("checked", false)
        )
        note.put("updated", System.currentTimeMillis())
    }

    private fun applyTicks(notes: JSONArray, ops: JSONArray) {
        for (i in 0 until ops.length()) {
            val op = ops.getJSONObject(i)
            val note = find(notes, op.getString("note")) ?: continue
            val items = note.getJSONArray("items")
            val kept = JSONArray()
            for (j in 0 until items.length()) {
                val item = items.getJSONObject(j)
                if (item.getString("id") != op.getString("item")) {
                    kept.put(item)
                } else if (!op.optBoolean("delete")) {
                    item.put("checked", op.getBoolean("checked"))
                    kept.put(item)
                }
            }
            note.put("items", kept)
            note.put("updated", System.currentTimeMillis())
        }
    }
}
