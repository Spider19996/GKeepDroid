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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
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
    account: String = "", noteKey: String = "new",
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
                // Blocks of "> " lines: the arrow at the first line of a block folds it; a folded one shows as its first line here.
                QuoteBlocks.version.intValue
                // A new note starts with every block open, not with what an earlier new note left folded.
                remember(noteKey) { if (noteKey == "new") QuoteBlocks.clear(context, account, noteKey); true }
                val quoteFolds = remember(QuoteBlocks.version.intValue) { QuoteBlocks.load(context) }
                val blocks = remember(text) { QuoteBlocks.find(text) }
                val isFolded = { b: QuoteBlocks.Block -> QuoteBlocks.key(account, noteKey, b.key) in quoteFolds }
                val quoteView = remember(text, quoteFolds) { QuoteBlocks.view(text, blocks, isFolded) }
                var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors()
                // The line of the categories' frames in the editor.
                val lineColor = MaterialTheme.colorScheme.outline
                val linkColor = MaterialTheme.colorScheme.primary
                val arrowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                val arrowDown = androidx.compose.ui.res.painterResource(R.drawable.ic_arrow_down)
                val arrowLeft = androidx.compose.ui.res.painterResource(R.drawable.ic_arrow_left)
                // The rounder field of Material 3 Expressive, as an OutlinedTextField is, but with the text layout at hand: the lines of
                // the blocks of "> " lines are drawn behind the text where the layout puts them.
                androidx.compose.foundation.text.BasicTextField(
                    // Enter after "1. ..." (or "- ...") starts the next line with "2. " (or "- "); Enter on an empty one ends the list.
                    value = TextFieldValue(text, textSel, textComp),
                    onValueChange = { typed ->
                        val result = ListContinue.apply(text, typed)
                        format.edited("text", text, result.text)
                        // A line typed into a folded block (Enter at its first line) would vanish at once: the block opens.
                        if (result.text != text) {
                            val at = result.selection.start
                            QuoteBlocks.find(result.text).firstOrNull { at > it.headEnd && at <= it.end && isFolded(it) }
                                ?.let { QuoteBlocks.set(context, account, noteKey, it.key, false) }
                        }
                        text = result.text
                        format.selection["text"] = result.selection
                        textSel = result.selection
                        textComp = if (result === typed) typed.composition else null
                    },
                    readOnly = !canEdit,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    interactionSource = interaction,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    onTextLayout = { layout = it },
                    visualTransformation = when {
                        blocks.isNotEmpty() -> remember(quoteView, format.version, linkColor) { QuoteTransformation(quoteView, if (canFormat) format.flagsFor("text", text) else null, linkColor) }
                        canFormat -> remember(format.version, text) { FlagsTransformation(format.flagsFor("text", text)) }
                        else -> VisualTransformation.None
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).onFocusChanged { if (it.isFocused) format.focus = "text" }
                        .then(if (canFormat && canEdit) Modifier.textStyleMenu(styleLabels, applyStyle) else Modifier),
                    decorationBox = { inner ->
                        androidx.compose.material3.OutlinedTextFieldDefaults.DecorationBox(
                            value = text, innerTextField = {
                                Box(
                                    Modifier.pointerInput(quoteView, layout) {
                                        // A tap on the first line of a block folds or unfolds it: on a folded one anywhere on the line, on an
                                        // open one on the arrow at the right, so the text itself can still be tapped to edit it.
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            val l = layout ?: return@awaitEachGesture
                                            val offset = l.getOffsetForPosition(down.position)
                                            val head = quoteView.folded.heads.firstOrNull { offset >= it.start && offset <= it.end } ?: return@awaitEachGesture
                                            val line = l.getLineForOffset(head.start)
                                            if (down.position.y > l.getLineBottom(line) || down.position.y < l.getLineTop(line)) return@awaitEachGesture
                                            if (!head.folded && down.position.x < size.width - 56.dp.toPx()) return@awaitEachGesture
                                            down.consume()
                                            val up = waitForUpOrCancellation(PointerEventPass.Initial)
                                            if (up != null) {
                                                up.consume()
                                                QuoteBlocks.set(context, account, noteKey, head.block.key, !head.folded)
                                            }
                                        }
                                    }.drawBehind {
                                        val l = layout ?: return@drawBehind
                                        quoteView.spans.forEach { span ->
                                            val last = span.end.coerceAtMost(l.layoutInput.text.length)
                                            val top = l.getLineTop(l.getLineForOffset(span.start.coerceAtMost(last)))
                                            val bottom = l.getLineBottom(l.getLineForOffset(last))
                                            quoteLine(top, bottom, 12.dp.toPx(), lineColor)
                                        }
                                        // The arrow at the right of each first line shows what a tap does.
                                        quoteView.folded.heads.forEach { head ->
                                            val line = l.getLineForOffset(head.start.coerceAtMost(l.layoutInput.text.length))
                                            val icon = 24.dp.toPx()
                                            val y = (l.getLineTop(line) + l.getLineBottom(line)) / 2 - icon / 2
                                            translate(size.width - icon - 4.dp.toPx(), y) {
                                                with(if (head.folded) arrowLeft else arrowDown) {
                                                    draw(androidx.compose.ui.geometry.Size(icon, icon), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(arrowColor))
                                                }
                                            }
                                        }
                                    },
                                ) { inner() }
                            },
                            enabled = true, singleLine = false, visualTransformation = VisualTransformation.None, interactionSource = interaction,
                            label = { Text(stringResource(R.string.note_hint)) }, colors = colors,
                            container = {
                                androidx.compose.material3.OutlinedTextFieldDefaults.Container(
                                    enabled = true, isError = false, interactionSource = interaction, colors = colors, shape = MaterialTheme.shapes.medium,
                                )
                            },
                        )
                    },
                )
}
