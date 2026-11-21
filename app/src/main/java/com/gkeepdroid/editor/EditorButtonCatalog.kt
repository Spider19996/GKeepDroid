package com.gkeepdroid



const val ZONE_TOP_LEFT = 0
const val ZONE_TOP_RIGHT = 1
const val ZONE_BOTTOM_LEFT = 2
const val ZONE_BOTTOM_RIGHT = 3
const val ZONE_MENU = 4
const val ZONE_HIDDEN = 5
// The note's own place: the note is one of its buttons (it cannot leave), the buttons before it show above the note's fields,
// those after it below them, as buttons with a name. Numbered after the older zones, so saved layouts keep their meaning.
const val ZONE_NOTE = 6

/** Where a note that is shorter than the screen sits between the bars. */
const val EDITOR_ALIGN_CENTER = 0
const val EDITOR_ALIGN_TOP = 1
const val EDITOR_ALIGN_BOTTOM = 2

/** Every action of the editor, in the order of the default layout. The menu is a place for them like the two bars are. */
val EDITOR_ACTIONS = listOf(
    ButtonSpec("close", R.drawable.ic_close, R.string.discard_action, 3, ZONE_TOP_LEFT),
    ButtonSpec("pin", R.drawable.ic_push_pin_outline, R.string.pin_note, 3, ZONE_TOP_RIGHT),
    ButtonSpec("pin_bottom", R.drawable.ic_pin_bottom, R.string.pin_to_bottom, 3, ZONE_TOP_RIGHT),
    ButtonSpec("reminder", R.drawable.ic_alarm, R.string.reminders_title, 3, ZONE_TOP_RIGHT),
    ButtonSpec("save", R.drawable.ic_done, R.string.save, 3, ZONE_TOP_RIGHT),
    ButtonSpec("more", R.drawable.ic_more_vert, R.string.more_actions, 3, ZONE_TOP_RIGHT),
    ButtonSpec("palette", R.drawable.ic_palette, R.string.color_and_background, 3, ZONE_BOTTOM_LEFT),
    ButtonSpec("labels", R.drawable.ic_label, R.string.labels_title, 3, ZONE_BOTTOM_LEFT),
    ButtonSpec("image", R.drawable.ic_image, R.string.image_add_keep, 3, ZONE_BOTTOM_LEFT),
    // The text styles of the selected text (or of what is typed next): one button with a list of them, off until it is placed;
    // set in the note's place it is a bar of its own.
    ButtonSpec("format", R.drawable.ic_text_format, R.string.format_title, 3, ZONE_HIDDEN),
    // Next to undo: a delete that was not meant can be taken back at once.
    ButtonSpec("list_delete_checked", R.drawable.ic_delete_sweep, R.string.list_delete_checked, 3, ZONE_BOTTOM_RIGHT),
    ButtonSpec("undo", R.drawable.ic_undo, R.string.undo, 3, ZONE_BOTTOM_RIGHT),
    ButtonSpec("redo", R.drawable.ic_redo, R.string.redo, 3, ZONE_BOTTOM_RIGHT),
    ButtonSpec("convert", R.drawable.ic_checklist, R.string.editor_convert, 0, ZONE_MENU),
    ButtonSpec("list_number", R.drawable.ic_list_numbered, R.string.list_number, 0, ZONE_MENU),
    ButtonSpec("list_check_all", R.drawable.ic_done_all, R.string.list_check_all, 0, ZONE_MENU),
    ButtonSpec("list_uncheck_all", R.drawable.ic_remove_done, R.string.list_uncheck_all, 0, ZONE_MENU),
    ButtonSpec("list_sort", R.drawable.ic_sort, R.string.list_sort_alpha, 0, ZONE_MENU),
    // Gives an ordinary checklist categories (its first one, empty), or takes them all away again.
    ButtonSpec("list_categories", R.drawable.ic_category, R.string.list_categorize, 0, ZONE_MENU),
    // Puts "> " before the lines of the selection (or the line of the cursor): a block that can be folded.
    ButtonSpec("quote_add", R.drawable.ic_format_quote, R.string.quote_add, 0, ZONE_MENU),
    ButtonSpec("open_in_keep", R.drawable.ic_open_new, R.string.open_in_keep, 1, ZONE_MENU),
    ButtonSpec("share", R.drawable.ic_share, R.string.share_note, 1, ZONE_MENU),
    ButtonSpec("hide_content", R.drawable.ic_visibility_off, R.string.hide_content, 1, ZONE_MENU),
    ButtonSpec("archive", R.drawable.ic_archive, R.string.archive_note, 2, ZONE_MENU),
    ButtonSpec("delete", R.drawable.ic_delete, R.string.move_to_trash, 2, ZONE_MENU),
    // A shortcut to the settings of these buttons; the last entry of the menu, apart from the others.
    ButtonSpec("editor_settings", R.drawable.ic_settings, R.string.editor_buttons_title, 5, ZONE_MENU),
    // The title field: in the note's place above the text, or taken out of it into a bar, where it is a field between the buttons.
    ButtonSpec("title", R.drawable.ic_title, R.string.editor_btn_title_field, 4, ZONE_NOTE),
    ButtonSpec("note", R.drawable.ic_notes, R.string.editor_buttons_note, 4, ZONE_NOTE),
    // Under the items of a checklist, as it always was; it can also be a symbol in a bar or an entry of the menu.
    ButtonSpec("add_item", R.drawable.ic_add, R.string.add_item, 0, ZONE_NOTE),
    // Beside the add button, under the items.
    ButtonSpec("add_divider", R.drawable.ic_divider, R.string.add_divider, 0, ZONE_NOTE),
)

/** The checklist tools; in the menu they share one entry that opens a page of their own. */
private val LIST_TOOLS = setOf("list_number", "list_check_all", "list_uncheck_all", "list_sort", "list_categories", "list_delete_checked", "add_item", "add_divider")

fun isListTool(id: String) = id in LIST_TOOLS

const val NOTE_ID = "note"
const val TITLE_ID = "title"

/** Tells a saved layout file of the editor from the files of other editors. */
const val EDITOR_LAYOUT_MARKER = "editorLayout"

/** What the sheets of the bottom bar (and the list of text styles) hold; set in the note's place, each shows itself there instead of opening. */
val NOTE_INLINE = setOf("palette", "labels", "format")

/**
 * Where an action of the editor may be moved to: the note's text only within its own place, the title there or in a bar, every other
 * action anywhere (the note's place included, as a button with a name).
 */
private fun editorAllowedZones(id: String): Set<Int>? = when (id) {
    NOTE_ID -> setOf(ZONE_NOTE)
    TITLE_ID -> setOf(ZONE_NOTE, ZONE_TOP_LEFT, ZONE_TOP_RIGHT, ZONE_BOTTOM_LEFT, ZONE_BOTTOM_RIGHT)
    else -> null
}

/** The editor's buttons and its rules; the layout of them is a [ButtonLayout] of this catalog. */
val EDITOR_CATALOG = ButtonCatalog(
    EDITOR_ACTIONS, hiddenZone = ZONE_HIDDEN, validZone = { it in ZONE_TOP_LEFT..ZONE_NOTE }, allowedFor = ::editorAllowedZones,
    optionRange = EDITOR_ALIGN_CENTER..EDITOR_ALIGN_BOTTOM,
    defaultAnchors = mapOf("list_delete_checked" to "undo", TITLE_ID to NOTE_ID),
)
