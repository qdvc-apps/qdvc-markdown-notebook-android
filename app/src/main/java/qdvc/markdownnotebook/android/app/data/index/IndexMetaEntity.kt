package qdvc.markdownnotebook.android.app.data.index

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-workspace bookkeeping for the index: when it last finished regenerating
 * and how many notes it holds. Keyed by the workspace tree URI.
 */
@Entity(tableName = "index_meta")
data class IndexMetaEntity(
    @PrimaryKey val workspaceUri: String,
    /** Epoch millis when a full reconciliation last completed, or 0 if never. */
    val lastRegenerated: Long,
    val noteCount: Int,
)
