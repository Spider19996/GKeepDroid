package com.gkeepdroid

import android.content.Context
import androidx.core.content.edit

/** Per-note override of whether the checked items of a checklist are collapsed in the widget. */
object CollapseStore {
    private fun prefs(context: Context) = context.getSharedPreferences("collapsed", Context.MODE_PRIVATE)

    fun key(account: String, noteId: String) = "$account|$noteId"

    fun load(context: Context): Map<String, Boolean> =
        prefs(context).all.mapNotNull { (k, v) -> (v as? Boolean)?.let { k to it } }.toMap()

    fun set(context: Context, account: String, noteId: String, collapsed: Boolean) {
        prefs(context).edit { putBoolean(key(account, noteId), collapsed) }
    }
}

/**
 * Every hint the user can dismiss for good (or that is shown only once), kept in three small stores:
 * the explanation above checklists, the notification notice and the one-time questions about notifications
 * and exact alarms, and the "No" to the offer to turn a note into a checklist. Resetting shows them all again.
 */
object Hints {
    private val stores = listOf("hints", "reminder_hints", "convert_hints")

    fun resetAll(context: Context) {
        stores.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit { clear() } }
    }
}

/** Notes folded to their title, in the app and in the widgets; stays on this device. */
object FoldStore {
    private fun prefs(context: Context) = context.getSharedPreferences("folded_notes", Context.MODE_PRIVATE)

    fun key(account: String, noteId: String) = "$account|$noteId"

    /** Changes whenever a note is folded or unfolded, so open screens redraw their cards at once. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun load(context: Context): Set<String> = prefs(context).getStringSet("keys", null)?.toSet().orEmpty()

    fun setAll(context: Context, keys: Collection<String>, folded: Boolean) {
        val all = load(context).toMutableSet()
        if (folded) all.addAll(keys) else all.removeAll(keys.toSet())
        prefs(context).edit { putStringSet("keys", all) }
        version.intValue++
    }

    fun set(context: Context, account: String, noteId: String, folded: Boolean) = setAll(context, listOf(key(account, noteId)), folded)
}

/** Notes whose content is hidden in the widgets (only the title shows); stays on this device. */
object PrivateNotes {
    private fun prefs(context: Context) = context.getSharedPreferences("private_notes", Context.MODE_PRIVATE)

    fun key(account: String, noteId: String) = "$account|$noteId"

    /** Hidden by the mark in Keep, or by the older mark that only this phone knows. */
    fun isPrivate(note: KeepNote, account: String, keys: Set<String>) = note.hidden || key(account, note.id) in keys

    fun load(context: Context): Set<String> = prefs(context).getStringSet("keys", null)?.toSet().orEmpty()

    /** Changes whenever a note is hidden or shown, so open screens redraw their cards at once. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun set(context: Context, account: String, noteId: String, hidden: Boolean) {
        version.intValue++
        val keys = load(context).toMutableSet()
        if (hidden) keys.add(key(account, noteId)) else keys.remove(key(account, noteId))
        prefs(context).edit { putStringSet("keys", keys) }
    }
}
