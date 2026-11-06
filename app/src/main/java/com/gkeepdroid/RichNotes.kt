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
    private const val BOLD = 1
    private const val ITALIC = 2
    private const val UNDERLINE = 4
    private const val STRIKE = 8
    private const val HEADING = 16

    private class Cached(val stamp: Long, val size: Long, val notes: Map<String, RichNote>, var checkedAt: Long)

    private val cache = HashMap<String, Cached>()
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
