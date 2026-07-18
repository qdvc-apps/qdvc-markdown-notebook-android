package qdvc.markdownnotebook.android.app

import android.app.Application
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import qdvc.markdownnotebook.android.app.data.NoteRepository
import qdvc.markdownnotebook.android.app.data.SettingsRepository
import qdvc.markdownnotebook.android.app.data.ThemeRepository
import qdvc.markdownnotebook.android.app.model.CustomFontSet
import qdvc.markdownnotebook.android.app.model.FolderEntry
import qdvc.markdownnotebook.android.app.model.FontSizes
import qdvc.markdownnotebook.android.app.model.FontVariant
import qdvc.markdownnotebook.android.app.model.NoteFile
import qdvc.markdownnotebook.android.app.model.OpenNote
import qdvc.markdownnotebook.android.app.model.PersistedOpenNote
import qdvc.markdownnotebook.android.app.model.SearchResult
import qdvc.markdownnotebook.android.app.model.Tab
import qdvc.markdownnotebook.android.app.model.ThemeMode
import qdvc.markdownnotebook.android.app.model.ThemeSpec
import qdvc.markdownnotebook.android.app.model.Workspace
import qdvc.markdownnotebook.android.app.ui.settings.CUSTOM_FONT_ID
import qdvc.markdownnotebook.android.app.ui.settings.DEFAULT_FONT_ID
import qdvc.markdownnotebook.android.app.util.CustomFont
import qdvc.markdownnotebook.android.app.util.SystemFont
import qdvc.markdownnotebook.android.app.util.SystemFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One level in the Browse navigation stack. [rootTreeUri] is the granted
 * workspace tree; [docId] identifies this folder inside that tree.
 */
data class BrowseFolder(
    val rootTreeUri: String,
    val docId: String,
    val title: String,
)

/** Which screen the Browse tab is showing. */
enum class BrowseMode { WORKSPACES, OVERVIEW, FOLDERS, ALL_NOTES, SEARCH }

