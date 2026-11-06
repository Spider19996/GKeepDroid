package com.gkeepdroid

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import org.json.JSONArray
import org.json.JSONObject

const val STYLE_BOLD = 1
const val STYLE_ITALIC = 2
const val STYLE_UNDERLINE = 4
const val STYLE_STRIKE = 8

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
