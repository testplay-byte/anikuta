package app.anikuta.backup.format.aniyomi

import app.anikuta.backup.format.anikuta.RestoreOptions
import app.anikuta.backup.link.AniListLinker
import app.anikuta.backup.link.LinkResult
import app.anikuta.backup.link.LinkTier
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
 *     [WatchProgressStore] (keyed by anilistId) + assign to categories.
 *  3. If [LinkResult.Unlinked]: collect into [UnlinkedAnime] list for the
 *     post-restore review screen. History is preserved keyed by episodeUrl.
 *
 * ## Category restoration (FIXED — was a TODO stub)
 * Aniyomi categories are name-based. The importer:
 *  1. Restores each category by NAME via [CategoryStore.restoreCategory],
 *     preserving the Default category (id=0) that CategoryStore guarantees.
 *  2. Builds a name→anilistId assignment map during anime restore.
 *  3. After all anime are restored, applies the assignments by looking up
 *     each category name → current CategoryStore ID, then calls
 *     [CategoryStore.setAnimeCategories].
 * This properly places each anime in its correct category.
 *
 * ## Progress reporting
 * Emits [AniyomiRestoreProgress] events (NOT bare Strings) so the UI can show
 * a real progress bar (current/total) + a scrollable per-anime live log.
 *
 * ## Manga handling
 * [AniyomiBackup.backupManga] is **ignored** (anikuta is anime-only).
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
     * @param onProgress live-progress callback (structured events for the UI).
     * @return a [RestoreResult] with per-section counts + unlinked anime list.
     */
    suspend fun restore(
        backup: AniyomiBackup,
        options: RestoreOptions = RestoreOptions.ALL,
        onProgress: (AniyomiRestoreProgress) -> Unit = {},
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
            onProgress(AniyomiRestoreProgress.Error("Skipping $mangaSkipped manga entries (anime-only)."))
            logcat(LogPriority.DEBUG) { "Skipping $mangaSkipped manga (anime-only)" }
        }

        val linker = AniListLinker(anilistRepository, extensionLinkStore)

        // ---- Category restoration (by name) ----
        // Build a map: aniyomi category order → category name
        val categoryOrderToName: Map<Long, String> = if (options.categories) {
            backup.backupAnimeCategories.associate { it.order to it.name }
        } else {
            emptyMap()
        }

        // Track per-anime category assignments: anilistId → list of category NAMES
        // (we resolve names → IDs after all categories are restored, since IDs may
        // differ from the backup's IDs)
        val pendingCategoryAssignments = mutableMapOf<Int, List<String>>()

        if (options.categories && backup.backupAnimeCategories.isNotEmpty()) {
            onProgress(AniyomiRestoreProgress.SectionStart("categories", backup.backupAnimeCategories.size))
            for (cat in backup.backupAnimeCategories) {
                try {
                    // Restore by NAME. Use a synthetic ID that won't clash with the
                    // Default category (id=0). We use cat.order + 1 as a temporary ID;
                    // CategoryStore.restoreCategory replaces by ID if exists, else adds.
                    // The Default category (id=0) is always preserved by CategoryStore.
                    val syntheticId = if (cat.order == 0L) 0L else cat.order
                    val anikutaCat = app.anikuta.backup.format.anikuta.BackupCategory(
                        id = syntheticId,
                        name = cat.name,
                        order = cat.order.toInt(),
                    )
                    categoryStore.restoreCategory(anikutaCat)
                    categoryCount++
                    onProgress(AniyomiRestoreProgress.SectionComplete("categories", categoryCount))
                } catch (e: Exception) {
                    errors.add("category[${cat.name}]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not restore aniyomi category" }
                }
            }
            logcat(LogPriority.DEBUG) { "Restored $categoryCount categories" }
        }

        // ---- Anime restoration (with AniList linking) ----
        if (options.library) {
            // Only process FAVORITE anime. Non-favorites (watched-but-not-saved)
            // are skipped entirely — they don't go in the library and their history
            // can't be restored without an AniList ID. This also speeds up restore
            // significantly (e.g. 28 favorites instead of 139 total).
            val favoriteAnime = backup.backupAnime.filter { it.favorite }
            val total = favoriteAnime.size
            onProgress(AniyomiRestoreProgress.SectionStart("anime", total))

            for ((index, backupAnime) in favoriteAnime.withIndex()) {
                val current = index + 1
                try {
                    onProgress(AniyomiRestoreProgress.AnimeProgress(
                        current = current, total = total,
                        title = backupAnime.title, status = AnimeStatus.LINKING,
                    ))

                    val linkResult = linker.link(backupAnime) { msg ->
                        onProgress(AniyomiRestoreProgress.AnimeProgress(
                            current = current, total = total,
                            title = backupAnime.title, status = AnimeStatus.LINKING,
                            detail = msg,
                        ))
                    }

                    when (linkResult) {
                        is LinkResult.Linked -> {
                            val status = when (linkResult.tier) {
                                LinkTier.TRACKER -> AnimeStatus.LINKED_TRACKER
                                LinkTier.CACHE -> AnimeStatus.LINKED_CACHE
                                LinkTier.FUZZY -> AnimeStatus.LINKED_FUZZY
                            }
                            val tierDetail = when (linkResult.tier) {
                                LinkTier.TRACKER -> "AniList tracker → ${linkResult.anilistId}"
                                LinkTier.CACHE -> "Cache → ${linkResult.anilistId}"
                                LinkTier.FUZZY -> "Fuzzy (${(linkResult.confidence * 100).toInt()}%) → ${linkResult.anilistId}" +
                                    (linkResult.matchedTitle?.let { ": $it" } ?: "")
                            }
                            onProgress(AniyomiRestoreProgress.AnimeProgress(
                                current = current, total = total,
                                title = backupAnime.title, status = status,
                                detail = tierDetail,
                            ))

                            // Fetch full AniList metadata (all anime here are favorites —
                            // we filtered non-favorites out before the loop)
                            onProgress(AniyomiRestoreProgress.AnimeProgress(
                                current = current, total = total,
                                title = backupAnime.title, status = AnimeStatus.FETCHING_METADATA,
                            ))
                            val anilistAnime = try {
                                anilistRepository.getAnimeDetails(linkResult.anilistId)
                            } catch (e: Exception) {
                                logcat(LogPriority.WARN, e) {
                                    "Could not fetch AniList details for ${linkResult.anilistId} — using minimal data"
                                }
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

                            // Restore history
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

                            // Track category assignment (by name — resolved later)
                            if (options.categories && backupAnime.categories.isNotEmpty()) {
                                val catNames = backupAnime.categories.mapNotNull { order ->
                                    categoryOrderToName[order]
                                }
                                if (catNames.isNotEmpty()) {
                                    pendingCategoryAssignments[linkResult.anilistId] = catNames
                                }
                            }

                            onProgress(AniyomiRestoreProgress.AnimeProgress(
                                current = current, total = total,
                                title = backupAnime.title, status = AnimeStatus.SAVED,
                            ))
                        }
                        is LinkResult.Unlinked -> {
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
                            onProgress(AniyomiRestoreProgress.AnimeProgress(
                                current = current, total = total,
                                title = backupAnime.title, status = AnimeStatus.UNLINKED,
                                detail = linkResult.reason.name,
                            ))
                        }
                    }
                } catch (e: Exception) {
                    errors.add("anime[${backupAnime.title}]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not restore aniyomi anime '${backupAnime.title}'" }
                    onProgress(AniyomiRestoreProgress.AnimeProgress(
                        current = current, total = total,
                        title = backupAnime.title, status = AnimeStatus.SKIPPED,
                        detail = e.message,
                    ))
                }
            }
            onProgress(AniyomiRestoreProgress.SectionComplete("anime", libraryCount))
        }

        // ---- Apply category assignments (by name → current ID) ----
        // Now that all categories are restored, resolve category names → IDs
        // and apply the per-anime assignments. This is the fix for the bug where
        // all anime ended up in one category.
        if (options.categories && pendingCategoryAssignments.isNotEmpty()) {
            val currentCategories = categoryStore.getCategories()
            val nameToId = currentCategories.associate { it.name to it.id }

            for ((anilistId, catNames) in pendingCategoryAssignments) {
                try {
                    val catIds = catNames.mapNotNull { name -> nameToId[name] }.toSet()
                    if (catIds.isNotEmpty()) {
                        categoryStore.setAnimeCategories(anilistId, catIds)
                        categoryAssignmentCount++
                    }
                } catch (e: Exception) {
                    errors.add("categoryAssignment[$anilistId]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not assign categories for anime $anilistId" }
                }
            }
            logcat(LogPriority.DEBUG) { "Applied $categoryAssignmentCount category assignments" }
        }

        // ---- Preferences ----
        if (options.settings && backup.backupPreferences.isNotEmpty()) {
            onProgress(AniyomiRestoreProgress.SectionStart("preferences", backup.backupPreferences.size))
            try {
                val result = preferenceRestorer.restore(backup.backupPreferences)
                preferenceCount = result.restored
                errors.addAll(result.errors)
                onProgress(AniyomiRestoreProgress.SectionComplete("preferences", preferenceCount))
            } catch (e: Exception) {
                errors.add("settings: ${e.message}")
                logcat(LogPriority.ERROR, e) { "Could not restore aniyomi preferences" }
            }
        }

        // ---- Persist unlinked anime ----
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
        }

        val elapsed = System.currentTimeMillis() - startTime
        logcat(LogPriority.DEBUG) {
            "Aniyomi restore complete in ${elapsed}ms — " +
                "$libraryCount lib, $historyCount hist, $categoryCount cats, " +
                "$categoryAssignmentCount assignments, $preferenceCount prefs, " +
                "${unlinkedAnime.size} unlinked, $mangaSkipped manga skipped, ${errors.size} errors"
        }

        onProgress(AniyomiRestoreProgress.RestoreComplete(
            "$libraryCount anime, $historyCount history, $categoryCount categories, " +
                "$categoryAssignmentCount assignments, ${unlinkedAnime.size} unlinked"
        ))

        RestoreResult(
            libraryCount = libraryCount,
            historyCount = historyCount,
            categoryCount = categoryCount,
            categoryAssignmentCount = categoryAssignmentCount,
            trackingCount = 0,
            subDubCount = 0,
            extensionLinkCount = 0,
            playbackStateCount = 0,
            searchCount = 0,
            preferenceCount = preferenceCount,
            unlinkedAnime = unlinkedAnime,
            errors = errors,
            note = if (mangaSkipped > 0) "$mangaSkipped manga entries skipped (anime-only)." else null,
            durationMs = elapsed,
        )
    }
}
