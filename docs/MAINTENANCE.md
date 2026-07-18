# Maintenance guide

This document is for whoever maintains or extends this codebase next, human or
otherwise. It explains how the app is put together, why the non-obvious choices
were made, and where to make the most common kinds of change. Read it before
touching the data layer or the Markdown rendering, since both contain decisions
that look wrong until you know the constraint behind them.

The user-facing overview lives in the root `README.md`; this file is the
technical companion to it.

---

## What the app is

A single-Activity, native Android app for reading and editing folders of
Markdown notes in place. Kotlin + Jetpack Compose, Material 3. The user grants
access to real device folders through the Storage Access Framework (SAF); the
app never copies notes — edits write straight back to the original files.

- **Language / UI:** Kotlin, Jetpack Compose, Material 3
- **Min SDK:** 26 (Android 8.0) · **compile / target SDK:** 34 · **JDK:** 17
- **Package / namespace:** `qdvc.markdownnotebook.android.app`
- **Persistence:** DataStore (preferences) for settings; Room (SQLite + FTS4)
  for the search index; SAF for the notes themselves.

---

## Build and verify

Open in Android Studio (Koala / 2024.1+) and Run, or:

```bash
./gradlew assembleDebug     # debug APK -> app/build/outputs/apk/debug/
./gradlew lint              # Android lint
```

The build uses the Kotlin Symbol Processing (KSP) plugin for Room's code
generation. If you change any Room `@Entity`, `@Dao`, or the `@Database`, the
generated code is refreshed on the next build; a stale build can be cleared with
`./gradlew clean`.

There is currently no automated test suite. When verifying changes by hand, the
paths most worth exercising are: granting and removing a workspace, All notes
and Search on a large folder, editing and saving a note (then confirming the
edit is searchable), and regenerating the index from the Index Status screen.

---

## Architecture at a glance

State flows one way. A single `AppViewModel` (an `AndroidViewModel`) owns all
application state as `StateFlow`s. `MainActivity` collects those flows and passes
plain values and callbacks down into stateless Composable screens. Screens never
hold app state themselves beyond transient UI details (a text field's contents,
which settings sub-page is open); everything that must survive a tab switch or
process death lives in the ViewModel or a repository.

```
MainActivity (Compose host, tab switching, folder/font pickers, BackHandler)
   │  collects StateFlows, dispatches callbacks
   ▼
AppViewModel  ── owns all UI state ──┐
   │                                  │
   ├── SettingsRepository  (DataStore: theme, fonts, sizes, workspaces, open notes)
   ├── NoteRepository      (SAF: list / read / write / create notes)
   ├── IndexRepository     (Room FTS index: fast All-notes + Search, background sync)
   └── ThemeRepository     (loads JSON colour themes from assets)
```

### Source layout

```
app/src/main/java/qdvc/markdownnotebook/android/app/
  MainActivity.kt          entry point; edge-to-edge; tab host; folder + font pickers; back handling
  AppViewModel.kt          all UI state; BrowseMode / BrowseState live here too

  model/                   plain data types and enums (no Android deps)
    Models.kt              Workspace, FolderEntry, NoteFile, ScannedNote, SearchResult, OpenNote, PersistedOpenNote
    Settings.kt            ThemeMode, FontVariant, CustomFontSet, FontSizes
    Tab.kt                 Tab enum (BROWSE, VIEW, EDIT, JUMP)
    ThemeSpec.kt           ThemeSpec, ThemeColors (a parsed JSON theme)
    IndexStatus.kt         IndexState, IndexStatus (for the Index Status screen)

  data/
    SettingsRepository.kt  DataStore-backed settings + persisted open-note identities
    NoteRepository.kt      all SAF access (see "The Storage Access Framework" below)
    ThemeRepository.kt     reads/caches themes from assets/themes/*.json
    IndexRepository.kt     the indexer: reconcile / regenerate / search / all-notes
    index/                 Room layer for the index
      IndexEntities.kt     IndexedNoteEntity (content) + NoteFtsEntity (@Fts4 shadow)
      IndexMetaEntity.kt   per-workspace bookkeeping (last regenerated, count)
      IndexDao.kt          queries incl. the FTS MATCH join
      IndexDatabase.kt     Room database singleton

  util/
    MarkdownHighlighter.kt Markdown -> coloured AnnotatedString (see "Markdown rendering")
    SystemFonts.kt         device font discovery + custom-font slot management

  ui/
    theme/Theme.kt         builds the Compose ColorScheme from a ThemeSpec
    components/             BottomBar, MarkdownVisualTransformation, SyntaxColorsProvider
    browse/BrowseScreen.kt  workspaces, overview, folders, all-notes, search, index-status
    view/ViewScreen.kt      read-only note
    edit/EditScreen.kt      editable note
    jump/JumpScreen.kt      open-note switcher (swipe-close, reorder)
    settings/SettingsScreen.kt  all settings pages
```

