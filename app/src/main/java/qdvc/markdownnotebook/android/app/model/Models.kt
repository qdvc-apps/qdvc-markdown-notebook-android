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
