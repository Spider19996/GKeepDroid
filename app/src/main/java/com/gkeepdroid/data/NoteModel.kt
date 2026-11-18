package com.gkeepdroid

import java.io.File

/** An item of a checklist; [indent] puts it under the item above, as a sub-item (Keep knows one level). */
data class KeepItem(val id: String, val text: String, val checked: Boolean, val indent: Boolean = false)

/** The text of a divider inside a checklist: an item of its own, so every other app (Keep on the web) shows it as a plain line of dashes. */
const val DIVIDER_TEXT = "----------"

/** A divider is an item that holds only dashes (three or more); the app draws it as a line without a check box. */
fun KeepItem.isDivider(): Boolean = text.trim().let { it.length >= 3 && it.all { c -> c == '-' } }

data class KeepNote(
    val id: String,
    /** Keep's own id of the note, used to link to it in the Keep app or on keep.google.com. */
    val serverId: String = "",
    val title: String,
    val text: String,
    val isList: Boolean,
    val items: List<KeepItem>,
    val color: String,
    val pinned: Boolean = false,
    /** File names of the note's downloaded images and drawings. */
    val images: List<String> = emptyList(),
    /** Keep's own sort value: higher means further up in the Keep app. */
    val sort: Long = 0,
    val updated: Long = 0,
    val created: Long = 0,
    /** "active", or "archived" / "trashed" for notes that are only listed in the app. */
    val state: String = "active",
    /** Without the hidden mark, which is not a label for the user (see [HIDDEN_LABEL]). */
    val labels: List<String> = emptyList(),
    /** Marked "hide content": the note carries [HIDDEN_LABEL] in Keep, so every device hides it. */
    val hidden: Boolean = false,
)

/** The special label that marks hidden notes in Keep, so devices with the same account share the mark. It never shows as a label. */
const val HIDDEN_LABEL = "GKeepDroid hidden"

/** The labels to send to Keep: the note's own, plus the hidden mark when it is hidden. */
fun withHiddenLabel(labels: List<String>, hidden: Boolean): List<String> =
    if (hidden) (labels + HIDDEN_LABEL).distinct() else labels

/** Keep's note colors, as named by gkeepapi, mapped to ARGB values. */
object NoteColors {
    private val argb = linkedMapOf(
        "Red" to 0xFFF28B82.toInt(),
        "Orange" to 0xFFFBBC04.toInt(),
        "Yellow" to 0xFFFFF475.toInt(),
        "Green" to 0xFFCCFF90.toInt(),
        "Teal" to 0xFFA7FFEB.toInt(),
        "Blue" to 0xFFCBF0F8.toInt(),
        "DarkBlue" to 0xFFAECBFA.toInt(),
        "Purple" to 0xFFD7AEFB.toInt(),
        "Pink" to 0xFFFDCFE8.toInt(),
        "Brown" to 0xFFE6C9A8.toInt(),
        "Gray" to 0xFFE8EAED.toInt(),
    )

    /** Keep's colors for its dark mode: the same names, deeper shades. */
    private val darkArgb = mapOf(
        "Red" to 0xFF77172E.toInt(),
        "Orange" to 0xFF692B17.toInt(),
        "Yellow" to 0xFF7C4A03.toInt(),
        "Green" to 0xFF264D3B.toInt(),
        "Teal" to 0xFF0C625D.toInt(),
        "Blue" to 0xFF256377.toInt(),
        "DarkBlue" to 0xFF284255.toInt(),
        "Purple" to 0xFF472E5B.toInt(),
        "Pink" to 0xFF6C394F.toInt(),
        "Brown" to 0xFF4B443A.toInt(),
        "Gray" to 0xFF232427.toInt(),
    )

    const val DEFAULT = "White"
    val names: List<String> = listOf(DEFAULT) + argb.keys

    /** Null for the default (uncolored) note. */
    fun argb(name: String): Int? = argb[name]

    /** The color for a dark or a light look. */
    fun argb(name: String, dark: Boolean): Int? = if (dark) darkArgb[name] else argb[name]
}

/**
 * Orders notes for a widget: the chosen sort with Keep's own pinned notes first (not for a manual
 * order) and the notes the user shows at the widget's bottom at the end. Hidden notes
 * are dropped unless [includeHidden] is set (the settings list shows all of them).
 */
fun sortNotes(notes: List<KeepNote>, settings: WidgetSettings, includeHidden: Boolean = false): List<KeepNote> {
    val shown = if (includeHidden) notes else notes.filter { it.id !in settings.hiddenNotes }
    val sorted = when (settings.sortMode) {
        SORT_EDITED -> shown.sortedByDescending { it.updated }
        SORT_CREATED -> shown.sortedByDescending { it.created }
        SORT_TITLE -> shown.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { noteLabel(it) })
        SORT_MANUAL -> {
            val position = settings.manualOrder.withIndex().associate { it.value to it.index }
            // Notes that are not in the manual order yet (new ones) come first, in Keep's order.
            shown.sortedByDescending { it.sort }.sortedBy { position[it.id] ?: -1 }
        }
        else -> shown.sortedByDescending { it.sort }
    }
    // The sorts are stable, so the chosen order is kept within each group.
    // Notes pinned in Keep come first, except with a manual order, where the user's order decides.
    val keepPinned = if (settings.sortMode == SORT_MANUAL) sorted else sorted.sortedByDescending { it.pinned }
    val bottom = keepPinned.filter { it.id in settings.pinBottom }
    val rest = keepPinned.filter { it.id !in settings.pinBottom }
    return rest + bottom
}

/** Title, or the first line of the text or list, for places that need a short name. */
fun noteLabel(note: KeepNote): String = note.title.ifBlank {
    if (note.isList) note.items.firstOrNull()?.text.orEmpty() else note.text.lineSequence().firstOrNull().orEmpty()
}

/** Whether a note matches a search: every word of [query] must appear in the title, text or an item. */
fun KeepNote.matches(query: String): Boolean {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = (listOf(title, text) + items.map { it.text } + labels).joinToString("\n").lowercase()
    return words.all { it in haystack }
}

/**
 * Moves a note one place up (-1) or down (+1) among the notes the widget shows. The result is a
 * manual order; hidden notes keep their place. Returns null if the note cannot move that way.
 */
fun reorderedSettings(notes: List<KeepNote>, settings: WidgetSettings, noteId: String, delta: Int): WidgetSettings? {
    val ordered = sortNotes(notes, settings, includeHidden = true).map { it.id }.toMutableList()
    val visible = ordered.filter { it !in settings.hiddenNotes }
    val from = visible.indexOf(noteId)
    val to = from + delta
    if (from < 0 || to !in visible.indices) return null
    java.util.Collections.swap(ordered, ordered.indexOf(visible[from]), ordered.indexOf(visible[to]))
    return settings.copy(sortMode = SORT_MANUAL, manualOrder = ordered)
}
