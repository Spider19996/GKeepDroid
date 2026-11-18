# 05 – Release size

**Goal:** smaller, faster release APK. Separate because it can break release only (debug unchanged).

- [ ] Measure current release APK per ABI (`./gradlew assembleRelease`, unsigned locally is fine) and note sizes here.
- [ ] Try `isMinifyEnabled = true` + `isShrinkResources = true` in `release` (`app/build.gradle.kts`) with `proguard-android-optimize.txt`.
- [ ] Keep rules (new `app/proguard-rules.pro`): Chaquopy (`com.chaquo.python.**`), classes called from Python, Glance receivers/actions
      (`ActionCallback` classes are created by name), WorkManager workers, `TileService`, anything referenced from the manifest.
- [ ] Test the minified build on the phone as a separate install (temporary `applicationIdSuffix` or debug-signed minified variant):
      login, sync, open/save note, widgets (tick, fold, add item), reminders, backup.
- [ ] Optional: ABI split APKs in the GitHub release (`.github/workflows/build.yml`) so users download only arm64.

Done when: release APK smaller, every flow above works in the minified build. If anything breaks that can't be fixed with keep rules, keep minify off.
