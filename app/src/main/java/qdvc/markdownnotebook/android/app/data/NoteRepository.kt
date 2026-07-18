package qdvc.markdownnotebook.android.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import qdvc.markdownnotebook.android.app.model.FolderEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes markdown notes through the Storage Access Framework so the
 * app works with any folder the user grants, on any Android 8+ device.
 *
 * Navigation stays inside the originally-granted tree ([rootTreeUri]): a folder
 * is identified by its document id within that tree, and its children are
 * queried directly. This avoids fabricating new tree uris (which have no
 * granted permission and silently return no children).
 */
class NoteRepository(private val context: Context) {

    private val markdownExtensions = listOf(".md", ".markdown")

    private fun isMarkdown(name: String): Boolean =
        markdownExtensions.any { name.lowercase().endsWith(it) }

    /**
     * Lists subfolders and markdown files directly under the folder whose
     * document id is [folderDocId], within the granted tree [rootTreeUri].
     */
    suspend fun listEntries(rootTreeUri: String, folderDocId: String): List<FolderEntry> =
        withContext(Dispatchers.IO) {
            val treeUri = Uri.parse(rootTreeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderDocId)
            val result = mutableListOf<FolderEntry>()
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)
                    ?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val docId = cursor.getString(0)
                            val name = cursor.getString(1) ?: continue
                            val mime = cursor.getString(2)
                            val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            when {
                                isDir -> result.add(FolderEntry(docUri.toString(), name, true, docId))
                                isMarkdown(name) ->
                                    result.add(FolderEntry(docUri.toString(), name, false, docId))
                            }
                        }
                    }
            } catch (e: Exception) {
                return@withContext emptyList()
            }
            result.sortedWith(
                compareByDescending<FolderEntry> { it.isDirectory }
                    .thenBy { it.displayName.lowercase() }
            )
        }

    /** The document id of the tree root itself (used to list a workspace root). */
    fun rootDocumentId(rootTreeUri: String): String =
        DocumentsContract.getTreeDocumentId(Uri.parse(rootTreeUri))


    suspend fun readNote(documentUri: String): String =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(Uri.parse(documentUri))?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: ""
        }

    suspend fun writeNote(documentUri: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // "wt" truncates so the file is fully replaced.
                context.contentResolver.openOutputStream(Uri.parse(documentUri), "wt")
                    ?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Creates a new markdown note named [rawName] inside the folder identified
     * by [folderDocId] within the granted tree [rootTreeUri]. Returns the
     * created entry, or null if creation failed.
     */
    suspend fun createNote(
        rootTreeUri: String,
        folderDocId: String,
        rawName: String,
    ): FolderEntry? =
        withContext(Dispatchers.IO) {
            try {
                val treeUri = Uri.parse(rootTreeUri)
                val folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, folderDocId)
                val name = if (isMarkdown(rawName)) rawName else "$rawName.md"
                val created = DocumentsContract.createDocument(
                    context.contentResolver, folderUri, "text/markdown", name
                ) ?: return@withContext null
                val newDocId = DocumentsContract.getDocumentId(created)
                val displayName = DocumentFile.fromSingleUri(context, created)?.name ?: name
                FolderEntry(created.toString(), displayName, false, newDocId)
            } catch (e: Exception) {
                null
            }
        }

    /** True if the document still exists (used to detect deleted notes). */
    suspend fun exists(documentUri: String): Boolean =
        withContext(Dispatchers.IO) {
            DocumentFile.fromSingleUri(context, Uri.parse(documentUri))?.exists() ?: false
        }
}
