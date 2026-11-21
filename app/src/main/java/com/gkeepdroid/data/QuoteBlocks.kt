package com.gkeepdroid

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit

/**
 * Blocks of lines that start with "> " in a text note: two or more lines in a row, which fold into their first line. Elsewhere
 * (the desktop, Keep itself) the note is plain text with a ">" before the lines, so nothing is stored in the text for this.
 */
object QuoteBlocks {
    /** The marks behind the first line of a block: its lines are hidden (folded) or shown. */
    const val MARK_FOLDED = " ▸"
    const val MARK_OPEN = " ▾"

    /** What stands in front of a line of a block in a text field: an en space and a quarter-em space: 12 dp, as far as the text in a category's frame is set in. */
    const val VIEW_INDENT = "\u2002\u2005"

    /** A block of [start] until [end] of the text (the end of its last line); its first line ends at [headEnd]. [key] names it. */
    class Block(val start: Int, val headEnd: Int, val end: Int, val key: String)

    private fun quoted(line: String) = line.startsWith("> ") || line == ">"

    /** The blocks of the text. A block is named by its first line (and a count when two blocks start alike). */
    fun find(text: String): List<Block> {
        if (!text.contains('>')) return emptyList()
        val blocks = mutableListOf<Block>()
        val seen = mutableMapOf<String, Int>()
        var offset = 0
        var start = -1
        var headEnd = 0
        var count = 0
        fun close(end: Int) {
            if (start >= 0 && count >= 2) {
                val head = text.substring(start, headEnd)
                val n = seen.merge(head, 1, Int::plus)!!
                blocks += Block(start, headEnd, end, if (n == 1) head else "$head#$n")
            }
            start = -1
            count = 0
        }
        for (line in text.split('\n')) {
            val lineEnd = offset + line.length
            if (quoted(line)) {
                if (start < 0) { start = offset; headEnd = lineEnd }
                count++
            } else {
                // The block ends where the previous line ended, not at the newline after it.
                close(offset - 1)
            }
            offset = lineEnd + 1
        }
        close(text.length)
        return blocks
    }

    // Which blocks are folded on this device (the rest is shown).
    private fun prefs(context: Context) = context.getSharedPreferences("folded_quotes", Context.MODE_PRIVATE)

    /** Changes whenever a block is folded or unfolded, so open screens redraw at once. */
    val version = mutableIntStateOf(0)

    fun key(account: String, noteId: String, blockKey: String) = "$account|$noteId|$blockKey"

    fun load(context: Context): Set<String> = prefs(context).getStringSet("keys", null)?.toSet().orEmpty()

    fun set(context: Context, account: String, noteId: String, blockKey: String, folded: Boolean) {
        val all = load(context).toMutableSet()
        if (folded) all.add(key(account, noteId, blockKey)) else all.remove(key(account, noteId, blockKey))
        prefs(context).edit { putStringSet("keys", all) }
        version.intValue++
    }

    /** Forgets which blocks of a note are folded: a note that is not saved yet shares the key "new" with every other new one. */
    fun clear(context: Context, account: String, noteId: String) {
        val prefix = "$account|$noteId|"
        val all = load(context)
        if (all.none { it.startsWith(prefix) }) return
        prefs(context).edit { putStringSet("keys", all.filterNot { it.startsWith(prefix) }.toSet()) }
        version.intValue++
    }

    /** The part of [doc] that lies in [from] until [to] of its text, moved to begin at 0 (null without a doc). */
    fun slice(doc: RichDoc?, from: Int, to: Int): RichDoc? = doc?.let { d ->
        RichDoc(
            d.text.substring(from.coerceAtMost(d.text.length), to.coerceAtMost(d.text.length)),
            d.runs.mapNotNull { (a, b, flags) -> maxOf(a, from).let { x -> minOf(b, to).let { y -> if (x < y) intArrayOf(x - from, y - from, flags) else null } } },
        )
    }

    /** One stretch of the original text that the folded text keeps, and where it begins there. */
    class Piece(val from: Int, val to: Int, val at: Int)

    /** The first line of a block in the folded text (with its mark), and the block it belongs to. */
    class Head(val start: Int, val end: Int, val block: Block, val folded: Boolean)

    /** The text as shown: the first line of each block carries a mark, and a folded block shows nothing else. */
    class Folded(val text: String, val pieces: List<Piece>, val heads: List<Head>) {
        /** Where a position of the original text lies in the folded one (a hidden one lies behind the mark). */
        fun toFolded(offset: Int): Int {
            for (p in pieces) {
                // Hidden between two pieces: behind the mark, where the next piece begins.
                if (offset < p.from) return p.at
                if (offset <= p.to) return p.at + offset - p.from
            }
            return text.length
        }

        /** Where a position of the folded text lies in the original one (the mark stands for the end of the first line). */
        fun toOriginal(offset: Int): Int {
            for ((i, p) in pieces.withIndex()) {
                val end = p.at + (p.to - p.from)
                if (offset <= end) return p.from + (offset - p.at).coerceAtLeast(0)
                val next = pieces.getOrNull(i + 1)
                if (next == null || offset < next.at) return p.to
            }
            return pieces.lastOrNull()?.to ?: 0
        }

        /** Keeps Keep's styles in place: the runs, cut to what is kept and moved to where it stands now. */
        fun runs(runs: List<IntArray>): List<IntArray> = runs.flatMap { (from, to, flags) ->
            pieces.mapNotNull { p ->
                val a = maxOf(from, p.from)
                val b = minOf(to, p.to)
                if (a < b) intArrayOf(a - p.from + p.at, b - p.from + p.at, flags) else null
            }
        }

