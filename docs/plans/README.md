# Plans

Plans for upcoming work; each file stands on its own. Plans 01–04 are done and removed (see git history before v1.8.1); 05, 06 and 07 are still open.

| # | Plan | Risk | Depends on |
|---|---|---|---|
| [05](05-release-size.md) | Release build size (R8) | high | – (independent) |
| [06](06-compact-mode.md) | Compact mode | medium (touches many layouts) | – (01, 02 are done) |
| [07](07-further-splits.md) | Further splits by responsibility | low (moves only) | – (do before 06: fewer places to touch) |

Working rules for every plan:
- One plan per session/commit series; commit locally after each step, push only when the user says so (version bump at push time).
- Each step ends with `./gradlew assembleDebug`, `adb install -r app/build/outputs/apk/debug/*.apk`, and the smoke test below.
- Smoke test: overview with `GKD-Test-Kategorien`; open it, swipe a category right and left (screenshot mid-swipe with
  `adb shell input motionevent DOWN/MOVE`, zoom the title corners), drag a category, fold/unfold, leave with Discard; both widgets
  on the DEBUG home screen. Never edit the user's other notes (check the opened title before swiping).
- Tick steps off in the plan file itself (`- [x]`).
