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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import java.util.UUID
import kotlinx.coroutines.launch

/** The body of a note that is not a checklist: the offer to make it one, and the text field. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.EditorTextBody(
    model: EditorModel, format: FormatState, existing: KeepNote?, canFormat: Boolean, styleLabels: List<String>, applyStyle: (Int) -> Unit,
    convertKey: String?, convertPrefs: android.content.SharedPreferences, requestConvert: () -> Unit,
) {
    val context = LocalContext.current
    val items = model.items
    var canEdit by model::canEdit
    var text by model::text
    var textSel by model::textSel
    var textComp by model::textComp
    var color by model::color
    var background by model::background
    var convertDismissed by model::convertDismissed
                // Lines that start with "-" (or "*", "[ ]") are a list: offer to make it one, once.
                if (!convertDismissed && ChecklistText.looksLikeList(text) && existing?.images.isNullOrEmpty()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
                            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.convert_to_list_hint),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                convertDismissed = true
                                convertKey?.let { convertPrefs.edit { putBoolean(it, true) } }
                            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.convert_to_list_no)) }
                            TextButton(onClick = { requestConvert() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.convert_to_list)) }
                        }
                    }
                }
                OutlinedTextField(
                    shape = MaterialTheme.shapes.medium,
                    // Enter after "1. ..." (or "- ...") starts the next line with "2. " (or "- "); Enter on an empty one ends the list.
                    value = TextFieldValue(text, textSel, textComp),
                    onValueChange = { typed ->
                        val result = ListContinue.apply(text, typed)
                        format.edited("text", text, result.text)
                        text = result.text
                        format.selection["text"] = result.selection
                        textSel = result.selection
                        textComp = if (result === typed) typed.composition else null
                    },
                    readOnly = !canEdit,
                    label = { Text(stringResource(R.string.note_hint)) },
                    visualTransformation = if (canFormat) remember(format.version, text) { FlagsTransformation(format.flagsFor("text", text)) } else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).onFocusChanged { if (it.isFocused) format.focus = "text" }
                        .then(if (canFormat && canEdit) Modifier.textStyleMenu(styleLabels, applyStyle) else Modifier),
                )
}
