# GKeepDroid

An unofficial Android app for your Google Keep notes: home screen widgets with a fully transparent background, and a full-screen app to read and edit all your notes.

> **Not affiliated with Google.** GKeepDroid is an independent, unofficial project. Google Keep and Google are trademarks of Google LLC. This app is neither made, endorsed nor supported by Google.

| Notes | Editor | Button bar | Synchronization |
|---|---|---|---|
| <img src="docs/screenshots/notes.png" width="200"> | <img src="docs/screenshots/editor.png" width="200"> | <img src="docs/screenshots/button-bar.png" width="200"> | <img src="docs/screenshots/sync.png" width="200"> |

## Features

- **Two widgets**: *Keep Notes* lists all notes of an account (optionally only one label), *Keep Note* shows a single note or checklist, such as a shopping list
- **Widget look**: background opacity from 0 to 100 %, text size and color, Keep's note colors, cards for notes without a color (transparent, gray or with a border)
- **Button bar**: every button (refresh, new note, search, ...) can be hidden or dragged to any side of the widget, in any order, with an adjustable size
- **Checklists**: tick items right in the widget, checked items move to the end, "3/7" progress, drag items to re-order them
- **Full app**: grid or list of all notes, search, label filter, archive and trash, undo, long-press to re-order notes in Keep's order, images, sharing
- **Labels**: create, assign, delete and filter by them
- **Private notes**: hide their content on the cards; they open only after the device lock (PIN, pattern, fingerprint)
- **Sign in** with your Google account, with a master token, or **without an account** (notes stay on the device). Several accounts are possible
- **Sync you control**: an interval while the display is on, sync on unlock and on opening the app, optional background sync, a minimum gap between syncs, none on mobile data. Edits made offline are sent later
- **Markdown folder, synced both ways**: every note is its own `.md` file; edit it in another app (Obsidian, Markor, ...) and the note changes, and the other way round
- **Also**: share text or links from any app into a new note, launcher shortcuts and a Quick Settings tile, light, dark and OLED themes, 15 languages (selectable in the settings), settings backup (without tokens)

## Usage

1. Open **GKeepDroid** and sign in with your Google account.
2. Long-press the home screen, open Widgets and add **Keep Notes** or **Keep Note**. The widget's settings open on their own; they are saved as you change them.
3. To change a widget later, tap its ⚙ button, or long-press the widget and choose reconfigure. The app's own settings are in its menu.

If you prefer a master token, follow the [gkeepapi instructions](https://gkeepapi.readthedocs.io/en/latest/#obtaining-a-master-token) and enter it under Accounts.

## How it works

Google Keep has no public API for personal Google accounts. GKeepDroid uses the unofficial
[gkeepapi](https://github.com/kiwiz/gkeepapi) library, which runs inside the app through
[Chaquopy](https://chaquo.com/chaquopy/). Keep has no push channel for other apps, so changes made elsewhere arrive at the next sync.

> **Warning**
> - gkeepapi is unofficial and may stop working at any time. Using it may violate Google's terms of service. Use it at your own risk.
> - Signing in gives the app a **master token**, which grants broad access to your Google account, not just Keep. Never share it. It is stored encrypted on your phone and is not part of any export.

**Limitations**
- Images can be opened and removed, but not added: use the Keep app for that. Audio, reminders and shared notes are not supported.
- Widgets show the first image of a note as a thumbnail and hide archived and trashed notes.

## Building

You need JDK 17, the Android SDK with platform 37, and Python 3.13 (Chaquopy needs a matching build Python). Create `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
chaquopy.buildPython=/path/to/python3.13
```

Then:

```sh
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build is its own app (`com.gkeepdroid.debug`, named "GKeepDroid Debug"), so it installs next to the signed release without touching its data.

Pushing a tag such as `v0.2.0` builds a signed release APK on GitHub Actions and attaches it to a release. The workflow needs the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

## License

GPL-3.0, see [LICENSE](LICENSE).
