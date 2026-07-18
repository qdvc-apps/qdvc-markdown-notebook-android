package qdvc.markdownnotebook.android.app.model

/** High-level state of a workspace's on-device index, for the Index Status UI. */
enum class IndexState {
    /** No index rows yet — searches fall back to a live scan. */
    NOT_BUILT,

    /** A reconciliation/rebuild is currently running in the background. */
    BUILDING,

    /** Index is present and up to date (as of the last reconciliation). */
    READY,
}

/**
 * A snapshot of a workspace's index for display: its [state], how many notes
 * are indexed, and when it last finished regenerating (epoch millis, or 0 if
 * never).
 */
data class IndexStatus(
    val state: IndexState = IndexState.NOT_BUILT,
    val noteCount: Int = 0,
    val lastRegenerated: Long = 0L,
)
