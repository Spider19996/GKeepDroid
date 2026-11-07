package com.gkeepdroid

import org.json.JSONArray
import org.json.JSONObject

/** Settings export/import. Master tokens are deliberately never part of the file. */
object Backup {
    private const val FORMAT_VERSION = 1

    /** Tells a saved layout file of the widget's bar from other files. */
    private const val LAYOUT_MARKER = "layout"

    /** Only the button bar: which buttons are shown, where, in which order, and their size. */
    fun exportLayout(w: WidgetSettings): String = w.buttons.toFileJson(LAYOUT_MARKER, JSONObject().put("headerSizeSp", w.headerSizeSp))

    /** Applies a saved layout on top of [base]; throws if the text is not a layout. */
    fun parseLayout(text: String, base: WidgetSettings): WidgetSettings {
        return base.copy(
            headerSizeSp = JSONObject(text).optInt("headerSizeSp", base.headerSizeSp),
            buttons = ButtonLayout.fromFileJson(WIDGET_CATALOG, text, LAYOUT_MARKER),
        )
    }

    data class Imported(val global: GlobalSettings, val widget: WidgetSettings, val reminders: JSONObject = JSONObject(), val app: JSONObject? = null)

    /** The widget's account (an e-mail address) is left out on purpose, like the token. */
    fun export(global: GlobalSettings, widget: WidgetSettings, reminders: JSONObject = JSONObject(), app: JSONObject = JSONObject()): String =
        JSONObject()
            .put("version", FORMAT_VERSION)
            .put("reminders", reminders)
            .put("app", app)
            .put(
                "global",
                JSONObject()
                    .put("themeMode", global.themeMode)
                    .put("syncIntervalMin", global.syncIntervalMin)
                    .put("syncOnUnlock", global.syncOnUnlock)
                    .put("syncOnOpen", global.syncOnOpen)
                    .put("minGapSec", global.minGapSec)
                    .put("backgroundSync", global.backgroundSync)
                    .put("backgroundIntervalMin", global.backgroundIntervalMin)
                    .put("wifiOnly", global.wifiOnly)
                    .put("language", global.language)
                    .put("reminderSound", global.reminderSound)
                    .put("reminderVibrate", global.reminderVibrate)
                    .put("reminderOverride", global.reminderOverride)
                    .put("reminderSnoozeMin", global.reminderSnoozeMin)
                    .put("reminderPresets", global.reminderPresets),
            )
            .put(
                "widget",
                JSONObject()
                    .put("backgroundAlpha", widget.backgroundAlpha.toDouble())
                    .put("textColor", widget.textColor)
                    .put("textSizeSp", widget.textSizeSp)
                    .put("showNoteColors", widget.showNoteColors)
                    .put("noteAlpha", widget.noteAlpha.toDouble())
                    .put("plainCardStyle", widget.plainCardStyle)
                    .put("headerSizeSp", widget.headerSizeSp)
                    .put("checkable", widget.checkable)
                    .put("checkedToEnd", widget.checkedToEnd)
                    .put("collapseChecked", widget.collapseChecked)
                    .put("listSpacingDp", widget.listSpacingDp)
                    .put("sortMode", widget.sortMode)
                    .put("showImages", widget.showImages)
                    .put("buttonLayout", widget.buttons.toJsonObject())
                    .put("checkboxStyle", widget.checkboxStyle)
                    .put("checkboxSizeDp", widget.checkboxSizeDp)
                    .put("tapWholeRow", widget.tapWholeRow)
                    .put("deleteChecked", widget.deleteChecked)
                    .put("maxLines", widget.maxLines)
                    .put("textColorAuto", widget.textColorAuto)
                    .put("showEditedTime", widget.showEditedTime)
                    .put("showLabels", widget.showLabels)
                    .put("showReminders", widget.showReminders)
                    .put("labelFilter", widget.labelFilter)
                    .put("showProgress", widget.showProgress),
            )
            .toString(2)

