package com.gkeepdroid

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * The place every note has in the list widget, from 1, by "account|note": the grid shows no clear order of its own. Each
 * account is counted the way its first placed list widget orders and filters it; without one, as a new widget would. Notes
 * the widget leaves out get no number.
 */
internal fun widgetPositions(context: Context, accounts: Collection<String>): Map<String, Int> {
    val placed = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, NotesWidgetReceiver::class.java))
        .sorted().map { SettingsStore.loadWidget(context, it) }
    return buildMap {
        accounts.forEach { account ->
            val settings = placed.firstOrNull { CredentialStore.resolve(context, it.account) == account }
                ?: SettingsStore.loadWidget(context, AppWidgetManager.INVALID_APPWIDGET_ID)
                    .copy(hiddenNotes = emptySet(), labelFilter = "")
            sortNotes(NotesCache.read(context, account), settings)
                .filter { settings.labelFilter.isEmpty() || settings.labelFilter in it.labels }
                .forEachIndexed { index, note -> put("$account|${note.id}", index + 1) }
        }
    }
}