data class BrowseState(
    val mode: BrowseMode = BrowseMode.WORKSPACES,
    // The workspace being explored (null only in WORKSPACES mode).
    val workspace: Workspace? = null,
    // Folder navigation stack, used in FOLDERS mode.
    val stack: List<BrowseFolder> = emptyList(),
    val entries: List<FolderEntry> = emptyList(),
    val loading: Boolean = false,
    // All-notes list (ALL_NOTES mode).
    val allNotes: List<NoteFile> = emptyList(),
    val allNotesLoading: Boolean = false,
    // Search (SEARCH mode).
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val searching: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val noteRepo = NoteRepository(app)

    val themeMode: StateFlow<ThemeMode> =
        settingsRepo.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AUTOMATIC)

    // Available themes (loaded from JSON assets) and the current selections.
    val lightThemes: List<ThemeSpec> = ThemeRepository.lightThemes(app)
    val darkThemes: List<ThemeSpec> = ThemeRepository.darkThemes(app)

    val lightThemeId: StateFlow<String> =
        settingsRepo.lightThemeId.stateIn(
            viewModelScope, SharingStarted.Eagerly, ThemeRepository.DEFAULT_LIGHT_ID
        )
    val darkThemeId: StateFlow<String> =
        settingsRepo.darkThemeId.stateIn(
            viewModelScope, SharingStarted.Eagerly, ThemeRepository.DEFAULT_DARK_ID
        )

    /** The resolved light/dark [ThemeSpec] for the current selections. */
    val lightTheme: StateFlow<ThemeSpec?> =
        settingsRepo.lightThemeId
            .map { ThemeRepository.lightOrDefault(app, it) }
            .stateIn(
                viewModelScope, SharingStarted.Eagerly,
                ThemeRepository.lightOrDefault(app, ThemeRepository.DEFAULT_LIGHT_ID)
            )
    val darkTheme: StateFlow<ThemeSpec?> =
        settingsRepo.darkThemeId
            .map { ThemeRepository.darkOrDefault(app, it) }
            .stateIn(
                viewModelScope, SharingStarted.Eagerly,
                ThemeRepository.darkOrDefault(app, ThemeRepository.DEFAULT_DARK_ID)
            )

    val viewFontId: StateFlow<String?> =
        settingsRepo.viewFontId.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val editFontId: StateFlow<String?> =
        settingsRepo.editFontId.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val viewFontSize: StateFlow<Float> =
        settingsRepo.viewFontSize.stateIn(viewModelScope, SharingStarted.Eagerly, FontSizes.DEFAULT)
    val editFontSize: StateFlow<Float> =
        settingsRepo.editFontSize.stateIn(viewModelScope, SharingStarted.Eagerly, FontSizes.DEFAULT)
    val viewCustomFontSet: StateFlow<CustomFontSet> =
        settingsRepo.viewCustomFontSet.stateIn(viewModelScope, SharingStarted.Eagerly, CustomFontSet())
    val editCustomFontSet: StateFlow<CustomFontSet> =
        settingsRepo.editCustomFontSet.stateIn(viewModelScope, SharingStarted.Eagerly, CustomFontSet())
    val workspaces: StateFlow<List<Workspace>> =
        settingsRepo.workspaces.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Fonts actually installed on this device, discovered once at startup.
    private val _systemFonts = MutableStateFlow<List<SystemFont>>(emptyList())
    val systemFonts: StateFlow<List<SystemFont>> = _systemFonts.asStateFlow()

    // The loaded custom font (all supplied variants) for each tab. Rebuilt from
    // the persisted URIs whenever the set changes. No fonts are stored in the
    // app; a transient cache backs Compose's file-based Font API.
    private val _viewCustomFont = MutableStateFlow<CustomFont?>(null)
    val viewCustomFont: StateFlow<CustomFont?> = _viewCustomFont.asStateFlow()
    private val _editCustomFont = MutableStateFlow<CustomFont?>(null)
    val editCustomFont: StateFlow<CustomFont?> = _editCustomFont.asStateFlow()

    private val _currentTab = MutableStateFlow(Tab.BROWSE)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()

    private val _openNotes = MutableStateFlow<List<OpenNote>>(emptyList())
    val openNotes: StateFlow<List<OpenNote>> = _openNotes.asStateFlow()

    private val _currentNoteUri = MutableStateFlow<String?>(null)
    val currentNoteUri: StateFlow<String?> = _currentNoteUri.asStateFlow()

    val hasCurrentNote: Boolean get() = _currentNoteUri.value != null

    init {
        restoreOpenNotes()
        loadSystemFonts()
        // Load each tab's custom font from its copied slot files at startup.
        reloadCustomFont(forView = true)
        reloadCustomFont(forView = false)
    }

    private fun loadSystemFonts() {
        viewModelScope.launch {
            val fonts = withContext(Dispatchers.IO) { SystemFonts.discover() }
            _systemFonts.value = fonts
        }
    }

    private fun reloadCustomFont(forView: Boolean) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val font = withContext(Dispatchers.IO) { SystemFonts.loadCustomFont(ctx, forView) }
            if (forView) _viewCustomFont.value = font else _editCustomFont.value = font
        }
    }

    /**
     * Copies the font at [uri] into the fixed slot for (tab, variant),
     * overwriting any existing file, records its name, and reloads the tab's
     * custom font. Does nothing if the file isn't a usable font.
     */
    fun setCustomFontVariant(forView: Boolean, variant: FontVariant, uri: android.net.Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val name = withContext(Dispatchers.IO) {
                SystemFonts.copyIntoSlot(ctx, uri, forView, variant)
            }
            if (name != null) {
                settingsRepo.setCustomFontVariantName(forView, variant, name)
                reloadCustomFont(forView)
            }
        }
    }

    /** Deletes one custom-font slot file and clears its stored name. */
    fun clearCustomFontVariant(forView: Boolean, variant: FontVariant) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            withContext(Dispatchers.IO) { SystemFonts.clearSlot(ctx, forView, variant) }
            settingsRepo.setCustomFontVariantName(forView, variant, null)
            reloadCustomFont(forView)
        }
    }

    /** Selects the custom font set for a tab (as opposed to default/system). */
    fun selectCustomFont(forView: Boolean) {
        viewModelScope.launch {
            if (forView) settingsRepo.setViewFontId(CUSTOM_FONT_ID)
            else settingsRepo.setEditFontId(CUSTOM_FONT_ID)
        }
    }

    /**
     * Resolves a tab's stored font id to a Compose [FontFamily]. Falls back to
     * the app default (monospace) when nothing is chosen or the chosen font is
     * unavailable. [forView] selects which tab's custom font to consult.
     */
    fun fontFamilyFor(id: String?, forView: Boolean): FontFamily {
        if (id == null || id == DEFAULT_FONT_ID) return FontFamily.Monospace
        if (id == CUSTOM_FONT_ID) {
            val custom = if (forView) _viewCustomFont.value else _editCustomFont.value
            return custom?.fontFamily ?: FontFamily.Monospace
        }
        return _systemFonts.value.firstOrNull { it.id == id }?.fontFamily ?: FontFamily.Monospace
    }

    /**
     * Rebuilds the Jump list from disk on launch. Each note's content is
     * re-read from its file; notes whose files have vanished are dropped.
     */
    private fun restoreOpenNotes() {
        viewModelScope.launch {
            val persisted = settingsRepo.loadOpenNotes()
            if (persisted.isEmpty()) return@launch
            val restored = mutableListOf<OpenNote>()
            for (p in persisted) {
                if (!noteRepo.exists(p.documentUri)) continue
                val content = noteRepo.readNote(p.documentUri)
                restored.add(
                    OpenNote(
                        documentUri = p.documentUri,
                        displayName = p.displayName,
                        workspaceName = p.workspaceName,
                        savedContent = content,
                        draftContent = content,
                    )
                )
            }
            _openNotes.value = restored
            val savedCurrent = settingsRepo.loadCurrentNoteUri()
            _currentNoteUri.value = restored
                .firstOrNull { it.documentUri == savedCurrent }?.documentUri
                ?: restored.lastOrNull()?.documentUri
        }
    }

    /** Writes the current Jump list + current selection to disk. */
    private fun persistOpenNotes() {
        val snapshot = _openNotes.value.map {
            PersistedOpenNote(it.documentUri, it.displayName, it.workspaceName)
        }
        val current = _currentNoteUri.value
        viewModelScope.launch { settingsRepo.saveOpenNotes(snapshot, current) }
    }

    fun currentNote(): OpenNote? =
        _openNotes.value.firstOrNull { it.documentUri == _currentNoteUri.value }

    // ---- Tab navigation ----
    fun selectTab(tab: Tab) {
        // View and Edit require an open note.
        if ((tab == Tab.VIEW || tab == Tab.EDIT) && !hasCurrentNote) return
        _currentTab.value = tab
    }

    // ---- Settings ----
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setLightThemeId(id: String) = viewModelScope.launch { settingsRepo.setLightThemeId(id) }
    fun setDarkThemeId(id: String) = viewModelScope.launch { settingsRepo.setDarkThemeId(id) }
    fun setViewFontId(id: String) = viewModelScope.launch { settingsRepo.setViewFontId(id) }
    fun setEditFontId(id: String) = viewModelScope.launch { settingsRepo.setEditFontId(id) }

    /** Sets a tab's font size (sp). Passing null restores the default. */
    fun setFontSize(forView: Boolean, sizeSp: Float?) =
        viewModelScope.launch { settingsRepo.setFontSize(forView, sizeSp) }

    // ---- Workspaces ----
    fun addWorkspace(treeUri: String, name: String) = viewModelScope.launch {
        settingsRepo.addWorkspace(Workspace(treeUri, name))
    }

    fun removeWorkspace(treeUri: String) = viewModelScope.launch {
        settingsRepo.removeWorkspace(treeUri)
    }

    // ---- Browsing ----

    /** Tapping a workspace shows its overview (Browse files / All notes / Search). */
    fun openWorkspace(workspace: Workspace) {
        _browse.value = BrowseState(mode = BrowseMode.OVERVIEW, workspace = workspace)
    }

    /** From the overview: open the folder browser at the workspace root. */
    fun openFolders() {
        val ws = _browse.value.workspace ?: return
        val rootDocId = noteRepo.rootDocumentId(ws.treeUri)
        _browse.value = _browse.value.copy(
            mode = BrowseMode.FOLDERS,
            stack = listOf(BrowseFolder(ws.treeUri, rootDocId, ws.name)),
            loading = true,
        )
        refreshCurrentFolder()
    }

    /** From the overview: show every note in the workspace. */
    fun openAllNotes() {
        val ws = _browse.value.workspace ?: return
        _browse.value = _browse.value.copy(mode = BrowseMode.ALL_NOTES, allNotesLoading = true)
        viewModelScope.launch {
            val notes = noteRepo.listAllNotes(ws.treeUri)
            _browse.value = _browse.value.copy(allNotes = notes, allNotesLoading = false)
        }
    }

    /** From the overview: open the search screen. */
    fun openSearch() {
        _browse.value = _browse.value.copy(
            mode = BrowseMode.SEARCH,
            searchQuery = "",
            searchResults = emptyList(),
            searching = false,
        )
    }

    fun updateSearchQuery(query: String) {
        _browse.value = _browse.value.copy(searchQuery = query)
    }

    fun runSearch() {
        val ws = _browse.value.workspace ?: return
        val query = _browse.value.searchQuery
        _browse.value = _browse.value.copy(searching = true)
        viewModelScope.launch {
            val results = noteRepo.search(ws.treeUri, query)
            _browse.value = _browse.value.copy(searchResults = results, searching = false)
        }
    }

    fun openSubFolder(entry: FolderEntry) {
        val current = _browse.value.stack.lastOrNull() ?: return
        _browse.value = _browse.value.copy(
            stack = _browse.value.stack +
                BrowseFolder(current.rootTreeUri, entry.docId, entry.displayName),
            loading = true,
        )
        refreshCurrentFolder()
    }

    /**
     * Handles a "back" step within the Browse tab. Returns true if it consumed
     * the back action, false if the tab is already at the workspace list.
     */
    fun browseUp(): Boolean {
        val state = _browse.value
        return when (state.mode) {
            BrowseMode.WORKSPACES -> false
            BrowseMode.OVERVIEW -> {
                _browse.value = BrowseState() // back to workspace list
                true
            }
            BrowseMode.ALL_NOTES, BrowseMode.SEARCH -> {
                _browse.value = _browse.value.copy(mode = BrowseMode.OVERVIEW)
                true
            }
            BrowseMode.FOLDERS -> {
                val newStack = state.stack.dropLast(1)
                if (newStack.isEmpty()) {
                    // Leaving the folder browser returns to the overview.
                    _browse.value = _browse.value.copy(
                        mode = BrowseMode.OVERVIEW, stack = emptyList(), entries = emptyList()
                    )
                } else {
                    _browse.value = _browse.value.copy(stack = newStack, loading = true)
                    refreshCurrentFolder()
                }
                true
            }
        }
    }

    /** Tapping the Browse tab (when already in it) jumps to the workspace list. */
    fun resetBrowseToWorkspaces() {
        _browse.value = BrowseState()
    }

    fun refreshCurrentFolder() {
        val folder = _browse.value.stack.lastOrNull() ?: return
        viewModelScope.launch {
            val entries = noteRepo.listEntries(folder.rootTreeUri, folder.docId)
            _browse.value = _browse.value.copy(entries = entries, loading = false)
        }
    }

    private fun currentWorkspaceName(): String =
        _browse.value.workspace?.name ?: _browse.value.stack.firstOrNull()?.title ?: ""

    fun createNoteInCurrentFolder(name: String, onDone: (Boolean) -> Unit) {
        val folder = _browse.value.stack.lastOrNull() ?: return onDone(false)
        viewModelScope.launch {
            val created = noteRepo.createNote(folder.rootTreeUri, folder.docId, name)
            if (created != null) {
                refreshCurrentFolder()
                openNote(created)
                onDone(true)
            } else onDone(false)
        }
    }

    /** Opens a note found via All notes / Search (already has its docId). */
    fun openNoteFile(note: NoteFile) {
        openNote(FolderEntry(note.documentUri, note.displayName, false, note.docId))
    }

    // ---- Opening notes ----
    fun openNote(entry: FolderEntry) {
        val existing = _openNotes.value.firstOrNull { it.documentUri == entry.documentUri }
        if (existing != null) {
            _currentNoteUri.value = existing.documentUri
            _currentTab.value = Tab.VIEW
            persistOpenNotes()
            return
        }
        viewModelScope.launch {
            val content = noteRepo.readNote(entry.documentUri)
            val note = OpenNote(
                documentUri = entry.documentUri,
                displayName = entry.displayName,
                workspaceName = currentWorkspaceName(),
                savedContent = content,
                draftContent = content,
            )
            _openNotes.value = _openNotes.value + note
            _currentNoteUri.value = note.documentUri
            _currentTab.value = Tab.VIEW
            persistOpenNotes()
        }
    }

    // ---- Editing ----
    fun updateDraft(uri: String, draft: String) {
        _openNotes.value = _openNotes.value.map {
            if (it.documentUri == uri) it.copy(draftContent = draft) else it
        }
    }

    fun saveNote(uri: String, onDone: (Boolean) -> Unit = {}) {
        val note = _openNotes.value.firstOrNull { it.documentUri == uri } ?: return onDone(false)
        viewModelScope.launch {
            val ok = noteRepo.writeNote(uri, note.draftContent)
            if (ok) {
                _openNotes.value = _openNotes.value.map {
                    if (it.documentUri == uri) it.copy(savedContent = it.draftContent) else it
                }
            }
            onDone(ok)
        }
    }

    // ---- Jump: close & reorder ----
    fun closeNote(uri: String) {
        val remaining = _openNotes.value.filterNot { it.documentUri == uri }
        _openNotes.value = remaining
        if (_currentNoteUri.value == uri) {
            _currentNoteUri.value = remaining.lastOrNull()?.documentUri
            if (_currentNoteUri.value == null &&
                (_currentTab.value == Tab.VIEW || _currentTab.value == Tab.EDIT)
            ) {
                _currentTab.value = Tab.JUMP
            }
        }
        persistOpenNotes()
    }

    fun setCurrentNote(uri: String) {
        if (_openNotes.value.any { it.documentUri == uri }) {
            _currentNoteUri.value = uri
            persistOpenNotes()
        }
    }

    fun moveOpenNote(from: Int, to: Int) {
        val list = _openNotes.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        _openNotes.value = list
        persistOpenNotes()
    }
}
