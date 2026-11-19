@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

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
import androidx.compose.material3.ExperimentalMaterial3Api
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

/** What the background choice holds while the note's background is a photo of the user's own. */
internal const val CUSTOM_BACKGROUND = "custom"

/** Edits an existing note (EXTRA_NOTE_ID) or creates a new note or checklist. */
class EditNoteActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    /** Set by the open editor: saves what was typed when the user leaves with the Home button. */
    internal var onHome: (() -> Unit)? = null

    // The color or picture behind this editor; it belongs to this activity, so the screen it returns to is not tinted by it.
    internal val screenTint = ScreenTint().apply { instant = true }

    /** A photo chosen as the note's own background (kept on this phone only), until the note is saved. */
    internal var pickedBackground by mutableStateOf<android.net.Uri?>(null)
    internal val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) pickedBackground = uri }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onHome?.invoke()
    }

    internal var layoutKey by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        layoutKey++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opened from a widget or a shortcut the editor is a task of its own, which stays in the recent apps unless that is
        // switched off. Opened from the app it belongs to the app's task, which is left as it is.
        if (isTaskRoot && !AppSettingsStore.load(this).editorInRecents) {
            getSystemService(android.app.ActivityManager::class.java).appTasks
                .firstOrNull { task ->
                    @Suppress("DEPRECATION")
                    task.taskInfo?.let { if (android.os.Build.VERSION.SDK_INT >= 29) it.taskId else it.persistentId } == taskId
                }?.setExcludeFromRecents(true)
        }
        val account = CredentialStore.resolve(this, intent.getStringExtra(EXTRA_ACCOUNT).orEmpty())
        if (account == null) {
            finish()
            return
        }
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        // Launcher shortcuts and the widget buttons say "list" in their data address.
        val newList = intent.getBooleanExtra(EXTRA_NEW_LIST, false) || intent.data?.pathSegments?.firstOrNull() == "list"
        val themeMode = SettingsStore.loadGlobal(this).themeMode
        // Pinning applies to the list widget the note was opened from.
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val provider = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        } else {
            null
        }
        val pinWidgetId = widgetId.takeIf { provider?.className == NotesWidgetReceiver::class.java.name }
        // A private note opens only after the device lock (PIN, pattern, fingerprint) was confirmed.
        val keyguard = getSystemService(KeyguardManager::class.java)
        val cachedNote = noteId?.let { id -> NotesCache.readAll(this, account).firstOrNull { it.id == id } }
        val needsLock = cachedNote != null && AppSettingsStore.load(this).lockPrivateNotes &&
            PrivateNotes.isPrivate(cachedNote, account, PrivateNotes.load(this)) && keyguard.isDeviceSecure
        var unlocked by mutableStateOf(!needsLock)
        val unlock = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) unlocked = true else finish()
        }
        if (needsLock) {
            @Suppress("DEPRECATION")
            unlock.launch(keyguard.createConfirmDeviceCredentialIntent(getString(R.string.app_name), getString(R.string.note_private_hint)))
        }
        setContent { KeepTheme(themeMode, tint = screenTint) { AppLock.Gate(this) { if (unlocked) EditorLoader(this@EditNoteActivity, account, noteId, newList, pinWidgetId) } } }
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_ACCOUNT = "account"
        const val EXTRA_PREFILL_TITLE = "prefill_title"
        const val EXTRA_PREFILL_TEXT = "prefill_text"
        const val EXTRA_NEW_LIST = "new_list"
    }
}
