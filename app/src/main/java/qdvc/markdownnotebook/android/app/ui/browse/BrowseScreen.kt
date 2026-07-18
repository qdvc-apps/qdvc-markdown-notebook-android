package qdvc.markdownnotebook.android.app.ui.browse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.markdownnotebook.android.app.BrowseMode
import qdvc.markdownnotebook.android.app.BrowseState
import qdvc.markdownnotebook.android.app.model.FolderEntry
import qdvc.markdownnotebook.android.app.model.IndexState
import qdvc.markdownnotebook.android.app.model.IndexStatus
import qdvc.markdownnotebook.android.app.model.NoteFile
import qdvc.markdownnotebook.android.app.model.SearchResult
import qdvc.markdownnotebook.android.app.model.Workspace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    workspaces: List<Workspace>,
    browse: BrowseState,
    onAddWorkspace: () -> Unit,
    onRemoveWorkspace: (String) -> Unit,
    onOpenWorkspace: (Workspace) -> Unit,
    onOpenFolders: () -> Unit,
    onOpenAllNotes: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenIndexStatus: () -> Unit,
    onRegenerateIndex: () -> Unit,
    indexStatus: IndexStatus,
    onOpenSubFolder: (FolderEntry) -> Unit,
    onOpenNote: (FolderEntry) -> Unit,
    onOpenNoteFile: (NoteFile) -> Unit,
    onBrowseUp: () -> Unit,
    onCreateNote: (String, (Boolean) -> Unit) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRunSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showNewNote by remember { mutableStateOf(false) }
    var workspaceToRemove by remember { mutableStateOf<Workspace?>(null) }

    val inFolder = browse.mode == BrowseMode.FOLDERS
    val showBack = browse.mode != BrowseMode.WORKSPACES

    val title = when (browse.mode) {
        BrowseMode.WORKSPACES -> "Workspaces"
        BrowseMode.OVERVIEW -> browse.workspace?.name ?: "Workspace"
        BrowseMode.FOLDERS -> browse.stack.lastOrNull()?.title ?: browse.workspace?.name ?: ""
        BrowseMode.ALL_NOTES -> "All notes"
        BrowseMode.SEARCH -> "Search"
        BrowseMode.INDEX_STATUS -> "Index status"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBrowseUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    when (browse.mode) {
                        BrowseMode.FOLDERS -> {
                            IconButton(onClick = { showNewNote = true }) {
                                Icon(Icons.Filled.NoteAdd, contentDescription = "New note")
                            }
                        }
                        BrowseMode.WORKSPACES -> {
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
                        else -> {}
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
        // Depth drives the slide direction: workspace list (0), overview (1),
        // then folders/all-notes/search (2+). Folder depth grows with the stack.
        val depth = when (browse.mode) {
            BrowseMode.WORKSPACES -> 0
            BrowseMode.OVERVIEW -> 1
            BrowseMode.ALL_NOTES, BrowseMode.SEARCH -> 2
            BrowseMode.INDEX_STATUS -> 2
            BrowseMode.FOLDERS -> 1 + browse.stack.size
        }
        AnimatedContent(
            targetState = depth,
            transitionSpec = {
                val deeper = targetState > initialState
                val spec = if (deeper) {
                    (slideInHorizontally(tween(280)) { it } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(280)) { it } + fadeOut())
                }
                // Snap the container size instead of animating it, so screens of
                // different heights slide straight across rather than appearing
                // to move diagonally from a corner.
                spec.using(SizeTransform(clip = false) { _, _ -> snap() })
            },
            label = "browseTransition",
            contentKey = { browse.mode },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { _ ->
            when (browse.mode) {
                BrowseMode.WORKSPACES -> WorkspaceList(
                    workspaces = workspaces,
                    onAdd = onAddWorkspace,
                    onOpen = onOpenWorkspace,
                    onRequestRemove = { workspaceToRemove = it },
                )
                BrowseMode.OVERVIEW -> WorkspaceOverview(
                    workspace = browse.workspace,
                    onBrowseFiles = onOpenFolders,
                    onAllNotes = onOpenAllNotes,
                    onSearch = onOpenSearch,
                    onIndexStatus = onOpenIndexStatus,
                )
                BrowseMode.FOLDERS -> FolderView(
                    browse = browse,
                    onOpenSubFolder = onOpenSubFolder,
                    onOpenNote = onOpenNote,
                )
                BrowseMode.ALL_NOTES -> AllNotesView(
                    browse = browse,
                    onOpenNoteFile = onOpenNoteFile,
                )
                BrowseMode.SEARCH -> SearchView(
                    browse = browse,
                    onQueryChange = onSearchQueryChange,
                    onRunSearch = onRunSearch,
                    onOpenNoteFile = onOpenNoteFile,
                )
                BrowseMode.INDEX_STATUS -> IndexStatusView(
                    status = indexStatus,
                    onRegenerate = onRegenerateIndex,
                )
            }
        }
    }

    if (showNewNote) {
        NewNoteDialog(
            onDismiss = { showNewNote = false },
            onConfirm = { name -> onCreateNote(name) { showNewNote = false } },
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
private fun WorkspaceList(
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
                        // Workspaces use a star; folders (inside a workspace) use folder icons.
                        Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary)
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
private fun WorkspaceOverview(
    workspace: Workspace?,
    onBrowseFiles: () -> Unit,
    onAllNotes: () -> Unit,
    onSearch: () -> Unit,
    onIndexStatus: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Show the workspace's underlying folder path in small text.
        Text(
            text = workspace?.let { prettyPath(it.treeUri) } ?: "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        OverviewRow(Icons.Filled.FolderOpen, "Browse files", "Explore the folder structure", onBrowseFiles)
        OverviewRow(Icons.Filled.Article, "All notes", "Every note, wherever it's filed", onAllNotes)
        OverviewRow(Icons.Filled.Search, "Search", "Full-text search of titles and contents", onSearch)
        OverviewRow(Icons.Filled.Storage, "Index status", "The on-device search index", onIndexStatus)
    }
}

@Composable
private fun OverviewRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun IndexStatusView(
    status: IndexStatus,
    onRegenerate: () -> Unit,
) {
    val (label, detail) = when (status.state) {
        IndexState.READY -> "Ready" to "The index is up to date and serving searches."
        IndexState.BUILDING -> "Updating…" to "The index is being brought up to date in the background."
        IndexState.NOT_BUILT -> "Not built yet" to
            "No index for this workspace yet. Search falls back to a live scan until it's built."
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusLine("Status", label)
        StatusLine("Indexed notes", status.noteCount.toString())
        StatusLine("Last regenerated", formatTimestamp(status.lastRegenerated))

        Text(
            detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onRegenerate,
            enabled = status.state != IndexState.BUILDING,
        ) {
            Text(if (status.state == IndexState.BUILDING) "Regenerating…" else "Regenerate now")
        }

        Text(
            "Regenerating rebuilds the index from scratch by reading every note once. " +
                "This only affects the app's private search index — your notes are never modified.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
    }
}

/** Formats an epoch-millis timestamp for display; 0 means "never". */
private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Never"
    val fmt = java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT
    )
    return fmt.format(java.util.Date(epochMillis))
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
                    if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
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
private fun AllNotesView(
    browse: BrowseState,
    onOpenNoteFile: (NoteFile) -> Unit,
) {
    if (browse.allNotesLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Scanning notes…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        }
        return
    }
    if (browse.allNotes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No notes in this workspace yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(browse.allNotes, key = { it.documentUri }) { note ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenNoteFile(note) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        note.displayName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (note.relativePath.isNotEmpty()) {
                        Text(
                            note.relativePath,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SearchView(
    browse: BrowseState,
    onQueryChange: (String) -> Unit,
    onRunSearch: () -> Unit,
    onOpenNoteFile: (NoteFile) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = browse.searchQuery,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            label = { Text("Search titles and contents") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                TextButton(onClick = onRunSearch, enabled = browse.searchQuery.isNotBlank()) {
                    Text("Search")
                }
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when {
            browse.searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            }
            browse.searchResults.isEmpty() && browse.searchQuery.isNotBlank() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No matches for \"${browse.searchQuery.trim()}\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                }
            browse.searchResults.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Type a query and tap Search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(browse.searchResults, key = { it.note.documentUri }) { result ->
                    SearchResultRow(result, onOpenNoteFile)
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, onOpen: (NoteFile) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(result.note) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                result.note.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result.note.relativePath.isNotEmpty()) {
                Text(
                    result.note.relativePath,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (result.snippet.isNotEmpty()) {
                Text(
                    result.snippet,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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

/**
 * Renders a SAF tree URI as a readable path, e.g.
 * "content://…/tree/primary:Notes/Work" -> "/Notes/Work".
 */
private fun prettyPath(treeUri: String): String {
    return try {
        val decoded = android.net.Uri.decode(treeUri)
        val afterColon = decoded.substringAfterLast(':', "")
        if (afterColon.isNotEmpty()) "/$afterColon" else decoded
    } catch (e: Exception) {
        treeUri
    }
}