A useful rule when reading: if you want to know *what state exists*, read
`AppViewModel`. If you want to know *how something is stored or fetched*, read
the matching repository. The screens are just projections of that state.

---

## The Storage Access Framework (read before editing `NoteRepository`)

This is the single most constraint-driven part of the app, and the easiest to
break with a "cleaner" rewrite.

- A workspace is a **tree URI** the user granted through the system folder
  picker. The app takes *persistable* read/write permission for it so the grant
  survives reboots. All file access goes through `ContentResolver`, not
  `java.io.File` — the app has no direct filesystem access to these folders.
- **Navigation must stay inside the granted tree.** A folder is addressed by its
  *document id within that tree*, and its children are queried with
  `DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)`. Do **not**
  fabricate a fresh tree URI for a subfolder: a tree URI the user did not grant
  carries no permission and silently returns zero children. This is why the code
  threads `rootTreeUri` + `docId` around instead of passing self-contained URIs.
- Each SAF call is IPC to the storage provider, so it is far more expensive than
  an ordinary file read. Cost scales with the number of files and folders. This
  expense is the entire reason the search index exists (below). Keep this in
  mind before adding any feature that walks the tree on the main path.
- `listAllNotesWithMeta` is deliberately the "cheap half" of a scan: it reads
  document id, name, last-modified and size only — never file bodies. Body reads
  happen one file at a time via `readNote`, and only when the index actually
  needs them.

If you add file operations, mirror the existing pattern: build child/document
URIs from the tree + id, do the work on `Dispatchers.IO`, and catch exceptions
per operation (a single unreadable file must not abort a whole scan).

---

## The search index (Room + FTS4)

All-notes and Search are backed by an on-device index so they don't pay the SAF
cost on every use. The index lives in the app's private storage
(`note_index.db`) and is a **pure cache** — it never touches the workspace. If
it is missing, empty, or throws, the reads fall back to a live SAF scan, so the
features keep working; they are just slower.

### Why FTS4 and not FTS5

Room provides first-class support for FTS3/FTS4 (`@Fts4`) but **not FTS5** —
FTS5 is not present in the SQLite shipped with older supported Android versions,
so Room deliberately omits it. Using FTS5 would mean dropping Room's schema
management and hand-writing `CREATE VIRTUAL TABLE ... USING fts5` plus the sync
triggers. FTS4 gives the same inverted-index matching, snippets and ranking we
need and scales fine to large workspaces, so the app uses `@Fts4`. If you ever
genuinely need an FTS5-only feature (e.g. the `bm25()` ranking function), that is
the point to weigh the raw-SQL route — do not casually "upgrade" the annotation,
because there is no `@Fts5` to upgrade to.

### How the tables fit together

- `IndexedNoteEntity` (`indexed_notes`) is the content + metadata table, one row
  per note, keyed by an autogenerated `rowId` with a unique index on
  `(workspaceUri, docId)`. It stores the note's full body in `content`.
- `NoteFtsEntity` (`notes_fts`) is an `@Fts4(contentEntity = IndexedNoteEntity)`
  shadow of that `content` column. Room generates the virtual table **and the
  triggers** that keep it in sync, so you only ever write to `indexed_notes`;
  the FTS table follows automatically (inserts, updates, deletes).
- Search joins the two on rowid: `indexed_notes.rowId = notes_fts.rowid`. This
  works because Room maps the autogenerated `Long` primary key to SQLite's true
  `rowid`, which is what the FTS content-table linkage requires. If you rename or
  retype that key, the join breaks silently — leave `rowId: Long` alone.
- `IndexMetaEntity` (`index_meta`) records, per workspace, when a full
  reconciliation last completed and the note count, for the Index Status screen.

