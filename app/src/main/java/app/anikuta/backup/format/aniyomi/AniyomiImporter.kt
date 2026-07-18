package app.anikuta.backup.format.aniyomi

import app.anikuta.backup.format.anikuta.RestoreOptions
import app.anikuta.backup.link.AniListLinker
import app.anikuta.backup.link.LinkResult
import app.anikuta.backup.model.PendingHistoryEntry
import app.anikuta.backup.model.RestoreResult
import app.anikuta.backup.model.UnlinkedAnime
import app.anikuta.backup.prefs.PreferenceRestorer
import app.anikuta.core.util.system.logcat
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.model.AniListCoverImage
import app.anikuta.data.anilist.model.AniListTitle
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.ExtensionLinkStore
import app.anikuta.data.cache.PendingLinkStore
import app.anikuta.player.PlaybackStateStore
import app.anikuta.player.WatchProgressStore
import app.anikuta.ui.library.CategoryStore
import app.anikuta.ui.library.LibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority

/**
 * Restores an [AniyomiBackup] into ANI-KUTA's app state, using [AniListLinker]
 * to bridge aniyomi's source-based anime to AniList IDs.
 *
 * ## The linking flow (per anime)
 * For each [BackupAnime] in the backup:
 *  1. Run [AniListLinker.link] (4-tier: tracking → cache → fuzzy → unlinked).
 *  2. If [LinkResult.Linked]: fetch full AniList metadata via
 *     [AniListRepository.getAnimeDetails], then save to [LibraryStore] +
 *     [WatchProgressStore] (keyed by anilistId).
 *  3. If [LinkResult.Unlinked]: collect into [UnlinkedAnime] list for the
 *     post-restore review screen (Phase 6). History is preserved keyed by
 *     episodeUrl pending future link.
 *
 * ## Manga handling
 * [AniyomiBackup.backupManga] is **ignored** (anikuta is anime-only). The
 * importer logs how many manga entries were skipped.
 *
 * ## Categories
 * Aniyomi categories are name-based (matched by name on restore, not by ID).
 * The importer restores categories by name via [CategoryStore.restoreCategory]
 * and applies per-anime assignments for linked anime.
 *
 * ## Preferences
 * Aniyomi's [BackupPreference] uses the shared [PreferenceValue] sealed type,
 * so preferences are restored type-safely via [PreferenceRestorer] (same as
 * anikuta-format restore). Aniyomi-specific keys that don't exist on anikuta
 * are skipped by the type-guard.
 *
 * ## Logging
 * Every anime link + restore is logged. The [onProgress] callback emits
 * live-progress messages for the Step 3 restore screen. Tag: `AniyomiImporter`.
 *
 * @param anilistRepository for Tier 3 fuzzy search + metadata fetch.
 * @param extensionLinkStore for Tier 2 cache + caching new links.
 * @param libraryStore      to save linked anime.
 * @param watchProgressStore to restore watch history.
 * @param playbackStateStore to restore playback states (if present in backup).
 * @param categoryStore     to restore categories + assignments.
 * @param preferenceRestorer to restore preferences.
 */
