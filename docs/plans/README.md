# Plans

Plans for upcoming work; each file stands on its own. Nothing here is implemented yet.

| # | Plan | Risk | Depends on |
|---|---|---|---|
| [01](01-imports-and-constants.md) | Imports and constants | low | – |
| [02](02-shared-swipe.md) | Shared swipe and frame geometry | medium (visual) | 01 |
| [03](03-file-splits.md) | File splits | low | 01 (02 for `CategoryFrame.kt`, `NoteCards.kt`) |
| [04](04-recomposition.md) | Recomposition and duplicated work | medium | 03 |
| [05](05-release-size.md) | Release build size (R8) | high | – (independent) |
| [06](06-compact-mode.md) | Compact mode | medium (touches many layouts) | 01, 02 |

Working rules for every plan:
- One plan per session/commit series; commit locally after each step, push only when the user says so (version bump at push time).
- Each step ends with `./gradlew assembleDebug`, `adb install -r app/build/outputs/apk/debug/*.apk`, and the smoke test below.
- Smoke test: overview with `GKD-Test-Kategorien`; open it, swipe a category right and left (screenshot mid-swipe with
  `adb shell input motionevent DOWN/MOVE`, zoom the title corners), drag a category, fold/unfold, leave with Discard; both widgets
  on the DEBUG home screen. Never edit the user's other notes (check the opened title before swiping).
- Tick steps off in the plan file itself (`- [x]`).
