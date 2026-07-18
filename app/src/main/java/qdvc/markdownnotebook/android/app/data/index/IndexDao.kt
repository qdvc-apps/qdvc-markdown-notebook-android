package qdvc.markdownnotebook.android.app.data.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Lightweight (docId, lastModified) pair used by the reconciliation pass. */
data class NoteStamp(val docId: String, val lastModified: Long)

/** Projection returned by the full-text search query. */
data class SearchRow(
    val docId: String,
    val documentUri: String,
    val displayName: String,
    val relativePath: String,
    val snippet: String,
)

/** Projection for the all-notes list (no body needed). */
data class NoteRow(
    val docId: String,
    val documentUri: String,
    val displayName: String,
    val relativePath: String,
)

@Dao
interface IndexDao {

    // ---- All-notes list -----------------------------------------------------

    @Query(
        "SELECT docId, documentUri, displayName, relativePath FROM indexed_notes " +
            "WHERE workspaceUri = :workspaceUri ORDER BY displayName COLLATE NOCASE"
    )
    suspend fun listNotes(workspaceUri: String): List<NoteRow>

    // ---- Full-text search ---------------------------------------------------
    //
    // Joins the metadata table with its FTS4 shadow on rowid and matches the
    // query against note bodies. snippet() returns a short excerpt around the
    // match with matched terms wrapped in the given delimiters; we strip those
    // in code. Results are ordered by FTS relevance (matchinfo-based rank is
    // overkill here; the default rowid order after MATCH is acceptable, but we
    // bias exact display-name matches to the top in code).

    @Query(
        "SELECT n.docId AS docId, n.documentUri AS documentUri, n.displayName AS displayName, " +
            "n.relativePath AS relativePath, " +
            "snippet(notes_fts, '\u0002', '\u0003', '…', -1, 40) AS snippet " +
            "FROM indexed_notes n JOIN notes_fts ON n.rowId = notes_fts.rowid " +
            "WHERE n.workspaceUri = :workspaceUri AND notes_fts MATCH :ftsQuery"
    )
    suspend fun searchBody(workspaceUri: String, ftsQuery: String): List<SearchRow>

    // Title-only fallback for matches that appear in the file name but not the
    // body (FTS only indexes the body).
    @Query(
        "SELECT docId, documentUri, displayName, relativePath FROM indexed_notes " +
            "WHERE workspaceUri = :workspaceUri AND displayName LIKE :like COLLATE NOCASE " +
            "ORDER BY displayName COLLATE NOCASE"
    )
    suspend fun searchTitles(workspaceUri: String, like: String): List<NoteRow>

    // ---- Reconciliation -----------------------------------------------------

    @Query("SELECT docId, lastModified FROM indexed_notes WHERE workspaceUri = :workspaceUri")
    suspend fun stamps(workspaceUri: String): List<NoteStamp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: IndexedNoteEntity)

    @Query("DELETE FROM indexed_notes WHERE workspaceUri = :workspaceUri AND docId = :docId")
    suspend fun deleteByDocId(workspaceUri: String, docId: String)

    @Query("DELETE FROM indexed_notes WHERE workspaceUri = :workspaceUri")
    suspend fun clearWorkspace(workspaceUri: String)

    @Query("SELECT COUNT(*) FROM indexed_notes WHERE workspaceUri = :workspaceUri")
    suspend fun count(workspaceUri: String): Int

    /**
     * Updates a single note's body (and its FTS shadow, via Room triggers) by
     * matching on its document URI. Returns the number of rows changed, so the
     * caller can tell whether the note was actually part of an index.
     */
    @Query(
        "UPDATE indexed_notes SET content = :content, lastModified = :lastModified, size = :size " +
            "WHERE documentUri = :documentUri"
    )
    suspend fun updateContentByUri(
        documentUri: String,
        content: String,
        lastModified: Long,
        size: Long,
    ): Int

    // ---- Per-workspace index metadata --------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: IndexMetaEntity)

    @Query("SELECT * FROM index_meta WHERE workspaceUri = :workspaceUri LIMIT 1")
    suspend fun meta(workspaceUri: String): IndexMetaEntity?
}
