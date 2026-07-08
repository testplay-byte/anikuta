package app.anikuta.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.CacheManager
import app.anikuta.source.bridge.AniyomiSourceBridge
import app.anikuta.source.bridge.SourceMatchResult
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.model.SAnime
import app.anikuta.source.api.model.SEpisode
import app.anikuta.source.api.model.Video
import app.anikuta.ui.library.LibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ViewModel for the anime detail page.
 *
 * Two-stage load:
 *  1. Fetch full AniList metadata via CacheManager (24h TTL).
 *  2. On metadata success, search installed extension sources for a matching
 *     SAnime (via [AniyomiSourceBridge], fuzzy ≥80% title match). If matched,
 *     fetch the episode list from that source.
 *
 * Episode playback: [resolveVideoUrl] fetches the video list for a given
 * episode on demand (not upfront — it's a network call per episode) and
 * returns the best video URL for the player.
 *
 * Phase 5 task 5.3: replaces the old "No streaming source available" +
 * "Play sample" placeholder with real extension-sourced episodes. The sample
 * stream button remains as a fallback when no extension matches.
 */
class DetailViewModel(
    private val anilistId: Int,
) : ViewModel() {

    private val anilistRepo: AniListRepository? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val cacheManager: CacheManager? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val sourceBridge: AniyomiSourceBridge? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val libraryStore: LibraryStore? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val json = Json { ignoreUnknownKeys = true }

    private val _anime = MutableStateFlow<DetailState>(DetailState.Loading)
    val anime: StateFlow<DetailState> = _anime.asStateFlow()

    // Initial value read from the LibraryStore so the bookmark icon reflects
    // the persisted state immediately on screen entry (Phase 5 task 5.6).
    private val _isSaved = MutableStateFlow(libraryStore?.isSaved(anilistId) ?: false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _episodes = MutableStateFlow<EpisodeState>(EpisodeState.Idle)
    val episodes: StateFlow<EpisodeState> = _episodes.asStateFlow()

    /** Resolved video URL for the player (set by resolveVideoUrl, consumed by the UI). */
    private val _playRequest = MutableStateFlow<PlayRequest?>(null)
    val playRequest: StateFlow<PlayRequest?> = _playRequest.asStateFlow()

    /** True while resolving an episode's video URL (extension network call). */
    private val _resolvingEpisode = MutableStateFlow(false)
    val resolvingEpisode: StateFlow<Boolean> = _resolvingEpisode.asStateFlow()

    /** Current pull-to-refresh stage (for the 3-stage indicator). */
    private val _refreshStage = MutableStateFlow<RefreshStage>(RefreshStage.Idle)
    val refreshStage: StateFlow<RefreshStage> = _refreshStage.asStateFlow()

    /** True while any refresh is in progress. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Videos available for the current episode — shown in the quality picker. */
    private val _videoPicker = MutableStateFlow<VideoPickerState>(VideoPickerState.Hidden)
    val videoPicker: StateFlow<VideoPickerState> = _videoPicker.asStateFlow()

    /** Expanded server section in the picker (key = serverName). Empty = all collapsed.
     *  Accordion behavior: only one server expanded at a time. */
    private val _expandedServers = MutableStateFlow<Set<String>>(emptySet())
    val expandedServers: StateFlow<Set<String>> = _expandedServers.asStateFlow()

    /**
     * Toggle a server's expand state. Accordion: if expanding, collapse all
     * others first. This ensures only one server is open at a time.
     */
    fun toggleServer(key: String) {
        _expandedServers.value = _expandedServers.value.let { current ->
            if (key in current) {
                // Already expanded → collapse it
                current - key
            } else {
                // Expanding → close all others, open only this one (accordion)
                setOf(key)
            }
        }
    }

    // Remember the matched source + SAnime so we can fetch episodes/videos later.
    private var matchedSource: AnimeCatalogueSource? = null
    private var matchedSAnime: SAnime? = null

    /**
     * Episode list cache — avoids re-fetching from the extension every time
     * the detail page is re-opened. Keyed by AniList ID. The user reported
     * that re-opening the detail page re-fetched the entire episode list,
     * which was slow + wasted network. Now we cache it in a companion object
     * (survives ViewModel destruction when navigating away + back).
     *
     * Phase 7: on cache hit, we now ALSO launch a background soft-refresh
     * to check for new episodes. The cached list is shown instantly; if the
     * background refresh finds changes, the UI updates smoothly.
     */
    companion object {
        private const val TAG = "DetailViewModel"
        private const val REFRESH_GUARD_MS = 5 * 60 * 1000L  // 5 minutes
        private const val VIDEO_CACHE_TTL_MS = 10 * 60 * 1000L  // 10 minutes
        private val episodeCache = mutableMapOf<Int, Pair<List<SEpisode>, String>>()
        /** Phase 7: short-TTL video cache. Keyed by episode.url. */
        private data class CachedVideoData(val serverSections: List<ServerSection>, val timestamp: Long)
        private val videoCache = mutableMapOf<String, CachedVideoData>()
    }

    private val preferenceStore: app.anikuta.core.preference.PreferenceStore? = try { Injekt.get() } catch (e: Exception) { null }

    init {
        loadAnimeDetails()
    }

    private fun loadAnimeDetails() {
        viewModelScope.launch {
            _anime.value = DetailState.Loading
            try {
                if (cacheManager == null || anilistRepo == null) {
                    _anime.value = DetailState.Error("App not properly initialized")
                    return@launch
                }
                val data = cacheManager.getOrFetch(
                    key = "anime_detail_$anilistId",
                    ttlMs = CacheManager.TTL_DETAIL_LONG,
                    supabaseKey = "anime_$anilistId",
                    fetch = { anilistRepo.getAnimeDetails(anilistId) },
                    serialize = { json.encodeToString(AniListAnime.serializer(), it) },
                    deserialize = { json.decodeFromString(AniListAnime.serializer(), it) },
                )
                if (data != null) {
                    _anime.value = DetailState.Success(data)
                    // Stage 2: now that we have the title, search extensions.
                    findEpisodeSource(data)
                } else {
                    _anime.value = DetailState.Error("Failed to load anime details")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Details fetch failed", e)
                _anime.value = DetailState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Search installed extensions for a matching source via the bridge.
     * On match, fetch the episode list. On no-match / not-aired, set the
     * appropriate state so the UI shows the right message.
     *
     * Phase 7: on in-memory cache hit, we now ALSO launch a background
     * soft-refresh to check for new episodes.
     */
    private fun findEpisodeSource(anime: AniListAnime) {
        // Check in-memory cache first (survives navigation, not app restart)
        episodeCache[anilistId]?.let { (eps, sourceName) ->
            Log.d(TAG, "Episode cache hit (in-memory): ${eps.size} episodes from $sourceName")
            _episodes.value = EpisodeState.Loaded(eps, sourceName)
            // Phase 7: launch background soft-refresh (guarded by 5-min TTL)
            backgroundRefreshEpisodes(anime, sourceName)
            return
        }
        val bridge = sourceBridge ?: run {
            _episodes.value = EpisodeState.Error("Source bridge not available")
            return
        }
        // Check persistent cache for the source match (survives app restart).
        // We cache the source name so we can skip the extension search (the
        // slow part). Episodes still need to be fetched from the extension.
        val cachedSourceName = preferenceStore?.getString("ext_match_$anilistId", "")?.get()
        val cachedSAnimeUrl = preferenceStore?.getString("ext_sanime_url_$anilistId", "")?.get()
        if (!cachedSourceName.isNullOrBlank() && !cachedSAnimeUrl.isNullOrBlank()) {
            Log.d(TAG, "Persistent cache hit: source=$cachedSourceName, fetching episodes...")
            _episodes.value = EpisodeState.Searching
            viewModelScope.launch {
                try {
                    val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
                    // Wait for the source manager to finish loading extensions.
                    // On app restart, the manager loads extensions async — if we
                    // check getCatalogueSources() before it's done, we get an
                    // empty list → "No streaming source" even though the
                    // extension IS installed. Wait up to 10 seconds.
                    var retries = 0
                    var source = mgr.getCatalogueSources().find { it.name == cachedSourceName }
                    while (source == null && retries < 20) {
                        kotlinx.coroutines.delay(500)
                        retries++
                        source = mgr.getCatalogueSources().find { it.name == cachedSourceName }
                    }
                    if (source != null) {
                        Log.d(TAG, "Cached source found after $retries retries")
                        val sAnime = app.anikuta.source.api.model.SAnime.create().apply {
                            url = cachedSAnimeUrl
                            title = anime.title.preferred()
                        }
                        loadEpisodes(sAnime, anime, cachedSourceName)
                    } else {
                        // Source not found after 10s — extension may be uninstalled
                        Log.w(TAG, "Cached source '$cachedSourceName' not found after 10s, falling back to search")
                        searchExtensions(anime, bridge)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Cached source lookup failed, falling back to search", e)
                    searchExtensions(anime, bridge)
                }
            }
            return
        }
        searchExtensions(anime, bridge)
    }

    private fun searchExtensions(anime: AniListAnime, bridge: AniyomiSourceBridge) {
        _episodes.value = EpisodeState.Searching
        viewModelScope.launch {
            try {
                // Wait for source manager to have sources loaded (same fix as
                // the persistent cache path — on first app launch, extensions
                // load async and getCatalogueSources() returns empty)
                val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
                var retries = 0
                while (mgr.getCatalogueSources().isEmpty() && retries < 20) {
                    kotlinx.coroutines.delay(500)
                    retries++
                }
                if (retries > 0) {
                    Log.d(TAG, "Waited ${retries * 500}ms for ${mgr.getCatalogueSources().size} sources to load")
                }
                val result = bridge.findMatch(anime)
                when (result) {
                    is SourceMatchResult.NotAired -> {
                        _episodes.value = EpisodeState.NotAired(
                            result.title,
                            result.seasonYear,
                            result.season,
                        )
                    }
                    is SourceMatchResult.NoMatch -> {
                        _episodes.value = EpisodeState.NoMatch(result.searchedTitle)
                    }
                    is SourceMatchResult.SingleMatch -> {
                        loadEpisodes(result.sAnime, anime, result.sourceName)
                    }
                    is SourceMatchResult.MultipleMatches -> {
                        // Auto-pick the best (Q2 decision). User can override later.
                        loadEpisodes(result.best, anime, result.bestSourceName)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Source bridge failed", e)
                _episodes.value = EpisodeState.Error(e.message ?: "Failed to search extensions")
            }
        }
    }

    /**
     * Fetch the episode list from the matched source. We need to find the
     * source object from the sourceManager to call getEpisodeList.
     */
    private suspend fun loadEpisodes(
        sAnime: SAnime,
        anime: AniListAnime,
        sourceName: String,
    ) {
        try {
            val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
            // The matched source should be a catalogue source — find it by name.
            val source = mgr.getCatalogueSources().find { it.name == sourceName }
            if (source == null) {
                _episodes.value = EpisodeState.Error("Source '$sourceName' not found")
                return
            }
            matchedSource = source
            matchedSAnime = sAnime
            _episodes.value = EpisodeState.LoadingEpisodes
            val eps = withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }
            Log.d(TAG, "Loaded ${eps.size} episodes from '$sourceName'")
            // Cache in-memory (survives navigation)
            episodeCache[anilistId] = Pair(eps, sourceName)
            // Cache source match persistently (survives app restart)
            preferenceStore?.getString("ext_match_$anilistId", "")?.set(sourceName)
            preferenceStore?.getString("ext_sanime_url_$anilistId", "")?.set(sAnime.url)
            _episodes.value = EpisodeState.Loaded(eps, sourceName)
        } catch (e: Exception) {
            Log.e(TAG, "Episode list fetch failed", e)
            _episodes.value = EpisodeState.Error(e.message ?: "Failed to load episodes")
        }
    }

    /**
     * Resolve the video URL for an episode. Called when the user taps an episode.
     *
     * Phase 7: checks the short-TTL video cache first. On cache hit, shows the
     * picker INSTANTLY with a "Refreshing…" badge and launches a background
     * re-resolve. On cache miss, shows the "Resolving video…" overlay.
     *
     * Videos are grouped by audio version (SUB/DUB/HSUB) using [groupVideosByAudio],
     * with qualities sorted descending within each server section.
     */
    fun playEpisode(episode: SEpisode) {
        val source = matchedSource ?: run {
            _playRequest.value = PlayRequest.Error("No source available")
            return
        }

        // Phase 7: check video cache (10-min TTL)
        val cached = videoCache[episode.url]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.timestamp < VIDEO_CACHE_TTL_MS) {
            Log.d(TAG, "Video cache hit (age=${(now - cached.timestamp) / 1000}s): ${cached.serverSections.size} server sections")
            // Show picker instantly with cached data + refreshing badge
            _videoPicker.value = VideoPickerState.Cached(episode, cached.serverSections, isRefreshing = true)
            // Background re-resolve for smooth update
            backgroundResolveVideos(episode, source)
            return
        }

        // Cache miss — show resolving overlay
        _resolvingEpisode.value = true
        _videoPicker.value = VideoPickerState.Resolving(episode)
        viewModelScope.launch {
            try {
                val audioSections = resolveVideos(episode, source)
                if (audioSections.isEmpty()) {
                    _videoPicker.value = VideoPickerState.Hidden
                    _playRequest.value = PlayRequest.Error("No playable video found for this episode")
                } else if (audioSections.flatMap { it.audioSections }.flatMap { it.videos }.size == 1) {
                    val v = audioSections.first().audioSections.first().videos.first()
                    _videoPicker.value = VideoPickerState.Hidden
                    _playRequest.value = PlayRequest.Play(
                        url = v.videoUrl,
                        title = episode.name,
                        episodeNumber = episode.episode_number,
                        anilistId = anilistId,
                        episodeUrl = episode.url,
                        videoHeaders = buildHeaders(v),
                    )
                } else {
                    val allVideos = audioSections.flatMap { it.audioSections }.flatMap { it.videos }
                    Log.d(TAG, "${allVideos.size} videos in ${audioSections.size} server section(s) — showing picker")
                    // Cache the result
                    videoCache[episode.url] = CachedVideoData(audioSections, now)
                    _videoPicker.value = VideoPickerState.Show(episode, audioSections)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video resolution failed", e)
                _videoPicker.value = VideoPickerState.Hidden
                _playRequest.value = PlayRequest.Error(e.message ?: "Failed to resolve video")
            } finally {
                _resolvingEpisode.value = false
            }
        }
    }

    /**
     * Phase 7: Background re-resolve for cached videos. Updates the picker
     * smoothly if the new result differs from cached.
     */
    private fun backgroundResolveVideos(episode: SEpisode, source: AnimeCatalogueSource) {
        viewModelScope.launch {
            try {
                val newSections = resolveVideos(episode, source)
                val now = System.currentTimeMillis()
                videoCache[episode.url] = CachedVideoData(newSections, now)
                // Smooth swap: update the picker state. If the data is the same,
                // the UI won't visibly change. If different, new qualities/servers
                // appear in their sorted positions.
                _videoPicker.value = VideoPickerState.Show(episode, newSections)
                Log.d(TAG, "Background re-resolve complete: ${newSections.size} server sections")
            } catch (e: Exception) {
                Log.w(TAG, "Background re-resolve failed (keeping cached data)", e)
                // Keep showing the cached data — just remove the refreshing badge
                val cached = videoCache[episode.url]
                if (cached != null) {
                    _videoPicker.value = VideoPickerState.Show(episode, cached.serverSections)
                }
            }
        }
    }

    /**
     * Fetch videos from the source (getHosterList → getVideoList fallback)
     * and group them by audio version using [groupVideosByAudio].
     */
    private suspend fun resolveVideos(episode: SEpisode, source: AnimeCatalogueSource): List<ServerSection> {
        // Try getHosterList first, fall back to getVideoList on ANY exception.
        val allVideos = try {
            val hosters = withContext(Dispatchers.IO) { source.getHosterList(episode) }
            hosters.mapNotNull { hoster ->
                hoster.videoList?.filter { it.videoUrl.isNotBlank() }
            }.flatten()
        } catch (e: Exception) {
            Log.d(TAG, "getHosterList failed (${e.javaClass.simpleName}: ${e.message}), falling back to getVideoList")
            val flat = withContext(Dispatchers.IO) { source.getVideoList(episode) }
            flat.filter { it.videoUrl.isNotBlank() }
        }
        return groupVideosByServer(allVideos)
    }

    /** User picked a specific video from the quality picker → launch the player. */
    fun playSpecificVideo(video: Video, episode: SEpisode) {
        Log.d(TAG, "User selected: ${video.videoTitle} → ${video.videoUrl}")
        _videoPicker.value = VideoPickerState.Hidden
        _playRequest.value = PlayRequest.Play(
            url = video.videoUrl,
            title = episode.name,
            episodeNumber = episode.episode_number,
            anilistId = anilistId,
            episodeUrl = episode.url,
            videoHeaders = buildHeaders(video),
        )
    }

    /**
     * Build HTTP headers string for MPV's http-header-fields option.
     * Combines the Video's headers with the source's default headers.
     * Format: "Key: Value,Key2: Value2" (commas escaped as \,).
     * Matches aniyomi's setHttpOptions().
     */
    private fun buildHeaders(video: Video): String {
        val headers = video.headers
        return if (headers != null && headers.size > 0) {
            headers.toMultimap()
                .mapValues { it.value.firstOrNull() ?: "" }
                .map { "${it.key}: ${it.value.replace(",", "\\,")}" }
                .joinToString(",")
        } else {
            // Default User-Agent so servers don't block us with 403
            "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
        }
    }

    /** Dismiss the quality picker without playing. */
    fun dismissVideoPicker() {
        _videoPicker.value = VideoPickerState.Hidden
    }

    /** Clear the play request after the UI has consumed it (launched the player). */
    fun consumePlayRequest() {
        _playRequest.value = null
    }

    /**
     * Toggle the saved state for this anime.
     *
     * Phase 5 task 5.6: persists to [LibraryStore] (PreferenceStore-backed
     * map of AniList ID → AniListAnime JSON). When saving, we need the full
     * AniListAnime object — fetched from the current [DetailState.Success].
     * If details haven't loaded yet, the toggle is a no-op (the UI button
     * is only visible after details load, so this shouldn't happen in
     * practice, but we guard anyway).
     */
    fun toggleSaved() {
        val store = libraryStore ?: run {
            Log.w(TAG, "LibraryStore unavailable — toggle is no-op")
            return
        }
        val anime = (_anime.value as? DetailState.Success)?.anime ?: run {
            Log.w(TAG, "Anime not loaded yet — cannot save")
            return
        }
        val nowSaved = !_isSaved.value
        if (nowSaved) {
            store.save(anime)
        } else {
            store.remove(anilistId)
        }
        _isSaved.value = nowSaved
        Log.d(TAG, "Save toggled: $nowSaved (anilistId=$anilistId)")
    }

    /**
     * Phase 7: Background soft-refresh of the episode list.
     * Guarded by [REFRESH_GUARD_MS] (5 min) per anime to avoid hammering.
     * If the refreshed list differs from cached, updates the UI smoothly.
     */
    private fun backgroundRefreshEpisodes(anime: AniListAnime, sourceName: String) {
        val lastRefresh = preferenceStore?.getLong("ext_last_refresh_$anilistId", 0L)?.get() ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastRefresh < REFRESH_GUARD_MS) {
            Log.d(TAG, "Background refresh skipped (within ${REFRESH_GUARD_MS}ms guard)")
            return
        }
        viewModelScope.launch {
            try {
                val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
                val source = mgr.getCatalogueSources().find { it.name == sourceName } ?: return@launch
                val sAnime = matchedSAnime ?: app.anikuta.source.api.model.SAnime.create().apply {
                    url = preferenceStore?.getString("ext_sanime_url_$anilistId", "")?.get() ?: ""
                    title = anime.title.preferred()
                }
                if (sAnime.url.isBlank()) return@launch
                Log.d(TAG, "Background refresh: fetching episodes from $sourceName...")
                val eps = withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }
                // Update cache + UI
                episodeCache[anilistId] = Pair(eps, sourceName)
                matchedSource = source
                matchedSAnime = sAnime
                _episodes.value = EpisodeState.Loaded(eps, sourceName)
                preferenceStore?.getLong("ext_last_refresh_$anilistId", 0L)?.set(now)
                Log.d(TAG, "Background refresh complete: ${eps.size} episodes")
            } catch (e: Exception) {
                Log.w(TAG, "Background refresh failed (keeping cached data)", e)
            }
        }
    }

    // ---- Phase 7: 3-stage pull-to-refresh methods ----

    /** Stage 1: Refresh episodes only (re-fetch from matched source). */
    fun refreshEpisodesOnly() {
        val anime = (_anime.value as? DetailState.Success)?.anime ?: return
        val sourceName = (episodeCache[anilistId]?.second) ?: return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
                val source = mgr.getCatalogueSources().find { it.name == sourceName }
                val sAnime = matchedSAnime ?: return@launch
                if (source != null) {
                    val eps = withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }
                    episodeCache[anilistId] = Pair(eps, sourceName)
                    matchedSource = source
                    _episodes.value = EpisodeState.Loaded(eps, sourceName)
                    preferenceStore?.getLong("ext_last_refresh_$anilistId", 0L)?.set(System.currentTimeMillis())
                    Log.d(TAG, "Refresh episodes: ${eps.size} episodes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh episodes failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Stage 2: Refresh details only (re-fetch AniList metadata, bypass cache). */
    fun refreshDetailsOnly() {
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val repo = anilistRepo ?: return@launch
                val data = withContext(Dispatchers.IO) { repo.getAnimeDetails(anilistId) }
                if (data != null) {
                    _anime.value = DetailState.Success(data)
                    Log.d(TAG, "Refresh details: ${data.title.preferred()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh details failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Stage 3: Refresh everything (episodes + details + source re-match). */
    fun refreshEverything() {
        _isRefreshing.value = true
        // Clear caches to force full re-fetch
        episodeCache.remove(anilistId)
        preferenceStore?.getString("ext_match_$anilistId", "")?.set("")
        preferenceStore?.getString("ext_sanime_url_$anilistId", "")?.set("")
        viewModelScope.launch {
            try {
                val repo = anilistRepo ?: return@launch
                val data = withContext(Dispatchers.IO) { repo.getAnimeDetails(anilistId) }
                if (data != null) {
                    _anime.value = DetailState.Success(data)
                    findEpisodeSource(data)
                    Log.d(TAG, "Refresh everything: re-fetching details + episodes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh everything failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Called by the 3-stage pull-to-refresh when the user releases. */
    fun onRefreshStage(stage: RefreshStage) {
        when (stage) {
            RefreshStage.Episodes -> refreshEpisodesOnly()
            RefreshStage.Details -> refreshDetailsOnly()
            RefreshStage.Everything -> refreshEverything()
            RefreshStage.Idle -> {}
        }
    }
}

sealed class DetailState {
    data object Loading : DetailState()
    data class Success(val anime: AniListAnime) : DetailState()
    data class Error(val message: String) : DetailState()
}

sealed class EpisodeState {
    /** Initial state — nothing searched yet. */
    data object Idle : EpisodeState()
    /** Searching extension sources for a match. */
    data object Searching : EpisodeState()
    /** Found a match, fetching the episode list. */
    data object LoadingEpisodes : EpisodeState()
    /** No extension source had this anime. */
    data class NoMatch(val searchedTitle: String) : EpisodeState()
    /** Anime hasn't aired yet. */
    data class NotAired(val title: String, val seasonYear: Int?, val season: String?) : EpisodeState()
    /** Episodes loaded successfully. */
    data class Loaded(val episodeList: List<SEpisode>, val sourceName: String) : EpisodeState()
    /** Something went wrong. */
    data class Error(val message: String) : EpisodeState()
}

sealed class PlayRequest {
    data class Play(
        val url: String,
        val title: String,
        val episodeNumber: Float,
        val anilistId: Int = -1,
        val episodeUrl: String = "",
        val videoHeaders: String = "",
    ) : PlayRequest()
    data class Error(val message: String) : PlayRequest()
}

/**
 * Video quality picker state. Phase 7 adds [Resolving] and [Cached] for
 * the short-TTL video cache + smooth soft-refresh.
 */
sealed class VideoPickerState {
    data object Hidden : VideoPickerState()
    data class Resolving(val episode: SEpisode) : VideoPickerState()
    data class Cached(val episode: SEpisode, val serverSections: List<ServerSection>, val isRefreshing: Boolean) : VideoPickerState()
    data class Show(val episode: SEpisode, val serverSections: List<ServerSection>) : VideoPickerState()
}

/**
 * A group of videos from the same server/hoster. Each group shows as a
 * section in the quality picker bottom sheet with the server name as a
 * header and the available qualities listed below.
 */
data class ServerGroup(
    val serverName: String,
    val videos: List<Video>,
)

/**
 * Phase 7 — 3-stage pull-to-refresh stages.
 * The user pulls down with increasing distance to unlock higher stages.
 */
enum class RefreshStage(val label: String) {
    Idle(""),
    Episodes("Release to refresh episodes"),
    Details("Release to refresh details"),
    Everything("Release to refresh everything"),
}
