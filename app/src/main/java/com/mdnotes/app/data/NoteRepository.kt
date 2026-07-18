package com.mdnotes.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mdnotes.app.model.FolderEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes markdown notes through the Storage Access Framework so the
 * app works with any folder the user grants, on any Android 8+ device.
 */
class NoteRepository(private val context: Context) {

    private val markdownExtensions = listOf(".md", ".markdown")

    private fun isMarkdown(name: String): Boolean =
        markdownExtensions.any { name.lowercase().endsWith(it) }

    /** Lists subfolders and markdown files directly under [folderTreeUri]. */
    suspend fun listEntries(folderTreeUri: String): List<FolderEntry> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(folderTreeUri))
                ?: return@withContext emptyList()
            root.listFiles()
                .mapNotNull { doc ->
                    val name = doc.name ?: return@mapNotNull null
                    when {
                        doc.isDirectory -> FolderEntry(doc.uri.toString(), name, true)
                        isMarkdown(name) -> FolderEntry(doc.uri.toString(), name, false)
                        else -> null
                    }
                }
                .sortedWith(
                    compareByDescending<FolderEntry> { it.isDirectory }
                        .thenBy { it.displayName.lowercase() }
                )
        }

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
     * Creates a new markdown note named [rawName] inside [folderTreeUri].
     * Returns the created entry, or null if creation failed.
     */
    suspend fun createNote(folderTreeUri: String, rawName: String): FolderEntry? =
        withContext(Dispatchers.IO) {
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderTreeUri))
                ?: return@withContext null
            val name = if (isMarkdown(rawName)) rawName else "$rawName.md"
            val created = folder.createFile("text/markdown", name) ?: return@withContext null
            FolderEntry(created.uri.toString(), created.name ?: name, false)
        }

    /** True if the document still exists (used to detect deleted notes). */
    suspend fun exists(documentUri: String): Boolean =
        withContext(Dispatchers.IO) {
            DocumentFile.fromSingleUri(context, Uri.parse(documentUri))?.exists() ?: false
        }
}
