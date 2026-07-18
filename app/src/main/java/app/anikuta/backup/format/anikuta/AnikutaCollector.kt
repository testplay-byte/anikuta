package app.anikuta.backup.format.anikuta

import app.anikuta.backup.model.BackupPreference
import app.anikuta.backup.prefs.PreferenceCollector
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
 * Collects all app state into an [AnikutaBackup] for export.
 *
 * This is the "gather everything" step of backup creation. It reads from every
 * SharedPreferences-backed store and assembles a complete [AnikutaBackup].
 *
 * Each data source is collected independently — adding a new source = adding
 * a new field to [AnikutaBackup] + a new collect method here.
 *
 * ## Data sources
 *  - [LibraryStore]            → [AnikutaBackup.library]
 *  - [WatchProgressStore]      → [AnikutaBackup.history]
 *  - recent searches pref      → [AnikutaBackup.recentSearches]
 *  - [CategoryStore]           → [AnikutaBackup.categories]
 *  - [PreferenceCollector]     → [AnikutaBackup.settings] (typed, type-safe)
 *  - [ReleaseTrackingStore]    → [AnikutaBackup.releaseTracking]
 *  - [SubDubStore]             → [AnikutaBackup.subDubCache]
 *  - [ExtensionLinkStore]      → [AnikutaBackup.extensionLinks]
 *  - [PlaybackStateStore]      → [AnikutaBackup.playbackStates]
 *
 * ## Logging
 * Each section logs its count at DEBUG. The final summary logs the total.
 * Tag: `AnikutaCollector`.
 */
class AnikutaCollector(
    private val libraryStore: LibraryStore,
    private val watchProgressStore: WatchProgressStore,
    private val categoryStore: CategoryStore,
    private val releaseTrackingStore: ReleaseTrackingStore,
    private val subDubStore: SubDubStore,
    private val extensionLinkStore: ExtensionLinkStore,
    private val playbackStateStore: PlaybackStateStore,
    private val preferenceCollector: PreferenceCollector,
) {

    companion object {
        private const val TAG = "AnikutaCollector"
        /** SharedPreferences key for recent search terms (must match SearchViewModel.RECENT_KEY). */
        private const val RECENT_SEARCHES_KEY = "search_recent_terms"
        private val json = Json { ignoreUnknownKeys = true }
    }

    /**
     * Gather all app state into an [AnikutaBackup].
     *
     * @param includePrivatePreferences if `true`, `__PRIVATE_*` prefs are included.
     *   Default `false` (tokens/credentials excluded).
     */
    suspend fun collect(includePrivatePreferences: Boolean = false): AnikutaBackup {
        val startTime = System.currentTimeMillis()
        logcat(LogPriority.DEBUG) { "Collecting AniKuta backup..." }

        // Library
        val library = libraryStore.getAll().map { anime ->
            BackupLibraryAnime(
                id = anime.id,
                titleRomaji = anime.title.romaji,
                titleEnglish = anime.title.english,
                titleNative = anime.title.native,
                coverLarge = anime.coverImage.extraLarge,
                coverMedium = anime.coverImage.large,
                coverColor = anime.coverImage.color,
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
                    BackupNextAiring(it.airingAt, it.episode, it.timeUntilAiring)
                },
                idMal = anime.idMal,
            )
        }
        logcat(LogPriority.DEBUG) { "  library: ${library.size} anime" }

        // History
        val history = watchProgressStore.getAll().map { (key, progress) ->
            BackupHistoryEntry(
                key = key,
                positionSeconds = progress.positionSeconds,
                durationSeconds = progress.durationSeconds,
                title = progress.title,
                updatedAt = progress.updatedAt,
                coverUrl = progress.coverUrl,
                animeTitle = progress.animeTitle,
                episodeNumber = progress.episodeNumber,
                thumbnailUrl = progress.thumbnailUrl,
            )
        }
        logcat(LogPriority.DEBUG) { "  history: ${history.size} entries" }

        // Recent searches
        val recentSearches = collectRecentSearches()
        logcat(LogPriority.DEBUG) { "  recentSearches: ${recentSearches.size} terms" }

        // Categories (list + assignments)
        val categoryState = categoryStore.getStateSnapshot()
        val categories = BackupCategories(
            categories = categoryState.categories.map {
                BackupCategory(it.id, it.name, it.order)
            },
            assignments = categoryState.assignments.mapValues { (_, set) -> set.toList() },
        )
        logcat(LogPriority.DEBUG) { "  categories: ${categories.categories.size} cats, ${categories.assignments.size} assignments" }

        // Settings (typed preferences — Bug #1 fix)
        val settings: List<BackupPreference> = preferenceCollector.collect(includePrivatePreferences)
        logcat(LogPriority.DEBUG) { "  settings: ${settings.size} prefs" }

        // Release tracking
        val releaseTracking = releaseTrackingStore.getAll().values.map { tracked ->
            BackupTrackedAnime(
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
            )
        }
        logcat(LogPriority.DEBUG) { "  releaseTracking: ${releaseTracking.size} tracked anime" }

        // Sub/Dub cache
        val subDubCache = subDubStore.getAll().mapValues { (_, info) ->
            BackupSubDubInfo(
                hasSub = info.hasSub,
                hasDub = info.hasDub,
                subCount = info.subCount,
                dubCount = info.dubCount,
                totalEpisodes = info.totalEpisodes,
                lastUpdated = info.lastUpdated,
            )
        }
        logcat(LogPriority.DEBUG) { "  subDubCache: ${subDubCache.size} entries" }

        // Extension links
        val extensionLinks = extensionLinkStore.getAll()
        logcat(LogPriority.DEBUG) { "  extensionLinks: ${extensionLinks.size} links" }

        // Playback states
        val playbackStates = playbackStateStore.getAll().mapValues { (_, state) ->
            BackupPlaybackState(
                videoUrl = state.videoUrl,
                videoServer = state.videoServer,
                videoAudio = state.videoAudio,
                videoQuality = state.videoQuality,
                videoHeaders = state.videoHeaders,
                audioTrackId = state.audioTrackId,
                subtitleTrackId = state.subtitleTrackId,
                sourceId = state.sourceId,
                updatedAt = state.updatedAt,
            )
        }
        logcat(LogPriority.DEBUG) { "  playbackStates: ${playbackStates.size} entries" }

        val backup = AnikutaBackup(
            library = library,
            history = history,
            recentSearches = recentSearches,
            categories = categories,
            settings = settings,
            releaseTracking = releaseTracking,
            subDubCache = subDubCache,
            extensionLinks = extensionLinks,
            playbackStates = playbackStates,
        )

        val elapsed = System.currentTimeMillis() - startTime
        logcat(LogPriority.DEBUG) {
            "AniKuta backup collected in ${elapsed}ms — v${backup.version}, " +
                "${library.size} anime, ${history.size} history, ${settings.size} prefs"
        }

        return backup
    }

    /**
     * Read the recent-search-terms preference.
     *
     * The key [RECENT_SEARCHES_KEY] must match `SearchViewModel.RECENT_KEY`.
     * If the key changes in SearchViewModel, update it here too.
     */
    private fun collectRecentSearches(): List<String> {
        return try {
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
            recentPref.get()
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Could not read recent searches: ${e.message}" }
            emptyList()
        }
    }
}
