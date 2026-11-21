# 07 – Further splits by responsibility

Goal: files and big functions that still mix several jobs get one job each. No behavior change, no renames of public behavior;
the package stays `com.gkeepdroid`. Nothing is split just to get a smaller number: a file under ~300 lines with one job stays.

Measured on v1.8.1 (20.7k lines of Kotlin, plus `keep_bridge.py` with 803 lines). The biggest files and blocks:

| Place | Lines | Mixed jobs |
|---|---|---|
| `editor/EditorScreen.kt` | 555 | one composable block of ~200 lines (screen frame, dialogs, back handling) plus `askEdit` (141) |
| `notes/MainActivity.kt` | 444 | `NotesApp()` of ~300 lines: navigation, selection, search, dialogs |
| `notes/NoteCards.kt` | 433 | card drawing, swipe handling, `swipeCategory` block of ~335 lines |
| `editor/EditorChecklist.kt` | 433 | list state, category handling, drag logic (~290-line block) |
| `settings/SettingsActivity.kt` | 423 | `SettingsScreen()` of ~320 lines: page routing, search, widget preview |
| `editor/EditorButtons.kt` | 386 | button bar layout and rich-text actions (~250-line block) |
| `widget/NotesWidget.kt` / `SingleNoteWidget.kt` | 364 / 261 | each builds its own card look, header and note body; the card parts are near-copies |
| `app/src/main/python/keep_bridge.py` | 803 | login, sync, note writes and media in one module |

## Steps (each one is one commit; order = lowest risk first)

- [ ] **1. Widget card parts shared.** Move what `NotesWidget` (`NoteCard`, 164 lines) and `SingleNoteWidget` (`NoteBody`) both do
  (card look, title row, labels/reminder lines) into `widget/WidgetCard.kt`; both widgets call it. This is also where a bug like
  the nested-click one would otherwise have to be fixed twice.
- [ ] **2. `NotesApp()` cut up.** `notes/MainActivity.kt`: selection state, search state and dialog hosting go to their own
  small state holders/composables in `notes/` (`NotesSelection.kt`, `NotesDialogs.kt`); `MainActivity` keeps navigation only.
- [ ] **3. `SettingsScreen()` cut up.** Page routing and the search overlay get their own files in `settings/`; `SettingsActivity`
  keeps the activity and the result handling.
- [ ] **4. `NoteCards.kt` split.** Card drawing stays; the swipe actions (`swipeCategory` and friends) move to `notes/NoteSwipe.kt`.
- [ ] **5. `EditorScreen.kt` / `EditorChecklist.kt`.** Dialog hosting out of `EditorScreen` into `EditorDialogHost.kt`; the drag
  logic of `EditorChecklist` into `EditorChecklistDrag.kt`. Plan 02's shared swipe code stays where it is.
- [ ] **6. `EditorButtons.kt`.** Button bar layout stays; the rich-text actions it triggers go to `EditorRichActions.kt`.
- [ ] **7. `keep_bridge.py`.** Only if it keeps growing: split into `keep_auth.py`, `keep_sync.py`, `keep_media.py` behind the
  same entry points (Chaquopy imports by module name, so `keep_bridge.py` stays as the facade). Left for last, because the
  bridge cannot be tested without the user's account; skip when nothing else touches it.

## Not worth splitting

`SettingsStore`, `SettingsState`, `Reminders`, `MarkdownSync`: long, but each has one job. Leave them.

## Rules

Follow the working rules in [README.md](README.md). After every step: `./gradlew assembleDebug`, install, the smoke test, and a
diff check that only moved code changed (`git diff -M --stat` shows renames; no logic edits in the same commit).
