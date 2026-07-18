package qdvc.markdownnotebook.android.app

import android.app.Application
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import qdvc.markdownnotebook.android.app.data.NoteRepository
import qdvc.markdownnotebook.android.app.data.SettingsRepository
import qdvc.markdownnotebook.android.app.model.CustomFontSet
import qdvc.markdownnotebook.android.app.model.DarkStyle
import qdvc.markdownnotebook.android.app.model.FolderEntry
import qdvc.markdownnotebook.android.app.model.FontVariant
import qdvc.markdownnotebook.android.app.model.OpenNote
import qdvc.markdownnotebook.android.app.model.PersistedOpenNote
import qdvc.markdownnotebook.android.app.model.Tab
import qdvc.markdownnotebook.android.app.model.ThemeMode
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

data class BrowseState(
    // null = showing the workspace home list; non-empty = inside folders.
    val stack: List<BrowseFolder> = emptyList(),
    val entries: List<FolderEntry> = emptyList(),
    val loading: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val noteRepo = NoteRepository(app)

    val themeMode: StateFlow<ThemeMode> =
        settingsRepo.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AUTOMATIC)
    val darkStyle: StateFlow<DarkStyle> =
        settingsRepo.darkStyle.stateIn(viewModelScope, SharingStarted.Eagerly, DarkStyle.REGULAR)
    val viewFontId: StateFlow<String?> =
        settingsRepo.viewFontId.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val editFontId: StateFlow<String?> =
        settingsRepo.editFontId.stateIn(viewModelScope, SharingStarted.Eagerly, null)
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
    fun setDarkStyle(style: DarkStyle) = viewModelScope.launch { settingsRepo.setDarkStyle(style) }
    fun setViewFontId(id: String) = viewModelScope.launch { settingsRepo.setViewFontId(id) }
    fun setEditFontId(id: String) = viewModelScope.launch { settingsRepo.setEditFontId(id) }

    // ---- Workspaces ----
    fun addWorkspace(treeUri: String, name: String) = viewModelScope.launch {
        settingsRepo.addWorkspace(Workspace(treeUri, name))
    }

    fun removeWorkspace(treeUri: String) = viewModelScope.launch {
        settingsRepo.removeWorkspace(treeUri)
    }

    // ---- Browsing ----
    fun openWorkspace(workspace: Workspace) {
        val rootDocId = noteRepo.rootDocumentId(workspace.treeUri)
        _browse.value = BrowseState(
            stack = listOf(BrowseFolder(workspace.treeUri, rootDocId, workspace.name)),
            loading = true,
        )
        refreshCurrentFolder()
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

    fun browseUp() {
        val stack = _browse.value.stack
        if (stack.isEmpty()) return
        val newStack = stack.dropLast(1)
        if (newStack.isEmpty()) {
            _browse.value = BrowseState() // back to workspace home
        } else {
            _browse.value = _browse.value.copy(stack = newStack, loading = true)
            refreshCurrentFolder()
        }
    }

    fun refreshCurrentFolder() {
        val folder = _browse.value.stack.lastOrNull() ?: return
        viewModelScope.launch {
            val entries = noteRepo.listEntries(folder.rootTreeUri, folder.docId)
            _browse.value = _browse.value.copy(entries = entries, loading = false)
        }
    }

    private fun currentWorkspaceName(): String =
        _browse.value.stack.firstOrNull()?.title ?: ""

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
