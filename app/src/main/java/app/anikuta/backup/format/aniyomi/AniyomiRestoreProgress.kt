package app.anikuta.backup.format.aniyomi

/**
 * Structured live-progress events emitted by [AniyomiImporter] during restore.
 *
 * Richer than a bare `String` callback — carries `current`/`total` so the UI
 * can show a real progress bar + a scrollable live log of per-anime activity.
 *
 * ## State machine
 * ```
 * SectionStart → AnimeProgress (repeated) → SectionComplete → ... → RestoreComplete
 * ```
 *
 * The UI collects these into a list (for the live log) + uses the latest
 * `AnimeProgress` for the progress bar.
 */
sealed class AniyomiRestoreProgress {
    /** A section (categories, anime, preferences) started. */
    data class SectionStart(val section: String, val total: Int) : AniyomiRestoreProgress()

    /** Per-anime progress: linking/restoring anime #current of #total. */
    data class AnimeProgress(
        val current: Int,
        val total: Int,
        val title: String,
        val status: AnimeStatus,
        val detail: String? = null,
    ) : AniyomiRestoreProgress() {
        val fraction: Float get() = if (total <= 0) 0f else current.toFloat() / total
    }

    /** A section completed. */
    data class SectionComplete(val section: String, val count: Int) : AniyomiRestoreProgress()

    /** Restore fully complete. */
    data class RestoreComplete(val summary: String) : AniyomiRestoreProgress()

    /** An error occurred (non-fatal — restore continues). */
    data class Error(val message: String) : AniyomiRestoreProgress()
}

/** Status of an individual anime during restore. */
enum class AnimeStatus {
    LINKING,         // running the 4-tier linker
    LINKED_TRACKER,  // Tier 1 matched
    LINKED_CACHE,    // Tier 2 matched
    LINKED_FUZZY,    // Tier 3 matched
    UNLINKED,        // all tiers failed → queued for review
    FETCHING_METADATA, // fetching AniList details
    SAVED,           // saved to library
    SKIPPED,         // skipped (error or manga)
}
