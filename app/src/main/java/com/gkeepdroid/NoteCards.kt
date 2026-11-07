package com.gkeepdroid

import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Wraps a card so it can be swiped sideways, but only as far as the symbol behind it is fully shown. The action
 * runs when the finger lets go past the trigger point, and the card slides back.
 */
@Composable
internal fun SwipeCard(app: AppSettings, enabled: Boolean, onAction: (Int) -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    if (!enabled || (app.swipeLeft == SWIPE_NONE && app.swipeRight == SWIPE_NONE)) {
        Box(modifier) { content() }
        return
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val maxPx = with(density) { 72.dp.toPx() }
    val trigger = maxPx * 0.75f
    var offsetX by remember { mutableStateOf(0f) }
    val current by androidx.compose.runtime.rememberUpdatedState(app)
    val action by androidx.compose.runtime.rememberUpdatedState(onAction)
    val toRight = offsetX > 0f
    val kind = if (toRight) app.swipeRight else app.swipeLeft
    Box(modifier) {
        if (offsetX != 0f && kind != SWIPE_NONE) {
            Box(
                Modifier.matchParentSize().clip(MaterialTheme.shapes.medium)
                    .background(
                        if (kind == SWIPE_TRASH) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                val (icon, name) = when (kind) {
                    SWIPE_ARCHIVE -> R.drawable.ic_archive to R.string.archive_note
                    SWIPE_TRASH -> R.drawable.ic_delete to R.string.move_to_trash
                    SWIPE_PIN -> R.drawable.ic_push_pin to R.string.pin_note
                    SWIPE_REMINDER -> R.drawable.ic_alarm to R.string.reminders_title
                    SWIPE_SHARE -> R.drawable.ic_share to R.string.share_note
                    SWIPE_HIDE -> R.drawable.ic_lock to R.string.hide_content
                    else -> return@Box
                }
                Icon(painterResource(icon), contentDescription = stringResource(name))
            }
        }
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
                .draggable(
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                        val lowest = if (current.swipeLeft != SWIPE_NONE) -maxPx else 0f
                        val highest = if (current.swipeRight != SWIPE_NONE) maxPx else 0f
                        offsetX = (offsetX + delta).coerceIn(lowest, highest)
                    },
                    onDragStopped = {
                        val passed = kotlin.math.abs(offsetX) >= trigger
                        val chosen = if (offsetX > 0f) current.swipeRight else current.swipeLeft
                        androidx.compose.animation.core.animate(offsetX, 0f) { value, _ -> offsetX = value }
                        if (passed) action(chosen)
                    },
                )
        ) { content() }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** One note as a card, in its Keep color. */
@Composable
internal fun NoteTile(
    activity: androidx.activity.ComponentActivity,
    resumeKey: Int,
    unlock: ((Int, (Boolean) -> Unit) -> Unit)?,
    note: KeepNote,
    account: String,
    app: AppSettings,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val foldedKeys = remember(FoldStore.version.intValue) { FoldStore.load(context) }
    val folded = app.foldNotes && FoldStore.key(account, note.id) in foldedKeys
    /** Ticks the item right on the card (or removes it): shown at once, sent to Keep a few seconds later. */
    fun toggleItem(item: KeepItem) {
        fun apply() {
            val checked = !item.checked
            val delete = app.listDeleteChecked && checked
            PendingToggles.add(context, PendingToggle(account, note.id, item.id, checked, delete))
            if (delete) NotesCache.removeItem(context, account, note.id, item.id)
            else NotesCache.setChecked(context, account, note.id, item.id, checked)
            NotesCache.version.intValue++
            scope.launch { WidgetRefresh.all(context) }
            ToggleItemWorker.schedule(context, account)
        }
        if (AppLock.editAllowed(activity)) apply() else unlock?.invoke(R.string.lock_prompt_edit) { if (it) apply() }
    }
    // Formatting and the background picture that Keep's own editor gave the note.
    val rich = remember(resumeKey, NotesCache.version.intValue, note.id) { RichNotes.of(context, account, note.id) }
    val backgroundName = rich?.background?.takeIf { app.showNoteColors && it.isNotEmpty() && it != "default" }
    // A photo of the user's own wins over Keep's pictures (choosing it takes Keep's picture away).
    val customStamp = remember(resumeKey, NotesCache.version.intValue, note.id) { CustomBackgrounds.stamp(context, account, note.id) }
    // In a dark theme Keep's pictures come in their dark version, with light text on them (a note can choose its version).
    val look = remember(resumeKey, NoteLook.version.intValue, note.id) { NoteLook.of(context, account, note.id) }
    val darkVariant = look.dark ?: LocalDarkTheme.current
    // What is on the device already is there in the first frame, so the picture does not pop in.
    val firstPicture = when {
        !app.showNoteColors -> null
        customStamp != 0L -> CustomBackgrounds.peek(context, account, note.id)
        backgroundName != null -> NoteBackgrounds.peek(context, backgroundName, darkVariant)
        else -> null
    }
    val backgroundImage by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(firstPicture, backgroundName, customStamp, app.showNoteColors, darkVariant) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            when {
                !app.showNoteColors -> null
                customStamp != 0L -> CustomBackgrounds.load(context, account, note.id)
                backgroundName != null -> NoteBackgrounds.load(context, backgroundName, darkVariant)
                else -> null
            }
        }
    }
    val onDarkPicture = backgroundImage != null && customStamp == 0L && darkVariant
    // The note's own choice of the light or dark version also decides its color.
    val darkCard = darkVariant
    val noteColor = if (app.showNoteColors) NoteColors.argb(note.color, darkCard) else null
    val gray = noteColor == null && app.plainCardStyle == PLAIN_GRAY
    val textColor = look.textColor?.let { Color(it) } ?: when {
        onDarkPicture || (noteColor != null && darkCard && backgroundImage == null) -> LightNoteText
        noteColor != null || backgroundImage != null -> DarkNoteText
        gray -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val background = when {
        noteColor != null -> Color(noteColor)
        gray -> Color(0xFF424242)
        app.plainCardStyle == PLAIN_BORDER -> MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }
    val shape = MaterialTheme.shapes.medium
    val markColor = Color(0x80FFC107)
    // Web addresses, e-mail addresses and phone numbers open in the app made for them.
    val linkColor = when {
        onDarkPicture || (noteColor != null && darkCard && backgroundImage == null) -> Color(0xFF8AB4F8)
        noteColor != null || backgroundImage != null -> Color(0xFF0B57D0)
        else -> MaterialTheme.colorScheme.primary
    }
    // A private note shows only its title until it is opened with the device lock.
    val hidden = PrivateNotes.isPrivate(note, account, remember(PrivateNotes.version.intValue) { PrivateNotes.load(context) })
    val thumb = remember(note.id, note.images.firstOrNull(), app.showImages, hidden) {
        if (app.showImages && !hidden) note.images.firstOrNull()?.let { NoteImages.thumbnail(context, account, it) } else null
    }
    val lines = if (app.previewLines <= 0) Int.MAX_VALUE else app.previewLines
    val progress = progressText(note)?.takeIf { app.showProgress && !hidden }
    // No title: no room is kept for one. The pin and the progress then sit beside the fold arrow in the corner.
    val hasTitleRow = note.title.isNotBlank()
    val cornerExtra = if (hasTitleRow) 0 else (if (note.pinned) 22 else 0) + (if (progress != null) 36 else 0)
    // The fold arrow floats over the card's corner: its touch area can be large without making the title row taller.
    Box(modifier.fillMaxWidth()) {
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(backgroundImage?.let { Modifier.noteBackground(it) } ?: Modifier)
            // With a picture the note's color shows as a frame around it.
            .then(if (backgroundImage != null && noteColor != null) Modifier.border(3.dp, Color(noteColor), shape) else Modifier)
            .then(
                if (noteColor == null && app.plainCardStyle == PLAIN_BORDER) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            // Tall enough for the fold arrow, also when a folded note has nothing else to show.
            .then(if (app.foldNotes) Modifier.heightIn(min = 44.dp) else Modifier)
            // Without a title row the arrow floats over the first line, so that line stops short of it.
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = if (!hasTitleRow && (app.foldNotes || cornerExtra > 0)) ((if (app.foldNotes) 36 else 0) + cornerExtra).dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = stringResource(R.string.note_image),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
            )
        }
        if (hasTitleRow) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    highlightMatches(androidx.compose.ui.text.AnnotatedString(note.title), query, markColor),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall.copy(hyphens = Hyphens.Auto),
                    modifier = Modifier.weight(1f),
                )
                if (progress != null) {
                    Text(progress, color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                }
                if (note.pinned) {
                    Icon(
                        painterResource(R.drawable.ic_push_pin),
                        contentDescription = stringResource(R.string.pinned_section),
                        tint = textColor,
                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                    )
                }
                // Room for the arrow that floats over the corner; its height is only the title's.
                if (app.foldNotes) Spacer(Modifier.size(width = 30.dp, height = 20.dp))
            }
        }
        // Read again whenever the screen comes back, as reminders are changed in the editor.
        remember(resumeKey, note.id) { Reminders.list(context, account, note.id) }.let { reminders ->
            if (!folded && app.showReminders && reminders.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(R.drawable.ic_alarm), contentDescription = stringResource(R.string.reminder), tint = textColor, modifier = Modifier.size(14.dp))
                Text(
                    Reminders.label(context, reminders.first().at) + if (reminders.size > 1) "  (+${reminders.size - 1})" else "",
                    color = textColor.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (folded) {
            // Folded: the title is all there is; a note without one shows its first line instead.
            if (note.title.isBlank() && !hidden) {
                val first = (if (note.isList) note.items.firstOrNull { it.text.isNotBlank() }?.text else note.text.lineSequence().firstOrNull { it.isNotBlank() }).orEmpty()
                if (first.isNotBlank()) Text(
                    first, color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (hidden) {
            Text(
                stringResource(R.string.note_private_hint),
                color = textColor.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
        } else {
        if (app.showLabels && note.labels.isNotEmpty()) {
            Text(
                note.labels.joinToString("  ·  "),
                color = textColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        if (note.isList) {
            val open = note.items.filter { !it.checked }
            val done = note.items.filter { it.checked }
            // Checked items go last, and can be left out altogether.
            val ordered = if (app.listCheckedToEnd) open + done else note.items
            val visible = if (app.listCheckedToEnd && app.listCollapseChecked) open else ordered
            // With the setting on, a numbered list counts again in the order shown here.
            val shownItems = ListNumbering.display(note.items, visible.take(lines), app.renumberDisplay)
            // The spacing between the rows belongs to the rows themselves, so the check box taps over the whole height of its
            // row, and a little further to the right than it is drawn. 48 dp would make every row taller than the spacing
            // setting says.
            val rowGap = (app.listSpacingDp / 2f).dp
            shownItems.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clickable(enabled = app.listCheckInApp) { toggleItem(item) }
                            .padding(start = 2.dp, top = rowGap + 2.dp, end = 8.dp, bottom = rowGap + 2.dp),
                    ) {
                        CheckboxPreview(app.listCheckboxStyle, item.checked, app.listCheckboxSizeDp, textColor.copy(alpha = if (item.checked) 0.55f else 1f))
                    }
                    Text(
                        highlightMatches(RichNotes.style(NoteLinks.annotate(item.text, linkColor), rich?.docs?.get(item.id)), query, markColor),
                        color = if (item.checked) textColor.copy(alpha = 0.55f) else textColor,
                        style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = rowGap),
                    )
                }
            }
            if (visible.size > lines) {
                Text(
                    pluralStringResource(R.plurals.items_more, visible.size - lines, visible.size - lines),
                    color = textColor.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else if (note.text.isNotBlank()) {
            Text(
                highlightMatches(RichNotes.style(NoteLinks.annotate(note.text, linkColor), rich?.docs?.values?.firstOrNull()), query, markColor),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto),
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (note.title.isBlank() && thumb == null) {
            Text(stringResource(R.string.note_empty), color = textColor.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium)
        }
        }
        if (!folded && app.showEditedTime && note.updated > 0) {
            Text(
                stringResource(
                    R.string.edited_time,
                    if (app.editedAsDate) {
                        DateUtils.formatDateTime(activity, note.updated, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME)
                    } else {
                        // The system text starts a sentence ("Vor 47 Minuten"), but here it continues one.
                        DateUtils.getRelativeTimeSpanString(minOf(note.updated, System.currentTimeMillis()), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                            .toString().replaceFirstChar { it.lowercase() }
                    },
                ),
                color = textColor.copy(alpha = 0.6f),
                // Android's narrow font, so "Edited 28 minutes ago" fits on one line more often.
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)),
                ),
            )
        }
        actions?.let { Column { it() } }
    }
    if (app.foldNotes || cornerExtra > 0) {
        Row(Modifier.align(Alignment.TopEnd).padding(end = if (app.foldNotes) 0.dp else 12.dp, top = if (app.foldNotes) 0.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!hasTitleRow) {
                if (progress != null) Text(progress, color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                if (note.pinned) {
                    Icon(
                        painterResource(R.drawable.ic_push_pin), contentDescription = stringResource(R.string.pinned_section),
                        tint = textColor, modifier = Modifier.padding(start = 6.dp).size(16.dp),
                    )
                }
            }
            if (app.foldNotes) Box(
                Modifier.size(48.dp).clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) {
                    FoldStore.set(context, account, note.id, !folded)
                    scope.launch { WidgetRefresh.all(context) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (folded) R.drawable.ic_arrow_left else R.drawable.ic_arrow_down),
                    contentDescription = stringResource(if (folded) R.string.unfold_all else R.string.fold_all),
                    tint = textColor, modifier = Modifier.size(24.dp),
                )
            }
        }
    }
    }
}
