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

- **Browse** — start here. A home list of workspaces (star icons; add or remove
  folders). Tapping a workspace opens an overview with three entries: Browse
  files (the folder structure), All notes (every markdown file in the workspace,
  wherever it's filed), and Search (full-text search of note titles and
  contents, on its own screen). The system back button steps back through this
  hierarchy, and re-tapping the Browse tab jumps straight to the workspace list.
  Inside the folder browser a toolbar button creates a new note. Tapping a note
  opens it and jumps to View. The overflow menu on the workspace list holds
  Settings (Appearance; Light Mode Style and Dark Mode Style — each offering a
  Regular option plus an Everforest palette, and Dark also offering pure-black
  OLED; and
  separate View Font and Edit Font pickers that list and preview the fonts
  actually installed on the device, plus a custom-font option with four file
  slots (regular, italic, bold, bold-italic) so syntax highlighting uses the
  right face. Chosen custom fonts are copied into the app's own storage — up to
  eight files (four per tab) at fixed paths, each overwritten when re-chosen —
  so they can't go missing between sessions. The status bar matches the top app
  bars and the navigation bar matches the bottom navigation bar.
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
