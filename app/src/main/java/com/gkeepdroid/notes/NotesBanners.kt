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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The hints and warnings above the notes: notifications are off, a change could not be sent, the sign-in ran out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.NotesBanners(
    activity: MainActivity, model: NotesModel, reloadKey: Int, accounts: List<String>, scope: kotlinx.coroutines.CoroutineScope, sync: () -> Unit,
) {
    val context = LocalContext.current
    var syncing by model::syncing
                val notificationsOn = rememberNotificationsOn(activity)
                val hintPrefs = remember { context.getSharedPreferences("reminder_hints", android.content.Context.MODE_PRIVATE) }
                var notifHintHidden by remember { mutableStateOf(hintPrefs.getBoolean("notif_hint_dismissed", false)) }
                if (!notificationsOn && !notifHintHidden) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 4.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium).padding(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.notif_off_main),
                            color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { context.startActivity(Reminders.notificationSettings(context)) }, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.reminder_open_settings))
                            }
                            TextButton(onClick = {
                                hintPrefs.edit { putBoolean("notif_hint_dismissed", true) }
                                notifHintHidden = true
                            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.notif_hint_dismiss)) }
                        }
                    }
                }
                val failedEdits = remember(reloadKey, syncing) { PendingEdits.all(context).filter { it.error.isNotBlank() } }
                if (!notificationsOn) failedEdits.forEach { edit ->
                    Text(
                        SaveFailures.message(context, edit, edit.error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // A failed sync stays visible until the next one works.
                val syncError = remember(reloadKey, syncing) { SyncPolicy.lastError(context) }
                val expired = remember(reloadKey, syncing) { SyncPolicy.expiredAccount(context) }
                if (expired != null && !syncing) {
                    // The login is refused: one tap leads to the accounts to sign in again.
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.sign_in_expired, CredentialStore.displayName(context, expired)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(context, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, "accounts")
                            )
                        }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.sign_in_again)) }
                    }
                } else if (syncError != null && !syncing) {
                    Text(
                        stringResource(R.string.sync_last_error, syncError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

}
