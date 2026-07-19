package app.anikuta.backup.format.anikuta

import app.anikuta.backup.model.RestoreResult
import app.anikuta.backup.prefs.PreferenceRestorer
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.core.util.system.logcat
import app.anikuta.data.cache.ExtensionLinkStore
import app.anikuta.data.cache.ReleaseTrackingStore
import app.anikuta.data.cache.SubDubStore
import app.anikuta.player.PlaybackStateStore
import app.anikuta.player.WatchProgressStore
import app.anikuta.ui.library.CategoryStore
import app.anikuta.ui.library.LibraryStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Restores an [AnikutaBackup] back into app state.
 *
 * This is the "write everything back" step of restore. It applies each section
 * of the backup to its corresponding store, with per-entry error isolation
 * (one bad entry doesn't abort the whole restore).
 *
 * ## Bug fixes vs the old BackupManager.restoreAnikutaBackup()
 *  - **Bug #1 (settings)**: NOW RESTORED via [PreferenceRestorer] with type-guard.
 *    Previously: collected into `Map<String,String>` but never written back.
 *  - **Bug #2 (category assignments)**: NOW RESTORED via
 *    [CategoryStore.restoreAssignments]. Previously: method existed but was
 *    never called.
 *  - **Bug #3 (playback states)**: NOW RESTORED via [PlaybackStateStore.save].
 *    Previously: collected but never written back.
 *  - **Bug #4 (extension-link key split)**: NOW uses `split(":", limit = 2)`
 *    so URLs containing `:` (e.g. `https://...`) don't break the split.
 *    Previously: `split(":")` produced >2 parts for such URLs → silently skipped.
 *  - **Bug #5 (sub/dub lastUpdated)**: NOW uses [SubDubStore.restore] which
 *    preserves the original timestamp. Previously: [SubDubStore.update] was
 *    used, which overwrote `lastUpdated` with `now`.
 *
 * ## Error handling
 * Each section is wrapped in its own try/catch. Per-entry failures are logged
 * at WARN and collected into [RestoreResult.errors], but don't abort the restore.
 * This matches aniyomi's per-entry error isolation pattern.
 *
 * ## Logging
 * Every section logs its progress + count. Tag: `AnikutaRestorer`.
 */
class AnikutaRestorer(
    private val libraryStore: LibraryStore,
    private val watchProgressStore: WatchProgressStore,
    private val categoryStore: CategoryStore,
    private val releaseTrackingStore: ReleaseTrackingStore,
    private val subDubStore: SubDubStore,
    private val extensionLinkStore: ExtensionLinkStore,
    private val playbackStateStore: PlaybackStateStore,
    private val preferenceRestorer: PreferenceRestorer,
) {

    companion object {
        private const val TAG = "AnikutaRestorer"
        private const val RECENT_SEARCHES_KEY = "search_recent_terms"
        private val json = Json { ignoreUnknownKeys = true }
    }

    /**
     * Restore all sections of an [AnikutaBackup] into app state.
     *
     * @param backup the backup to restore.
     * @param options which sections to restore (default: all).
     * @return a [RestoreResult] with per-section counts + any errors.
     */
    suspend fun restore(backup: AnikutaBackup, options: RestoreOptions = RestoreOptions.ALL): RestoreResult {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        var libraryCount = 0
        var historyCount = 0
        var categoryCount = 0
        var categoryAssignmentCount = 0
        var trackingCount = 0
        var subDubCount = 0
        var extensionLinkCount = 0
        var playbackStateCount = 0
        var searchCount = 0
        var preferenceCount = 0

        // Library
        if (options.library) {
            for (anime in backup.library) {
                try {
                    val anilistAnime = app.anikuta.data.anilist.model.AniListAnime(
                        id = anime.id,
                        idMal = anime.idMal,
                        title = app.anikuta.data.anilist.model.AniListTitle(
                            romaji = anime.titleRomaji,
                            english = anime.titleEnglish,
                            native = anime.titleNative,
                        ),
                        coverImage = app.anikuta.data.anilist.model.AniListCoverImage(
                            extraLarge = anime.coverLarge,
                            large = anime.coverMedium,
                            medium = anime.coverMedium, // Bug #6 (cosmetic): medium URL lost in round-trip
                            color = anime.coverColor,
                        ),
                        bannerImage = anime.bannerImage,
                        description = anime.description,
                        averageScore = anime.averageScore,
                        episodes = anime.episodes,
                        genres = anime.genres,
                        season = anime.season,
                        seasonYear = anime.seasonYear,
                        format = anime.format,
                        status = anime.status,
                        nextAiringEpisode = anime.nextAiringEpisode?.let {
                            app.anikuta.data.anilist.model.AniListNextAiring(it.airingAt, it.episode, it.timeUntilAiring)
                        },
                    )
                    libraryStore.save(anilistAnime)
                    libraryCount++
                } catch (e: Exception) {
                    errors.add("library[${anime.id}]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not restore anime ${anime.id}" }
                }
            }
            logcat(LogPriority.DEBUG) { "Restored library: $libraryCount anime" }
        }

        // History
        if (options.history) {
            for (entry in backup.history) {
                try {
                    // Bug #4 fix: split with limit=2 so URLs containing ':' don't break
                    val parts = entry.key.split(":", limit = 2)
                    if (parts.size == 2) {
                        val anilistId = parts[0].toIntOrNull() ?: continue
                        val episodeUrl = parts[1]
                        watchProgressStore.save(
                            anilistId = anilistId,
                            episodeUrl = episodeUrl,
                            positionSeconds = entry.positionSeconds,
                            durationSeconds = entry.durationSeconds,
                            title = entry.title,
                            coverUrl = entry.coverUrl,
                            animeTitle = entry.animeTitle,
                            episodeNumber = entry.episodeNumber,
                            thumbnailUrl = entry.thumbnailUrl,
                        )
                        historyCount++
                    }
                } catch (e: Exception) {
                    errors.add("history[${entry.key}]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not restore history entry" }
                }
            }
            logcat(LogPriority.DEBUG) { "Restored history: $historyCount entries" }
        }

        // Recent searches
        if (options.searches && backup.recentSearches.isNotEmpty()) {
            try {
                val prefs = Injekt.get<PreferenceStore>()
                val recentPref = prefs.getObject(
                    key = RECENT_SEARCHES_KEY,
                    defaultValue = emptyList<String>(),
                    serializer = { terms: List<String> ->
                        json.encodeToString(ListSerializer(String.serializer()), terms)
                    },
                    deserializer = { raw: String ->
                        json.decodeFromString(ListSerializer(String.serializer()), raw)
                    },
                )
                recentPref.set(backup.recentSearches)
                searchCount = backup.recentSearches.size
            } catch (e: Exception) {
                errors.add("searches: ${e.message}")
                logcat(LogPriority.WARN, e) { "Could not restore recent searches" }
            }
            logcat(LogPriority.DEBUG) { "Restored searches: $searchCount terms" }
        }

        // Categories (list)
        if (options.categories) {
            for (cat in backup.categories.categories) {
                try {
                    categoryStore.restoreCategory(
                        BackupCategory(cat.id, cat.name, cat.order),
                    )
                    categoryCount++
                } catch (e: Exception) {
                    errors.add("category[${cat.name}]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not restore category" }
                }
            }
            // Bug #2 fix: NOW restore assignments (was never called before)
            try {
                if (backup.categories.assignments.isNotEmpty()) {
                    categoryStore.restoreAssignments(backup.categories.assignments)
                    categoryAssignmentCount = backup.categories.assignments.size
                }
            } catch (e: Exception) {
                errors.add("categoryAssignments: ${e.message}")
                logcat(LogPriority.WARN, e) { "Could not restore category assignments" }
            }
            logcat(LogPriority.DEBUG) { "Restored categories: $categoryCount cats, $categoryAssignmentCount assignments" }
        }

        // Release tracking
        if (options.tracking) {
            for (tracked in backup.releaseTracking) {
                try {
                    releaseTrackingStore.put(
                        ReleaseTrackingStore.TrackedAnime(
                            anilistId = tracked.anilistId,
                            title = tracked.title,
                            coverUrl = tracked.coverUrl,
                            sourceId = tracked.sourceId,
                            animeUrl = tracked.animeUrl,
                            lastKnownEpisodeCount = tracked.lastKnownEpisodeCount,
                            lastKnownSubCount = tracked.lastKnownSubCount,
                            lastKnownDubCount = tracked.lastKnownDubCount,
                            lastCheckTime = tracked.lastCheckTime,
                            lastSeenAiringAt = tracked.lastSeenAiringAt,
                            nextScheduledCheck = tracked.nextScheduledCheck,
                            isCompleted = tracked.isCompleted,
                            hasPendingDub = tracked.hasPendingDub,
                            subReleaseOffsetMs = tracked.subReleaseOffsetMs,
                            dubReleaseOffsetMs = tracked.dubReleaseOffsetMs,
                            subOffsetSampleCount = tracked.subOffsetSampleCount,
                            dubOffsetSampleCount = tracked.dubOffsetSampleCount,
                            notifyOnNew = tracked.notifyOnNew,
                            notifySub = tracked.notifySub,
                            notifyDub = tracked.notifyDub,
                            autoDownloadNew = tracked.autoDownloadNew,
                            autoDownloadSub = tracked.autoDownloadSub,
                            autoDownloadDub = tracked.autoDownloadDub,
                            autoDownloadQuality = tracked.autoDownloadQuality,
                            autoDownloadAudio = tracked.autoDownloadAudio,
                        ),
                    )
                    trackingCount++
                } catch (e: Exception) {
                    errors.add("tracking[${tracked.anilistId}]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not restore tracking" }
                }
            }
            logcat(LogPriority.DEBUG) { "Restored tracking: $trackingCount entries" }
        }

        // Sub/Dub cache
        if (options.tracking) { // sub/dub grouped with tracking (both are notification-related)
            for ((key, info) in backup.subDubCache) {
                try {
                    val anilistId = key.toIntOrNull() ?: continue
                    // Bug #5 fix: use restore() which preserves lastUpdated
                    subDubStore.restore(
                        anilistId = anilistId,
                        hasSub = info.hasSub,
                        hasDub = info.hasDub,
                        subCount = info.subCount,
                        dubCount = info.dubCount,
                        totalEpisodes = info.totalEpisodes,
                        lastUpdated = info.lastUpdated,
                    )
                    subDubCount++
                } catch (e: Exception) {
                    errors.add("subDub[$key]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not restore sub/dub" }
                }
            }
            logcat(LogPriority.DEBUG) { "Restored sub/dub: $subDubCount entries" }
        }

        // Extension links
        if (options.library) { // extension links grouped with library
            for ((key, anilistId) in backup.extensionLinks) {
                try {
                    // Bug #4 fix: split with limit=2
                    val parts = key.split(":", limit = 2)
                    if (parts.size == 2) {
                        extensionLinkStore.link(parts[0].toLong(), parts[1], anilistId)
                        extensionLinkCount++
                    }
                } catch (e: Exception) {
                    errors.add("extLink[$key]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not restore extension link" }
                }
            }
            logcat(LogPriority.DEBUG) { "Restored extension links: $extensionLinkCount entries" }
        }

        // Playback states (Bug #3 fix: NOW restored — was never written back before)
        if (options.history) { // playback states grouped with history (both are resume-related)
            for ((key, state) in backup.playbackStates) {
                try {
                    val parts = key.split(":", limit = 2)
                    if (parts.size == 2) {
                        val anilistId = parts[0].toIntOrNull() ?: continue
                        val episodeUrl = parts[1]
                        playbackStateStore.save(
                            anilistId = anilistId,
                            episodeUrl = episodeUrl,
                            videoUrl = state.videoUrl,
                            videoServer = state.videoServer,
                            videoAudio = state.videoAudio,
                            videoQuality = state.videoQuality,
                            videoHeaders = state.videoHeaders,
                            audioTrackId = state.audioTrackId,
                            subtitleTrackId = state.subtitleTrackId,
                            sourceId = state.sourceId,
                        )
                        playbackStateCount++
                    }
                } catch (e: Exception) {
                    errors.add("playback[$key]: ${e.message}")
                    logcat(LogPriority.WARN, e) { "Could not restore playback state" }
                }
            }
            logcat(LogPriority.DEBUG) { "Restored playback states: $playbackStateCount entries" }
        }

        // Settings (Bug #1 fix: NOW restored via typed PreferenceRestorer)
        if (options.settings) {
            try {
                val result = preferenceRestorer.restore(backup.settings)
                preferenceCount = result.restored
                errors.addAll(result.errors)
                logcat(LogPriority.DEBUG) {
                    "Restored preferences: ${result.restored} restored, ${result.skipped} skipped"
                }
            } catch (e: Exception) {
                errors.add("settings: ${e.message}")
                logcat(LogPriority.ERROR, e) { "Could not restore preferences" }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        val skippedSections = options.skippedSections()

        logcat(LogPriority.DEBUG) {
            "AniKuta restore complete in ${elapsed}ms — " +
                "$libraryCount lib, $historyCount hist, $categoryCount cats, " +
                "$trackingCount track, $subDubCount sd, $extensionLinkCount links, " +
                "$playbackStateCount pb, $searchCount search, $preferenceCount prefs, " +
                "${errors.size} errors"
        }

        return RestoreResult(
            libraryCount = libraryCount,
            historyCount = historyCount,
            categoryCount = categoryCount,
            categoryAssignmentCount = categoryAssignmentCount,
            trackingCount = trackingCount,
            subDubCount = subDubCount,
            extensionLinkCount = extensionLinkCount,
            playbackStateCount = playbackStateCount,
            searchCount = searchCount,
            preferenceCount = preferenceCount,
            errors = errors,
            skippedSections = skippedSections,
            durationMs = elapsed,
        )
    }
}

/**
 * Options controlling which sections of a backup to restore.
 *
 * Allows the user to selectively restore just library, just settings, etc.
 * Mirrors aniyomi's `RestoreOptions` boolean array.
 *
 * Use [RestoreOptions.ALL] to restore everything (default), or build a custom
 * set via copy().
 */
data class RestoreOptions(
    val library: Boolean = true,
    val history: Boolean = true,
    val searches: Boolean = true,
    val categories: Boolean = true,
    val tracking: Boolean = true,
    val settings: Boolean = true,
) {
    companion object {
        /** Restore everything (default). */
        val ALL = RestoreOptions()
    }

    /** Returns the names of sections that are disabled (for the result summary). */
    fun skippedSections(): Set<String> {
        val skipped = mutableSetOf<String>()
        if (!library) skipped.add("library")
        if (!history) skipped.add("history")
        if (!searches) skipped.add("searches")
        if (!categories) skipped.add("categories")
        if (!tracking) skipped.add("tracking")
        if (!settings) skipped.add("settings")
        return skipped
    }
}