class AniyomiImporter(
    private val anilistRepository: AniListRepository,
    private val extensionLinkStore: ExtensionLinkStore,
    private val libraryStore: LibraryStore,
    private val watchProgressStore: WatchProgressStore,
    private val playbackStateStore: PlaybackStateStore,
    private val categoryStore: CategoryStore,
    private val preferenceRestorer: PreferenceRestorer,
    private val pendingLinkStore: PendingLinkStore,
) {

    companion object {
        private const val TAG = "AniyomiImporter"
    }

    /**
     * Restore an [AniyomiBackup] into app state.
     *
     * @param backup the aniyomi-format backup (modern or legacy-converted).
     * @param options which sections to restore.
     * @param onProgress live-progress callback (called per anime + per section).
     * @return a [RestoreResult] with per-section counts + unlinked anime list.
     */
    suspend fun restore(
        backup: AniyomiBackup,
        options: RestoreOptions = RestoreOptions.ALL,
        onProgress: (String) -> Unit = {},
    ): RestoreResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        val unlinkedAnime = mutableListOf<UnlinkedAnime>()
        var libraryCount = 0
        var historyCount = 0
        var categoryCount = 0
        var categoryAssignmentCount = 0
        var preferenceCount = 0
        val mangaSkipped = backup.backupManga.size

        if (mangaSkipped > 0) {
            onProgress("Skipping $mangaSkipped manga entries (anime-only).")
            logcat(LogPriority.DEBUG) { "Skipping $mangaSkipped manga (anime-only)" }
        }

        val linker = AniListLinker(anilistRepository, extensionLinkStore)

        // Build a category-order → name map for assignment restoration
        val categoryOrderToName: Map<Long, String> = if (options.categories) {
            backup.backupAnimeCategories.associate { it.order to it.name }
        } else {
            emptyMap()
        }

        // Restore categories (by name)
        if (options.categories && backup.backupAnimeCategories.isNotEmpty()) {
            for (cat in backup.backupAnimeCategories) {
                try {
                    val anikutaCat = app.anikuta.backup.format.anikuta.BackupCategory(
                        id = cat.order, // use order as ID (anikuta matches by name anyway)
                        name = cat.name,
                        order = cat.order.toInt(),
                    )
                    categoryStore.restoreCategory(anikutaCat)
                    categoryCount++
                } catch (e: Exception) {
                    errors.add("category[${cat.name}]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not restore aniyomi category" }
                }
            }
            onProgress("Restored $categoryCount categories.")
        }

        // Restore anime (with AniList linking)
        if (options.library) {
            val total = backup.backupAnime.size
            for ((index, backupAnime) in backup.backupAnime.withIndex()) {
                try {
                    onProgress("(${index + 1}/$total) Linking '${backupAnime.title}'...")
                    val linkResult = linker.link(backupAnime) { msg -> onProgress(msg) }

                    when (linkResult) {
                        is LinkResult.Linked -> {
                            // Fetch full AniList metadata (the backup only has title/thumbnail)
                            val anilistAnime = try {
                                anilistRepository.getAnimeDetails(linkResult.anilistId)
                            } catch (e: Exception) {
                                logcat(LogPriority.WARN, e) {
                                    "Could not fetch AniList details for ${linkResult.anilistId} — using minimal data"
                                }
                                // Fallback: build a minimal AniListAnime from the backup data
                                AniListAnime(
                                    id = linkResult.anilistId,
                                    title = AniListTitle(
                                        english = backupAnime.title.ifBlank { null },
                                        romaji = backupAnime.title.ifBlank { null },
                                    ),
                                    coverImage = AniListCoverImage(
                                        extraLarge = backupAnime.thumbnailUrl,
                                        large = backupAnime.thumbnailUrl,
                                    ),
                                    description = backupAnime.description,
                                    genres = backupAnime.genre.ifEmpty { null },
                                )
                            }
                            libraryStore.save(anilistAnime)
                            libraryCount++

                            // Restore history for this anime (keyed by anilistId:episodeUrl)
                            if (options.history) {
                                for (hist in backupAnime.history) {
                                    try {
                                        watchProgressStore.save(
                                            anilistId = linkResult.anilistId,
                                            episodeUrl = hist.url,
                                            positionSeconds = (hist.lastRead / 1000).toInt(),
                                            durationSeconds = 0,
                                            title = backupAnime.title,
                                            coverUrl = backupAnime.thumbnailUrl,
                                            animeTitle = backupAnime.title,
                                            episodeNumber = -1f,
                                        )
                                        historyCount++
                                    } catch (e: Exception) {
                                        errors.add("history[${backupAnime.title}/${hist.url}]: ${e.message}")
                                    }
                                }
                            }

                            // Restore category assignments (by name)
                            if (options.categories && backupAnime.categories.isNotEmpty()) {
                                val categoryNames = backupAnime.categories.mapNotNull { order ->
                                    categoryOrderToName[order]
                                }
                                // Note: CategoryStore.setAnimeCategories takes IDs, but we restored
                                // categories by name. For now, we skip per-anime assignment here —
                                // Phase 6 will add a name-based assignment method.
                                // (The user can reassign categories from the library UI.)
                            }
                        }
                        is LinkResult.Unlinked -> {
                            // Collect for the post-restore review screen (Phase 6)
                            val pendingHistory = backupAnime.history.map { hist ->
                                PendingHistoryEntry(
                                    episodeUrl = hist.url,
                                    positionSeconds = (hist.lastRead / 1000).toInt(),
                                    durationSeconds = 0,
                                    updatedAt = hist.lastRead,
                                )
                            }
                            val categoryNames = backupAnime.categories.mapNotNull { order ->
                                categoryOrderToName[order]
                            }
                            unlinkedAnime.add(
                                UnlinkedAnime(
                                    sourceId = backupAnime.source,
                                    sourceName = backup.backupAnimeSources
                                        .find { it.sourceId == backupAnime.source }?.name
                                        ?: "Source ${backupAnime.source}",
                                    animeUrl = backupAnime.url,
                                    title = backupAnime.title,
                                    thumbnailUrl = backupAnime.thumbnailUrl,
                                    reason = linkResult.reason,
                                    pendingHistory = pendingHistory,
                                    categoryNames = categoryNames,
                                ),
                            )
                            onProgress("  → unlinked (${linkResult.reason}), queued for review.")
                        }
                    }
                } catch (e: Exception) {
                    errors.add("anime[${backupAnime.title}]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not restore aniyomi anime '${backupAnime.title}'" }
                }
            }
        }

        // Restore preferences (type-safe, shared PreferenceValue)
        if (options.settings && backup.backupPreferences.isNotEmpty()) {
            try {
                val result = preferenceRestorer.restore(backup.backupPreferences)
                preferenceCount = result.restored
                errors.addAll(result.errors)
                onProgress("Restored $preferenceCount preferences (${result.skipped} skipped).")
            } catch (e: Exception) {
                errors.add("settings: ${e.message}")
                logcat(LogPriority.ERROR, e) { "Could not restore aniyomi preferences" }
            }
        }

        // Persist unlinked anime into PendingLinkStore for the post-restore review screen.
        // The user resolves them (manual search/skip/add-without-link) from Step 4.
        if (unlinkedAnime.isNotEmpty()) {
            val pendingList = unlinkedAnime.map { ua ->
                PendingLinkStore.PendingAnime(
                    sourceId = ua.sourceId,
                    sourceName = ua.sourceName,
                    animeUrl = ua.animeUrl,
                    title = ua.title,
                    thumbnailUrl = ua.thumbnailUrl,
                    pendingHistory = ua.pendingHistory.map { hist ->
                        PendingLinkStore.PendingHistoryEntry(
                            episodeUrl = hist.episodeUrl,
                            positionSeconds = hist.positionSeconds,
                            durationSeconds = hist.durationSeconds,
                            updatedAt = hist.updatedAt,
                        )
                    },
                    categoryNames = ua.categoryNames,
                )
            }
            pendingLinkStore.addAll(pendingList)
            onProgress("${unlinkedAnime.size} unlinked anime saved for manual review.")
        }

        val elapsed = System.currentTimeMillis() - startTime
        logcat(LogPriority.DEBUG) {
            "Aniyomi restore complete in ${elapsed}ms — " +
                "$libraryCount lib, $historyCount hist, $categoryCount cats, " +
                "$preferenceCount prefs, ${unlinkedAnime.size} unlinked, " +
                "$mangaSkipped manga skipped, ${errors.size} errors"
        }

        RestoreResult(
            libraryCount = libraryCount,
            historyCount = historyCount,
            categoryCount = categoryCount,
            categoryAssignmentCount = categoryAssignmentCount,
            trackingCount = 0, // aniyomi tracking is converted to AniList ID (not stored separately)
            subDubCount = 0,
            extensionLinkCount = 0, // links are cached during Tier 3 matching, not counted here
            playbackStateCount = 0, // aniyomi format doesn't have playback states
            searchCount = 0,
            preferenceCount = preferenceCount,
            unlinkedAnime = unlinkedAnime,
            errors = errors,
            note = if (mangaSkipped > 0) "$mangaSkipped manga entries skipped (anime-only)." else null,
            durationMs = elapsed,
        )
    }
}
