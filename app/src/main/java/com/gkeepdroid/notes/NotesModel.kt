package com.gkeepdroid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the notes screen of the app holds while it is open: which screen, the search and the filters, the selection and the state of
 * its menus and dialogs. The parts of the screen that live in other files get this object and read and change what they need.
 */
internal class NotesModel {
    var screen by mutableStateOf<AppScreen>(AppScreen.Notes)
    var query by mutableStateOf<String>("")
    var filterAccount by mutableStateOf<String?>(null)
    var filterLabel by mutableStateOf<String?>(null)
    var filterKind by mutableStateOf<Int>(0)
    var filterReminder by mutableStateOf<Boolean>(false)
    var filterRecent by mutableStateOf<Boolean>(false)
    var filterImage by mutableStateOf<Boolean>(false)
    var filterPinned by mutableStateOf<Boolean>(false)
    var filterDialog by mutableStateOf<Boolean>(false)
    var undoKey by mutableStateOf<Int>(0)
    var syncing by mutableStateOf<Boolean>(false)
    var pulling by mutableStateOf<Boolean>(false)
    var selected by mutableStateOf<Set<String>>(emptySet())
    var selectionMenu by mutableStateOf<Boolean>(false)
    var colorDialog by mutableStateOf<Boolean>(false)
    var labelDialog by mutableStateOf<Boolean>(false)
    var emptyTrashDialog by mutableStateOf<Boolean>(false)
    var deleteSelected by mutableStateOf<List<Pair<KeepNote, String>>?>(null)
    var searchFocused by mutableStateOf<Boolean>(false)
    var history by mutableStateOf<List<String>>(emptyList())
    var menuOpen by mutableStateOf<Boolean>(false)
    var newMenuOpen by mutableStateOf<Boolean>(false)
    var deleteTarget by mutableStateOf<Pair<KeepNote, String>?>(null)
}
