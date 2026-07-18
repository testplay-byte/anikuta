package app.anikuta.backup

import android.content.Context
import android.net.Uri
import app.anikuta.backup.format.anikuta.AnikutaCodec
import app.anikuta.backup.format.anikuta.AnikutaCollector
import app.anikuta.backup.format.anikuta.AnikutaRestorer
import app.anikuta.backup.format.anikuta.AnikutaBackup
import app.anikuta.backup.format.anikuta.RestoreOptions
import app.anikuta.core.util.system.logcat
import app.anikuta.data.cache.ExtensionLinkStore
import app.anikuta.data.cache.ReleaseTrackingStore
import app.anikuta.data.cache.SubDubStore
import app.anikuta.player.PlaybackStateStore
import app.anikuta.player.WatchProgressStore
import app.anikuta.ui.library.CategoryStore
import app.anikuta.ui.library.LibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The backup orchestrator facade. Creates and restores backups in two formats:
 *  - **AniKuta format** (`.anikuta`) — JSON with magic header, our own complete format.
 *  - **Aniyomi format** (`.tachibk`) — gzip+protobuf, aniyomi-compatible.
 *
 * ## Modular architecture (Phase 2 refactor)
 * This class is now a **thin facade** — it delegates to focused modules:
 *  - [AnikutaCollector] — gathers app state → [AnikutaBackup]
 *  - [AnikutaRestorer]  — applies [AnikutaBackup] → app state (fixes bugs #1–#5)
 *  - [AnikutaCodec]     — serializes/deserializes the `.anikuta` format
 *  - [BackupFormatDetector] — auto-detects format from bytes
 *
 * The aniyomi format still uses the legacy `AniyomiBackup` / `convertToAniyomiFormat`
 * / `restoreAniyomiBackup` code — these will be rewritten in Phase 4 (modern proto
 * schema 500-506) and Phase 5 (real restore with AniList linking).
 *
 * ## Public API
 *  - [createAnikutaBackup] — export to `.anikuta`
 *  - [createAniyomiBackup] — export to `.tachibk` (legacy impl until Phase 4)
 *  - [restoreBackup]       — import (auto-detects format)
 *  - [peekBackup]          — preview a backup without restoring (Phase 3)
 *
 * ## Restore flow (Phase 3+)
 * The full 4-step restore flow (decode → summary → restore-progress → review)
 * is orchestrated by the UI (BackupSettingsScreen), which calls [peekBackup]
 * for Step 1-2 and [restoreBackup] for Step 3.
 */
class BackupManager(
    private val context: Context,
    private val libraryStore: LibraryStore,
    private val watchProgressStore: WatchProgressStore,
    private val categoryStore: CategoryStore,
    private val releaseTrackingStore: ReleaseTrackingStore,
    private val subDubStore: SubDubStore,
    private val extensionLinkStore: ExtensionLinkStore,
    private val playbackStateStore: PlaybackStateStore,
) {

    companion object {
        private const val TAG = "BackupManager"
    }

    /**
     * Lazily-initialized collector. Uses Injekt to get [PreferenceCollector] (registered in DI).
     * Lazy because the DI may not be fully initialized at BackupManager construction time.
     */
    private val collector: AnikutaCollector by lazy {
        AnikutaCollector(
            libraryStore = libraryStore,
            watchProgressStore = watchProgressStore,
            categoryStore = categoryStore,
            releaseTrackingStore = releaseTrackingStore,
            subDubStore = subDubStore,
            extensionLinkStore = extensionLinkStore,
            playbackStateStore = playbackStateStore,
            preferenceCollector = Injekt.get(),
        )
    }

    /**
     * Lazily-initialized restorer. Uses Injekt to get [PreferenceRestorer].
     */
    private val restorer: AnikutaRestorer by lazy {
        AnikutaRestorer(
            libraryStore = libraryStore,
            watchProgressStore = watchProgressStore,
            categoryStore = categoryStore,
            releaseTrackingStore = releaseTrackingStore,
            subDubStore = subDubStore,
            extensionLinkStore = extensionLinkStore,
            playbackStateStore = playbackStateStore,
            preferenceRestorer = Injekt.get(),
        )
    }

    // =========================================================================
    // CREATE (export)
    // =========================================================================

    /**
     * Create a backup in AniKuta format (`.anikuta`).
     * Writes to the given output Uri (e.g. a SAF document URI).
     *
     * @return `true` on success, `false` on failure (error logged).
     */
    suspend fun createAnikutaBackup(outputUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val backup = collector.collect()
            context.contentResolver.openOutputStream(outputUri)?.use { output ->
                AnikutaCodec.write(backup, output)
            }
            logcat(LogPriority.DEBUG) {
                "✓ AniKuta backup created: ${backup.library.size} anime, ${backup.history.size} history, ${backup.settings.size} prefs"
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "❌ Failed to create AniKuta backup" }
            false
        }
    }

    /**
     * Create a backup in Aniyomi format (`.tachibk` — gzip+protobuf).
     *
     * **Phase 2 status:** Still uses the legacy `AniyomiBackup` / `convertToAniyomiFormat`
     * code with the WRONG proto field numbers (legacy 3/4/103 instead of modern
     * 500/501/502/503). This will be rewritten in Phase 4 to use the modern schema
     * so aniyomi can actually read our backups.
     *
     * @return `true` on success, `false` on failure (error logged).
     */
    suspend fun createAniyomiBackup(outputUri: Uri): Boolean = withContext(Dispatchers.IO) {
        // Phase 4 will replace this with AniyomiExporter.export(collector.collect(), outputUri)
        try {
            val anikutaBackup = collector.collect()
            val aniyomiBackup = convertToAniyomiFormat(anikutaBackup)
            context.contentResolver.openOutputStream(outputUri)?.use { output ->
                BackupFormatDetector.writeAniyomi(aniyomiBackup, output)
            }
            logcat(LogPriority.DEBUG) {
                "✓ Aniyomi backup created (LEGACY schema — Phase 4 will modernize): ${aniyomiBackup.backupAnime.size} anime"
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "❌ Failed to create Aniyomi backup" }
            false
        }
    }

    // =========================================================================
    // RESTORE (import)
    // =========================================================================

    /**
     * Restore a backup from an input Uri. Auto-detects the format.
     *
     * Reads all bytes ONCE into memory (ContentResolver streams don't support
     * mark/reset), then dispatches to the format-specific restorer.
     *
     * For AniKuta format: delegates to [AnikutaRestorer] (fixes bugs #1–#5).
     * For Aniyomi format: uses the legacy stub `restoreAniyomiBackup` (Phase 5
     * will replace with real AniList-linking restore).
     *
     * @return a [RestoreResult] (legacy sealed class for UI compat).
     */
    suspend fun restoreBackup(inputUri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(inputUri)?.use { it.readBytes() }
                ?: return@withContext RestoreResult.Error("Could not open file")

            if (bytes.isEmpty()) {
                return@withContext RestoreResult.Error("Backup file is empty")
            }

            logcat(LogPriority.DEBUG) {
                "Restore: read ${bytes.size} bytes, first 8: ${String(bytes, 0, minOf(8, bytes.size), Charsets.UTF_8)}"
            }

            val format = BackupFormatDetector.detect(bytes)
            logcat(LogPriority.DEBUG) { "Restore: detected format = $format" }

            when (format) {
                BackupFormatDetector.Format.ANIKUTA -> {
                    val backup = AnikutaCodec.read(bytes)
                    if (backup != null) {
                        logcat(LogPriority.DEBUG) {
                            "Restore: AniKuta backup v${backup.version} parsed — ${backup.library.size} anime, ${backup.history.size} history, ${backup.settings.size} prefs"
                        }
                        val result = restorer.restore(backup)
                        RestoreResult.Success(
                            libraryCount = result.libraryCount,
                            historyCount = result.historyCount,
                            searchCount = result.searchCount,
                            categoryCount = result.categoryCount,
                            note = if (result.errors.isEmpty()) null
                                   else "${result.errors.size} errors (see logcat: tag AnikutaRestorer)",
                        )
                    } else {
                        RestoreResult.Error("Could not parse AniKuta backup (JSON decode failed)")
                    }
                }
                BackupFormatDetector.Format.ANIYOMI -> {
                    val backup = BackupFormatDetector.readAniyomi(bytes)
                    if (backup != null) {
                        logcat(LogPriority.DEBUG) {
                            "Restore: Aniyomi backup parsed — ${backup.backupAnime.size} anime (LEGACY stub restore — Phase 5 will implement real restore)"
                        }
                        restoreAniyomiBackup(backup)
                    } else {
                        RestoreResult.Error("Could not parse Aniyomi backup (protobuf decode failed)")
                    }
                }
                BackupFormatDetector.Format.UNKNOWN -> {
                    RestoreResult.Error("Unknown backup format (first 8 bytes: ${String(bytes, 0, minOf(8, bytes.size), Charsets.UTF_8)})")
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "❌ Restore failed" }
            RestoreResult.Error(e.message ?: "Restore failed")
        }
    }

    // =========================================================================
    // LEGACY: Aniyomi format (Phase 4 will rewrite the schema,
    //         Phase 5 will implement real restore)
    // =========================================================================

    private fun convertToAniyomiFormat(backup: AnikutaBackup): AniyomiBackup {
        // Phase 4 will replace this with AniyomiExporter
        val animeList = backup.library.map { libAnime ->
            AniyomiBackupAnime(
                source = 0,
                url = "anilist:${libAnime.id}",
                title = libAnime.titleEnglish ?: libAnime.titleRomaji ?: "Unknown",
                description = libAnime.description,
                genre = libAnime.genres ?: emptyList(),
                status = 0,
                thumbnailUrl = libAnime.coverLarge ?: libAnime.coverMedium,
                dateAdded = System.currentTimeMillis(),
                favorite = true,
                episodes = emptyList(),
                history = backup.history
                    .filter { it.key.startsWith("${libAnime.id}:") }
                    .map { hist ->
                        AniyomiBackupHistory(
                            url = hist.key.substringAfter(":"),
                            lastRead = hist.updatedAt,
                            readDuration = hist.positionSeconds.toLong(),
                        )
                    },
                categories = backup.categories.assignments[libAnime.id.toString()] ?: emptyList(),
            )
        }

        val categories = backup.categories.categories.map { cat ->
            AniyomiBackupCategory(name = cat.name, order = cat.order.toLong())
        }

        // Note: settings are now List<BackupPreference> (typed), but the legacy
        // AniyomiBackupPreference uses bare String. Phase 4 will use the shared
        // PreferenceValue sealed type for real interop. For now, we skip prefs
        // in aniyomi export (better to emit nothing than wrong-type data).
        return AniyomiBackup(
            backupAnime = animeList,
            backupAnimeCategories = categories,
            backupPreferences = emptyList(),
        )
    }

    private suspend fun restoreAniyomiBackup(backup: AniyomiBackup): RestoreResult {
        // Phase 5 will replace this with AniyomiImporter (real restore + AniList linking)
        var libraryCount = 0
        var historyCount = 0

        for (anime in backup.backupAnime) {
            try {
                libraryCount++
                historyCount += anime.history.size
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not restore aniyomi anime" }
            }
        }

        return RestoreResult.Success(
            libraryCount = libraryCount,
            historyCount = historyCount,
            searchCount = 0,
            categoryCount = backup.backupAnimeCategories.size,
            note = "Aniyomi format restore is not yet fully implemented (Phase 5). " +
                   "Found $libraryCount anime, $historyCount history entries. " +
                   "No data was written.",
        )
    }

    // =========================================================================
    // Result type (legacy sealed class — kept for UI compat until Phase 3
    // introduces the new RestoreResult + preview flow)
    // =========================================================================

    sealed class RestoreResult {
        data class Success(
            val libraryCount: Int,
            val historyCount: Int,
            val searchCount: Int,
            val categoryCount: Int,
            val note: String? = null,
        ) : RestoreResult()

        data class Error(val message: String) : RestoreResult()
    }
}
