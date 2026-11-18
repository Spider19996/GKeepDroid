package com.gkeepdroid

/**
 * The Markdown form of one note. A short header holds what Markdown cannot say (the note's id,
 * color, pin); then comes the title as a heading and the text, or the checklist as `- [ ]` lines.
 * Other apps can edit the file freely: the id in the header is what ties it to its note.
 */
object MarkdownNote {
    /** A file read back. [id] is null for a file that was not written by this app. */
    data class Parsed(
        val id: String?,
        val title: String,
        val text: String,
        val items: List<Pair<String, Boolean>>,
        val isChecklist: Boolean,
        val color: String?,
        val pinned: Boolean?,
        /** The labels named in the header, or null if the header has none (then the note's stay as they are). */
        val labels: List<String>?,
    )

    private val CHECK_LINE = Regex("""^\s*[-*+]\s+\[([ xX])]\s?(.*)$""")
    private val IMAGE_LINE = Regex("""^\s*!\[[^\]]*]\(images/[^)]*\)\s*$""")

    fun render(note: KeepNote, imageNames: Map<String, String>): String = buildString {
        appendLine("---")
        appendLine("gkeepdroid-id: ${note.id}")
        if (note.color != NoteColors.DEFAULT) appendLine("color: ${note.color}")
        if (note.pinned) appendLine("pinned: true")
        if (note.labels.isNotEmpty()) appendLine("labels: ${org.json.JSONArray(note.labels)}")
        appendLine("---")
        if (note.title.isNotBlank()) {
            appendLine()
            appendLine("# ${note.title.replace("\n", " ")}")
        } else if (!note.isList && note.text.trimStart().startsWith("#")) {
            // An empty title is a lone "#", so that a text starting with a heading is not taken for the title.
            appendLine()
            appendLine("#")
        }
        note.images.mapNotNull { imageNames[it] }.forEach {
            appendLine()
            appendLine("![](images/$it)")
        }
        if (note.isList) {
            if (note.items.isNotEmpty()) appendLine()
            note.items.forEach { appendLine("- [${if (it.checked) "x" else " "}] ${it.text.replace("\n", " ")}") }
        } else if (note.text.isNotBlank()) {
            appendLine()
            appendLine(note.text.trimEnd())
        }
    }

    /** [fallbackTitle] (the file name) names a plain file that has no heading and no header. */
    fun parse(text: String, fallbackTitle: String): Parsed {
        val lines = text.replace("\r\n", "\n").split("\n")
        var index = 0
        val meta = HashMap<String, String>()
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
            if (end != null) {
                for (i in 1 until end) {
                    val colon = lines[i].indexOf(':')
                    if (colon > 0) meta[lines[i].substring(0, colon).trim()] = lines[i].substring(colon + 1).trim()
                }
                index = end + 1
            }
        }
        val rest = lines.drop(index).dropWhile { it.isBlank() }.toMutableList()
        var title = ""
        var hasTitleLine = false
        if (rest.isNotEmpty() && rest[0].startsWith("#") && !rest[0].startsWith("##")) {
            title = rest.removeAt(0).removePrefix("#").trim()
            hasTitleLine = true
        }
        val id = meta["gkeepdroid-id"]?.takeIf { it.isNotEmpty() }
        if (id == null && !hasTitleLine) title = fallbackTitle

        val body = rest.filter { !IMAGE_LINE.matches(it) }.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        val checks = body.mapNotNull { CHECK_LINE.matchEntire(it) }
        return Parsed(
            id = id,
            title = title,
            text = body.joinToString("\n"),
            items = checks.map { it.groupValues[2] to (it.groupValues[1] != " ") },
            isChecklist = checks.isNotEmpty() && checks.size == body.count { it.isNotBlank() },
            color = meta["color"]?.takeIf { it in NoteColors.names },
            pinned = meta["pinned"]?.let { it == "true" },
            labels = meta["labels"]?.let { raw ->
                try { org.json.JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } } catch (e: Exception) { null }
            },
        )
    }

    /** Whether the file says the same as the note, ignoring white space around lines. */
    fun sameContent(parsed: Parsed, note: KeepNote): Boolean =
        parsed.title.trim() == note.title.trim() &&
            (parsed.color ?: NoteColors.DEFAULT) == note.color &&
            (parsed.pinned ?: false) == note.pinned &&
            (parsed.labels ?: note.labels).toSet() == note.labels.toSet() &&
            if (note.isList) {
                parsed.items.map { it.first.trim() to it.second } == note.items.map { it.text.trim() to it.checked }
            } else {
                parsed.text.trim() == note.text.trim()
            }

    /** A file name made of the title; [taken] names are avoided by numbering. */
    fun fileName(note: KeepNote, taken: Set<String>): String {
        val base = noteLabel(note).replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").trim().take(60).trim()
            .ifEmpty { "Untitled" }
        var name = "$base.md"
        var number = 2
        while (name in taken) name = "$base ${number++}.md"
        return name
    }

    /** A value that changes whenever something the file could show changes. */
    fun signature(note: KeepNote): Int = listOf(
        note.title, note.text, note.color, note.pinned, note.isList, note.items.map { it.text to it.checked }, note.labels,
    ).hashCode()
}
