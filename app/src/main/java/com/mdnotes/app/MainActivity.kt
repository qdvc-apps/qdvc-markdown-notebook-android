package com.mdnotes.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.documentfile.provider.DocumentFile
import com.mdnotes.app.model.Tab
import com.mdnotes.app.ui.browse.BrowseScreen
import com.mdnotes.app.ui.components.BottomBar
import com.mdnotes.app.ui.edit.EditScreen
import com.mdnotes.app.ui.jump.JumpScreen
import com.mdnotes.app.ui.settings.SettingsScreen
import com.mdnotes.app.ui.theme.MarkdownNotesTheme
import com.mdnotes.app.ui.view.ViewScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    // SAF folder picker. On result we persist read/write permission and add the
    // folder as a workspace.
    private val openFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: SecurityException) {
                }
                val name = DocumentFile.fromTreeUri(this, uri)?.name ?: uri.lastPathSegment ?: "Workspace"
                vm.addWorkspace(uri.toString(), name)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by vm.themeMode.collectAsState()
            val darkStyle by vm.darkStyle.collectAsState()

            MarkdownNotesTheme(themeMode = themeMode, darkStyle = darkStyle) {
                AppRoot(vm = vm, onPickFolder = { openFolder.launch(null) })
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel, onPickFolder: () -> Unit) {
    val workspaces by vm.workspaces.collectAsState()
    val browse by vm.browse.collectAsState()
    val openNotes by vm.openNotes.collectAsState()
    val currentTab by vm.currentTab.collectAsState()
    val currentNoteUri by vm.currentNoteUri.collectAsState()

    var showSettings by remember { mutableStateOf(false) }

    val currentNote = openNotes.firstOrNull { it.documentUri == currentNoteUri }
    val noteOpen = currentNoteUri != null

    if (showSettings) {
        SettingsScreen(
            themeMode = vm.themeMode.collectAsState().value,
            darkStyle = vm.darkStyle.collectAsState().value,
            onThemeMode = vm::setThemeMode,
            onDarkStyle = vm::setDarkStyle,
            onClose = { showSettings = false },
        )
        return
    }

    Scaffold(
        bottomBar = {
            BottomBar(
                current = currentTab,
                noteOpen = noteOpen,
                onSelect = vm::selectTab,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                Tab.BROWSE -> BrowseScreen(
                    workspaces = workspaces,
                    browse = browse,
                    onAddWorkspace = onPickFolder,
                    onRemoveWorkspace = vm::removeWorkspace,
                    onOpenWorkspace = vm::openWorkspace,
                    onOpenSubFolder = vm::openSubFolder,
                    onOpenNote = vm::openNote,
                    onBrowseUp = vm::browseUp,
                    onCreateNote = vm::createNoteInCurrentFolder,
                    onOpenSettings = { showSettings = true },
                )

                Tab.VIEW -> ViewScreen(note = currentNote)

                Tab.EDIT -> EditScreen(
                    note = currentNote,
                    onDraftChange = { draft ->
                        currentNoteUri?.let { vm.updateDraft(it, draft) }
                    },
                    onSave = { currentNoteUri?.let { vm.saveNote(it) } },
                )

                Tab.JUMP -> JumpScreen(
                    notes = openNotes,
                    currentUri = currentNoteUri,
                    onSelect = { uri ->
                        vm.setCurrentNote(uri)
                        vm.selectTab(Tab.VIEW)
                    },
                    onCloseConfirmed = vm::closeNote,
                    onMove = vm::moveOpenNote,
                )
            }
        }
    }
}
