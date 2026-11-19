package com.gkeepdroid

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The row at the top of the notes screen: the search field and the menu, or, while notes are selected, what can be done with them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.NotesTopBar(
    activity: MainActivity, model: NotesModel, app: AppSettings, shown: List<Pair<KeepNote, String>>, selectedEntries: List<Pair<KeepNote, String>>,
    scope: kotlinx.coroutines.CoroutineScope, sync: () -> Unit, keyOf: (Pair<KeepNote, String>) -> String,
    moveSelected: (suspend (KeepNote, String) -> Unit) -> Unit, editSelected: ((KeepNote, String) -> KeepNote) -> Unit,
    trashSelected: () -> Unit, duplicateSelected: () -> Unit,
) {
    val context = LocalContext.current
    var screen by model::screen
    var query by model::query
    var syncing by model::syncing
    var selected by model::selected
    var selectionMenu by model::selectionMenu
    var colorDialog by model::colorDialog
    var labelDialog by model::labelDialog
    var emptyTrashDialog by model::emptyTrashDialog
    var deleteSelected by model::deleteSelected
    var searchFocused by model::searchFocused
    var history by model::history
    var menuOpen by model::menuOpen
                // Search field and the menu. A running sync shows as a thin line on the row's lower edge, which takes no room.
                Box {
                if (selected.isEmpty()) Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (screen != AppScreen.Notes) {
                        BackButton { screen = AppScreen.Notes }
                    }
                    AppSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { history = SearchHistory.add(context, query) },
                        onFocusChange = {
                            searchFocused = it
                            if (!it) history = SearchHistory.add(context, query)
                        },
                        // The field names how many notes it searches (the ones the filters leave).
                        placeholder = when {
                            else -> pluralStringResource(
                                when (screen) {
                                    AppScreen.Notes -> R.plurals.search_count_notes
                                    AppScreen.Archive -> R.plurals.search_count_archive
                                    AppScreen.Trash -> R.plurals.search_count_trash
                                },
                                shown.size, shown.size,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_actions))
                        }
                        GroupedMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            groups = listOf(
                                listOf(MenuEntry(stringResource(R.string.refresh_action), R.drawable.ic_refresh) { menuOpen = false; sync() }),
                                if (screen == AppScreen.Trash && shown.isNotEmpty()) listOf(
                                    MenuEntry(stringResource(R.string.empty_trash), R.drawable.ic_delete) { menuOpen = false; emptyTrashDialog = true },
                                ) else emptyList(),
                                listOf(
                                    MenuEntry(stringResource(R.string.archive_note), R.drawable.ic_archive) { menuOpen = false; screen = AppScreen.Archive },
                                    MenuEntry(stringResource(R.string.trash_title), R.drawable.ic_delete) { menuOpen = false; screen = AppScreen.Trash },
                                ),
                                if (screen == AppScreen.Notes && app.foldNotes) listOf(
                                    MenuEntry(stringResource(R.string.fold_all), R.drawable.ic_fold) {
                                        menuOpen = false
                                        FoldStore.setAll(context, shown.map { FoldStore.key(it.second, it.first.id) }, true)
                                        scope.launch { WidgetRefresh.all(context) }
                                    },
                                    MenuEntry(stringResource(R.string.unfold_all), R.drawable.ic_unfold) {
                                        menuOpen = false
                                        FoldStore.setAll(context, shown.map { FoldStore.key(it.second, it.first.id) }, false)
                                        scope.launch { WidgetRefresh.all(context) }
                                    },
                                ) else emptyList(),
                                buildList {
                                    if (app.lockApp && AppLock.enabled(activity)) {
                                        add(MenuEntry(stringResource(R.string.app_lock_title), R.drawable.ic_lock) { menuOpen = false; AppLock.lockNow() })
                                    }
                                    add(
                                        MenuEntry(stringResource(R.string.menu_settings), R.drawable.ic_settings) {
                                            menuOpen = false
                                            activity.startActivity(Intent(context, SettingsActivity::class.java))
                                        },
                                    )
                                },
                            ),
                        )
                    }
                }
                else Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(SearchBarDefaults.InputFieldHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selected = emptySet() }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(android.R.string.cancel))
                    }
                    Text(
                        pluralStringResource(R.plurals.selected_count, selected.size, selected.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (screen == AppScreen.Notes) IconButton(onClick = {
                        val pin = selectedEntries.any { !it.first.pinned }
                        editSelected { note, _ -> note.copy(pinned = pin) }
                    }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_push_pin), contentDescription = stringResource(R.string.pinned_section))
                    }
                    if (screen != AppScreen.Trash) IconButton(onClick = {
                        if (screen == AppScreen.Archive) moveSelected { note, account -> KeepRepository.unarchiveNote(context, account, note.id) }
                        else moveSelected { note, account -> if (!CredentialStore.isLocal(account)) KeepRepository.archiveNote(context, account, note.id) }
                    }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painterResource(R.drawable.ic_archive),
                            contentDescription = stringResource(if (screen == AppScreen.Archive) R.string.unarchive_note else R.string.archive_note),
                        )
                    }
                    IconButton(onClick = {
                        if (screen == AppScreen.Trash) deleteSelected = selectedEntries else trashSelected()
                    }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(if (screen == AppScreen.Trash) R.string.delete_forever else R.string.trash_title),
                        )
                    }
                    Box {
                        IconButton(onClick = { selectionMenu = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_actions))
                        }
                        GroupedMenu(
                            expanded = selectionMenu,
                            onDismissRequest = { selectionMenu = false },
                            groups = listOf(
                                if (screen == AppScreen.Trash) listOf(
                                    MenuEntry(stringResource(R.string.restore_note), R.drawable.ic_refresh) {
                                        selectionMenu = false
                                        moveSelected { note, account -> KeepRepository.untrashNote(context, account, note.id) }
                                    },
                                ) else listOf(
                                    MenuEntry(stringResource(R.string.color), R.drawable.ic_color_none) { selectionMenu = false; colorDialog = true },
                                    MenuEntry(stringResource(R.string.labels_title), R.drawable.ic_checklist) { selectionMenu = false; labelDialog = true },
                                    MenuEntry(stringResource(R.string.duplicate_note), R.drawable.ic_open_new) { selectionMenu = false; duplicateSelected() },
                                ),
                                listOf(MenuEntry(stringResource(R.string.select_all), R.drawable.ic_done) {
                                    selectionMenu = false
                                    selected = shown.map { keyOf(it) }.toSet()
                                }),
                            ),
                        )
                    }
                }
                if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(2.dp))
                }

}
