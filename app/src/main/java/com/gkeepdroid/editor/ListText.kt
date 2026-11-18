package com.gkeepdroid

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import android.content.Intent
import java.util.UUID
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color

/** Recognises text typed as a list ("- milk", "* eggs", "[ ] bread") and turns it into checklist items. */
internal object ChecklistText {
    private val marker = Regex("""^\s*(?:[-–—*•](?:\s+|(?=\p{L}))(?:\[([ xX]?)\]\s*)?|\[([ xX]?)\]\s*|\d{1,3}[.)]\s+)(.*)$""")

    /** At least one line that starts with a list marker ("- ", "* ", "• ", "[ ] " or "1. ") followed by text. */
    fun looksLikeList(text: String): Boolean =
        text.lineSequence().any { line -> marker.matchEntire(line)?.groupValues?.get(3)?.isNotBlank() == true }

    private val numberedLine = Regex("""^\s*\d{1,3}[.)]\s+\S""")

    fun items(text: String): List<KeepItem> = text.lineSequence().filter { it.isNotBlank() }.map { line ->
        // A numbered line keeps its number in the text: the list is then a numbered list.
        if (numberedLine.containsMatchIn(line)) return@map KeepItem(UUID.randomUUID().toString(), line.trim(), false)
        val m = marker.find(line)
        val checked = m != null && (m.groupValues[1].equals("x", true) || m.groupValues[2].equals("x", true))
        KeepItem(UUID.randomUUID().toString(), (m?.groupValues?.get(3) ?: line).trim(), checked)
    }.toList()
}

/** A web address, e-mail address or phone number found in a note, with the intent that opens it. */
internal data class NoteLink(val label: String, val intent: Intent)

internal object NoteLinks {
    private val phone = Regex("""[+0][\d ()/.-]{5,}\d""")

