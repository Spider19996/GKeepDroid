package com.gkeepdroid

/**
 * How many of a checklist item are meant, written into its text the way people write a shopping list: "2x Tortellini",
 * "2 × Tortellini" or "Tortellini x2". Keep stores only the text, so every other app shows it as typed; this app shows the
 * number as a small pill beside the item instead and can count it up and down.
 */
object Quantities {
    /** The number of an item and where it stands in the text ([start] to [end], spaces included), so it can be left out. */
    data class Found(val count: Int, val start: Int, val end: Int)

    // A number in front needs the x and a space after it, so "3xl Shirt" or "4 Eier" stay text; at the end the x comes first.
    private val PREFIX = Regex("""^\s*(\d{1,3})\s*[xX×]\s+(?=\S)""")
    private val SUFFIX = Regex("""(?<=\S)\s+[xX×]\s*(\d{1,3})\s*$""")

    fun find(text: String): Found? {
        PREFIX.find(text)?.let { m -> return found(m, text) }
        SUFFIX.find(text)?.let { m -> return found(m, text) }
        return null
    }

    private fun found(m: MatchResult, text: String): Found? {
        val count = m.groupValues[1].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        // Nothing but the number is no item with a quantity.
        if (m.range.first == 0 && m.range.last + 1 >= text.trimEnd().length) return null
        return Found(count, m.range.first, m.range.last + 1)
    }

    /** The text without its quantity, and the styles moved along with it. */
    fun strip(text: String, doc: RichDoc?): Triple<Int, String, RichDoc?> {
        val f = find(text) ?: return Triple(1, text, doc)
        val rest = text.removeRange(f.start, f.end)
        val cut = f.end - f.start
        val runs = doc?.runs?.mapNotNull { (from, to, flags) ->
            fun move(i: Int) = when { i <= f.start -> i; i >= f.end -> i - cut; else -> f.start }
            val a = move(from); val b = move(to)
            if (a < b) intArrayOf(a, b, flags) else null
        }
        return Triple(f.count, rest, runs?.let { RichDoc(rest, it) })
    }

    /**
     * How cards and widgets write an item: the number in front as text ("2x Tortellini", also for "Tortellini x2"), and with
     * [one] a "1x" for an item without one. The styles move along.
     */
    fun display(text: String, doc: RichDoc?, one: Boolean): Pair<String, RichDoc?> {
        if (text.isBlank()) return text to doc
        val (count, rest, restDoc) = strip(text, doc)
        if (count <= 1 && !one) return text to doc
        val head = "${count}x "
        val shown = head + rest
        return shown to restDoc?.let { d -> RichDoc(shown, d.runs.map { (a, b, f) -> intArrayOf(a + head.length, b + head.length, f) }) }
    }

    /** [text] with [count] written in: the old number replaced where it stood, a new one in front; 1 removes it. */
    fun withCount(text: String, count: Int): String {
        val f = find(text)
        val base = if (f == null) text else text.removeRange(f.start, f.end)
        if (count <= 1) return base
        val n = count.coerceAtMost(999)
        return when {
            f == null -> "${n}x $base"
            f.start == 0 -> "${n}x " + base
            else -> base + " x$n"
        }
    }
}