        fun doc(doc: RichDoc?): RichDoc? = doc?.let { RichDoc(text, runs(it.runs)) }
    }

    /** A text built from stretches of the original one (here without the "> " of its lines), and where each lies in it. */
    class Built(val text: String, val pieces: List<Piece>) {
        /** Keeps Keep's styles in place: the runs, cut to the stretches kept and moved to where they stand now. */
        fun doc(doc: RichDoc?): RichDoc? = doc?.let { d ->
            RichDoc(text, d.runs.flatMap { (from, to, flags) ->
                pieces.mapNotNull { p ->
                    val a = maxOf(from, p.from)
                    val b = minOf(to, p.to)
                    if (a < b) intArrayOf(a - p.from + p.at, b - p.from + p.at, flags) else null
                }
            })
        }
    }

    /** What a note of text shows, in order: stretches of plain text and blocks (the first line apart, the others as the body). */
    sealed interface Part {
        class Plain(val text: Built) : Part
        class Quote(val block: Block, val folded: Boolean, val head: Built, val body: Built?) : Part
    }

    private fun prefix(line: String) = if (line.startsWith("> ")) 2 else line.length.coerceAtMost(1)

    /** Joins the lines of [text] from [start] until [end], each without its "> ", into one text. */
    private fun stripped(text: String, start: Int, end: Int): Built {
        val out = StringBuilder()
        val pieces = mutableListOf<Piece>()
        var offset = start
        text.substring(start, end).split('\n').forEachIndexed { i, line ->
            if (i > 0) out.append('\n')
            val cut = prefix(line)
            pieces += Piece(offset + cut, offset + line.length, out.length)
            out.append(line, cut, line.length)
            offset += line.length + 1
        }
        return Built(out.toString(), pieces)
    }

    fun parts(text: String, blocks: List<Block>, folded: (Block) -> Boolean): List<Part> {
        val out = mutableListOf<Part>()
        var from = 0
        fun plain(end: Int) {
            if (end > from) out += Part.Plain(Built(text.substring(from, end), listOf(Piece(from, end, 0))))
        }
        blocks.forEach { b ->
            // The newline before a block belongs to no stretch, so the block starts a line of its own.
            plain((b.start - 1).coerceAtLeast(from))
            val closed = folded(b)
            val head = stripped(text, b.start, b.headEnd)
            val body = if (!closed && b.end > b.headEnd) stripped(text, b.headEnd + 1, b.end) else null
            out += Part.Quote(b, closed, head, body)
            from = (b.end + 1).coerceAtMost(text.length)
        }
        if (from < text.length) plain(text.length)
        return out
    }

    /** A block as it lies in the text of [view]: from the start of its first line to the end of its last visible one. */
    class Span(val start: Int, val end: Int, val block: Block, val folded: Boolean)

    /** The text of a note as a text field shows it: without the "> " of the quoted lines, a folded block as its first line and a mark. */
    class View(val folded: Folded, val spans: List<Span>)

    fun view(text: String, blocks: List<Block>, isFolded: (Block) -> Boolean): View {
        val out = StringBuilder()
        val pieces = mutableListOf<Piece>()
        val heads = mutableListOf<Head>()
        val spans = mutableListOf<Span>()
        var offset = 0
        // The block whose lines come now, and where its other lines (not the first one) begin in the text shown.
        var open: Block? = null
        var bodyStart = -1
        var openFolded = false
        fun close() {
            val b = open
            if (b != null && bodyStart >= 0) spans += Span(bodyStart, out.length, b, openFolded)
            open = null
            bodyStart = -1
        }
        text.split('\n').forEachIndexed { i, line ->
            val lineStart = offset
            val lineEnd = offset + line.length
            offset = lineEnd + 1
            val block = blocks.firstOrNull { lineStart >= it.start && lineEnd <= it.end }
            val head = block != null && lineStart == block.start
            val hidden = block != null && !head && isFolded(block)
            if (block == null || head) close()
            if (hidden) return@forEachIndexed
            // The newline before a line that is shown is the one in the text right before it.
            if (i > 0 && out.isNotEmpty()) {
                pieces += Piece(lineStart - 1, lineStart, out.length)
                out.append('\n')
            }
            val cut = if (block != null) prefix(line) else 0
            val start = out.length
            // The other lines of a block are set in, where the block's line runs, by spaces that are not in the text; the first one is not.
            if (block != null && !head) {
                if (bodyStart < 0) bodyStart = start
                out.append(VIEW_INDENT)
            }
            pieces += Piece(lineStart + cut, lineEnd, out.length)
            out.append(line, cut, line.length)
            if (head) {
                val closed = isFolded(block!!)
                heads += Head(start, out.length, block, closed)
                open = block
                openFolded = closed
            }
        }
        close()
        return View(Folded(out.toString(), pieces, heads), spans)
    }

    fun fold(text: String, blocks: List<Block>, folded: (Block) -> Boolean): Folded {
        val out = StringBuilder()
        val pieces = mutableListOf<Piece>()
        val heads = mutableListOf<Head>()
        var from = 0
        blocks.forEach { b ->
            val closed = folded(b)
            // Up to the end of the first line, then its mark; an open block goes on from there, a folded one jumps to its end.
            pieces += Piece(from, b.headEnd, out.length)
            out.append(text, from, b.headEnd)
            val start = out.length - (b.headEnd - b.start)
            out.append(if (closed) MARK_FOLDED else MARK_OPEN)
            heads += Head(start, out.length, b, closed)
            from = if (closed) b.end else b.headEnd
        }
        pieces += Piece(from, text.length, out.length)
        out.append(text, from, text.length)
        return Folded(out.toString(), pieces, heads)
    }
}
