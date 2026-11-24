package com.gkeepdroid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange

/**
 * What the editor of a note holds while it is open: the content (title, text, items), the look (color, picture) and the state of its
 * menus, sheets and dialogs. [EditNoteActivity] sets the starting values; the parts of the editor that live in other files get
 * this object and read and change what they need.
 */
internal class EditorModel {
    var canEdit by mutableStateOf<Boolean>(false)
    var isList by mutableStateOf<Boolean>(false)
    var title by mutableStateOf<String>("")
    var text by mutableStateOf<String>("")
    var textSel by mutableStateOf<TextRange>(TextRange(0))
    var textComp by mutableStateOf<TextRange?>(null)
    var color by mutableStateOf<String>(NoteColors.DEFAULT)
    var background by mutableStateOf<String>("")
    var localHint by mutableStateOf<Boolean>(false)
    var variantDark by mutableStateOf<Boolean?>(null)
    var ownText by mutableStateOf<Int?>(null)
    var labels by mutableStateOf<List<String>>(emptyList())
    var numbered by mutableStateOf<Boolean>(false)
    var numberOfferDismissed by mutableStateOf<Boolean>(false)
    var focusItemId by mutableStateOf<String?>(null)
    var addMenuOpen by mutableStateOf<Boolean>(false)
    var newCategoryDialog by mutableStateOf<Boolean>(false)
    /** The item whose counter the "Quantity" action asked to open. */
    var quantityItem by mutableStateOf<String?>(null)
    var newCategoryFor by mutableStateOf<String?>(null)
    var newCategoryEmpty by mutableStateOf<Boolean>(false)
    var moveItems by mutableStateOf<List<String>?>(null)
    var armedForget by mutableStateOf<String?>(null)
    var historyVersion by mutableStateOf<Int>(0)
    var renameCategory by mutableStateOf<String?>(null)
    var editorViewport by mutableStateOf<Rect?>(null)
    var targetAccount by mutableStateOf<String>("")
    var hiddenInWidgets by mutableStateOf<Boolean>(false)
    var saving by mutableStateOf<Boolean>(false)
    var menuOpen by mutableStateOf<Boolean>(false)
    var formatMenuOpen by mutableStateOf<Boolean>(false)
    var menuPage by mutableStateOf<Int>(0)
    var confirmDelete by mutableStateOf<Boolean>(false)
    var viewerImage by mutableStateOf<String?>(null)
    var removedImages by mutableStateOf<Set<String>>(emptySet())
    var pinned by mutableStateOf<Boolean>(false)
    var pinBottom by mutableStateOf<Boolean>(false)
    var reminders by mutableStateOf<List<Reminder>>(emptyList())
    var remindersOpen by mutableStateOf<Boolean>(false)
    var draftReminders by mutableStateOf<List<Reminder>>(emptyList())
    var convertFrom by mutableStateOf<KeepNote?>(null)
    var convertDismissed by mutableStateOf<Boolean>(false)
    var error by mutableStateOf<String>("")
    var confirmDiscard by mutableStateOf<Boolean>(false)
    var paletteOpen by mutableStateOf<Boolean>(false)
    var labelsOpen by mutableStateOf<Boolean>(false)

    /** The items of a checklist. */
    val items = mutableStateListOf<KeepItem>()
}
