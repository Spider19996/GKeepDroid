package com.gkeepdroid

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import org.json.JSONObject

/** One text of a note (its body or a checklist item) with the styles Keep's editor gave it. */
class RichDoc(val text: String, val runs: List<IntArray>)

/** What Keep knows about a note beyond its plain text: formatting and the background picture. */
class RichNote(val background: String, val docs: Map<String, RichDoc>)

/** Reads the file the Python bridge writes (see _fetch_rich in keep_bridge.py). */
object RichNotes {
    const val BOLD = 1
    const val ITALIC = 2
    const val UNDERLINE = 4
    const val STRIKE = 8
    const val HEADING = 16

    private class Cached(val stamp: Long, val size: Long, val notes: Map<String, RichNote>, var checkedAt: Long)

    private val cache = HashMap<String, Cached>()

    /** Changes whenever the app writes formatting itself, so open cards read it again at once. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)
    private const val RECHECK_MS = 500L

    private fun load(context: Context, account: String): Map<String, RichNote> {
        val now = android.os.SystemClock.uptimeMillis()
        // Every card asks: the file is looked at again only after a moment, not once per card.
        synchronized(cache) { cache[account]?.let { if (now - it.checkedAt < RECHECK_MS) return it.notes } }
        val file = NotesCache.richFile(context, account)
        if (!file.exists()) return emptyMap()
        synchronized(cache) {
            cache[account]?.let {
                if (it.stamp == file.lastModified() && it.size == file.length()) {
                    it.checkedAt = now
                    return it.notes
                }
            }
            val notes = runCatching {
                val all = JSONObject(file.readText()).getJSONObject("n")
                all.keys().asSequence().associateWith { id ->
                    val entry = all.getJSONObject(id)
                    val docs = entry.getJSONObject("d")
                    RichNote(
                        entry.optString("bg"),
                        docs.keys().asSequence().associateWith { key ->
                            val doc = docs.getJSONObject(key)
                            val runs = doc.getJSONArray("r")
                            RichDoc(doc.getString("t"), (0 until runs.length()).map { i ->
                                runs.getJSONArray(i).let { r -> intArrayOf(r.getInt(0), r.getInt(1), r.getInt(2)) }
                            })
                        },
                    )
                }
            }.getOrDefault(emptyMap())
            cache[account] = Cached(file.lastModified(), file.length(), notes, now)
            return notes
        }
    }

    /** The names of all background pictures the notes of an account use. */
    fun backgrounds(context: Context, account: String): Set<String> =
        load(context, account).values.map { it.background }.filter { it.isNotEmpty() }.toSet()

    fun of(context: Context, account: String, noteId: String): RichNote? = load(context, account)[noteId]

    /**
     * Writes formatting into the file the app reads it from, at once: for a local note it is the only place, for a Keep note it
     * is shown until Keep sends it back (otherwise a note opened again right after saving would show its old styles, and a bold
     * category word would look like an ordinary one). [json] is what the editor sends: per item id (or "#n" for the n-th of
     * [itemIds], "text" for the body) the text and its styled stretches.
     */
    fun applyLocally(context: Context, account: String, noteId: String, itemIds: List<String>, json: String) {
        synchronized(cache) {
            val file = NotesCache.richFile(context, account)
            val all = runCatching { JSONObject(file.readText()) }.getOrNull() ?: JSONObject()
            val byNote = all.optJSONObject("n") ?: JSONObject().also { all.put("n", it) }
            val entry = byNote.optJSONObject(noteId) ?: JSONObject().put("bg", "").put("d", JSONObject()).also { byNote.put(noteId, it) }
            val docs = entry.optJSONObject("d") ?: JSONObject().also { entry.put("d", it) }
            val wanted = runCatching { JSONObject(json) }.getOrNull() ?: return
            wanted.keys().forEach { key ->
                val target = if (key.startsWith("#")) key.drop(1).toIntOrNull()?.let { itemIds.getOrNull(it) } else key
                if (target == null) return@forEach
                val doc = wanted.getJSONObject(key)
                // The body of a text note is the note's only document; its key in Keep's file is not "text".
                val name = if (target == "text") docs.keys().asSequence().firstOrNull() ?: "text" else target
                if ((doc.optJSONArray("r")?.length() ?: 0) == 0) docs.remove(name) else docs.put(name, doc)
            }
            file.writeText(all.toString())
            cache.remove(account)
        }
        version.intValue++
    }

