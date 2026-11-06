package com.gkeepdroid

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider

/** The button bars of the widgets: which buttons, where, and how they are drawn. */

/** The name of a button, which a screen reader speaks for its icon. */
private fun buttonLabelRes(id: String): Int = when (id) {
    BTN_REFRESH -> R.string.btn_refresh
    BTN_ADD_ITEM -> R.string.btn_add_item
    BTN_REMINDER -> R.string.reminders_title
    BTN_NEW_NOTE -> R.string.btn_new_note
    BTN_NEW_LIST -> R.string.btn_new_list
    BTN_SORT -> R.string.btn_sort
    BTN_SEARCH -> R.string.btn_search
    BTN_APP -> R.string.btn_app
    BTN_KEEP -> R.string.btn_keep
    BTN_FOLD -> R.string.btn_fold
    else -> R.string.btn_settings
}

private data class BarButton(val id: String, @DrawableRes val icon: Int, val action: Action)

/** The buttons that are switched on for this widget. */
internal fun enabledButtons(settings: WidgetSettings, withNewButtons: Boolean, hasAddItem: Boolean = false): List<String> {
    val on = enabledSet(settings, withNewButtons, hasAddItem)
    return settings.orderedIds().filter { it in on }
}

private fun enabledSet(settings: WidgetSettings, withNewButtons: Boolean, hasAddItem: Boolean): Set<String> =
    settings.buttonSides.keys.filter {
        when (it) {
            BTN_ADD_ITEM -> hasAddItem
            // Only the single-note widget has one note to set a reminder for.
            BTN_REMINDER -> !withNewButtons
            BTN_NEW_NOTE, BTN_NEW_LIST, BTN_SEARCH -> withNewButtons
            // The sort button only appears with a manual order.
            BTN_SORT -> withNewButtons && settings.sortMode == SORT_MANUAL
            // Only when folding is switched on for the widget.
            BTN_FOLD -> withNewButtons && settings.foldable
            else -> true
        }
    }.toSet()

/** All button ids in the order the user chose; buttons never moved keep their default place at the end. */
internal fun WidgetSettings.orderedIds(): List<String> = (buttonOrder + ALL_BUTTONS).distinct().filter { it in ALL_BUTTONS }

internal fun WidgetSettings.sideOf(button: String): Int = buttonSides[button] ?: POS_TOP

internal fun hasBarAt(settings: WidgetSettings, side: Int, withNewButtons: Boolean, hasAddItem: Boolean = false): Boolean =
    enabledButtons(settings, withNewButtons, hasAddItem).any { settings.sideOf(it) == side }

/** Width the bars at the left and right edge take away from the content (icon plus its padding). */
internal fun sideBarWidth(settings: WidgetSettings, withNewButtons: Boolean, hasAddItem: Boolean = false): Dp {
    val bars = listOf(POS_LEFT, POS_RIGHT).count { hasBarAt(settings, it, withNewButtons, hasAddItem) }
    return ((settings.headerSizeSp + 16) * bars).dp
}

/**
 * One bar of the button bar: the buttons assigned to [side] (top, bottom, left or right). Every
 * button picks its own side. A bar is a row or a column, with the buttons sharing its whole length.
 */
