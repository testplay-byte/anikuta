package app.anikuta.backup.model

/**
 * A pre-restore preview of a backup file — shown to the user in Step 2
 * (the summary/confirm screen) BEFORE any data is written.
 *
 * Produced by [app.anikuta.backup.validator.BackupValidator.peekBackup].
 * Contains everything the user needs to decide whether to proceed:
 *  - what format + when it was created
 *  - how much data it contains (per section)
 *  - what warnings exist (missing sources, anime needing manual link)
 *  - what options the user can toggle before restoring
 *
 * This is the "decode without writing" result — it reads the backup file,
 * parses it, and inspects the contents, but does NOT modify any app state.
 *
 * @property format        the detected backup format.
 * @property createdAt     when the backup was created (epoch ms), or 0 if unknown.
 * @property appVersion    the app version that created the backup, or "" if unknown.
 * @property schemaVersion the backup schema version (e.g. AniKuta v2), or 0 if N/A.
 * @property libraryCount  anime in the library section.
 * @property historyCount  watch-progress entries.
 * @property categoryCount categories.
 * @property trackingCount release-tracking entries (notifications).
 * @property subDubCount   sub/dub cache entries.
 * @property extensionLinkCount  extension→AniList links.
 * @property playbackStateCount  playback-state entries.
 * @property searchCount   recent-search terms.
 * @property preferenceCount  user preferences.
 * @property mangaCount    manga entries (always 0 for anikuta format; may be >0
 *   for aniyomi format — these are SKIPPED on restore since anikuta is anime-only).
 * @property missingSources  extension source names in the backup that aren't
 *   installed on this device (aniyomi format only).
 * @property estimatedUnlinkedCount  rough estimate of how many anime will need
 *   manual linking (Tier 1-2 only in preview; Tier 3 fuzzy runs during restore).
 * @property warnings      human-readable warning strings for the UI.
 * @property parseError    if non-null, the backup couldn't be parsed and this
 *   is the error message. All count fields are 0.
 */
data class BackupSummary(
    val format: BackupFormat,
    val createdAt: Long = 0L,
    val appVersion: String = "",
    val schemaVersion: Int = 0,
    val libraryCount: Int = 0,
    val historyCount: Int = 0,
    val categoryCount: Int = 0,
    val trackingCount: Int = 0,
    val subDubCount: Int = 0,
    val extensionLinkCount: Int = 0,
    val playbackStateCount: Int = 0,
    val searchCount: Int = 0,
    val preferenceCount: Int = 0,
    val mangaCount: Int = 0,
    val missingSources: List<String> = emptyList(),
    val estimatedUnlinkedCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val parseError: String? = null,
) {
    /** `true` if the backup could be parsed (no fatal error). */
    val isParseable: Boolean get() = parseError == null

    /** Total number of restorable items (excluding manga, which is skipped). */
    val totalRestorableItems: Int
        get() = libraryCount + historyCount + categoryCount + trackingCount +
            subDubCount + extensionLinkCount + playbackStateCount + searchCount + preferenceCount
}

/** The detected backup file format. */
enum class BackupFormat {
    /** AniKuta's own format (`.anikuta`, JSON + magic header). */
    ANIKUTA,

    /** Aniyomi-compatible format (`.tachibk` / `.json.gz`, gzip+protobuf). */
    ANIYOMI,

    /** Could not be parsed as either format. */
    UNKNOWN,
}