    fun parse(json: String): Imported {
        val root = JSONObject(json)
        require(root.optInt("version", 0) == FORMAT_VERSION) { "Unsupported file version" }
        val g = root.getJSONObject("global")
        val w = root.getJSONObject("widget")
        val dg = GlobalSettings()
        val dw = WidgetSettings()
        return Imported(
            reminders = root.optJSONObject("reminders") ?: JSONObject(),
            app = root.optJSONObject("app"),
            global = GlobalSettings(
                themeMode = g.optInt("themeMode", dg.themeMode).coerceIn(THEME_SYSTEM, THEME_OLED),
                syncIntervalMin = g.optInt("syncIntervalMin", dg.syncIntervalMin).coerceAtLeast(0),
                syncOnUnlock = g.optBoolean("syncOnUnlock", dg.syncOnUnlock),
                syncOnOpen = g.optBoolean("syncOnOpen", dg.syncOnOpen),
                minGapSec = g.optInt("minGapSec", dg.minGapSec).coerceAtLeast(0),
                backgroundSync = g.optBoolean("backgroundSync", dg.backgroundSync),
                backgroundIntervalMin = g.optInt("backgroundIntervalMin", dg.backgroundIntervalMin).coerceAtLeast(1),
                wifiOnly = g.optBoolean("wifiOnly", dg.wifiOnly),
                language = g.optString("language", dg.language),
                reminderSound = g.optString("reminderSound", dg.reminderSound),
                reminderVibrate = g.optBoolean("reminderVibrate", dg.reminderVibrate),
                reminderOverride = g.optBoolean("reminderOverride", dg.reminderOverride),
                reminderSnoozeMin = g.optInt("reminderSnoozeMin", dg.reminderSnoozeMin),
                reminderPresets = g.optString("reminderPresets", dg.reminderPresets),
            ),
            widget = WidgetSettings(
                backgroundAlpha = w.optDouble("backgroundAlpha", dw.backgroundAlpha.toDouble()).toFloat().coerceIn(0f, 1f),
                textColor = w.optInt("textColor", dw.textColor),
                textSizeSp = w.optInt("textSizeSp", dw.textSizeSp).coerceIn(8, 40),
                showNoteColors = w.optBoolean("showNoteColors", dw.showNoteColors),
                noteAlpha = w.optDouble("noteAlpha", dw.noteAlpha.toDouble()).toFloat().coerceIn(0f, 1f),
                plainCardStyle = w.optInt("plainCardStyle", dw.plainCardStyle).coerceIn(PLAIN_TRANSPARENT, PLAIN_BORDER),
                headerSizeSp = w.optInt("headerSizeSp", dw.headerSizeSp).coerceIn(12, 48),
                checkable = w.optBoolean("checkable", dw.checkable),
                checkedToEnd = w.optBoolean("checkedToEnd", dw.checkedToEnd),
                collapseChecked = w.optBoolean("collapseChecked", dw.collapseChecked),
                listSpacingDp = w.optInt("listSpacingDp", dw.listSpacingDp).coerceIn(0, 24),
                sortMode = w.optInt("sortMode", dw.sortMode).coerceIn(SORT_KEEP, SORT_MANUAL),
                showImages = w.optBoolean("showImages", dw.showImages),
                buttons = if (w.has("buttonLayout")) ButtonLayout.fromJson(WIDGET_CATALOG, w.getJSONObject("buttonLayout").toString()) else dw.buttons,
                checkboxStyle = w.optInt("checkboxStyle", dw.checkboxStyle).coerceIn(CB_SQUARE, CB_CLASSIC),
                checkboxSizeDp = w.optInt("checkboxSizeDp", dw.checkboxSizeDp).coerceIn(14, 56),
                tapWholeRow = w.optBoolean("tapWholeRow", dw.tapWholeRow),
                deleteChecked = w.optBoolean("deleteChecked", dw.deleteChecked),
                maxLines = w.optInt("maxLines", dw.maxLines).coerceIn(0, 40),
                textColorAuto = w.optBoolean("textColorAuto", dw.textColorAuto),
                showEditedTime = w.optBoolean("showEditedTime", dw.showEditedTime),
                showLabels = w.optBoolean("showLabels", dw.showLabels),
                showReminders = w.optBoolean("showReminders", dw.showReminders),
                labelFilter = w.optString("labelFilter", dw.labelFilter),
                showProgress = w.optBoolean("showProgress", dw.showProgress),
            ),
        )
    }
}
