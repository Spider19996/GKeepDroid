package com.gkeepdroid

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject

/**
 * One button of an editor of buttons (the note editor, the widget's bar): what it looks like, the group it belongs to (for
 * dividers between groups of a menu) and where it stands by default.
 */
class ButtonSpec(val id: String, @DrawableRes val icon: Int, @StringRes val label: Int, val group: Int = 0, val defaultZone: Int)

/**
 * The buttons of one editor and its rules: [specs] in the order of the default layout, the zone of a button that is off
 * ([hiddenZone]), which zones exist ([validZone]), where a button may be moved to ([allowedFor]: null anywhere, a set only to
 * those zones, an empty set nowhere) and the range of the one option a layout can carry ([optionRange], for example where a short
 * note sits). A zone is a number the editor chooses; the layout only keeps them apart.
 */
class ButtonCatalog(
    val specs: List<ButtonSpec>,
    val hiddenZone: Int,
    private val validZone: (Int) -> Boolean,
    private val allowedFor: (String) -> Set<Int>? = { null },
    val optionRange: IntRange = 0..0,
    /**
     * Buttons whose default place was moved in front of another one ("button" to "stands before this one") after layouts were
     * saved: a saved layout takes them there too, as long as the person has not moved the button.
     */
    val defaultAnchors: Map<String, String> = emptyMap(),
) {
    val byId = specs.associateBy { it.id }
    fun allowed(id: String): Set<Int>? = allowedFor(id)
    fun isValid(zone: Int) = validZone(zone)
    /** The layout nobody has changed. */
    fun layout() = ButtonLayout(this)
}

/**
 * Where each button of a [catalog] stands: its zone, and the order of all buttons (the order inside a zone is the order here).
 * The same for every editor of buttons: it moves buttons by the rules of the catalog and turns into text and back.
 */
data class ButtonLayout(
    val catalog: ButtonCatalog,
    val zones: Map<String, Int> = emptyMap(),
    val order: List<String> = catalog.specs.map { it.id },
    val option: Int = 0,
) {
    /**
     * Where a button stands. A zone that does not exist (any more) or that the rules do not allow for the button means its
     * default place; a button that is unknown is off.
     */
    fun zoneOf(id: String): Int {
        val default = catalog.byId[id]?.defaultZone ?: catalog.hiddenZone
        val zone = zones[id] ?: return default
        return if (!catalog.isValid(zone) || catalog.allowed(id)?.let { zone !in it } == true) default else zone
    }

    /** All buttons in the order of the layout; ones that are new or not known (any more) are handled here. */
    fun ids(): List<String> = (order + catalog.specs.map { it.id }).distinct().filter { it in catalog.byId }

    /** The buttons of a zone in their order. */
    fun inZone(zone: Int): List<ButtonSpec> = ids().map { catalog.byId.getValue(it) }.filter { zoneOf(it.id) == zone }

    /** [id] moved to [zone], in front of [beforeId] (the end of the zone if that is null); this layout if the rules do not allow it. */
    fun moved(id: String, zone: Int, beforeId: String?): ButtonLayout {
        if (id !in catalog.byId || !catalog.isValid(zone) || catalog.allowed(id)?.let { zone !in it } == true) return this
        val rest = ids().filter { it != id }
        val at = beforeId?.let { rest.indexOf(it) }?.takeIf { it >= 0 }
        val next = if (at != null) rest.take(at) + id + rest.drop(at) else {
            // The end of a zone: right after the last button that is in it, so it does not jump to the end of everything.
            val last = rest.indexOfLast { zoneOf(it) == zone }
            if (last >= 0) rest.take(last + 1) + id + rest.drop(last + 1) else rest + id
        }
        return copy(zones = zones + (id to zone), order = next)
    }

    fun toJsonObject(): JSONObject = JSONObject()
        .put("order", JSONArray(order))
        .put("option", option)
        .put("zones", JSONObject().also { o -> zones.forEach { (k, v) -> o.put(k, v) } })

    fun toJson(): String = toJsonObject().toString()

    /** The layout as a file of its own for saving it; [marker] tells it from other files and from the layouts of other editors. */
    fun toFileJson(marker: String, extra: JSONObject = JSONObject()): String =
        toJsonObject().put(marker, 1).also { o -> extra.keys().forEach { o.put(it, extra.get(it)) } }.toString(2)

    companion object {
        /** A layout read from text; the layout nobody has changed if the text is not one. */
        fun fromJson(catalog: ButtonCatalog, json: String?): ButtonLayout = runCatching { parse(catalog, JSONObject(json ?: return catalog.layout())) }
            .getOrDefault(catalog.layout())

        /** A layout read from a file; throws if the text is not one of [marker]. */
        fun fromFileJson(catalog: ButtonCatalog, text: String, marker: String): ButtonLayout {
            val o = JSONObject(text)
            require(o.has(marker)) { "Not a layout file" }
            return parse(catalog, o)
        }

        private fun parse(catalog: ButtonCatalog, o: JSONObject): ButtonLayout {
            var order = o.getJSONArray("order").let { a -> (0 until a.length()).map { a.getString(it) } }
            val z = o.getJSONObject("zones")
            catalog.defaultAnchors.forEach { (id, anchor) ->
                if (!z.has(id) && id in order && anchor in order) {
                    val rest = order.filter { it != id }
                    order = rest.take(rest.indexOf(anchor)) + id + rest.drop(rest.indexOf(anchor))
                }
            }
            return ButtonLayout(
                catalog, z.keys().asSequence().associateWith { z.getInt(it) }, order,
                o.optInt("option", catalog.optionRange.first).coerceIn(catalog.optionRange),
            )
        }
    }
}