    /**
     * The styles that every letter of [start] until [end] of [text] has (spaces do not count); 0 if the text here is not the one
     * Keep has, so formatting never lands in the wrong place. A widget cannot show styles inside a line, only for the whole of one.
     */
    fun uniformFlags(doc: RichDoc?, text: String, start: Int = 0, end: Int = text.length): Int {
        if (doc == null || doc.text.trimEnd('\n') != text.trimEnd('\n')) return 0
        val per = IntArray(text.length)
        doc.runs.forEach { (from, to, flags) -> for (i in from until minOf(to, per.size)) per[i] = flags }
        var all = -1
        for (i in start.coerceAtLeast(0) until end.coerceAtMost(text.length)) if (!text[i].isWhitespace()) all = all and per[i]
        return if (all == -1) 0 else all
    }

    /**
     * The text with Keep's formatting as Android spans, which a widget can show; null if the text here is not the one Keep has
     * or nothing in it is styled. [extra] styles the whole text on top (a checked item is struck through).
     */
    fun spanned(text: String, doc: RichDoc?, extra: Int = 0): CharSequence? {
        if (doc == null || doc.text.trimEnd('\n') != text.trimEnd('\n') || doc.runs.isEmpty()) return null
        val out = android.text.SpannableString(text)
        fun mark(flags: Int, start: Int, end: Int) {
            val flag = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            if (flags and (BOLD or HEADING) != 0) out.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, flag)
            if (flags and ITALIC != 0) out.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), start, end, flag)
            if (flags and UNDERLINE != 0) out.setSpan(android.text.style.UnderlineSpan(), start, end, flag)
            if (flags and STRIKE != 0) out.setSpan(android.text.style.StrikethroughSpan(), start, end, flag)
            if (flags and HEADING != 0) out.setSpan(android.text.style.RelativeSizeSpan(1.25f), start, end, flag)
        }
        doc.runs.forEach { (start, end, flags) -> minOf(end, text.length).let { to -> if (start < to) mark(flags, start, to) } }
        if (extra != 0 && text.isNotEmpty()) mark(extra, 0, text.length)
        return out
    }

    /**
     * The text with Keep's formatting. The styles are positions in the text Keep has; if the text here is a different
     * one (changed on this phone and not synced yet), they would sit in the wrong place, so the text stays plain.
     */
    fun style(text: AnnotatedString, doc: RichDoc?): AnnotatedString {
        if (doc == null || doc.text.trimEnd('\n') != text.text.trimEnd('\n')) return text
        return buildAnnotatedString {
            append(text)
            doc.runs.forEach { (start, end, flags) ->
                val to = minOf(end, text.length)
                if (start >= to) return@forEach
                val decoration = listOfNotNull(
                    TextDecoration.Underline.takeIf { flags and UNDERLINE != 0 },
                    TextDecoration.LineThrough.takeIf { flags and STRIKE != 0 },
                )
                addStyle(
                    SpanStyle(
                        fontWeight = if (flags and (BOLD or HEADING) != 0) FontWeight.Bold else null,
                        fontStyle = if (flags and ITALIC != 0) FontStyle.Italic else null,
                        textDecoration = if (decoration.isEmpty()) null else TextDecoration.combine(decoration),
                        fontSize = if (flags and HEADING != 0) 1.25.em else androidx.compose.ui.unit.TextUnit.Unspecified,
                    ),
                    start, to,
                )
            }
        }
    }
}
