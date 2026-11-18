# 04 – Recomposition

**Goal:** less repeated work per frame; measure first, keep only what helps.

Measure: Layout Inspector recomposition counts (Android Studio, debug build) on the overview while scrolling and in the editor while typing/swiping;
note numbers before/after in this file.

Candidates:
- [ ] Overview `NoteTile`: `CategoryFolds.load`, `Categories.groups(note.items, rich)`, `FoldStore.load` per card → `remember` keyed on
      `note`, `NotesCache.version`, `RichNotes.version`, `CategoryFolds.version`.
- [ ] Settings reads: `AppSettingsStore.load` (19 call sites) → load once in `NotesModel` / `EditorModel` / widget `provideGlance`, pass down.
- [ ] Editor: `categories.groups()` called from `EditorChecklist`, `EditorDialogs`, `EditorController` → one `derivedStateOf` in `EditorModel`.
- [ ] `EditorChecklist`: `pieceBounds` / `rowBounds` / `headerBounds` written on every layout → write only when changed (already partly) and
      `pieceBounds` only while a category swipe is active.
- [ ] Widgets: `RichNotes.of` and `CategoryFolds.load` once per `provideGlance`, not per note row.

Done when: measurably fewer recompositions, no visible change.
