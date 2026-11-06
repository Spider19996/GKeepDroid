package com.gkeepdroid

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import org.json.JSONArray
import org.json.JSONObject

/** The bits the editor can change; headings (16) are kept as Keep has them. */
const val TEXT_STYLE_MASK = 15

/** The styles of every character of one text while it is edited (the same bits as in [RichNotes]). */
class DocFlags(var flags: IntArray) {
    /** Starts from the styles Keep has, if they belong to this very text. */
    constructor(text: String, doc: RichDoc?) : this(IntArray(text.length).also { flags ->
        if (doc != null && doc.text.trimEnd('\n') == text.trimEnd('\n')) {
            doc.runs.forEach { (start, end, value) -> for (i in start until minOf(end, flags.size)) flags[i] = value }
        }
    })

    /** Keeps the styles with their characters when the text changes; new characters take the style of the one before them. */
    fun edit(old: String, new: String) {
        if (old == new) return
        val limit = minOf(old.length, new.length)
        var prefix = 0
        while (prefix < limit && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < limit - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        val inherited = (if (prefix > 0) flags.getOrElse(prefix - 1) { 0 } else 0) and TEXT_STYLE_MASK
        val inserted = new.length - prefix - suffix
        val out = IntArray(new.length)
        for (i in 0 until prefix) out[i] = flags.getOrElse(i) { 0 }
        for (i in 0 until inserted) out[prefix + i] = inherited
        for (i in 0 until suffix) out[new.length - suffix + i] = flags.getOrElse(old.length - suffix + i) { 0 }
        flags = out
    }

    /** Whether every character from [start] to [end] has the style. */
    fun has(start: Int, end: Int, bit: Int): Boolean {
        val s = start.coerceIn(0, flags.size)
        val e = end.coerceIn(s, flags.size)
        return s < e && (s until e).all { flags[it] and bit != 0 }
    }

    /** Switches the style on for the range, or off if all of it has it already. */
    fun toggle(start: Int, end: Int, bit: Int) {
        val s = start.coerceIn(0, flags.size)
        val e = end.coerceIn(s, flags.size)
        if (s == e) return
        val on = !has(s, e, bit)
        for (i in s until e) flags[i] = if (on) flags[i] or bit else flags[i] and bit.inv()
    }

    fun runs(): List<IntArray> {
        val runs = mutableListOf<IntArray>()
        var start = 0
        for (i in 1..flags.size) {
            if (i == flags.size || flags[i] != flags[start]) {
                if (flags[start] != 0) runs += intArrayOf(start, i, flags[start])
                start = i
            }
        }
        return runs
    }

    /** What the Python bridge gets: the text and the runs. */
    fun toJson(text: String): JSONObject = JSONObject().put("t", text).put("r", JSONArray().apply { runs().forEach { put(JSONArray(it.toList())) } })
}

/** Shows the styles in a text field while its text stays what it is. */
class FlagsTransformation(private val doc: DocFlags) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = if (doc.flags.size == text.length) RichNotes.style(text, RichDoc(text.text, doc.runs())) else text
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/**
 * Adds the four text styles to the selection menu of Android (next to Cut, Copy, Paste and Select all) of the text field this
 * modifier belongs to. [onStyle] gets the style bit when one is chosen.
 */
@androidx.compose.foundation.ExperimentalFoundationApi
fun androidx.compose.ui.Modifier.textStyleMenu(labels: List<String>, onStyle: (Int) -> Unit): androidx.compose.ui.Modifier =
    appendTextContextMenuComponents {
        labels.forEachIndexed { i, label ->
            item("text_style_$i", label) {
                onStyle(1 shl i)
                close()
            }
        }
    }


/**
 * The text styles of one note while it is edited: the styles of each of its texts (the body is "text", an item is its id),
 * which texts were changed, and which text has the focus and what is selected in it.
 */
class FormatState(private val enabled: Boolean, private val rich: RichNote?) {
    private val docs = mutableMapOf<String, DocFlags>()
    val touched = mutableStateListOf<String>()
    var version by mutableIntStateOf(0)
        private set
    var focus by mutableStateOf<String?>(null)
    val selection = mutableStateMapOf<String, TextRange>()

