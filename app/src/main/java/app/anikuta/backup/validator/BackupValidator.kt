package app.anikuta.backup.validator

import android.content.Context
import android.net.Uri
import app.anikuta.backup.BackupFormatDetector
import app.anikuta.backup.format.anikuta.AnikutaCodec
import app.anikuta.backup.format.anikuta.AnikutaBackup
import app.anikuta.backup.model.BackupFormat
import app.anikuta.backup.model.BackupSummary
import app.anikuta.core.util.system.logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority

/**
 * Pre-restore validator + preview generator.
 *
 * Decodes a backup file **without writing anything** — produces a [BackupSummary]
 * that the UI shows to the user in Step 2 of the restore flow (the
 * summary/confirm screen) BEFORE any data is restored.
 *
 * ## What it checks
 *  - Format detection (anikuta / aniyomi / unknown).
 *  - Decodability (can we parse it?).
 *  - Per-section content counts (anime, history, categories, tracking, etc.).
 *  - Missing sources (aniyomi format — which extension sources in the backup
 *    aren't installed on this device).
 *  - Estimated unlinked-anime count (anime without AniList tracking — rough
 *    estimate; the real fuzzy-match linking runs during restore in Phase 5).
 *  - Warnings (e.g. manga data present → will be skipped).
 *
 * ## Usage
 * ```
 * val summary = BackupValidator(context).peekBackup(uri)
 * if (summary.isParseable) {
 *     // show RestorePreviewDialog with summary
 * } else {
 *     // show error: summary.parseError
 * }
 * ```
 *
 * ## Thread safety
 * All methods are safe to call from any thread. Uses [Dispatchers.IO] for file reads.
 */
class BackupValidator(
    private val context: Context,
) {

    companion object {
        private const val TAG = "BackupValidator"
    }

    /**
     * Read + decode a backup file without restoring anything.
     *
     * @param uri the backup file URI (from SAF OpenDocument).
     * @return a [BackupSummary] describing the backup's contents, or a summary
     *   with [BackupSummary.parseError] set if the file couldn't be parsed.
     */
    suspend fun peekBackup(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext BackupSummary(
                    format = BackupFormat.UNKNOWN,
                    parseError = "Could not open file",
                )

            if (bytes.isEmpty()) {
                return@withContext BackupSummary(
                    format = BackupFormat.UNKNOWN,
                    parseError = "Backup file is empty",
                )
            }

            logcat(LogPriority.DEBUG) {
                "peekBackup: ${bytes.size} bytes, first 8: ${String(bytes, 0, minOf(8, bytes.size), Charsets.UTF_8)}"
            }

            val format = BackupFormatDetector.detect(bytes)
            when (format) {
                BackupFormatDetector.Format.ANIKUTA -> peekAnikuta(bytes)
                BackupFormatDetector.Format.ANIYOMI -> peekAniyomi(bytes)
                BackupFormatDetector.Format.UNKNOWN -> BackupSummary(
                    format = BackupFormat.UNKNOWN,
                    parseError = "Unknown backup format (first 8 bytes: ${
                        String(bytes, 0, minOf(8, bytes.size), Charsets.UTF_8)
                    })",
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "peekBackup failed" }
            BackupSummary(
                format = BackupFormat.UNKNOWN,
                parseError = e.message ?: "Failed to read backup file",
            )
        }
    }

    /**
     * Peek an AniKuta-format backup.
     */
    private fun peekAnikuta(bytes: ByteArray): BackupSummary {
        val backup = AnikutaCodec.read(bytes)
            ?: return BackupSummary(
                format = BackupFormat.ANIKUTA,
                parseError = "Could not parse AniKuta backup (JSON decode failed)",
            )

        val warnings = mutableListOf<String>()

        // Warn about manga (anikuta never has manga, but be defensive)
        // (AnikutaBackup has no manga field, so this is always 0.)

        // Warn if version is newer than current
        if (backup.version > AnikutaBackup.CURRENT_VERSION) {
            warnings.add("Backup is version ${backup.version} (this app supports up to ${AnikutaBackup.CURRENT_VERSION}). " +
                "Some data may be lost on restore.")
        }

        // Count anime that would need manual linking (anikuta format has AniList IDs directly, so 0)
        val estimatedUnlinked = 0

        return BackupSummary(
            format = BackupFormat.ANIKUTA,
            createdAt = backup.createdAt,
            appVersion = backup.appVersion,
            schemaVersion = backup.version,
            libraryCount = backup.library.size,
            historyCount = backup.history.size,
            categoryCount = backup.categories.categories.size,
            trackingCount = backup.releaseTracking.size,
            subDubCount = backup.subDubCache.size,
            extensionLinkCount = backup.extensionLinks.size,
            playbackStateCount = backup.playbackStates.size,
            searchCount = backup.recentSearches.size,
            preferenceCount = backup.settings.size,
            mangaCount = 0,
            missingSources = emptyList(),
            estimatedUnlinkedCount = estimatedUnlinked,
            warnings = warnings,
        )
    }

    /**
     * Peek an Aniyomi-format backup.
     *
     * Phase 5 will enhance this with real missing-source detection (checking
     * installed extensions) and AniList-tracking-based unlinked estimation.
     * For now, it reports basic counts + manga-skipped warning.
     */
    private fun peekAniyomi(bytes: ByteArray): BackupSummary {
        val backup = BackupFormatDetector.readAniyomi(bytes)
            ?: return BackupSummary(
                format = BackupFormat.ANIYOMI,
                parseError = "Could not parse Aniyomi backup (protobuf decode failed)",
            )

        val warnings = mutableListOf<String>()

        // Manga data — will be skipped (anikuta is anime-only)
        val mangaCount = backup.backupManga.size
        if (mangaCount > 0) {
            warnings.add("$mangaCount manga entries found — manga is not supported and will be skipped.")
        }

        // Estimate unlinked anime: anime without AniList tracking (syncId=2)
        // Phase 5 will do real Tier-1/2/3 linking; this is a rough preview estimate.
        val animeWithoutTracking = backup.backupAnime.count { anime ->
            anime.tracking.none { it.syncId == 2 } // syncId 2 = AniList
        }

        // Missing sources — Phase 5 will check against installed extensions.
        // For now, just list the source names from the backup.
        val sourceNames = backup.backupAnimeSources.map { it.name }.filter { it.isNotEmpty() }

        return BackupSummary(
            format = BackupFormat.ANIYOMI,
            createdAt = 0L, // aniyomi format has no top-level timestamp
            appVersion = "",
            schemaVersion = 0,
            libraryCount = backup.backupAnime.size,
            historyCount = backup.backupAnime.sumOf { it.history.size },
            categoryCount = backup.backupAnimeCategories.size,
            trackingCount = backup.backupAnime.sumOf { it.tracking.size },
            subDubCount = 0,
            extensionLinkCount = 0,
            playbackStateCount = 0,
            searchCount = 0,
            preferenceCount = backup.backupPreferences.size,
            mangaCount = mangaCount,
            missingSources = sourceNames,
            estimatedUnlinkedCount = animeWithoutTracking,
            warnings = warnings,
        )
    }
}
