package com.gkeepdroid

import android.appwidget.AppWidgetManager
import androidx.core.content.edit
import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.app.KeyguardManager
import android.os.Bundle
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.draggable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.key
import java.util.UUID
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

/** The dialogs of the editor about categories (rename, choose or name one) and the question before a note is thrown away. */
@Composable
internal fun EditorDialogs(
    model: EditorModel, categories: EditorCategories, account: String, noteKey: String,
    createCategory: (String) -> Unit, discard: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionCategories = categories.session
    fun categoryGroups() = categories.groups()
    fun setCategory(id: String, name: String?) = categories.set(id, name)
    fun keepCategory(name: String?) = categories.keep(name)
    var title by model::title
    var newCategoryDialog by model::newCategoryDialog
    var newCategoryFor by model::newCategoryFor
    var newCategoryEmpty by model::newCategoryEmpty
    var moveItems by model::moveItems
    var armedForget by model::armedForget
    var historyVersion by model::historyVersion
    var renameCategory by model::renameCategory
    var confirmDiscard by model::confirmDiscard
        renameCategory?.let { old ->
            RenameCategoryDialog(
                old,
                onDone = { clean ->
                    if (clean.isNotEmpty() && clean != old) {
                        // Every item of the category gets the new name in front; the category keeps its place.
                        val at = sessionCategories.indexOf(old)
                        categoryGroups()?.firstOrNull { it.name == old }?.items?.forEach { setCategory(it.id, clean) }
                        sessionCategories.remove(clean)
                        if (at >= 0 && at <= sessionCategories.size) { sessionCategories.remove(old); sessionCategories.add(at.coerceAtMost(sessionCategories.size), clean) }
                    }
                    renameCategory = null
                },
                onDismiss = { renameCategory = null },
            )
        }
        if (newCategoryDialog) {
            val moving = moveItems
            val current = categoryGroups()?.mapNotNull { it.name }.orEmpty()
            // The categories this list had before (also ones that are gone since it was last saved), less the ones it has now.
            val earlier = historyVersion.let { CategoryHistory.load(context, account, noteKey) }.filter { it !in current }
            val close = { newCategoryDialog = false; newCategoryFor = null; newCategoryEmpty = false; moveItems = null; armedForget = null }
            CategoryChoiceDialog(
                moving = moving != null, current = current, earlier = earlier, armed = armedForget,
                onLongPress = { old ->
                    // Held once an earlier category turns red, held again it is forgotten.
                    if (armedForget == old) {
                        CategoryHistory.remove(context, account, noteKey, old)
                        armedForget = null
                        historyVersion++
                    } else armedForget = old
                },
                onChoose = { target ->
                    if (moving != null) {
                        // Moving items by a swipe: every category, the ones they are in too, and "no category".
                        close()
                        moving.forEach { setCategory(it, target) }
                        if (target != null) keepCategory(target)
                    } else if (target != null) { newCategoryDialog = false; createCategory(target) }
                },
                onCreate = { typed ->
                    if (moving != null) { val n = typed.trim(); close(); moving.forEach { setCategory(it, n) }; keepCategory(n) }
                    else { newCategoryDialog = false; createCategory(typed) }
                },
                onDismiss = close,
            )
        }
        if (confirmDiscard) AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; discard() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.discard_action)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.keep_editing)) } },
        )
}
