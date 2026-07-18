package qdvc.markdownnotebook.android.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import qdvc.markdownnotebook.android.app.data.NoteRepository
import qdvc.markdownnotebook.android.app.data.SettingsRepository
import qdvc.markdownnotebook.android.app.model.AppFont
import qdvc.markdownnotebook.android.app.model.DarkStyle
import qdvc.markdownnotebook.android.app.model.FolderEntry
import qdvc.markdownnotebook.android.app.model.OpenNote
import qdvc.markdownnotebook.android.app.model.Tab
import qdvc.markdownnotebook.android.app.model.ThemeMode
import qdvc.markdownnotebook.android.app.model.Workspace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val viewFont: StateFlow<AppFont> =
        settingsRepo.viewFont.stateIn(viewModelScope, SharingStarted.Eagerly, AppFont.MONOSPACE)
    val editFont: StateFlow<AppFont> =
        settingsRepo.editFont.stateIn(viewModelScope, SharingStarted.Eagerly, AppFont.MONOSPACE)
    val workspaces: StateFlow<List<Workspace>> =
        settingsRepo.workspaces.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentTab = MutableStateFlow(Tab.BROWSE)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()

    private val _openNotes = MutableStateFlow<List<OpenNote>>(emptyList())
    val openNotes: StateFlow<List<OpenNote>> = _openNotes.asStateFlow()

    private val _currentNoteUri = MutableStateFlow<String?>(null)
    val currentNoteUri: StateFlow<String?> = _currentNoteUri.asStateFlow()

    val hasCurrentNote: Boolean get() = _currentNoteUri.value != null

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
    fun setViewFont(font: AppFont) = viewModelScope.launch { settingsRepo.setViewFont(font) }
    fun setEditFont(font: AppFont) = viewModelScope.launch { settingsRepo.setEditFont(font) }

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
    }

    fun setCurrentNote(uri: String) {
        if (_openNotes.value.any { it.documentUri == uri }) {
            _currentNoteUri.value = uri
        }
    }

    fun moveOpenNote(from: Int, to: Int) {
        val list = _openNotes.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        _openNotes.value = list
    }
}
