package qdvc.markdownnotebook.android.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import qdvc.markdownnotebook.android.app.model.FolderEntry
import qdvc.markdownnotebook.android.app.model.NoteFile
import qdvc.markdownnotebook.android.app.model.ScannedNote
import qdvc.markdownnotebook.android.app.model.SearchResult
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

    /**
     * Recursively collects every markdown note in the workspace [rootTreeUri],
     * each tagged with the folder path it lives under (relative to the root).
     * Sorted by name.
     */
    suspend fun listAllNotes(rootTreeUri: String): List<NoteFile> =
        withContext(Dispatchers.IO) {
            val treeUri = Uri.parse(rootTreeUri)
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val result = mutableListOf<NoteFile>()
            collectNotes(treeUri, rootId, "", result)
            result.sortedBy { it.displayName.lowercase() }
        }

    private fun collectNotes(
        treeUri: Uri,
        folderDocId: String,
        relativePath: String,
        out: MutableList<NoteFile>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        // Gather this level first so the cursor is closed before we recurse.
        val subFolders = mutableListOf<Pair<String, String>>() // docId to name
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        subFolders.add(docId to name)
                    } else if (isMarkdown(name)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        out.add(NoteFile(docUri.toString(), name, docId, relativePath))
                    }
                }
            }
        } catch (e: Exception) {
            return
        }
        for ((childId, childName) in subFolders) {
            val childPath = if (relativePath.isEmpty()) childName else "$relativePath/$childName"
            collectNotes(treeUri, childId, childPath, out)
        }
    }

    /**
     * Like [listAllNotes] but also captures each note's last-modified time and
     * size, which the on-device index uses to detect changed files cheaply
     * (without reading any bodies). This is the "cheap half" of a reconciliation
     * pass: many SAF queries, but no file opens.
     */
    suspend fun listAllNotesWithMeta(rootTreeUri: String): List<ScannedNote> =
        withContext(Dispatchers.IO) {
            val treeUri = Uri.parse(rootTreeUri)
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val result = mutableListOf<ScannedNote>()
            collectNotesWithMeta(treeUri, rootId, "", result)
            result
        }

    private fun collectNotesWithMeta(
        treeUri: Uri,
        folderDocId: String,
        relativePath: String,
        out: MutableList<ScannedNote>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val subFolders = mutableListOf<Pair<String, String>>()
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    val lastModified = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    val size = if (cursor.isNull(4)) 0L else cursor.getLong(4)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        subFolders.add(docId to name)
                    } else if (isMarkdown(name)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        out.add(
                            ScannedNote(docUri.toString(), name, docId, relativePath, lastModified, size)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            return
        }
        for ((childId, childName) in subFolders) {
            val childPath = if (relativePath.isEmpty()) childName else "$relativePath/$childName"
            collectNotesWithMeta(treeUri, childId, childPath, out)
        }
    }

    /**
     * Full-text search across every note in the workspace. Matches note titles
     * (file names and markdown headings) and body text, case-insensitively, and
     * returns a short snippet around the first match. [query] is trimmed; blank
     * queries return nothing.
     */
    suspend fun search(rootTreeUri: String, query: String): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()
            val needle = q.lowercase()
            val notes = listAllNotes(rootTreeUri)
            val results = mutableListOf<SearchResult>()
            for (note in notes) {
                val content = try {
                    readNote(note.documentUri)
                } catch (e: Exception) {
                    ""
                }
                val nameMatch = note.displayName.lowercase().contains(needle)
                val idx = content.lowercase().indexOf(needle)
                if (idx >= 0) {
                    results.add(SearchResult(note, snippetAround(content, idx, q.length)))
                } else if (nameMatch) {
                    results.add(SearchResult(note, firstMeaningfulLine(content)))
                }
            }
            results
        }

    /** A ~120-char window around [index], with ellipses and collapsed whitespace. */
    private fun snippetAround(text: String, index: Int, matchLen: Int): String {
        val radius = 60
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + matchLen + radius).coerceAtMost(text.length)
        val core = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return "$prefix$core$suffix"
    }

    private fun firstMeaningfulLine(text: String): String =
        text.lineSequence()
            .map { it.trim().trimStart('#', ' ') }
            .firstOrNull { it.isNotEmpty() }
            ?.take(120)
            ?: ""


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
