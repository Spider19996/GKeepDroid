# Plan: Code cleanup and compact mode (planning only, nothing implemented)

## Context
After the category and editor work (v1.8.0), the code has grown to ~19,600 lines in 9 folders. The user asked
(pasted list) whether we can simplify, merge, optimise or split more, and whether a **compact mode** (less wasted space)
makes sense. This file is the plan to pick up tomorrow. **Nothing of it is done yet.**
This file lives in `docs/plans/`; edit it and tick things off here.

Findings from a read-only look (numbers as of today):

- Biggest files: `editor/EditorScreen.kt` 591, `notes/NoteCards.kt` 575, `notes/MainActivity.kt` 535, `widget/WidgetParts.kt` 446,
  `editor/EditorActions.kt` 438, `editor/EditorChecklist.kt` 429, `settings/SettingsActivity.kt` 418, `reminders/Reminders.kt` 415.
- ~136 inline fully-qualified `androidx.compose...` calls (left over from compile-driven extraction) instead of imports.
- Swipe code exists four times: `ChecklistRow.kt`, `CategoryFrame.kt` (header), `notes/NoteCards.kt` (`SwipeCard`, `CategorySwipe`);
  `72.dp.toPx()` / 0.75 trigger repeated in three files.
- Frame geometry (18 dp radius, 8 dp side padding, 12 dp gap, 10 dp header inset) lives in `CategoryFrame.kt`, `CategorySwipeBand`,
  `NoteCards.kt`, and the widget drawables, as separate literals.
- `CategoryFolds.load(context)` and `AppSettingsStore.load(...)` (19 call sites) are read in several composables each.

## 1. Simplify
1. Imports instead of inline FQNs (mechanical, one pass per file, compile-checked). Start with `CategoryFrame.kt`, `NoteCards.kt`,
   `EditorChecklist.kt`, `ChecklistRow.kt`.
2. `CategorySwipeBand` / `EditorCategoryHeader`: pull the shape builder (`shape(dx, grow)`) and the frame constants into one
   `FrameGeometry` object (radius, side, reach, inset) used by `categoryFrame`, the band and the cover/line code.
3. Remove dead parameters and leftovers (`onSwipe` default lambdas, unused `framed`/`carried` paths after the swipe rework) – find with
   a Kotlin unused-warning pass (`./gradlew assembleDebug` warnings, then `lint`).
4. Strings/dp magic numbers in the editor (12.dp gap, 8.dp frame padding) → named constants next to `FRAME_*` in `CategoryFrame.kt`.

## 2. Merge (duplicates)
1. One swipe helper: `SwipeState` (offset, max 72 dp, trigger 0.75, animate-back, direction limits) + `Modifier.swipeable(...)`
   in `ui/` used by `ChecklistRow`, `EditorCategoryHeader`, `SwipeCard`, overview `CategorySwipe`.
2. One `swipeLook(action)` for the editor and the overview (already shared; make the overview card version use it too instead of its
   own icon/color `when`).
3. `swipeCategory` (overview `NoteTile`) and `swipeItems` (editor) share the tick/remove semantics: put the "tick all / untick when all
   ticked / remove" rule in `data/Categories.kt` or a small `CategorySwipes` helper, UI-agnostic.
4. Widget: `SingleNoteWidget` and `ChecklistBody` (`WidgetParts.kt`) both render the category loop; extract one
   `categoryRows(groups, …)` helper.
5. Read settings once per screen (`AppSettingsStore.load` → remembered in `NotesModel` / `EditorModel`) instead of per composable.

## 3. Optimise
1. Composition cost in the overview: `RichNotes.of`, `CategoryFolds.load`, `Categories.groups` run per card on every recomposition
   → `remember` keyed on `NotesCache.version` / `RichNotes.version` (partly done), measure with Layout Inspector recomposition counts.
2. Editor: `categories.groups()` is recomputed several times per frame (`EditorChecklist`, `EditorDialogs`, `EditorController`);
   compute once per change.
