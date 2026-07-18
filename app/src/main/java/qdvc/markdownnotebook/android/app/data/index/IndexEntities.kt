package qdvc.markdownnotebook.android.app.data.index

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per markdown note across all workspaces. This is the metadata +
 * content table; full-text search over bodies is served by [NoteFtsEntity],
 * which Room keeps in sync as an FTS4 "shadow" of this table.
 *
 * The whole index is a pure cache in the app's private storage — it never
 * touches the workspace folders. A note is identified by (workspaceUri, docId);
 * [rowId] is the integer key the FTS table joins against.
 */
@Entity(
    tableName = "indexed_notes",
    indices = [
        Index(value = ["workspaceUri"]),
        Index(value = ["workspaceUri", "docId"], unique = true),
    ],
)
data class IndexedNoteEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val workspaceUri: String,
    val docId: String,
    val documentUri: String,
    val displayName: String,
    val relativePath: String,
    /** Last-modified time (epoch millis) recorded when the body was indexed. */
    val lastModified: Long,
    val size: Long,
    /** The full note body, used for snippets and mirrored into the FTS table. */
    val content: String,
)

/**
 * FTS4 shadow table over [IndexedNoteEntity.content]. Room generates the
 * virtual table and the triggers that keep it in sync with the content table,
 * so we only ever write to [IndexedNoteEntity]; searches join the two on rowid.
 *
 * Room supports FTS3/FTS4 (FTS5 is unavailable on the SQLite shipped with older
 * Android versions, so Room does not support it). FTS4 provides the same
 * inverted-index matching and ranking we need and scales to large workspaces.
 */
@Fts4(contentEntity = IndexedNoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val content: String,
)