### Freshness model

- **On launch,** `AppViewModel.reindexAllWorkspaces()` kicks off a background
  `reconcile` per workspace. Reconcile walks the tree for `(docId, lastModified)`
  only, re-reads bodies for **new or changed** files, prunes rows whose files
  disappeared, and updates the metadata. Unchanged workspaces cost almost
  nothing to verify.
- **On save,** `updateSavedNote` updates just the edited row (matched by
  document URI) so the change is searchable immediately, without waiting for the
  next reconcile.
- **External edits** (made in another app) are picked up on the next launch's
  reconcile. This is the accepted staleness window; the timestamp check bounds it.
- **Manual regenerate** (Index Status screen) clears the workspace's rows and
  rebuilds from scratch.

Reconciliation is serialised per workspace with a `Mutex` so launch-time and
manual runs can't collide. During a run the `IndexStatus` `StateFlow` is updated
per file (current file name + rising count); `StateFlow` conflation means the UI
stays responsive even on large workspaces. If you add work inside that loop,
keep it cheap and keep it on `Dispatchers.IO`.

### FTS query building

User input becomes a safe MATCH expression in `IndexRepository.toFtsQuery`: it
splits on whitespace, strips FTS operator characters, and turns each token into
a prefix term (`foo*`) AND-ed together. This is why a search for `foo` also
matches `foobar`. Body matches (via FTS) and title matches (a `LIKE` on the file
name, since FTS only indexes bodies) are merged and de-duplicated, body matches
first. Snippets come from SQLite's `snippet()`; the delimiter characters it
inserts are stripped in `cleanSnippet`, and the UI re-bolds query terms itself.

---

## Markdown rendering (read before editing the highlighter)

Markdown is shown by **colouring monospaced text, never by resizing it** —
headings are coloured and weighted, not enlarged, so the character grid stays
stable. Highlight colours are pulled from the active theme via
`SyntaxColorsProvider`, so new themes recolour highlighting for free.

### The hanging-indent / `ParagraphStyle` trap

`MarkdownHighlighter.highlight(...)` has **two code paths**, and the split is
deliberate — do not try to unify them:

1. **View path** (`hangingIndentFontSizeSp != null`): each line is wrapped in its
   own `ParagraphStyle` with a `TextIndent` so that wrapped/continuation lines of
   a list item hang-indent under the item text. Crucially this path emits **no
   literal `\n`** between lines — the per-line `ParagraphStyle` supplies the line
   breaks itself.
2. **Editor / plain path** (`null`): literal `\n` between lines and **no**
   `ParagraphStyle`, preserving an exact 1:1 character mapping between source and
   displayed text.

The reason for the split: a Compose `ParagraphStyle` behaves "as if it had line
feeds at the beginning and end". If you combine per-line `ParagraphStyle` *and*
literal newlines, every line is double-spaced (an empty line appears after each
line). More importantly, in the editor the visual transformation must keep the
character count identical to the source so the cursor maps correctly
(`OffsetMapping.Identity` in `MarkdownVisualTransformation`); a `ParagraphStyle`
that alters character/line accounting corrupts cursor positioning.

**Consequence, accepted deliberately:** hanging indents are **View-only**. The
editor renders lists flat. This is not a bug to "fix" by adding `ParagraphStyle`
to the editor path — doing so reintroduces the cursor-mapping breakage. If you
ever need hanging indents in the editor, it requires a real custom
`OffsetMapping` that accounts for the inserted breaks, not a quick reuse of the
View path.

---

## Themes

Colour themes are plain JSON files in `app/src/main/assets/themes/`, one file per
theme. `ThemeRepository` loads and caches them; `Theme.kt` turns a parsed
`ThemeSpec` into a Compose `ColorScheme`.

Each file has an `id`, a display `name`, a `dark` boolean, and a `colors` object
with the handful of roles the app uses (`background`, `surface`, `surfaceVariant`,
`onBackground`, `onSurfaceVariant`, `outline`, `primary`, `onPrimary`,
`secondary`, `onSecondary`, `error`), all hex strings.

**To add a theme:** drop a new JSON file in that folder following the same shape.
It becomes selectable automatically — light themes appear under Light Mode Style,
dark ones under Dark Mode Style, keyed off the `dark` flag. No code change is
needed. The selected theme is persisted by its `id`; the defaults are
`regular_light` and `regular_dark` (see `ThemeRepository`). Because syntax
highlighting reads `MaterialTheme.colorScheme`, a new theme automatically
restyles Markdown too.