    fun flagsFor(key: String, current: String): DocFlags {
        val have = docs[key]
        if (have != null && have.flags.size == current.length) return have
        // The text changed in a way that cannot be followed (sorting, converting): no formatting is sent for it.
        touched.remove(key)
        val doc = if (key == "text") rich?.docs?.values?.firstOrNull() else rich?.docs?.get(key)
        return DocFlags(current, doc).also { docs[key] = it }
    }

    /** The styles of every text and which ones were changed, for Undo. */
    fun snapshot(): Pair<Map<String, IntArray>, List<String>> = docs.mapValues { it.value.flags.copyOf() } to touched.toList()

    fun restore(styles: Map<String, IntArray>, touchedKeys: List<String>) {
        docs.clear()
        styles.forEach { (key, flags) -> docs[key] = DocFlags(flags.copyOf()) }
        touched.clear()
        touched.addAll(touchedKeys)
        version++
    }

    /** The text of [key] changed; the styles stay with their characters. */
    fun edited(key: String, old: String, new: String) {
        if (enabled) docs[key]?.edit(old, new)
    }

    /** Switches the style [bit] on for the selected text of the focused field (or off, if all of it has it). */
    fun toggleSelected(textOf: (String) -> String, bit: Int) {
        val key = focus ?: return
        val range = selection[key] ?: return
        if (range.collapsed) return
        flagsFor(key, textOf(key)).toggle(range.min, range.max, bit)
        if (key !in touched) touched.add(key)
        version++
    }

    /**
     * The changed formatting as the Python bridge wants it, or null if nothing changed. For a note that is made new (a conversion)
     * the items have no ids in Keep yet: [order] lists them, and they are addressed by their place.
     */
    fun toJson(textOf: (String) -> String, order: List<String>?, newNote: Boolean): String? {
        if (!enabled || touched.isEmpty()) return null
        val json = JSONObject()
        touched.toList().forEach { key ->
            val current = textOf(key)
            val doc = docs[key]
            if (doc != null && doc.flags.size == current.length) {
                val name = if (newNote && key != "text") order?.indexOf(key)?.takeIf { it >= 0 }?.let { "#$it" } else key
                if (name != null) json.put(name, doc.toJson(current))
            }
        }
        return if (json.length() == 0) null else json.toString()
    }

    /** Takes the styles along when the note changes its kind: lines become items and items become lines. */
    fun carry(toList: Boolean, oldText: String, oldItems: List<KeepItem>, newText: String, newItems: List<KeepItem>) {
        if (!enabled) return
        val carried = mutableMapOf<String, DocFlags>()
        if (toList) {
            val body = flagsFor("text", oldText)
            var start = 0
            val lines = oldText.split("\n").map { line -> (start to line).also { start += line.length + 1 } }.filter { it.second.isNotBlank() }
            newItems.forEachIndexed { i, item ->
                val (lineStart, line) = lines.getOrNull(i) ?: return@forEachIndexed
                val offset = line.indexOf(item.text)
                if (item.text.isEmpty() || offset < 0) return@forEachIndexed
                carried[item.id] = DocFlags(IntArray(item.text.length) { body.flags.getOrElse(lineStart + offset + it) { 0 } })
            }
        } else {
            val flags = IntArray(newText.length)
            var start = 0
            val items = oldItems.filter { it.text.isNotBlank() }
            newText.split("\n").forEachIndexed { i, line ->
                val item = items.getOrNull(i)
                if (item != null && line.endsWith(item.text)) {
                    val source = flagsFor(item.id, item.text)
                    val offset = line.length - item.text.length
                    for (k in item.text.indices) flags[start + offset + k] = source.flags.getOrElse(k) { 0 }
                }
                start += line.length + 1
            }
            carried["text"] = DocFlags(flags)
        }
        docs.clear()
        touched.clear()
        carried.forEach { (key, doc) ->
            docs[key] = doc
            if (doc.flags.any { it and TEXT_STYLE_MASK != 0 }) touched.add(key)
        }
        version++
    }
}
