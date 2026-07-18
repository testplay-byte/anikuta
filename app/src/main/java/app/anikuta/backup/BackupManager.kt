package app.anikuta.backup

import android.content.Context
import android.net.Uri
import app.anikuta.backup.format.anikuta.AnikutaCodec
import app.anikuta.backup.format.anikuta.AnikutaCollector
import app.anikuta.backup.format.anikuta.AnikutaRestorer
import app.anikuta.backup.format.anikuta.AnikutaBackup
import app.anikuta.backup.format.anikuta.RestoreOptions
import app.anikuta.backup.format.aniyomi.AniyomiCodec
import app.anikuta.backup.format.aniyomi.AniyomiExporter
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
     * Create a backup in Aniyomi format (`.tachibk` — gzip+protobuf, modern schema).
     *
     * Uses [AniyomiExporter] to convert the AniKuta backup to aniyomi's modern
     * protobuf format (fields 500-506, `isLegacy = false`), then [AniyomiCodec]
     * to gzip+protobuf-encode it. The result is readable by aniyomi.
     *
     * Emits a [BackupAnimeTracking] with `syncId=2` (AniList) + `mediaId=<anilistId>`
     * for every anime, so aniyomi can auto-link to AniList and anikuta can use
     * Tier 1 linking when re-importing.
     *
     * @return `true` on success, `false` on failure (error logged).
     */
    suspend fun createAniyomiBackup(outputUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val anikutaBackup = collector.collect()
            val aniyomiBackup = AniyomiExporter().export(anikutaBackup)
            context.contentResolver.openOutputStream(outputUri)?.use { output ->
                AniyomiCodec.write(aniyomiBackup, output)
            }
            logcat(LogPriority.DEBUG) {
                "✓ Aniyomi backup created (modern schema): ${aniyomiBackup.backupAnime.size} anime, " +
                    "${aniyomiBackup.backupAnimeSources.size} sources, isLegacy=${aniyomiBackup.isLegacy}"
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "❌ Failed to create Aniyomi backup" }
            false
        }
    }

    /** Lazily-initialized aniyomi exporter. */
    private val aniyomiExporter: AniyomiExporter by lazy { AniyomiExporter() }

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
                    val backup = AniyomiCodec.read(bytes)
                    if (backup != null) {
                        logcat(LogPriority.DEBUG) {
                            "Restore: Aniyomi backup parsed (isLegacy=${backup.isLegacy}) — " +
                                "${backup.backupAnime.size} anime, ${backup.backupManga.size} manga (skipped)"
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

    /**
     * Restore a backup with [RestoreOptions] and live progress callbacks.
     *
     * This is the enhanced restore entry point used by the Step 3 live-progress
     * UI (RestoreProgressScreen). It accepts per-section options (from the
     * RestorePreviewDialog) and emits progress events as the restore proceeds.
     *
     * @param inputUri the backup file URI.
     * @param options which sections to restore (from the preview dialog).
     * @param onProgress called with [RestoreProgress] events as restore proceeds.
     *   Called on Dispatchers.IO — the UI should marshal to the main thread.
     * @return a [RestoreResult] (legacy sealed class for UI compat).
     */
    suspend fun restoreBackupWithOptions(
        inputUri: Uri,
        options: RestoreOptions,
        onProgress: (RestoreProgress) -> Unit,
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            onProgress(RestoreProgress.Decoding("Reading backup file..."))
            val bytes = context.contentResolver.openInputStream(inputUri)?.use { it.readBytes() }
                ?: return@withContext RestoreResult.Error("Could not open file")

            if (bytes.isEmpty()) {
                return@withContext RestoreResult.Error("Backup file is empty")
            }

            val format = BackupFormatDetector.detect(bytes)
            onProgress(RestoreProgress.Decoding("Detected format: $format"))

            when (format) {
                BackupFormatDetector.Format.ANIKUTA -> {
                    val backup = AnikutaCodec.read(bytes)
                    if (backup != null) {
                        onProgress(RestoreProgress.Restoring(
                            total = backup.library.size + backup.history.size,
                            current = 0,
                            message = "Restoring AniKuta backup (v${backup.version})...",
                        ))
                        val result = restorer.restore(backup, options)
                        onProgress(RestoreProgress.Complete(
                            summary = "Restored ${result.libraryCount} anime, ${result.historyCount} history, ${result.preferenceCount} prefs",
                        ))
                        RestoreResult.Success(
                            libraryCount = result.libraryCount,
                            historyCount = result.historyCount,
                            searchCount = result.searchCount,
                            categoryCount = result.categoryCount,
                            note = if (result.errors.isEmpty()) null
                                   else "${result.errors.size} errors (see logcat)",
                        )
                    } else {
                        RestoreResult.Error("Could not parse AniKuta backup")
                    }
                }
                BackupFormatDetector.Format.ANIYOMI -> {
                    val backup = AniyomiCodec.read(bytes)
                    if (backup != null) {
                        onProgress(RestoreProgress.Restoring(
                            total = backup.backupAnime.size,
                            current = 0,
                            message = "Aniyomi restore (Phase 5 will implement full linking)...",
                        ))
                        restoreAniyomiBackup(backup)
                    } else {
                        RestoreResult.Error("Could not parse Aniyomi backup")
                    }
                }
                BackupFormatDetector.Format.UNKNOWN -> {
                    RestoreResult.Error("Unknown backup format")
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "❌ Restore (with options) failed" }
            RestoreResult.Error(e.message ?: "Restore failed")
        }
    }

    // =========================================================================
    // Aniyomi format restore (Phase 5: real restore with AniList linking)
    // =========================================================================

    /** Lazily-initialized aniyomi importer. Uses Injekt for its deps. */
    private val aniyomiImporter: app.anikuta.backup.format.aniyomi.AniyomiImporter by lazy {
        app.anikuta.backup.format.aniyomi.AniyomiImporter(
            anilistRepository = Injekt.get(),
            extensionLinkStore = extensionLinkStore,
            libraryStore = libraryStore,
            watchProgressStore = watchProgressStore,
            playbackStateStore = playbackStateStore,
            categoryStore = categoryStore,
            preferenceRestorer = Injekt.get(),
        )
    }

    private suspend fun restoreAniyomiBackup(
        backup: app.anikuta.backup.format.aniyomi.AniyomiBackup,
    ): RestoreResult {
        val result = aniyomiImporter.restore(backup, RestoreOptions.ALL) { msg ->
            logcat(LogPriority.DEBUG) { "Aniyomi restore: $msg" }
        }
        return RestoreResult.Success(
            libraryCount = result.libraryCount,
            historyCount = result.historyCount,
            searchCount = result.searchCount,
            categoryCount = result.categoryCount,
            note = buildString {
                if (result.unlinkedAnime.isNotEmpty()) {
                    append("${result.unlinkedAnime.size} anime could not be auto-linked to AniList. ")
                    append("They will be available for manual linking in the review screen. ")
                }
                if (result.errors.isNotEmpty()) {
                    append("${result.errors.size} errors (see logcat: tag AniyomiImporter). ")
                }
                result.note?.let { append(it) }
            }.ifBlank { null },
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