3. `pieceBounds` map in `EditorChecklist` writes on every layout; only write while a swipe is active.
4. R8 / resource shrinking: check `app/build.gradle.kts` release block (`isMinifyEnabled`, `isShrinkResources`) and APK size per ABI.
5. Check Python/Chaquopy startup (`app/PythonRuntime.kt`): lazy init off the main thread, measure cold start with `adb shell am start -W`.

## 4. Files to split (candidates, in order)
| File | Lines | Proposal |
|---|---|---|
| `editor/EditorScreen.kt` | 591 | layout section (OnNoteTheme … EditorBar) into `EditorLayout.kt`; state aliases into `EditorModel` accessors |
| `notes/NoteCards.kt` | 575 | `NoteTile` body into `NoteTileContent.kt`, `CategoryHeader` + `CategorySwipe` into `NoteCategories.kt`, `SwipeCard` into `ui/Swipe.kt` |
| `notes/MainActivity.kt` | 535 | intent/lock handling and `swiped()` into `MainActions.kt` |
| `widget/WidgetParts.kt` | 446 | `ChecklistBody` + `ItemRow` into `WidgetChecklist.kt` |
| `editor/EditorActions.kt` | 438 | `ButtonSpec` catalog vs. `EditorBar` UI |
| `editor/CategoryFrame.kt` | 396 | `CategorySwipeBand` into `CategorySwipeBand.kt`, drop slots into `CategoryDropSlots.kt` |
| `settings/SettingsActivity.kt`, `reminders/Reminders.kt` | ~415 | only if touched for other reasons |
Rule: split by responsibility, not by line count; keep package `com.gkeepdroid`, folder by area.

## 5. Compact mode ("less wasted space")
Goal: more content per screen in the editor, overview cards and widgets, switchable, default off.
1. Settings: one switch "Compact" (App settings page) plus per-area override later; stored in `AppSettings` (new
   `compact: Boolean`, persisted like `listSpacingDp` in `AppSettings.kt` load/save), strings in `values/` and all `values-*`.
2. A `Dimens` holder (`ui/Dimens.kt`) with normal and compact values, provided by a `CompositionLocal` in `KeepTheme`
   (`ui/Theme.kt`); replaces literals step by step.
3. Candidate savings (measure each on the phone before keeping it):
   - Editor: row gap 12 dp → 6 dp (`EditorScreen` `Arrangement.spacedBy`, and the matching `reach` in `categoryFrame`/band, so the frame stays
     continuous), checklist row height 56 dp → 44 dp (`ChecklistRow` handle strip, `handleHeightPx`), frame bottom padding 8 dp → 4 dp.
   - Overview cards: card padding, list spacing (`listSpacingDp` default 2 → 0), category frame padding, edited-time line hidden.
   - Widgets: header height, item row padding, category frame gap (`WidgetCategories`, `WidgetHeader`).
4. Must stay touch-friendly (≥ 40 dp targets for handle/checkbox) → keep hit areas, shrink only padding.
5. Screenshots in README for compact mode (English, demo mode) once done.

## Order of work (suggested)
1. Imports + constants (low risk) → build, smoke test.
2. Shared swipe helper and `FrameGeometry` (needs care: re-check swipe screenshots incl. corners, both directions).
3. File splits (mechanical, compile-driven as before).
4. Optimisations that measure well.
5. Compact mode last (touches every layout; do after structure is clean).
Each step: build, install debug, smoke test (open `GKD-Test-Kategorien`, swipe categories both ways, overview, widget), commit locally.
Not pushed without the user's word; version bump only at push time.

## Verification
- `./gradlew assembleDebug`, install on the phone (`adb install -r`), open editor / overview / both widgets.
- Screenshots of category swipe (right and left) zoomed at the title corners compared with v1.8.0.
- Compact mode: before/after screenshots, count visible rows per screen.
- Open questions for the user tomorrow: compact mode as one global switch or per editor/overview/widgets? Which of the splits matter
  most? Is a lint/unused-code pass wanted before refactoring?
