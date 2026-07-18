package qdvc.markdownnotebook.android.app.ui.browse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.markdownnotebook.android.app.BrowseState
import qdvc.markdownnotebook.android.app.model.FolderEntry
import qdvc.markdownnotebook.android.app.model.Workspace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    workspaces: List<Workspace>,
    browse: BrowseState,
    onAddWorkspace: () -> Unit,
    onRemoveWorkspace: (String) -> Unit,
    onOpenWorkspace: (Workspace) -> Unit,
    onOpenSubFolder: (FolderEntry) -> Unit,
    onOpenNote: (FolderEntry) -> Unit,
    onBrowseUp: () -> Unit,
    onCreateNote: (String, (Boolean) -> Unit) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val inFolder = browse.stack.isNotEmpty()
    var menuOpen by remember { mutableStateOf(false) }
    var showNewNote by remember { mutableStateOf(false) }
    var workspaceToRemove by remember { mutableStateOf<Workspace?>(null) }

    val title = if (inFolder) browse.stack.last().title else "Workspaces"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (inFolder) {
                        IconButton(onClick = onBrowseUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (inFolder) {
                        IconButton(onClick = { showNewNote = true }) {
                            Icon(Icons.Filled.NoteAdd, contentDescription = "New note")
                        }
                    } else {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = { menuOpen = false; onOpenSettings() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        // Slide animation between navigation levels: workspace home, a folder,
        // and any nested subfolder. Going deeper slides in from the right;
        // going back slides in from the left.
        AnimatedContent(
            targetState = browse.stack.size,
            transitionSpec = {
                val deeper = targetState > initialState
                if (deeper) {
                    (slideInHorizontally(tween(280)) { it } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(280)) { it } + fadeOut())
                }
            },
            label = "browseTransition",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { depth ->
            if (depth == 0) {
                WorkspaceHome(
                    workspaces = workspaces,
                    onAdd = onAddWorkspace,
                    onOpen = onOpenWorkspace,
                    onRequestRemove = { workspaceToRemove = it },
                )
            } else {
                FolderView(
                    browse = browse,
                    onOpenSubFolder = onOpenSubFolder,
                    onOpenNote = onOpenNote,
                )
            }
        }
    }

    if (showNewNote) {
        NewNoteDialog(
            onDismiss = { showNewNote = false },
            onConfirm = { name ->
                onCreateNote(name) { showNewNote = false }
            },
        )
    }

    workspaceToRemove?.let { ws ->
        AlertDialog(
            onDismissRequest = { workspaceToRemove = null },
            title = { Text("Remove workspace") },
            text = { Text("Remove \"${ws.name}\" from the list? The folder and its notes are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveWorkspace(ws.treeUri)
                    workspaceToRemove = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { workspaceToRemove = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WorkspaceHome(
    workspaces: List<Workspace>,
    onAdd: () -> Unit,
    onOpen: (Workspace) -> Unit,
    onRequestRemove: (Workspace) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onAdd)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary)
            Text("Add workspace", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (workspaces.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No workspaces yet.\nAdd a folder of markdown notes to get started.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(workspaces, key = { it.treeUri }) { ws ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(ws) }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            ws.name,
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { onRequestRemove(ws) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove workspace",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun FolderView(
    browse: BrowseState,
    onOpenSubFolder: (FolderEntry) -> Unit,
    onOpenNote: (FolderEntry) -> Unit,
) {
    if (browse.entries.isEmpty() && !browse.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "This folder has no subfolders or notes.\nUse the new-note button to add one.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(browse.entries, key = { it.documentUri }) { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (entry.isDirectory) onOpenSubFolder(entry) else onOpenNote(entry)
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (entry.isDirectory) Icons.Filled.Folder
                    else Icons.Filled.Description,
                    contentDescription = null,
                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    entry.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun NewNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New note") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Note name") },
                placeholder = { Text("untitled") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
