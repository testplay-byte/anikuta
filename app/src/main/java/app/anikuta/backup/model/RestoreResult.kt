package app.anikuta.backup.model

/**
 * The result of a restore operation — reported back to the UI.
 *
 * This is the **post-restore** result (Step 4 of the restore flow). It
 * contains per-section counts of what was restored, plus any errors and
 * the list of anime that couldn't be auto-linked to AniList (for the
 * post-restore review screen).
 *
 * Replaces the old `BackupManager.RestoreResult` sealed class (which only
 * had 4 counts + a note). This version is exhaustive so the UI can show
 * a complete summary.
 *
 * @property libraryCount     anime restored to the library.
 * @property historyCount     watch-progress entries restored.
 * @property categoryCount    categories restored.
 * @property categoryAssignmentCount  anime→category assignments restored.
 * @property trackingCount    release-tracking entries restored (notifications).
 * @property subDubCount      sub/dub cache entries restored.
 * @property extensionLinkCount  extension→AniList links restored.
 * @property playbackStateCount  playback-state entries restored (resume memory).
 * @property searchCount      recent-search terms restored.
 * @property preferenceCount  user preferences restored.
 * @property unlinkedAnime    anime that couldn't be auto-linked to AniList
 *   (for aniyomi-format restores). The user resolves these in Step 4.
 * @property errors           per-entry error messages (empty if none).
 * @property skippedSections  sections the user chose to skip via restore options.
 * @property note             optional human-readable note (e.g. "Manga data skipped").
 * @property durationMs       how long the restore took (for logging/debugging).
 */
data class RestoreResult(
    val libraryCount: Int = 0,
    val historyCount: Int = 0,
    val categoryCount: Int = 0,
    val categoryAssignmentCount: Int = 0,
    val trackingCount: Int = 0,
    val subDubCount: Int = 0,
    val extensionLinkCount: Int = 0,
    val playbackStateCount: Int = 0,
    val searchCount: Int = 0,
    val preferenceCount: Int = 0,
    val unlinkedAnime: List<UnlinkedAnime> = emptyList(),
    val errors: List<String> = emptyList(),
    val skippedSections: Set<String> = emptySet(),
    val note: String? = null,
    val durationMs: Long = 0L,
) {
    /**
     * `true` if the restore completed without fatal errors.
     * Non-fatal per-entry errors are in [errors] but don't make this `false`.
     */
    val isSuccess: Boolean get() = errors.isEmpty() || errors.size < libraryCount + historyCount

    /** A one-line summary suitable for a toast or notification. */
    val summary: String
        get() = buildString {
            append("$libraryCount anime, $historyCount history, $categoryCount categories")
            if (trackingCount > 0) append(", $trackingCount tracked")
            if (preferenceCount > 0) append(", $preferenceCount prefs")
            if (unlinkedAnime.isNotEmpty()) append(", ${unlinkedAnime.size} unlinked")
            if (errors.isNotEmpty()) append(", ${errors.size} errors")
        }
}

/**
 * An anime from an aniyomi backup that couldn't be auto-linked to an AniList ID.
 *
 * The user resolves these in the post-restore review screen (Step 4):
 *  - manually search AniList and link, or
 *  - skip (history preserved, anime not in library), or
 *  - add to library without linking (source-based entry).
 *
 * @property sourceId    the aniyomi extension source ID (Long).
 * @property sourceName  the source's display name (for showing "from Gogoanime" etc.).
 * @property animeUrl    the source-relative URL (SAnime.url).
 * @property title       the anime title (for display + manual search).
 * @property thumbnailUrl optional cover image URL.
 * @property reason      why auto-linking failed.
 * @property pendingHistory  orphaned watch-progress entries (keyed by episodeUrl)
 *   preserved until the anime is linked or skipped.
 * @property categoryNames  category names from the backup that this anime
 *   belonged to (restored when the anime is linked).
 */
data class UnlinkedAnime(
    val sourceId: Long,
    val sourceName: String,
    val animeUrl: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val reason: UnlinkReason = UnlinkReason.NO_MATCH,
    val pendingHistory: List<PendingHistoryEntry> = emptyList(),
    val categoryNames: List<String> = emptyList(),
)

/** Why an anime couldn't be auto-linked to AniList. */
enum class UnlinkReason {
    /** No AniList tracking entry in the backup. */
    NO_TRACKER,

    /** ExtensionLinkStore cache had no match for this source+url. */
    NO_CACHE_MATCH,

    /** Fuzzy title search returned no results. */
    FUZZY_NO_MATCH,

    /** Fuzzy title search returned ambiguous results (needs user pick). */
    FUZZY_AMBIGUOUS,

    /** The extension source isn't installed (can't verify the anime). */
    SOURCE_NOT_INSTALLED,
}

/**
 * An orphaned watch-progress entry for an unlinked anime.
 *
 * Keyed by episodeUrl (not anilistId, since we don't have one yet). When the
 * anime is later linked, these are migrated to WatchProgressStore keyed by
 * `"$anilistId:$episodeUrl"`.
 */
data class PendingHistoryEntry(
    val episodeUrl: String,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val updatedAt: Long,
    val episodeNumber: Float = -1f,
    val episodeName: String? = null,
)
