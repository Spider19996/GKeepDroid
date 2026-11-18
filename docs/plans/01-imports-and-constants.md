# 01 – Imports and constants

**Goal:** readable code without behaviour change; groundwork for 02 and 06.

Steps:
- [ ] Replace inline `androidx.compose.*` FQNs with imports, file by file, biggest first: `editor/CategoryFrame.kt`,
      `editor/EditorChecklist.kt`, `editor/EditorScreen.kt`, `editor/ChecklistRow.kt`, `notes/NoteCards.kt`, `notes/MainActivity.kt`,
      then the rest (`grep -c 'androidx\.compose\.[a-z.]*\.[A-Za-z]*'` per file to find them).
      Method: a small script collects the FQNs of a file, adds sorted imports, shortens the uses; name clashes
      (e.g. `androidx.compose.animation.core.animate` vs. others, `Box` from glance vs. compose in widget files) stay qualified.
- [ ] Frame and swipe numbers as named constants in `editor/CategoryFrame.kt` next to `FRAME_*`:
      `FRAME_RADIUS = 18.dp`, `FRAME_SIDE = 8.dp`, `ROW_GAP = 12.dp` (also used by `Arrangement.spacedBy` in `EditorScreen.kt`),
      `HEADER_INSET = 10.dp`, `SWIPE_MAX = 72.dp`, `SWIPE_TRIGGER = 0.75f`.
      Replace the literals in `categoryFrame`, `CategorySwipeBand`, `EditorChecklist.kt`, `ChecklistRow.kt`, `NoteCards.kt`.
- [ ] Compiler warnings pass: build, list `w:` lines (unused parameters/variables, deprecated APIs), fix those that are leftovers.
- [ ] Optional: `./gradlew lintDebug`, fix only obvious issues (missing translations are real bugs).

Done when: build has no new warnings, smoke test unchanged, swipe corner screenshots identical to v1.8.0.
