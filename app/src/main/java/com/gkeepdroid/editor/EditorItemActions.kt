package com.gkeepdroid

import androidx.compose.runtime.snapshots.SnapshotStateList
import java.util.UUID

/** What the editor does with the items of a checklist: the swipe actions, and making items and categories. */
internal class EditorItemActions(
    private val items: SnapshotStateList<KeepItem>,
    private val categories: EditorCategories,
    private val model: EditorModel,
) {
    /** A swipe on one item, or on a category's header for all its items: tick, remove, or choose another category. */
    fun swipeItems(ids: List<String>, action: Int) {
        when (action) {
            ITEM_SWIPE_TICK -> {
                val tick = Categories.tickTarget(items.filter { it.id in ids })
                for (i in items.indices) if (items[i].id in ids && !items[i].isDivider()) items[i] = items[i].copy(checked = if (ids.size == 1) !items[i].checked else tick)
            }
            ITEM_SWIPE_REMOVE -> {
                items.removeAll { it.id in ids }
                if (items.isEmpty()) items.add(KeepItem(UUID.randomUUID().toString(), "", false))
                if (items.isNotEmpty() && items[0].indent) items[0] = items[0].copy(indent = false)
            }
            ITEM_SWIPE_CATEGORY -> {
                model.moveItems = ids
                model.newCategoryDialog = true
            }
        }
    }

    /** A new item at the end of category [name] (a new category at the end of the list), its name in bold; null: no category. */
    fun addInCategory(name: String?) {
        if (name == null) {
            val id = UUID.randomUUID().toString()
            items.add(KeepItem(id, "", false))
            model.focusItemId = id
        } else model.focusItemId = categories.addIn(name)
    }

    /** The name for a new category was given: for an item dropped onto "New category", as a first empty one, or with a new item. */
    fun createCategory(name: String) {
        val clean = name.trim()
        val forItem = model.newCategoryFor
        when {
            forItem != null -> {
                categories.set(forItem, clean)
                // To the end, where the new category's header appears.
                val at = items.indexOfFirst { it.id == forItem }
                if (at >= 0) items.add(items.removeAt(at))
            }
            model.newCategoryEmpty -> categories.keep(clean)
            else -> addInCategory(clean)
        }
        model.newCategoryFor = null
        model.newCategoryEmpty = false
    }
}
