package qdvc.markdownnotebook.android.app.model

/**
 * A workspace is a folder (picked via the Storage Access Framework) that holds
 * markdown notes. [treeUri] is the persisted document-tree URI string.
 */
data class Workspace(
    val treeUri: String,
    val name: String,
)

/** A folder or markdown file inside a workspace, as browsed via SAF. */
data class FolderEntry(
    val documentUri: String,
    val displayName: String,
    val isDirectory: Boolean,
    val docId: String,
)

/**
 * A markdown note found anywhere in a workspace, with the folder path it lives
 * under (relative to the workspace root, e.g. "Projects/2026").
 */
data class NoteFile(
    val documentUri: String,
    val displayName: String,
    val docId: String,
    val relativePath: String,
)

/**
 * A full-text search hit: the matched note plus a short snippet of surrounding
 * text showing the first match.
 */
data class SearchResult(
    val note: NoteFile,
    val snippet: String,
)

/**
 * A note that is currently open (listed in the Jump tab). Content is held in
 * memory so edits and unsaved state survive tab switches.
 */
data class OpenNote(
    val documentUri: String,
    val displayName: String,
    val workspaceName: String,
    val savedContent: String,
    val draftContent: String,
) {
    val hasUnsavedChanges: Boolean get() = savedContent != draftContent
}

/**
 * The lightweight, on-disk description of an open note (its identity, not its
 * content). Used to restore the Jump list on a fresh launch; content is
 * re-read from the file itself.
 */
data class PersistedOpenNote(
    val documentUri: String,
    val displayName: String,
    val workspaceName: String,
)
