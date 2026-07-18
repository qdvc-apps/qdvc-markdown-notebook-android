# Markdown Notes

A native Android app for opening folders of Markdown notes and viewing or
editing them, built with Kotlin and Jetpack Compose (Material 3).

Your notes stay where they are. You point the app at real folders on your
device through Android's system folder picker, and edits are written straight
back to the original `.md` / `.markdown` files — nothing is copied or locked
away in the app.

## Features

- **Workspaces** — open any number of device folders and browse their structure.
- **All notes & full-text search** — find any note across a workspace by title
  or contents, backed by a fast on-device index.
- **View & Edit** — read-only and editable views of a note, each with its own
  font and text size. Markdown is syntax-highlighted, never resized.
- **Jump** — quickly switch between all your open notes.
- **Themes & fonts** — light/dark colour themes (including Everforest and Rose
  Pine, plus an OLED pure-black), device fonts, and custom font files.

## Requirements

- Android Studio (Koala / 2024.1 or newer)
- Android SDK 34, minimum device API 26 (Android 8.0)
- JDK 17

## Build & run

Open the project in Android Studio and press Run, or from the command line:

```bash
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.

## Documentation

Maintaining or extending the code? See [`docs/MAINTENANCE.md`](docs/MAINTENANCE.md)
for the architecture, the design decisions behind the trickier parts, and where
to make common changes.
