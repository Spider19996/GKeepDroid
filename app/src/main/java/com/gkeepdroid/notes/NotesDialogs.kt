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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The dialogs of the notes screen: deleting for good, emptying the trash, choosing a color or labels for the selected notes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotesDialogs(
    activity: MainActivity, model: NotesModel, app: AppSettings, selectedEntries: List<Pair<KeepNote, String>>, scope: kotlinx.coroutines.CoroutineScope, sync: () -> Unit,
    editSelected: ((KeepNote, String) -> KeepNote) -> Unit, moveSelected: (suspend (KeepNote, String) -> Unit) -> Unit, act: (() -> Unit, suspend () -> Unit) -> Unit, accounts: List<String>,
) {
    val context = LocalContext.current
    var screen by model::screen
    var filterAccount by model::filterAccount
    var colorDialog by model::colorDialog
    var labelDialog by model::labelDialog
    var emptyTrashDialog by model::emptyTrashDialog
    var deleteSelected by model::deleteSelected
    var deleteTarget by model::deleteTarget
        deleteTarget?.let { (note, account) ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                text = { Text(stringResource(R.string.delete_local_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget = null
                        act({}) { KeepRepository.deleteForever(context, account, note.id) }
                    }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.delete_forever)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }

        deleteSelected?.let { targets ->
            AlertDialog(
                onDismissRequest = { deleteSelected = null },
                text = { Text(stringResource(R.string.delete_selected_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteSelected = null
                        act({}) { targets.forEach { (note, account) -> KeepRepository.deleteForever(context, account, note.id) } }
                    }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.delete_forever)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteSelected = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        if (emptyTrashDialog) {
            AlertDialog(
                onDismissRequest = { emptyTrashDialog = false },
                text = { Text(stringResource(R.string.empty_trash_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        emptyTrashDialog = false
                        // Everything in the trash of the accounts on screen, whatever the search says.
                        val trashed = accounts.filter { filterAccount == null || it == filterAccount }.flatMap { account ->
                            NotesCache.readOther(context, account).filter { it.state == "trashed" }.map { it to account }
                        }
                        act({}) { trashed.forEach { (note, account) -> KeepRepository.deleteForever(context, account, note.id) } }
                    }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.empty_trash)) }
                },
                dismissButton = {
                    TextButton(onClick = { emptyTrashDialog = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        if (colorDialog) {
            ColorChoiceDialog(
                onPick = { name -> colorDialog = false; editSelected { note, _ -> note.copy(color = name) } },
                onDismiss = { colorDialog = false },
            )
        }
        if (labelDialog) {
            val accountsOfSelection = selectedEntries.map { it.second }.distinct()
            val labelOptions = accountsOfSelection.flatMap { NotesCache.labels(context, it) }.distinct().sortedBy { it.lowercase() }
            val allHave = labelOptions.filter { label -> selectedEntries.all { label in it.first.labels } }.toSet()
            LabelChoiceDialog(
                labels = labelOptions,
                allHave = allHave,
                onApply = { added, removed ->
                    labelDialog = false
                    editSelected { note, account ->
                        // A label is only added where the account knows it.
                        val known = NotesCache.labels(context, account)
                        note.copy(labels = ((note.labels + added.filter { it in known }) - removed).distinct())
                    }
                },
                onDismiss = { labelDialog = false },
            )
        }

}