---

## Fonts

Each of View and Edit has an independent font setting, stored as an id:

- the default sentinel = built-in monospace,
- a system-font file path (fonts discovered at startup by `SystemFonts.discover`),
- or the custom sentinel = user-supplied font files.

Custom fonts use **eight fixed slots** — four variants (regular, italic, bold,
bold-italic) for each of the two tabs — copied into the app's private storage at
`filesDir/custom_fonts/{view,edit}_{variant}.ttf`, with a sidecar `.name` file
per slot. Copying (rather than holding a SAF URI) is what stops a custom font
from vanishing between sessions if the source file moves. Multi-variant families
are assembled with `FontFamily(List<Font>)` so bold/italic Markdown uses the
right face. Slot management lives in `SystemFonts`; the wiring is in
`AppViewModel` (`setCustomFontVariant`, `clearCustomFontVariant`,
`selectCustomFont`, `fontFamilyFor`).

Font sizes are per-tab floats (`FontSizes` defines default/min/max/step and the
line-height ratio) with a one-tap reset to default.

---

## Navigation and back-button behaviour

There are four bottom tabs (`Tab` enum): Browse, View, Edit, Jump. View and Edit
are disabled until a note is open.

Browse is itself a small hierarchy driven by `BrowseMode`
(`WORKSPACES → OVERVIEW → FOLDERS / ALL_NOTES / SEARCH / INDEX_STATUS`), held in
`BrowseState` in the ViewModel. `browseUp()` encodes the back-step logic.

**Back-button convention (keep this consistent when adding screens):** if a
screen shows a back arrow in its toolbar, the Android system back button must do
*exactly what that arrow does* — step up one level within the screen, and only
leave the screen when already at its root. On Browse this is `browseUp()`; in
Settings the toolbar arrow and a `BackHandler` share one `goBack` lambda so they
can't diverge. Re-tapping the Browse tab while already on it jumps straight back
to the workspace list. When you add a new sub-screen, wire its system-back to the
same handler that drives its toolbar arrow rather than adding an independent
`BackHandler`.

---

## Common changes, and where to make them

- **Add a colour theme** — add a JSON file under `assets/themes/`. No code.
- **Add a Settings page** — add a value to the `SettingsPage` enum in
  `SettingsScreen.kt`, a title case, a row that navigates to it, and a branch in
  the content `when`. Back is already handled by the shared `goBack`.
- **Add a Browse sub-screen** — add a `BrowseMode`, a title/depth case and a
  dispatch branch in `BrowseScreen.kt`, a handler in `browseUp()`, and pass any
  new callbacks/state from `MainActivity`.
- **Change what search matches or how snippets look** — `IndexRepository`
  (`toFtsQuery`, `search`, `cleanSnippet`) for the query/data side; the
  `highlightQuery` / `SearchResultRow` helpers in `BrowseScreen.kt` for display.
- **Change the index schema** — edit the entities/DAO, bump the `@Database`
  version. The database is a disposable cache and uses
  `fallbackToDestructiveMigration()`, so a version bump simply rebuilds it from a
  scan; you generally do **not** need to write a `Migration`.
- **Add a persisted setting** — add a key + flow + setter in
  `SettingsRepository`, expose it as a `StateFlow` in `AppViewModel`, and read it
  where needed. Follow the existing DataStore patterns.
- **Touch file access** — `NoteRepository`, honouring the SAF constraints above.

---

## Gotchas checklist

- Don't fabricate subfolder tree URIs — navigate by tree + document id.
- Don't add `ParagraphStyle` to the editor Markdown path — it breaks cursor
  mapping. Hanging indents are View-only on purpose.
- Don't rename/retype `IndexedNoteEntity.rowId` — the FTS join relies on it being
  the SQLite rowid.
- Don't try to switch the index to FTS5 by annotation — Room has no `@Fts5`.
- Don't treat the index as a source of truth — it's a cache; always keep the live
  SAF fallback working.
- Keep all SAF and file work off the main thread and exception-safe per file.
- When adding a screen with a toolbar back arrow, route system-back through the
  same handler.
