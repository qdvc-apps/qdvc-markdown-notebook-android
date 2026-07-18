package qdvc.markdownnotebook.android.app.data

import android.content.Context
import qdvc.markdownnotebook.android.app.data.index.IndexDatabase
import qdvc.markdownnotebook.android.app.data.index.IndexedNoteEntity
import qdvc.markdownnotebook.android.app.data.index.IndexMetaEntity
import qdvc.markdownnotebook.android.app.model.IndexState
import qdvc.markdownnotebook.android.app.model.IndexStatus
import qdvc.markdownnotebook.android.app.model.NoteFile
import qdvc.markdownnotebook.android.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Maintains the on-device full-text index and serves all-notes/search from it.
 *
 * The index is a cache: it never modifies the workspace. Callers should use
 * [search] and [listAllNotes], which return null when no usable index exists
 * yet so the caller can fall back to a live SAF scan. Freshness is maintained
 * by [reconcile] (cheap: walks the tree for timestamps, re-reads only changed
 * files) and by [updateSavedNote] when a note is saved in-app.
 */
class IndexRepository(
    private val context: Context,
    private val noteRepo: NoteRepository,
) {
    private val dao = IndexDatabase.get(context).indexDao()

    // One status flow per workspace, created lazily.
    private val statuses = mutableMapOf<String, MutableStateFlow<IndexStatus>>()
    private val statusLock = Any()

    // Serialises reconciliation per workspace so launch + manual regenerate
    // can't run over each other.
    private val locks = mutableMapOf<String, Mutex>()

    private fun lockFor(workspaceUri: String): Mutex = synchronized(locks) {
        locks.getOrPut(workspaceUri) { Mutex() }
    }

    private fun flowFor(workspaceUri: String): MutableStateFlow<IndexStatus> =
        synchronized(statusLock) {
            statuses.getOrPut(workspaceUri) { MutableStateFlow(IndexStatus()) }
        }

    fun status(workspaceUri: String): StateFlow<IndexStatus> = flowFor(workspaceUri).asStateFlow()

    /** Loads persisted meta into the status flow (called when a workspace opens). */
    suspend fun refreshStatus(workspaceUri: String) {
        val count = dao.count(workspaceUri)
        val meta = dao.meta(workspaceUri)
        val flow = flowFor(workspaceUri)
        // Don't clobber a BUILDING state that a running reconcile owns.
        if (flow.value.state == IndexState.BUILDING) return
        flow.value = IndexStatus(
            state = if (count > 0) IndexState.READY else IndexState.NOT_BUILT,
            noteCount = count,
            lastRegenerated = meta?.lastRegenerated ?: 0L,
        )
    }

    // ---- Reads (null => no index yet, caller should fall back) --------------

    suspend fun listAllNotes(workspaceUri: String): List<NoteFile>? =
        withContext(Dispatchers.IO) {
            if (dao.count(workspaceUri) == 0) return@withContext null
            dao.listNotes(workspaceUri).map {
                NoteFile(it.documentUri, it.displayName, it.docId, it.relativePath)
            }
        }

    suspend fun search(workspaceUri: String, query: String): List<SearchResult>? =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()
            if (dao.count(workspaceUri) == 0) return@withContext null

            val results = LinkedHashMap<String, SearchResult>()

            // Body matches via FTS.
            val ftsQuery = toFtsQuery(q)
            if (ftsQuery.isNotEmpty()) {
                try {
                    for (row in dao.searchBody(workspaceUri, ftsQuery)) {
                        val note = NoteFile(row.documentUri, row.displayName, row.docId, row.relativePath)
                        results[row.docId] = SearchResult(note, cleanSnippet(row.snippet))
                    }
                } catch (e: Exception) {
                    // Malformed FTS expression, etc. — ignore, title search still runs.
                }
            }

            // Title matches (FTS only indexes bodies), de-duplicated against the above.
            for (row in dao.searchTitles(workspaceUri, "%$q%")) {
                if (!results.containsKey(row.docId)) {
                    val note = NoteFile(row.documentUri, row.displayName, row.docId, row.relativePath)
                    results[row.docId] = SearchResult(note, "")
                }
            }

            results.values.sortedByDescending { it.snippet.isNotEmpty() }
        }

    // ---- Maintenance --------------------------------------------------------

    /**
     * Brings the index for [workspaceUri] in line with the workspace on disk.
     * Cheap when little changed: walks the tree for (docId, lastModified) and
     * re-reads bodies only for new or modified files, pruning deletions. Safe
     * to call on launch; never blocks the caller's thread beyond the IO work.
     */
    suspend fun reconcile(workspaceUri: String) = withContext(Dispatchers.IO) {
        val mutex = lockFor(workspaceUri)
        if (mutex.isLocked) return@withContext // a reconcile is already running
        mutex.withLock {
            val flow = flowFor(workspaceUri)
            val existingCount = dao.count(workspaceUri)
            flow.value = flow.value.copy(
                state = IndexState.BUILDING,
                noteCount = existingCount,
            )
            try {
                val scanned = noteRepo.listAllNotesWithMeta(workspaceUri)
                val known = dao.stamps(workspaceUri).associate { it.docId to it.lastModified }
                val seen = HashSet<String>(scanned.size)

                for (note in scanned) {
                    seen.add(note.docId)
                    val prev = known[note.docId]
                    if (prev != null && prev == note.lastModified) continue // unchanged
                    val body = try {
                        noteRepo.readNote(note.documentUri)
                    } catch (e: Exception) {
                        ""
                    }
                    dao.upsert(
                        IndexedNoteEntity(
                            workspaceUri = workspaceUri,
                            docId = note.docId,
                            documentUri = note.documentUri,
                            displayName = note.displayName,
                            relativePath = note.relativePath,
                            lastModified = note.lastModified,
                            size = note.size,
                            content = body,
                        )
                    )
                }

                // Prune notes that no longer exist on disk.
                for (docId in known.keys) {
                    if (docId !in seen) dao.deleteByDocId(workspaceUri, docId)
                }

                val now = System.currentTimeMillis()
                val count = dao.count(workspaceUri)
                dao.putMeta(IndexMetaEntity(workspaceUri, now, count))
                flow.value = IndexStatus(IndexState.READY, count, now)
            } catch (e: Exception) {
                // Leave whatever we had; reflect a best-effort status.
                val count = dao.count(workspaceUri)
                val meta = dao.meta(workspaceUri)
                flow.value = IndexStatus(
                    state = if (count > 0) IndexState.READY else IndexState.NOT_BUILT,
                    noteCount = count,
                    lastRegenerated = meta?.lastRegenerated ?: 0L,
                )
            }
        }
    }

    /** Full rebuild: clears the workspace's rows then reconciles from scratch. */
    suspend fun regenerate(workspaceUri: String) = withContext(Dispatchers.IO) {
        lockFor(workspaceUri).withLock {
            dao.clearWorkspace(workspaceUri)
        }
        reconcile(workspaceUri)
    }

    /**
     * Updates a saved note's indexed body by its document URI, so search
     * reflects an in-app edit immediately without waiting for the next
     * reconcile. No-op if the note isn't part of any index yet.
     */
    suspend fun updateSavedNote(documentUri: String, content: String) =
        withContext(Dispatchers.IO) {
            dao.updateContentByUri(
                documentUri = documentUri,
                content = content,
                lastModified = System.currentTimeMillis(),
                size = content.toByteArray(Charsets.UTF_8).size.toLong(),
            )
            Unit
        }

    // ---- Helpers ------------------------------------------------------------

    /**
     * Turns a user query into a safe FTS4 MATCH expression: each whitespace-
     * separated token becomes a prefix term ("foo*"), AND-ed together. Quotes
     * and FTS operator characters are stripped so user input can't break the
     * query syntax.
     */
    private fun toFtsQuery(query: String): String {
        val tokens = query
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[\"*()^:-]"), "").trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" ") { "$it*" }
    }

    /** Removes the snippet match delimiters we asked SQLite to insert. */
    private fun cleanSnippet(raw: String): String =
        raw.replace("\u0002", "").replace("\u0003", "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
