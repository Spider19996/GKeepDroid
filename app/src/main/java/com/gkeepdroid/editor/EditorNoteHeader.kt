package com.gkeepdroid

import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import java.util.UUID
import kotlinx.coroutines.launch

/** What stands above the fields of the note: the warning about an unsent change, the question before deleting, the account of a new note, labels, hints about changes in Keep, the pictures and the offer to turn the note into the other kind. */
@Composable
internal fun ColumnScope.EditorNoteHeader(
    model: EditorModel, existing: KeepNote?, account: String, allAccounts: List<String>, unsent: PendingEdit?, remote: KeepNote?, offline: Boolean, images: List<String>,
    scope: kotlinx.coroutines.CoroutineScope, onReload: () -> Unit, leaveNote: (suspend () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val items = model.items
    var text by model::text
    var color by model::color
    var labels by model::labels
    var targetAccount by model::targetAccount
    var confirmDelete by model::confirmDelete
    var viewerImage by model::viewerImage
    var removedImages by model::removedImages
    var convertFrom by model::convertFrom
    var error by model::error
    var labelsOpen by model::labelsOpen
            if (unsent != null) {
                Text(
                    if (unsent.error.isBlank()) stringResource(R.string.note_unsent)
                    else stringResource(R.string.note_unsent_failed, unsent.error),
                    color = if (unsent.error.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }


            if (confirmDelete && existing != null) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    text = { Text(stringResource(R.string.delete_local_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDelete = false
                            leaveNote { KeepRepository.trashNote(context, account, existing.id) }
                        }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.delete_note)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) }
                    },
                )
            }

            if (existing == null && allAccounts.size > 1) {
                Text(stringResource(R.string.save_to_account), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allAccounts.forEach { candidate ->
                        FilterChip(
                            selected = targetAccount == candidate,
                            onClick = { targetAccount = candidate },
                            label = { Text(CredentialStore.displayName(context, candidate)) },
                        )
                    }
                }
            }

            // The labels of the note as chips; they are chosen in the sheet of the bottom bar.
            if (labels.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEach { name -> AssistChip(onClick = { labelsOpen = true }, label = { Text(name) }) }
            }

            if (remote != null) {
                // The note changed in Keep while it was open here.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.note_changed_remote),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onReload, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reload)) }
                }
            }

            if (offline) {
                Text(
                    stringResource(R.string.offline_note),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Tap an image to view it full screen; it can also be opened in the gallery or marked for
            // removal. Nothing is deleted before "Save", and "Cancel" keeps every image.
            images.forEach { imageId ->
                if (imageId in removedImages) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.image_removal_pending),
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { removedImages = removedImages - imageId }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.image_undo))
                        }
                    }
                } else {
                    val bitmap = remember(imageId) { NoteImages.thumbnail(context, account, imageId) }
                    if (bitmap != null) {
                        Column {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.note_image),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clickable { viewerImage = imageId },
                            )
                            // Centered under the image, sharing the width equally.
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                TextButton(onClick = { context.openInGallery(account, imageId) }, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(R.string.image_open_gallery))
                                }
                                if (existing != null) {
                                    TextButton(onClick = { removedImages = removedImages + imageId }, shapes = ButtonDefaults.shapes()) {
                                        Text(stringResource(R.string.image_remove))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            viewerImage?.let { imageId ->
                val full = remember(imageId) { NoteImages.full(context, account, imageId) }
                if (full != null) ImageViewerDialog(full) { viewerImage = null }
            }

            if (convertFrom != null) {
                Text(
                    stringResource(R.string.convert_existing_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
}
