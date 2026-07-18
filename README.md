# Markdown Notes

A native Android app for opening folders of Markdown notes and viewing/editing
them. Built with Kotlin and Jetpack Compose (Material 3).

## Requirements

- Android Studio (Koala/2024.1 or newer)
- Android SDK 34, minimum device API 26 (Android 8.0)
- JDK 17

## Build & run

Open the project in Android Studio and press Run, or from the command line:

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## How folders work

Workspaces are real folders on the device, chosen through Android's system
folder picker (Storage Access Framework). The app keeps persistable read/write
permission for each folder it is granted, so nothing is copied — edits write
straight back to the original `.md` / `.markdown` files.

## The four tabs

- **Browse** — start here. A home list of workspaces (add or remove folders).
  Tap a workspace to slide into its contents; a toolbar button creates a new
  note. Tapping a note opens it and jumps to View. The overflow menu holds
  Settings (Appearance; Dark Mode Style with a pure-black OLED option; and
  separate View Font and Edit Font pickers drawn from the system font
  families). The status bar and navigation bar follow the active theme.
- **View** — the open note, read-only but selectable (long-press to select and
  copy). Markdown is shown through syntax highlighting, never by resizing text.
  The font is whatever you pick under Settings → Fonts → View Font.
- **Edit** — the same, but writable, with its own font choice (Settings → Fonts
  → Edit Font). A Save button appears only when there are unsaved changes.
- **Jump** — every open note, for quick switching. Swipe a row to reveal a red
  close button; closing a note with unsaved changes asks first. The toolbar
  toggles a reorder mode.

View and Edit are disabled until a note is open.

## Project layout

```
app/src/main/java/com/mdnotes/app/
  MainActivity.kt          entry point, theme + tab host + folder picker
  AppViewModel.kt          all UI state (settings, browsing, open notes)
  model/                   plain data types and enums
  data/                    settings (DataStore) + notes (SAF) repositories
  util/MarkdownHighlighter markdown -> coloured AnnotatedString
  ui/browse|view|edit|jump|settings   one package per screen
  ui/theme/                light, regular-dark, and pure-black colour schemes
  ui/components/           bottom bar, editor transformation, helpers
```
