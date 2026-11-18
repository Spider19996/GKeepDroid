# 03 – File splits

**Goal:** files by responsibility; nothing over ~450 lines without reason. Compile-driven moves as before (same package, folder by area).

| File (lines) | Split |
|---|---|
| `editor/EditorScreen.kt` (591) | layout part (`OnNoteTheme` … `EditorBar`, sheets) → `EditorLayout.kt`; category helpers (`swipeItems`, `addInCategory`, `createCategory`, `dissolveCategories`) → methods on `EditorCategories` |
| `notes/NoteCards.kt` (575) | `NoteTile` body → `NoteTile.kt`; `CategoryHeader` + `CategorySwipe` → `NoteCategories.kt`; `SwipeCard` → `ui/Swipe.kt` (after 02) |
| `notes/MainActivity.kt` (535) | selection actions (`editSelected`, `moveSelected`, `duplicateSelected`, `trashSelected`) and `swiped()` → `NotesActions.kt` (class taking the scope and model) |
| `widget/WidgetParts.kt` (446) | `ChecklistBody`, `ItemRow`, `AddItemRow`, `CheckedHeader` → `WidgetChecklist.kt`; also one shared category loop for `ChecklistBody` and `SingleNoteWidget.NoteBody` |
| `editor/EditorActions.kt` (438) | `ButtonSpec` catalog, zones, tools → `EditorButtonCatalog.kt`; `EditorBar`, `FormatBar`, `NoteActionButtons` stay |
| `editor/CategoryFrame.kt` (396) | `CategorySwipeBand` → `CategorySwipeBand.kt`; drop slots, `DropLine`, `NewCategoryDropZone` → `CategoryDropSlots.kt` |
| `settings/SettingsActivity.kt`, `reminders/Reminders.kt` (~415) | leave unless touched |

Steps: one file per commit; after each: build, smoke test of that area. Update `CLAUDE.md` only if the folder list changes.
