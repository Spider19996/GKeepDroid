package com.gkeepdroid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange

/** Everything in the editor that Undo and Redo bring back (not images, reminders or the account). */
class EditSnapshot(
    val title: String, val text: String, val selection: TextRange, val isList: Boolean, val color: String, val background: String,
    val labels: List<String>, val numbered: Boolean, val items: List<KeepItem>, val ownText: Int?, val variantDark: Boolean?,
    val styles: Map<String, IntArray>, val touched: List<String>,
) {
    private fun sameStructure(o: EditSnapshot) =
        isList == o.isList && color == o.color && background == o.background && labels == o.labels && numbered == o.numbered &&
            ownText == o.ownText && variantDark == o.variantDark && items.map { Triple(it.id, it.checked, it.indent) } == o.items.map { Triple(it.id, it.checked, it.indent) }

    private fun sameWords(o: EditSnapshot) = title == o.title && text == o.text && items.map { it.text } == o.items.map { it.text }

    private fun sameStyles(o: EditSnapshot) = styles.keys == o.styles.keys && styles.all { (k, v) -> v.contentEquals(o.styles[k]) }

    fun sameContent(o: EditSnapshot) = sameStructure(o) && sameWords(o) && sameStyles(o)

    /** Only words were typed since [this]: such steps are gathered, one step for a burst of typing. */
    fun typedTo(o: EditSnapshot) = sameStructure(o) && !sameWords(o)

    /** The same note as far as a reopened editor can tell (item ids and styles may be new after a save). */
    fun looksLike(o: EditSnapshot) = title == o.title && text == o.text && isList == o.isList && color == o.color && labels == o.labels &&
        items.map { it.text to it.checked } == o.items.map { it.text to it.checked }
}

/** The steps of Undo and Redo of one open editor. */
class EditHistory {
    private val steps = ArrayList<EditSnapshot>()
    private var at = -1
    private var version by mutableIntStateOf(0)

    val canUndo: Boolean get() = version >= 0 && at > 0
    val canRedo: Boolean get() = version >= 0 && at < steps.lastIndex

    /** A burst of typing is still going on if the words changed and nothing else. */
    fun isTyping(now: EditSnapshot) = at >= 0 && steps[at].typedTo(now)

    fun record(now: EditSnapshot) {
        if (at >= 0 && steps[at].sameContent(now)) return
        while (steps.lastIndex > at) steps.removeAt(steps.lastIndex)
        steps.add(now)
        if (steps.size > MAX_STEPS) steps.removeAt(0)
        at = steps.lastIndex
        version++
    }

    fun undo(): EditSnapshot? = if (canUndo) steps[--at].also { version++ } else null

    fun redo(): EditSnapshot? = if (canRedo) steps[++at].also { version++ } else null

    /** Takes over a history that was kept after closing, if it ends where the note stands now. */
    fun adopt(now: EditSnapshot): Boolean {
        if (at < 0 || !steps[at].looksLike(now)) return false
        steps[at] = now
        version++
        return true
    }

    private companion object {
        const val MAX_STEPS = 100
    }
}

/** The history of a note stays for a few minutes after its editor is closed, so a slip can still be taken back. */
object EditHistoryKeep {
    private const val MINUTES = 10
    private class Kept(val history: EditHistory, val at: Long)

    private val kept = HashMap<String, Kept>()

    fun put(key: String, history: EditHistory) {
        val now = System.currentTimeMillis()
        kept.values.removeAll { now - it.at > MINUTES * 60_000L }
        kept[key] = Kept(history, now)
    }

    /** The kept history of the note, or null if there is none, it is too old or the note changed since. */
    fun take(key: String, now: EditSnapshot): EditHistory? {
        val found = kept.remove(key) ?: return null
        if (System.currentTimeMillis() - found.at > MINUTES * 60_000L) return null
        return found.history.takeIf { it.adopt(now) }
    }
}