    /** The text with its links made tappable: the system opens each in the app made for it (browser, mail, phone). */
    fun annotate(text: String, color: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString {
        val taken = mutableListOf<IntRange>()
        val ranges = mutableListOf<Pair<IntRange, String>>()
        fun add(range: IntRange, url: String) {
            if (taken.none { it.first <= range.last && range.first <= it.last }) {
                taken += range
                ranges += range to url
            }
        }
        // E-mail addresses first, so their domain is not taken for a web address.
        android.util.Patterns.EMAIL_ADDRESS.matcher(text).let { m ->
            while (m.find()) add(m.start() until m.end(), "mailto:" + m.group())
        }
        android.util.Patterns.WEB_URL.matcher(text).let { m ->
            while (m.find()) add(m.start() until m.end(), if (m.group().contains("://")) m.group() else "https://" + m.group())
        }
        phone.findAll(text).forEach { m ->
            if (m.value.count { it.isDigit() } >= 7) add(m.range, "tel:" + m.value.filter { it.isDigit() || it == '+' })
        }
        if (ranges.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
        return androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            ranges.forEach { (range, url) ->
                addLink(
                    androidx.compose.ui.text.LinkAnnotation.Url(
                        url,
                        androidx.compose.ui.text.TextLinkStyles(
                            androidx.compose.ui.text.SpanStyle(color = color, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                        ),
                    ),
                    range.first, range.last + 1,
                )
            }
        }
    }

    fun find(texts: List<String>): List<NoteLink> {
        val all = texts.joinToString("\n")
        val found = linkedMapOf<String, NoteLink>()
        fun add(label: String, intent: Intent) {
            // The chip shows the address without "https://www." and a trailing slash: shorter, and still clear.
            val shown = label.removePrefix("https://").removePrefix("http://").removePrefix("www.").removeSuffix("/")
            if (found.size < 8) found.putIfAbsent(label, NoteLink(shown.take(36), intent))
        }
        android.util.Patterns.WEB_URL.matcher(all).let { m ->
            while (m.find()) {
                val url = m.group()
                add(url, Intent(Intent.ACTION_VIEW, (if (url.contains("://")) url else "https://$url").toUri()))
            }
        }
        android.util.Patterns.EMAIL_ADDRESS.matcher(all).let { m ->
            while (m.find()) add(m.group(), Intent(Intent.ACTION_SENDTO, ("mailto:" + m.group()).toUri()))
        }
        phone.findAll(all).forEach { m ->
            if (m.value.count { it.isDigit() } >= 7) add(m.value.trim(), Intent(Intent.ACTION_DIAL, ("tel:" + m.value.filter { it.isDigit() || it == '+' }).toUri()))
        }
        // An address that is part of an e-mail address is not offered a second time.
        return found.values.filterNot { link -> link.intent.action == Intent.ACTION_VIEW && found.keys.any { it.contains("@") && it.contains(link.label) } }
    }
}

/** Continues a typed list: numbers count up, dashes and stars repeat, and an empty item ends the list. */
internal object ListContinue {
    private val numbered = Regex("""^(\s*)(\d{1,3})([.)])\s(.*)$""")
    private val bulleted = Regex("""^(\s*)([-*•])\s(.*)$""")

    fun apply(old: String, typed: androidx.compose.ui.text.input.TextFieldValue): androidx.compose.ui.text.input.TextFieldValue {
        val text = typed.text
        val pos = typed.selection.start
        // Only one Enter typed at the cursor counts; anything else (paste, delete, other typing) is left alone.
        val enter = typed.selection.collapsed && text.length == old.length + 1 && pos > 0 && text[pos - 1] == '\n' &&
            text.removeRange(pos - 1, pos) == old
        if (!enter) return typed
        val lineStart = old.lastIndexOf('\n', pos - 2).let { if (pos - 2 < 0) 0 else it + 1 }
        val line = old.substring(lineStart, pos - 1)
        val marker: String
        val empty: Boolean
        val n = numbered.matchEntire(line)
        val b = bulleted.matchEntire(line)
        when {
            n != null -> {
                marker = n.groupValues[1] + (n.groupValues[2].toInt() + 1) + n.groupValues[3] + " "
                empty = n.groupValues[4].isBlank()
            }
            b != null -> {
                marker = b.groupValues[1] + b.groupValues[2] + " "
                empty = b.groupValues[3].isBlank()
            }
            else -> return typed
        }
        return if (empty) {
            // Enter on an empty item: the marker goes away and the list is over.
            androidx.compose.ui.text.input.TextFieldValue(
                old.substring(0, lineStart) + old.substring(pos - 1), androidx.compose.ui.text.TextRange(lineStart),
            )
        } else {
            androidx.compose.ui.text.input.TextFieldValue(
                text.substring(0, pos) + marker + text.substring(pos), androidx.compose.ui.text.TextRange(pos + marker.length),
            )
        }
    }
}

/** Numbered checklists: the numbers are part of the items' text ("1. Milk"), so Keep shows them as well. */
internal object ListNumbering {
    private val marker = Regex("""^\s*\d{1,3}[.)]\s+""")

    /** Every filled item starts with a number. */
    fun isNumbered(items: List<KeepItem>): Boolean {
        val filled = items.filter { it.text.isNotBlank() && !it.isDivider() }
        return filled.isNotEmpty() && filled.all { marker.containsMatchIn(it.text) }
    }

    fun strip(items: List<KeepItem>): List<KeepItem> = items.map { it.copy(text = it.text.replaceFirst(marker, "")) }

    /** Puts "1. ", "2. " ... in front of the filled items, counting from [startAt]. */
    fun apply(items: List<KeepItem>, startAt: Int = 1): List<KeepItem> {
        var n = startAt - 1
        return items.map { if (it.text.isBlank() || it.isDivider()) it else { n++; it.copy(text = "$n. ${it.text}") } }
    }

    /** For cards and widgets: with the setting on, a numbered list counts again in the order it is shown. */
    fun display(all: List<KeepItem>, shown: List<KeepItem>, renumber: Boolean, startAt: Int = 1): List<KeepItem> =
        if (renumber && isNumbered(all)) apply(strip(shown), startAt) else shown
}