@Composable
internal fun HeaderBar(
    side: Int,
    appWidgetId: Int,
    account: String?,
    settings: WidgetSettings,
    textColor: ColorProvider,
    withNewButtons: Boolean,
    reordering: Boolean = false,
    /** The checklist the single-note widget shows; gives it a button to add an item. */
    addItemNoteId: String? = null,
    /** The note the single-note widget shows; the reminder button is for this note. */
    reminderNoteId: String? = null,
    /** Every note of the list widget is folded: the fold button then unfolds. */
    allFolded: Boolean = false,
) {
    val context = LocalContext.current
    val buttons = enabledButtons(settings, withNewButtons, addItemNoteId != null)
        .filter { settings.sideOf(it) == side }
        .map { id ->
            when (id) {
                BTN_REFRESH -> BarButton(id, R.drawable.ic_refresh, actionRunCallback<RefreshAction>())
                BTN_ADD_ITEM -> BarButton(
                    id, R.drawable.ic_add,
                    actionStartActivity<AddItemActivity>(actionParametersOf(AccountKey to account.orEmpty(), NoteIdKey to addItemNoteId.orEmpty())),
                )
                BTN_REMINDER -> BarButton(
                    id, R.drawable.ic_alarm,
                    actionStartActivity<RemindersActivity>(actionParametersOf(AccountKey to account.orEmpty(), NoteIdKey to reminderNoteId.orEmpty())),
                )
                BTN_NEW_NOTE -> BarButton(id, R.drawable.ic_add, actionStartActivity(newNoteIntent(context, appWidgetId, account, isList = false)))
                BTN_NEW_LIST -> BarButton(id, R.drawable.ic_checklist, actionStartActivity(newNoteIntent(context, appWidgetId, account, isList = true)))
                // Shows or hides the up/down arrows on the notes; a check mark means "done".
                BTN_SORT -> BarButton(
                    id, if (reordering) R.drawable.ic_done else R.drawable.ic_sort,
                    actionRunCallback<ToggleReorderAction>(actionParametersOf(WidgetIdKey to appWidgetId)),
                )
                // A widget cannot hold a text field, so the button opens the search screen.
                BTN_SEARCH -> BarButton(
                    id, R.drawable.ic_search,
                    actionStartActivity<SearchActivity>(actionParametersOf(AccountKey to account.orEmpty(), WidgetIdKey to appWidgetId)),
                )
                BTN_FOLD -> BarButton(
                    id, if (allFolded) R.drawable.ic_unfold else R.drawable.ic_fold,
                    actionRunCallback<ToggleFoldAllAction>(actionParametersOf(WidgetIdKey to appWidgetId)),
                )
                BTN_APP -> BarButton(id, R.drawable.ic_apps, actionStartActivity<MainActivity>())
                BTN_KEEP -> BarButton(id, R.drawable.ic_open_new, actionStartActivity(keepIntent(context)))
                else -> BarButton(
                    id, R.drawable.ic_settings,
                    actionStartActivity<SettingsActivity>(actionParametersOf(WidgetIdKey to appWidgetId)),
                )
            }
        }
    // Without any button there is no bar at all, not even an empty gap.
    if (buttons.isEmpty()) return

    val size = settings.headerSizeSp
    val vertical = side == POS_LEFT || side == POS_RIGHT
    // The buttons sit at the start, in the middle or at the end of the bar.
    fun group(slot: Int) = buttons.filter { (settings.buttonSlots[it.id] ?: ALIGN_CENTER) == slot }

    if (vertical) {
        Column(GlanceModifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            run {
                Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.Top) {
                    group(ALIGN_START).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
                Column {
                    group(ALIGN_CENTER).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
                Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.Bottom) {
                    group(ALIGN_END).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
            }
        }
    } else {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            run {
                // Equal weights on both outer groups keep the middle group centered.
                Row(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Start) {
                    group(ALIGN_START).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
                Row {
                    group(ALIGN_CENTER).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
                Row(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.End) {
                    group(ALIGN_END).forEach { HeaderIcon(GlanceModifier, it.icon, textColor, size, it.action, context.getString(buttonLabelRes(it.id))) }
                }
            }
        }
    }
}

/** Lays out [body] with up to four bars around it; [bar] draws the bar of one side. */
@Composable
internal fun WithHeaderBar(
    modifier: GlanceModifier,
    bar: @Composable (side: Int) -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        bar(POS_TOP)
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            bar(POS_LEFT)
            Column(GlanceModifier.defaultWeight().fillMaxHeight(), content = body)
            bar(POS_RIGHT)
        }
        bar(POS_BOTTOM)
    }
}
