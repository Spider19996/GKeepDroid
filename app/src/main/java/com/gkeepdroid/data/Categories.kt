package com.gkeepdroid

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit

/**
 * Checklists with categories, kept the way Keep can show them too: an item that starts with a bold word (or words) followed by a
 * space belongs to the category of that word ("**Apotheke** Perenterol"). A checklist with at least one such item is shown in
 * groups: the items without a category first, then each category under a header, in the order the categories first appear.
 */
object Categories {
    /** One group of a checklist: [name] is null for the items without a category. */
    class Group(val name: String?, val items: List<KeepItem>)

    /** The category of an item and where the rest of its text starts, or null if it has none. */
    class Split(val name: String, val restStart: Int)

    fun split(item: KeepItem, doc: RichDoc?): Split? {
        val text = item.text
        if (item.isDivider() || doc == null || doc.text.trimEnd('\n') != text.trimEnd('\n')) return null
        // The bold stretch at the very start; its flags may have more styles than bold, as long as bold is one of them.
        var end = 0
        val per = IntArray(text.length)
        doc.runs.forEach { (from, to, flags) -> for (i in from until minOf(to, per.size)) per[i] = flags }
        while (end < text.length && per[end] and RichNotes.BOLD != 0) end++
        // A bold trailing space counts as the separator too.
        var name = text.substring(0, end)
        val trimmed = name.trimEnd()
        if (trimmed.isBlank()) return null
        var rest = end
        if (trimmed.length == name.length) {
            if (end >= text.length || text[end] != ' ') return null
            rest = end + 1
        }
        name = trimmed
        while (rest < text.length && text[rest] == ' ') rest++
        // The rest may be empty: an item just made in a category, before anything is typed into it.
        return Split(name, rest)
    }

    /** The groups of a checklist, or null if none of its items has a category (it is then shown as always). */
    fun groups(items: List<KeepItem>, rich: RichNote?): List<Group>? {
        if (rich == null) return null
        val names = items.map { split(it, rich.docs[it.id])?.name }
        if (names.all { it == null }) return null
        // In the order the items are stored in (the editor saves them group by group), so the items without a category stand
        // where the editor had them, not always first.
        val order = mutableListOf<String?>()
        names.forEach { if (it !in order) order.add(it) }
        return order.map { name -> Group(name, items.filterIndexed { i, _ -> names[i] == name }) }.filter { it.name != null || it.items.isNotEmpty() }
    }

    /** What a swipe that ticks does to a group of items: tick them all, or untick them when they all are ticked already. Dividers do not count. */
    fun tickTarget(items: List<KeepItem>): Boolean = !items.filter { !it.isDivider() }.all { it.checked }

    /** How far a category is, as the checklists show it: ticked of all its items ("1/3"); dividers do not count. */
    fun progress(items: List<KeepItem>): String {
        val real = items.filter { !it.isDivider() }
        return "${real.count { it.checked }}/${real.size}"
    }

    /** The names of the categories a checklist has, in order. */
    fun names(items: List<KeepItem>, rich: RichNote?): List<String> = groups(items, rich).orEmpty().mapNotNull { it.name }

    /** What an item shows under its category's header: its text without the category, and the styles that go with it. */
    fun rest(item: KeepItem, doc: RichDoc?): Pair<String, RichDoc?> {
        val split = split(item, doc) ?: return item.text to doc
        val text = item.text.substring(split.restStart)
        val runs = doc!!.runs.mapNotNull { (from, to, flags) ->
            val a = maxOf(from - split.restStart, 0)
            val b = minOf(to - split.restStart, text.length)
            if (a < b) intArrayOf(a, b, flags) else null
        }
        return text to RichDoc(text, runs)
    }

    /** The text of a new item in [name] and how long its bold category is. */
    fun prefixed(name: String, text: String): Pair<String, Int> = "${name.trim()} ${text.trim()}" to name.trim().length
}

/** The categories a checklist has had on this device, also ones that are gone; offered again when an item goes into a new one. */
object CategoryHistory {
    private fun prefs(context: Context) = context.getSharedPreferences("category_history", Context.MODE_PRIVATE)

    fun load(context: Context, account: String, noteId: String): List<String> =
        runCatching { org.json.JSONArray(prefs(context).getString("$account|$noteId", "[]")) }.getOrNull()
            ?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

    fun remove(context: Context, account: String, noteId: String, name: String) {
        prefs(context).edit { putString("$account|$noteId", org.json.JSONArray(load(context, account, noteId) - name).toString()) }
        DeviceSync.changed(context)
    }

    /** The history of every checklist, by "account|note", for the sync between phones. */
    fun all(context: Context): Map<String, List<String>> = prefs(context).all.keys.associateWith { k ->
        val (account, note) = k.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        load(context, account, note)
    }

    fun add(context: Context, account: String, noteId: String, names: Collection<String>) {
        val all = (load(context, account, noteId) + names.map { it.trim() }.filter { it.isNotEmpty() }).distinct()
        prefs(context).edit { putString("$account|$noteId", org.json.JSONArray(all).toString()) }
        DeviceSync.changed(context)
    }
}

/** Which categories of which checklists are folded on this device. */
object CategoryFolds {
    private fun prefs(context: Context) = context.getSharedPreferences("folded_categories", Context.MODE_PRIVATE)

    fun key(account: String, noteId: String, name: String) = "$account|$noteId|$name"

    /** Changes whenever a category is folded or unfolded, so open screens redraw at once. */
    val version = mutableIntStateOf(0)

    fun load(context: Context): Set<String> = prefs(context).getStringSet("keys", null)?.toSet().orEmpty()

    fun set(context: Context, account: String, noteId: String, name: String, folded: Boolean) {
        val all = load(context).toMutableSet()
        if (folded) all.add(key(account, noteId, name)) else all.remove(key(account, noteId, name))
        prefs(context).edit { putStringSet("keys", all) }
        version.intValue++
        DeviceSync.changed(context)
    }
}
