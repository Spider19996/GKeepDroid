package com.gkeepdroid

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background

/** What a tap in a widget does: parameter keys, the refresh and the action callbacks. */

internal val NoteIdKey = ActionParameters.Key<String>(EditNoteActivity.EXTRA_NOTE_ID)
internal val AccountKey = ActionParameters.Key<String>(EditNoteActivity.EXTRA_ACCOUNT)
internal val ItemIdKey = ActionParameters.Key<String>("item_id")
internal val CheckedKey = ActionParameters.Key<Boolean>("checked")
internal val CollapsedKey = ActionParameters.Key<Boolean>("collapsed")
internal val DeleteKey = ActionParameters.Key<Boolean>("delete")
internal val DeltaKey = ActionParameters.Key<Int>("delta")
internal val WidgetIdKey = ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID)


/** Redraws every placed widget of both kinds with the current notes cache and settings. */
object WidgetRefresh {
    internal val VersionKey = longPreferencesKey("version")

    suspend fun all(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val kinds = listOf(NotesWidget::class.java, SingleNoteWidget::class.java)
        kinds.forEach { kind ->
            manager.getGlanceIds(kind).forEach { id ->
                updateAppWidgetState(context, id) { it[VersionKey] = System.currentTimeMillis() }
            }
        }
        NotesWidget().updateAll(context)
        SingleNoteWidget().updateAll(context)
    }

    suspend fun hasWidgets(context: Context): Boolean {
        val manager = GlanceAppWidgetManager(context)
        return manager.getGlanceIds(NotesWidget::class.java).isNotEmpty() ||
            manager.getGlanceIds(SingleNoteWidget::class.java).isNotEmpty()
    }
}

/** Ticks or unticks a checklist item: instant local change, Keep is updated in the background. */
class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val account = parameters[AccountKey] ?: return
        val noteId = parameters[NoteIdKey] ?: return
        val itemId = parameters[ItemIdKey] ?: return
        val checked = parameters[CheckedKey] ?: return
        val delete = parameters[DeleteKey] ?: false
        applyTick(context, account, noteId, itemId, checked, delete)
    }
}

/** Instant local change; Keep is updated a few seconds after the last tick. */
internal suspend fun applyTick(context: Context, account: String, noteId: String, itemId: String, checked: Boolean, delete: Boolean) {
    PendingToggles.add(context, PendingToggle(account, noteId, itemId, checked, delete))
    val changed = if (delete) {
        NotesCache.removeItem(context, account, noteId, itemId)
    } else {
        NotesCache.setChecked(context, account, noteId, itemId, checked)
    }
    if (changed) WidgetRefresh.all(context)
    ToggleItemWorker.schedule(context, account)
}

/** Folds every note of the list widget, or unfolds them all when they are all folded already. */
class ToggleFoldAllAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = parameters[WidgetIdKey] ?: return
        val settings = SettingsStore.loadWidget(context, widgetId)
        val account = CredentialStore.resolve(context, settings.account) ?: return
        val keys = NotesCache.read(context, account)
            .filter { settings.labelFilter.isEmpty() || settings.labelFilter in it.labels }
            .map { FoldStore.key(account, it.id) }
        val folded = FoldStore.load(context)
        FoldStore.setAll(context, keys, !keys.all { it in folded })
        WidgetRefresh.all(context)
    }
}

/** Folds a note to its title or unfolds it. */
class ToggleFoldAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val account = parameters[AccountKey] ?: return
        val noteId = parameters[NoteIdKey] ?: return
        FoldStore.set(context, account, noteId, parameters[CollapsedKey] ?: return)
        WidgetRefresh.all(context)
    }
}

class ToggleCollapseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val account = parameters[AccountKey] ?: return
        val noteId = parameters[NoteIdKey] ?: return
        CollapseStore.set(context, account, noteId, parameters[CollapsedKey] ?: return)
        WidgetRefresh.all(context)
    }
}

/** Switches the arrows for re-ordering notes on or off for one widget. */
class ToggleReorderAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = parameters[WidgetIdKey] ?: return
        SettingsStore.setReordering(context, widgetId, !SettingsStore.isReordering(context, widgetId))
        WidgetRefresh.all(context)
    }
}

/** Moves a note up or down in the widget's manual order. */
class MoveNoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = parameters[WidgetIdKey] ?: return
        val noteId = parameters[NoteIdKey] ?: return
        val delta = parameters[DeltaKey] ?: return
        val settings = SettingsStore.loadWidget(context, widgetId)
        val account = CredentialStore.resolve(context, settings.account) ?: return
        val updated = reorderedSettings(NotesCache.read(context, account), settings, noteId, delta) ?: return
        SettingsStore.saveWidget(context, widgetId, updated)
        WidgetRefresh.all(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SyncWorker.syncNow(context)
    }
}
